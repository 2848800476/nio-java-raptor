package cn.com.sparkle.raptor.core.transport.socket.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.log4j.Logger;

import cn.com.sparkle.raptor.core.buff.AbstractIoBuffer;
import cn.com.sparkle.raptor.core.buff.AllocateBytesBuff;
import cn.com.sparkle.raptor.core.buff.CycleAllocateBytesBuffPool;
import cn.com.sparkle.raptor.core.buff.CycleBuff;
import cn.com.sparkle.raptor.core.buff.IoBuffer;
import cn.com.sparkle.raptor.core.collections.LastAccessTimeLinkedList;
import cn.com.sparkle.raptor.core.collections.LastAccessTimeLinkedList.Entity;
import cn.com.sparkle.raptor.core.collections.MaximumSizeArrayCycleQueue.Bulk;
import cn.com.sparkle.raptor.core.collections.MaximumSizeArrayCycleQueue;
import cn.com.sparkle.raptor.core.collections.Queue;
import cn.com.sparkle.raptor.core.delaycheck.DelayChecked;
import cn.com.sparkle.raptor.core.delaycheck.DelayCheckedTimer;
import cn.com.sparkle.raptor.core.protocol.MultiThreadProtecolHandler.ProtocolHandlerIoSession;
import cn.com.sparkle.raptor.core.util.TimeUtil;

public class NioSocketProcessor {
	private Logger logger = Logger.getLogger(NioSocketProcessor.class);
	
//	public volatile boolean debug = false;

	private Selector selector;
	private ReentrantLock lock = new ReentrantLock();

	private Queue<IoSession> registerQueueWrite = new MaximumSizeArrayCycleQueue<IoSession>(
			IoSession.class, 100000);
	private Queue<ReadInterest> registerQueueRead = new MaximumSizeArrayCycleQueue<ReadInterest>(
			ReadInterest.class, 100000);
	private Queue<IoSession> reRegisterQueueWrite = new MaximumSizeArrayCycleQueue<IoSession>(
			IoSession.class, 300000);
	private CycleAllocateBytesBuffPool memPool;

	private final static class ReadInterest {
		private IoSession session;
		private boolean isInterest;

		public ReadInterest(IoSession session, boolean isInterest) {
			super();
			this.session = session;
			this.isInterest = isInterest;
		}

	}

	// private RecieveMessageDealer recieveMessageDealer;

	private LastAccessTimeLinkedList<IoSession> activeSessionLinedLinkedList = new LastAccessTimeLinkedList<IoSession>();

	private NioSocketConfigure nscfg;

	private DelayChecked checkRegisterRead;
	private DelayChecked checkRegisterWrite;
	private DelayChecked checkReRegisterWrite;
	private DelayChecked checkTimeoutSession;

	public NioSocketProcessor(NioSocketConfigure nscfg) throws IOException {

		this.nscfg = nscfg;
		selector = Selector.open();
		memPool = new CycleAllocateBytesBuffPool(
				nscfg.getCycleRecieveBuffCellSize(),
				nscfg.getRecieveBuffSize() * 2 / 3);
		// recieveMessageDealer = new RecieveMessageDealer(
		// nscfg.getCycleRecieveBuffCellSize());
		// recieveMessageDealer.setDaemon(true);
		// recieveMessageDealer.start();

		Thread t = new Thread(new Processor());
		t.setDaemon(true);
		t.start();
		checkRegisterRead = new DelayChecked(nscfg.getRegisterReadDelay()) {
			@Override
			public void goToRun() {
				selector.wakeup();
			}
		};
		checkRegisterWrite = new DelayChecked(nscfg.getRegisterWriteDelay()) {

			@Override
			public void goToRun() {
				selector.wakeup();
			}
		};
		checkReRegisterWrite = new DelayChecked(nscfg.getReRegisterWriteDelay()) {
			@Override
			public void goToRun() {
				selector.wakeup();
			}
		};
		checkTimeoutSession = new DelayChecked(
				nscfg.getClearTimeoutSessionInterval(), true) {
			@Override
			public void goToRun() {
				selector.wakeup();
			}
		};
		DelayCheckedTimer.addDelayCheck(checkRegisterRead);
		DelayCheckedTimer.addDelayCheck(checkRegisterWrite);
		DelayCheckedTimer.addDelayCheck(checkReRegisterWrite);
		DelayCheckedTimer.addDelayCheck(checkTimeoutSession);

		checkTimeoutSession.needRun();
	}

