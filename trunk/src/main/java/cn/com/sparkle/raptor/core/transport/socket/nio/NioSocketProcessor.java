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
import cn.com.sparkle.raptor.core.buff.SyncBuffPool;
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

	// public volatile boolean debug = false;

	private Selector selector;
	private ReentrantLock lock = new ReentrantLock();
	private ReentrantLock readLock = new ReentrantLock();
	private ReentrantLock closeLock = new ReentrantLock();

	private Queue<IoSession> registerQueueWrite = new MaximumSizeArrayCycleQueue<IoSession>(
			IoSession.class, 1000);
	private Queue<IoSession> registerQueueClose = new MaximumSizeArrayCycleQueue<IoSession>(
			IoSession.class, 1000);
	private Queue<ReadInterest> registerQueueRead = new MaximumSizeArrayCycleQueue<ReadInterest>(
			ReadInterest.class, 1000);
	private Queue<IoSession> reRegisterQueueWrite = new MaximumSizeArrayCycleQueue<IoSession>(
			IoSession.class, 10000);
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
	private Thread thread;
	// private RecieveMessageDealer recieveMessageDealer;

	private LastAccessTimeLinkedList<IoSession> activeSessionLinedLinkedList = new LastAccessTimeLinkedList<IoSession>();

	private NioSocketConfigure nscfg;

	private DelayChecked checkRegisterRead;
	private DelayChecked checkRegisterWrite;
	private DelayChecked checkReRegisterWrite;
	private DelayChecked checkTimeoutSession;

	public NioSocketProcessor(NioSocketConfigure nscfg, SyncBuffPool memPool,String name)
			throws IOException {

		this.nscfg = nscfg;
		selector = Selector.open();
		this.memPool = memPool;
		// memPool = new SyncBuffPool(nscfg.getCycleRecieveBuffCellSize(),
		// nscfg.getRecieveBuffSize() * 2 / 3) ;
		// memPool =
		// new CycleAllocateBytesBuffPool(
		// nscfg.getCycleRecieveBuffCellSize(),
		// nscfg.getRecieveBuffSize() * 2 / 3);

		// recieveMessageDealer = new RecieveMessageDealer(
		// nscfg.getCycleRecieveBuffCellSize());
		// recieveMessageDealer.setDaemon(true);
		// recieveMessageDealer.start();

		thread = new Thread(new Processor());
		thread.setDaemon(true);
		thread.setName("Raptor-Nio-Processor " + name );
		thread.start();
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
		try {
			readLock.lock();
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
		} finally {
			readLock.unlock();
		}

	}

	public void registerRead(IoSession session) {
		try {
			readLock.lock();
			while (true) {
				try {
					registerQueueRead.push(new ReadInterest(session, true));
					checkRegisterRead.needRun();
					break;
				} catch (Exception e) {
					try {
						Thread.sleep(1);
					} catch (InterruptedException e1) {
					}
				}
			}
		} finally {
			readLock.unlock();
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
						Thread.sleep(1);
					} catch (InterruptedException e1) {
					}
				}
			}
		} finally {
			lock.unlock();
		}
	}
	public void registerClose(IoSession session) {
		try {
			closeLock.lock();
			while (true) {
				try {
					registerQueueClose.push(session);
					selector.wakeup();
					break;
				} catch (Exception e) {
					logger.debug(e);
					try {
						Thread.sleep(1);
					} catch (InterruptedException e1) {
					}
				}
			}
		} finally {
			closeLock.unlock();
		}
	}

	private boolean changeInterestWrite(SelectionKey key, boolean isInterest,
			IoSession session) {
		// logger.debug(session.getRemoteAddress() + " interest write:" +
		// isInterest);
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

	private boolean interestRead(SelectionKey key, boolean isInterest,
			IoSession session) {
		int i = key.interestOps();
		// logger.debug( "local:" + session.getLocalAddress() + "  remote:" +
		// session.getRemoteAddress() + " interest read:" + isInterest);
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
	
	public Thread getThread() {
		return thread;
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
				
				while((session = (IoSession)registerQueueClose.peek()) != null ){
					registerQueueClose.poll();
					session.closeSession();
				}
				while ((session = (IoSession) registerQueueWrite.peek()) != null) {
					registerQueueWrite.poll();
					if (session.peekWaitSendBulk() == null) {
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
//							activeSessionLinedLinkedList.putOrMoveFirst(session
//									.getLastAccessTimeLinkedListwrapSession());
						} catch (ClosedChannelException e) {
							activeSessionLinedLinkedList.remove(session
									.getLastAccessTimeLinkedListwrapSession());
							session.getHandler().catchException(session, e);
							session.closeSession();
						}
					} else {
						key.attach(session);
						changeInterestWrite(key, true, session);
//						activeSessionLinedLinkedList.putOrMoveFirst(session
//								.getLastAccessTimeLinkedListwrapSession());
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

//								activeSessionLinedLinkedList
//										.putOrMoveFirst(session
//												.getLastAccessTimeLinkedListwrapSession());
							} catch (ClosedChannelException e) {
								activeSessionLinedLinkedList
										.remove(session
												.getLastAccessTimeLinkedListwrapSession());
								session.getHandler().catchException(session, e);
								session.closeSession();
							}
						}
					} else {
						interestRead(key, readInterest.isInterest, session);
//						activeSessionLinedLinkedList.putOrMoveFirst(session
//								.getLastAccessTimeLinkedListwrapSession());
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
						changeInterestWrite(key, true, session);
						session.isRegisterReWrite = false;
						// logger.debug("change to rewrite" +
						// session.getLastActiveTime());
						// activeSessionLinedLinkedList.putOrMoveFirst(session
						// .getLastAccessTimeLinkedListwrapSession());
					}
					reRegisterQueueWrite.poll();
				}
				if (i > 0) {
					Iterator<SelectionKey> iter = selector.selectedKeys()
							.iterator();
					while (iter.hasNext()) {
						SelectionKey key = iter.next();
						session = (IoSession) key.attachment();
						iter.remove();
						activeSessionLinedLinkedList.putOrMoveFirst(session
								.getLastAccessTimeLinkedListwrapSession());
						try {
							if (key.isReadable()) {
								IoBuffer buff = null;
								try {

									SocketChannel sc = (SocketChannel) key
											.channel();
									session = (IoSession) key.attachment();
									for (int k = 0; k < 10; ++k) {
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
											buff = null;
										}
									}
									if (buff != null
											&& buff.getByteBuffer().position() != 0) {
										buff.getByteBuffer()
												.limit(buff.getByteBuffer()
														.position())
												.position(0);
										session.getHandler().onMessageRecieved(
												session, buff);
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
							if (key.isWritable()) {
								// logger.debug("send message");
								MaximumSizeArrayCycleQueue<ByteBuffer>.Bulk buffW = session
										.peekWaitSendBulk();
								session.getRegisterBarrier().set(1);
								if (buffW != null) {
									// set up memory barrier
									SocketChannel sc = (SocketChannel) key
											.channel();
									long sendSize = 0;
									for (int j = 0; j < nscfg.getTrySendNum(); j++) {
										try {
											sendSize = sc.write(
													buffW.getQueue(),
													buffW.getOffset(),
													buffW.getLength());
										} catch (IllegalArgumentException e) {
											logger.debug(buffW.getOffset()
													+ "  " + buffW.getLength()
													+ "  ");
											throw e;
										}
										if (sendSize != 0)
											break;
									}
									// logger.debug(sendSize + " " +
									// session.getRegisterBarrier().get() + "  "
									// + session.getDebugQueue().size() );
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
												isClearWrite = true;
												break;
											}
											sendSize = 1;// avoid session be
											// pushed into
											// reRegisterQueueWrite
										} else {
											break;
										}
									}
									if (session.peekWaitSendBulk() == null) {
										isClearWrite = true;
									} else if (sendSize == 0) {
										// logger.debug("delay send ,and unregister writer");
										// 若果当尝试了trySendNum次后发送依然为0,则当前网络压力大或是客户端网络不良造成发送数据堆积
										// 服务器，此時當前session将进入到等待队列，等候一段时间后重新注册写事件
										try {
											
											if (!session.isRegisterReWrite) {
												reRegisterQueueWrite
														.push(session);
												session.isRegisterReWrite = true;
												activeSessionLinedLinkedList
														.remove(session
																.getLastAccessTimeLinkedListwrapSession());
												checkReRegisterWrite.needRun();
											}
											isClearWrite = true;
										} catch (Exception e) {
										}

									}

								} else {
									isClearWrite = true;
								}

								if (isClearWrite) {
									if (session.getRegisterBarrier().getAndSet(
											0) == 1) {
										changeInterestWrite(key, false, session);
									} else {
										session.getRegisterBarrier().set(1);
									}
								}
							}
						} catch (Exception e) {
							session.getHandler().catchException(session, e);
							key.cancel();
							session.closeSession();
							activeSessionLinedLinkedList.remove(session
									.getLastAccessTimeLinkedListwrapSession());
						}
					}
				}
				Entity<IoSession> entity;
				while ((entity = activeSessionLinedLinkedList.
						getLast()) != null) {
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
