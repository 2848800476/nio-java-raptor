package cn.com.sparkle.raptor.core.transport.socket.nio;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.locks.ReentrantLock;

import cn.com.sparkle.raptor.core.buff.CycleAllocateBytesBuffPool;
import cn.com.sparkle.raptor.core.buff.CycleBuff;
import cn.com.sparkle.raptor.core.buff.IoBuffer;
import cn.com.sparkle.raptor.core.collections.LastAccessTimeLinkedList;
import cn.com.sparkle.raptor.core.collections.LastAccessTimeLinkedList.Entity;
import cn.com.sparkle.raptor.core.collections.MaximumSizeArrayCycleQueue;
import cn.com.sparkle.raptor.core.collections.Queue;
import cn.com.sparkle.raptor.core.delaycheck.DelayChecked;
import cn.com.sparkle.raptor.core.delaycheck.DelayCheckedTimer;
import cn.com.sparkle.raptor.core.session.IoSession;
import cn.com.sparkle.raptor.core.util.TimeUtil;

public class NioSocketProcessor {
	private Selector selector;
	private ReentrantLock lock = new ReentrantLock();

	private Queue<IoSession> registerQueueWrite = new MaximumSizeArrayCycleQueue<IoSession>(
			100000);
	private Queue<IoSession> registerQueueRead = new MaximumSizeArrayCycleQueue<IoSession>(
			100000);
	private Queue<IoSession> reRegisterQueueWrite = new MaximumSizeArrayCycleQueue<IoSession>(
			100000);
	private CycleAllocateBytesBuffPool memPool;
	
	private RecieveMessageDealer recieveMessageDealer;
	
	private LastAccessTimeLinkedList<IoSession> activeSessionLinedLinkedList = new LastAccessTimeLinkedList<IoSession>();

	private NioSocketConfigure nscfg;

	private DelayChecked checkRegisterRead;
	private DelayChecked checkRegisterWrite;
	private DelayChecked checkReRegisterWrite;
	private DelayChecked checkTimeoutSession;