	public void unRegisterRead(IoSession session) {
		while (true) {
			try {
				registerQueueRead.push(new ReadInterest(session, false));
				checkRegisterRead.needRun();
				break;
			} catch (Exception e) {
				logger.debug(e);
				try {
					Thread.sleep(10);
				} catch (InterruptedException e1) {
				}
			}
		}
	}

	public void registerRead(IoSession session) {
		while (true) {
			try {
				registerQueueRead.push(new ReadInterest(session, true));
				checkRegisterRead.needRun();
				break;
			} catch (Exception e) {
				try {
					Thread.sleep(10);
				} catch (InterruptedException e1) {
				}
			}
		}
	}

	public void registerWrite(IoSession session) {
		try {
			lock.lock();
			while (true) {
				try {
					registerQueueWrite.push(session);
					checkRegisterWrite.needRun();
					break;
				} catch (Exception e) {
					logger.debug(e);
					try {
						Thread.sleep(10);
					} catch (InterruptedException e1) {
					}
				}
			}
		} finally {
			lock.unlock();
		}
	}

	// public ReentrantLock getLock() {
	// return lock;
	// }

	
	private boolean changeInterestWrite(SelectionKey key, boolean isInterest) {
//		if(debug){
//			logger.debug(isInterest);
//		}
		int i = key.interestOps();
		if (isInterest) {
			if ((i & SelectionKey.OP_WRITE) == 0) {
				key.interestOps(i | SelectionKey.OP_WRITE);
				return true;
			} else
				return false;
		} else {

			if ((i & SelectionKey.OP_WRITE) != 0) {
				key.interestOps(i ^ SelectionKey.OP_WRITE);
				return true;
			} else
				return false;
		}
	}

	private boolean interestRead(SelectionKey key, boolean isInterest) {
		int i = key.interestOps();
		if (isInterest) {
			if ((i & SelectionKey.OP_READ) == 0) {
				key.interestOps(i | SelectionKey.OP_READ);
				return true;
			} else
				return false;
		} else {

			if ((i & SelectionKey.OP_READ) != 0) {
				key.interestOps(i ^ SelectionKey.OP_READ);
				return true;
			} else
				return false;
		}
	}

	class Processor implements Runnable {
		public void run() {
			int readSize = 0;
			boolean isClearWrite = false;
			IoSession session;
			while (true) {
				int i;
				try {
					i = selector.select(1);
				} catch (Throwable e) {
					throw new RuntimeException(e);
				}
				while ((session = (IoSession) registerQueueWrite.peek()) != null) {
					registerQueueWrite.poll();
					if (session.peekWaitSendBulk() == null){
						continue;// if peek() return null indicates the
									// message have proceeded in last send
									// process.
					}
					SelectionKey key = session.getChannel().keyFor(selector);
					if (key == null) {
						if (session.isClose()) {
							continue;
						}
						try {

							key = session.getChannel().register(selector,
									SelectionKey.OP_WRITE);
							key.attach(session);
							activeSessionLinedLinkedList.putOrMoveFirst(session
									.getLastAccessTimeLinkedListwrapSession());
						} catch (ClosedChannelException e) {
							activeSessionLinedLinkedList.remove(session
									.getLastAccessTimeLinkedListwrapSession());
							session.getHandler().catchException(session, e);
							session.closeSession();
						}
					} else {
						key.attach(session);
						changeInterestWrite(key, true);
//						if(debug){
//							logger.debug("change to write");
//						}
						activeSessionLinedLinkedList.putOrMoveFirst(session
								.getLastAccessTimeLinkedListwrapSession());
					}
				}
				ReadInterest readInterest = null;
				while ((readInterest = (ReadInterest) registerQueueRead.peek()) != null) {
					session = readInterest.session;
					SelectionKey key = session.getChannel().keyFor(selector);
					registerQueueRead.poll();
					if (key == null) {
						if (readInterest.isInterest) {
							try {
								key = session.getChannel().register(selector,
										SelectionKey.OP_READ);
								key.attach(session);

								activeSessionLinedLinkedList
										.putOrMoveFirst(session
												.getLastAccessTimeLinkedListwrapSession());
							} catch (ClosedChannelException e) {
								activeSessionLinedLinkedList
										.remove(session
												.getLastAccessTimeLinkedListwrapSession());
								session.getHandler().catchException(session, e);
								session.closeSession();
							}
						}
					} else {
						interestRead(key, readInterest.isInterest);
						activeSessionLinedLinkedList.putOrMoveFirst(session
								.getLastAccessTimeLinkedListwrapSession());
					}
				}
				// 检查reRegisterQueueWrite是否有已经可以激活的发送session如果有则注册写事件
				long now = TimeUtil.currentTimeMillis();
				while ((session = reRegisterQueueWrite.peek()) != null) {
					if (now - session.getLastActiveTime() < nscfg
							.getReRegisterWriteDelay()) {
						checkReRegisterWrite.needRun();
						break;
					}
					if (!session.isClose()) {
						SelectionKey key = session.getChannel()
								.keyFor(selector);
						key.attach(session);
						changeInterestWrite(key, true);
						session.isRegisterReWrite = false;
//						logger.debug("change to rewrite" + session.getLastActiveTime());
//						activeSessionLinedLinkedList.putOrMoveFirst(session
//								.getLastAccessTimeLinkedListwrapSession());
					}
					reRegisterQueueWrite.poll();
				}
				if (i > 0) {
//					if(debug){
//						logger.debug("active session");
//					}
					Iterator<SelectionKey> iter = selector.selectedKeys()
							.iterator();
					while (iter.hasNext()) {
						SelectionKey key = iter.next();
						iter.remove();
						session = (IoSession) key.attachment();
						activeSessionLinedLinkedList.putOrMoveFirst(session
								.getLastAccessTimeLinkedListwrapSession());
						try {
							if (key.isWritable()) {
								MaximumSizeArrayCycleQueue<ByteBuffer>.Bulk buffW = session
										.peekWaitSendBulk();
								session.getRegisterBarrier().set(1);
								if (buffW != null) {
									//set up memory barrier
									SocketChannel sc = (SocketChannel) key
											.channel();
									long sendSize = 0;
									for (int j = 0; j < nscfg.getTrySendNum(); j++) {
										// int pp =
										// buffW.getByteBuffer().position();
										// int limit =
										// buffW.getByteBuffer().limit();
										LinkedList<Integer> pp = new LinkedList<Integer>();
										LinkedList<Integer> limit = new LinkedList<Integer>();
										LinkedList<ByteBuffer> bl = new LinkedList<ByteBuffer>();
										for (int k = buffW.getOffset(); k < buffW
												.getOffset()
												+ buffW.getLength(); k++) {
											pp.addLast(buffW.getQueue()[k]
													.position());
											limit.addLast(buffW.getQueue()[k]
													.limit());
										}
										try {
											sendSize = sc.write(
													buffW.getQueue(),
													buffW.getOffset(),
													buffW.getLength());
										} catch (IllegalArgumentException e) {
											logger.debug(buffW.getOffset()
													+ "  " + buffW.getLength()
													+ "  ");

											for (int k = buffW.getOffset(); k < buffW
													.getOffset()
													+ buffW.getLength(); k++) {

												logger.debug("curp"
														+ buffW.getQueue()[k]
																.position()
														+ "curl"
														+ buffW.getQueue()[k]
																.limit() + "p"
														+ pp.removeFirst()
														+ "l"
														+ limit.removeFirst());
											}

											throw e;
										}
										if (sendSize != 0)
											break;
									}
//									if(debug){
//										logger.debug(sendSize + " " + session.getRegisterBarrier().get() + "  " + session.getDebugQueue().size() );
//									}
									isClearWrite = false;
									for (int j = buffW.getOffset(); j < buffW
											.getOffset() + buffW.getLength(); j++) {
										if (!buffW.getQueue()[j].hasRemaining()) {
											IoBuffer buffer = session
													.peekIoBuffer();
											if (session.pollWaitSendBuff()) {
												session.getHandler()
														.onMessageSent(session,
																buffer);
											} else {
												sendSize = 1;// avoid session be
																// pushed into
																// reRegisterQueueWrite
												isClearWrite = true;
												break;
											}
										} else {
											break;
										}
									}
									if (session.peekWaitSendBulk() == null) {
										isClearWrite = true;
									} else if (sendSize == 0) {
										// 若果当尝试了trySendNum次后发送依然为0,则当前网络压力大或是客户端网络不良造成发送数据堆积
										// 服务器，此時當前session将进入到等待队列，等候一段时间后重新注册写事件
										try {
											isClearWrite = true;
											if(!session.isRegisterReWrite){
												session.isRegisterReWrite = true;
												reRegisterQueueWrite.push(session);
												activeSessionLinedLinkedList
														.remove(session
																.getLastAccessTimeLinkedListwrapSession());
												checkReRegisterWrite.needRun();
											}
										} catch (Exception e) {
										}
										
									}
									
								}else{
									isClearWrite = true;
								}
								
								if(isClearWrite){
									if(session.getRegisterBarrier().getAndSet(0) == 1){
										changeInterestWrite(key, false);
									}else{
										session.getRegisterBarrier().set(1);
									}
								}
							} else if (key.isReadable()) {
								IoBuffer buff = null;
								try {

									SocketChannel sc = (SocketChannel) key
											.channel();
									session = (IoSession) key.attachment();
									for(int k = 0 ; k < 10 ; ++k){
										if (buff == null) {
											for (int j = 0; j < 3; j++) {
												if ((buff = memPool.tryGet()) != null) {
													break;
												}
												try {
													Thread.sleep(1);
												} catch (InterruptedException e) {
												}
											}
											if (buff == null) {
												buff = new AllocateBytesBuff(
														memPool.getCellCapacity() * 2 / 3,
														false);
												logger.warn("Recieve mem pool is empty!Creat a buff!May be you need to increase size of the pool!");
											}
										}
										
										readSize = sc
												.read(buff.getByteBuffer());
										if (readSize <= 0) {
											break;
										}
										if (!buff.getByteBuffer()
												.hasRemaining()) {
											buff.getByteBuffer()
													.limit(buff.getByteBuffer()
															.position())
													.position(0);
											session.getHandler()
													.onMessageRecieved(session,
															buff);
											if (!buff.getByteBuffer()
													.hasRemaining()
													&& buff instanceof CycleBuff) {
												((CycleBuff) buff).close();
											}
											buff = null;
										}
									}
									if (buff != null && buff.getByteBuffer().position() != 0) {
										buff.getByteBuffer()
												.limit(buff.getByteBuffer()
														.position())
												.position(0);
										session.getHandler().onMessageRecieved(
												session, buff);
										if (!buff.getByteBuffer()
												.hasRemaining()
												&& buff instanceof CycleBuff) {
											((CycleBuff) buff).close();
										}
									} else {
										if (buff instanceof CycleBuff) {
											((CycleBuff) buff).close();
										}
									}
									if (readSize < 0) {
										session.closeSession();
										activeSessionLinedLinkedList
												.remove(session
														.getLastAccessTimeLinkedListwrapSession());
									}
								} catch (IOException e) {
									if (buff != null
											&& buff instanceof CycleBuff) {
										((CycleBuff) buff).close();
									}
									throw e;
								}
							}
						} catch (Exception e) {
							session.getHandler().catchException(session, e);
							session.closeSession();
							activeSessionLinedLinkedList.remove(session
									.getLastAccessTimeLinkedListwrapSession());
						}
					}
				}
				Entity<IoSession> entity;
				while ((entity = activeSessionLinedLinkedList.getLast()) != null) {
					if (now - entity.getElement().getLastActiveTime() < nscfg
							.getClearTimeoutSessionInterval()) {
						break;
					}
					entity.getElement().closeSession();
					activeSessionLinedLinkedList.remove(entity);
				}

			}
		}
	}
}