	public NioSocketProcessor(NioSocketConfigure nscfg) throws IOException {

		this.nscfg = nscfg;
		selector = Selector.open();
		memPool = new CycleAllocateBytesBuffPool(nscfg.getCycleRecieveBuffCellSize(), nscfg.getRevieveBuffSize() * 2 / 3);
		recieveMessageDealer = new RecieveMessageDealer(nscfg.getCycleRecieveBuffCellSize());
		recieveMessageDealer.setDaemon(true);
		recieveMessageDealer.start();
		
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
		checkTimeoutSession = new DelayChecked(nscfg.getClearTimeoutSessionInterval(),true) {
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
	
	public ReentrantLock getLock() {
		return lock;
	}

	public void registerWrite(IoSession session) {
		while (true) {
			try {
				registerQueueWrite.push(session);
				checkRegisterWrite.needRun();
				break;
			} catch (Exception e) {
				try {
					Thread.sleep(nscfg.getRegisterWriteDelay());
				} catch (InterruptedException e1) {
				}
			}
		}
	}

//	public ReentrantLock getLock() {
//		return lock;
//	}

	public void registerRead(IoSession session) {
		while (true) {
			try {
				registerQueueRead.push(session);
				checkRegisterRead.needRun();
				break;
			} catch (Exception e) {
				try {
					Thread.sleep(nscfg.getRegisterReadDelay());
				} catch (InterruptedException e1) {
				}
			}
		}
	}

	private boolean changeInterestWrite(SelectionKey key, boolean isInterest) {
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
			int readSize;
			IoSession session;
			while (true) {
				int i;
				try {
					i = selector.select(1);
				} catch (Throwable e) {
					throw new RuntimeException(e);
				}
					while ((session = (IoSession) registerQueueWrite.peek()) != null) {
						if (session.getWaitSendQueue().peek() == null)
							continue;// if peek() return null indicates the
										// message have proceeded in last send
										// process.
						SelectionKey key = session.getChannel()
								.keyFor(selector);
						if(key == null){
							if(session.isClose()){
								registerQueueWrite.poll();
								continue;
							}
							try {
								
								key = session.getChannel().register(selector,
										SelectionKey.OP_WRITE);
								key.attach(session);
								registerQueueWrite.poll();
								activeSessionLinedLinkedList.putOrMoveFirst(session.getLastAccessTimeLinkedListwrapSession());
							} catch (ClosedChannelException e) {
								activeSessionLinedLinkedList.remove(session.getLastAccessTimeLinkedListwrapSession());
								session.getHandler()
										.catchException(session, e);
								session.close();
							}
						}else{
							key.attach(session);
							changeInterestWrite(key, true);
							registerQueueWrite.poll();
							activeSessionLinedLinkedList.putOrMoveFirst(session.getLastAccessTimeLinkedListwrapSession());
						}
					}
					while ((session = (IoSession) registerQueueRead.peek()) != null) {
						SelectionKey key = session.getChannel()
								.keyFor(selector);
						if(key == null){
							try {
								key = session.getChannel().register(selector,
										SelectionKey.OP_READ);
								key.attach(session);
								registerQueueRead.poll();
								activeSessionLinedLinkedList.putOrMoveFirst(session.getLastAccessTimeLinkedListwrapSession());
							} catch (ClosedChannelException e) {
								activeSessionLinedLinkedList.remove(session.getLastAccessTimeLinkedListwrapSession());
								session.getHandler()
										.catchException(session, e);
								session.close();
							}
						}else{
							interestRead(key,true);
							registerQueueRead.poll();
							activeSessionLinedLinkedList.putOrMoveFirst(session.getLastAccessTimeLinkedListwrapSession());
						}
					}
					// 检查reRegisterQueueWrite是否有已经可以激活的发送session如果有则注册写事件
					long now = TimeUtil.currentTimeMillis();
					while ((session =  reRegisterQueueWrite.peek()) != null) {
						if (now - session.getLastActiveTime() < nscfg
								.getReRegisterWriteDelay()){
							checkReRegisterWrite.needRun();
							break;
						}
						SelectionKey key = session.getChannel()
								.keyFor(selector);
						key.attach(session);
						changeInterestWrite(key, true);
						reRegisterQueueWrite.poll();
						activeSessionLinedLinkedList.putOrMoveFirst(session.getLastAccessTimeLinkedListwrapSession());
					}
					
					
					if (i > 0) {
						Iterator<SelectionKey> iter = selector.selectedKeys()
								.iterator();
						while (iter.hasNext()) {
							SelectionKey key = iter.next();
							iter.remove();
							session = (IoSession) key
									.attachment();
							activeSessionLinedLinkedList
							.putOrMoveFirst(session.getLastAccessTimeLinkedListwrapSession());
							try {
								if (key.isWritable()) {
									IoBuffer buffW = session
											.getWaitSendQueue().peek();

									SocketChannel sc = (SocketChannel) key
											.channel();

									int sendSize = 0;
									for (int j = 0; j < nscfg.getTrySendNum(); j++) {
										sendSize = sc.write(buffW.getByteBuffer());
										if (sendSize != 0)
											break;
									}
									if (!buffW.getByteBuffer().hasRemaining()) {
										session.getWaitSendQueue().poll();
										buffW.getByteBuffer().position(0);
										session.getHandler().onMessageSent(
												session, buffW);
										if (session.getWaitSendQueue().peek() == null) {
											changeInterestWrite(key, false);
										}
									}else if (sendSize == 0) {
										// 若果当尝试了trySendNum次后发送依然为0,则当前网络压力大或是客户端网络不良造成发送数据堆积
										// 服务器，此時當前session将进入到等待队列，等候一段时间后重新注册写事件
										try {
											reRegisterQueueWrite
													.push(session);
											changeInterestWrite(key, false);
											activeSessionLinedLinkedList
													.remove(session.getLastAccessTimeLinkedListwrapSession());
											checkReRegisterWrite.needRun();
										} catch (Exception e) {
										}
									} 
										
									// System.out.println("send " + sendSize);

								} else if (key.isReadable()) {
									
									CycleBuff buff = null;
									try{
										buff = memPool.get();
										SocketChannel sc = (SocketChannel) key
												.channel();
										session = (IoSession) key.attachment();
										while((readSize = sc.read(buff.getByteBuffer())) > 0){
											if(!buff.getByteBuffer().hasRemaining()){
												buff.getByteBuffer().limit(buff.getByteBuffer().position()).position(0);
												recieveMessageDealer.register(session, buff);
												buff = memPool.get();
											}
										}
										if(buff.getByteBuffer().position() != 0){
											buff.getByteBuffer().limit(buff.getByteBuffer().position()).position(0);
											recieveMessageDealer.register(session, buff);
										}else{
											buff.close();
										}
										if (readSize < 0) {
												session.close();
												activeSessionLinedLinkedList.remove(session.getLastAccessTimeLinkedListwrapSession());
										}
									}catch(IOException e){
										if(buff != null){
											buff.close();
										}
										throw e;
									}
								}
							} catch (IOException e) {
								session.getHandler()
										.catchException(session, e);
								session.close();
								activeSessionLinedLinkedList.remove(session.getLastAccessTimeLinkedListwrapSession());
							}
						}
					}
					
					Entity<IoSession> entity;
					while( (entity = activeSessionLinedLinkedList.getLast()) != null){
						if(now - entity.getElement().getLastActiveTime() < nscfg.getClearTimeoutSessionInterval()){
							break;
						}
						entity.getElement().close();
						activeSessionLinedLinkedList.remove(entity);
					}
				
			}
		}
	}
}
