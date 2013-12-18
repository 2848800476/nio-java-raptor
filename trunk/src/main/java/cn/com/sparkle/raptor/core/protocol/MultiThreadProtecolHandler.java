package cn.com.sparkle.raptor.core.protocol;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;







import org.apache.log4j.Logger;

import cn.com.sparkle.raptor.core.buff.BuffPool;
import cn.com.sparkle.raptor.core.buff.CycleBuff;
import cn.com.sparkle.raptor.core.buff.IoBuffer;
import cn.com.sparkle.raptor.core.buff.SyncBuffPool;
import cn.com.sparkle.raptor.core.buff.BuffPool.PoolEmptyException;
import cn.com.sparkle.raptor.core.collections.LastAccessTimeLinkedList.Entity;
import cn.com.sparkle.raptor.core.collections.MaximumSizeArrayCycleQueue;
import cn.com.sparkle.raptor.core.handler.IoHandler;
import cn.com.sparkle.raptor.core.transport.socket.nio.IoSession;
import cn.com.sparkle.raptor.core.transport.socket.nio.NioSocketProcessor;
import cn.com.sparkle.raptor.core.transport.socket.nio.exception.SessionHavaClosedException;

public class MultiThreadProtecolHandler implements IoHandler {
	private final static Logger logger = Logger
			.getLogger(MultiThreadProtecolHandler.class);

	private final static int MAX_EVENT_QUEUE_SIZE = 200;
	private final static int CONTINUE_READ_THRESHOLD = (int)(MAX_EVENT_QUEUE_SIZE * 0.6);

	private SyncBuffPool buffPool;
	private ThreadPoolExecutor threadPool;
	private Protocol protocol;
	private ProtocolHandler handler;

	private Protocol nullProtocol = new Protocol() {
		@Override
		public Object decode(ProtocolHandlerIoSession attachment, IoBuffer buff) {
			return buff;
		}

		@Override
		public IoBuffer[] encode(BuffPool Buffpool, Object obj) {
			throw new RuntimeException("not supported method");
		}

		@Override
		public void init(ProtocolHandlerIoSession attachment) {
		}

		@Override
		public IoBuffer[] encode(BuffPool buffpool, Object message,
				IoBuffer lastWaitSendBuff) {
			throw new RuntimeException("not supported method");
		}

	};
	private static abstract class Do<T> {
		public T o;

		public abstract void doJob(IoSession session);
	}
	private class OpenJob<T> extends Do<T>{
		@Override
		public void doJob(IoSession session) {
			handler.onOneThreadSessionOpen((ProtocolHandlerIoSession) session
					.attachment());
		}
	}
	private class CloseJob<T> extends Do<T>{
		@Override
		public void doJob(IoSession session) {
			ProtocolHandlerIoSession mySession = (ProtocolHandlerIoSession) session
					.attachment();
			if (mySession != null) {
				while (mySession.unFinishedList.size() > 0) {
					if (mySession.unFinishedList.getFirst() instanceof CycleBuff) {
						((CycleBuff) mySession.unFinishedList.getFirst()).close();
					} else {
						// logger.debug("close new create mem");
					}
					mySession.unFinishedList.removeFirst();

				}
				
			}
			handler.onOneThreadSessionClose(mySession);
		}
	}
	private class SentJob extends Do<Integer>{
		public void doJob(IoSession session) {
			handler.onOneThreadMessageSent((ProtocolHandlerIoSession) session
					.attachment(),this.o);
		}
	}
	private class ExceptionDo extends  Do<Throwable>{
		@Override
		public void doJob(IoSession session) {
			handler.onOneThreadCatchException(
					session,
					session.attachment() instanceof ProtocolHandlerIoSession ? (ProtocolHandlerIoSession) session
							.attachment() : null, o);
		}
	};

	public MultiThreadProtecolHandler(int sendBuffTotalCellSize,
			int sendBuffCellCapacity, int corePoolSize, int maximumPoolSize,
			long keepAliveTime, TimeUnit unit, Protocol protocol,
			ProtocolHandler handler,final String threadPoolName) {
		buffPool = new SyncBuffPool(sendBuffTotalCellSize, sendBuffCellCapacity);
		threadPool = new ThreadPoolExecutor(corePoolSize, maximumPoolSize,
				keepAliveTime, unit, new LinkedBlockingQueue<Runnable>(),new ThreadFactory() {
					private int count = 0;
					@Override
					public Thread newThread(Runnable r) {
						Thread newThread = new Thread(r);
						newThread.setName("Raptor-MultiThreadProtecolHandler-Pool-Thread-" + threadPoolName + (++count));
						return newThread;
					}
				});
		this.protocol = protocol;
		this.handler = handler;
		if (protocol == null) {
			protocol = nullProtocol;
		}
	}
	public MultiThreadProtecolHandler(int sendBuffTotalCellSize,
			int sendBuffCellCapacity, int corePoolSize, int maximumPoolSize,
			long keepAliveTime, TimeUnit unit, Protocol protocol,
			ProtocolHandler handler) {
		this(sendBuffTotalCellSize,sendBuffCellCapacity,corePoolSize,maximumPoolSize,keepAliveTime,unit,protocol,handler,"default");
	}
	
	@Override
	public final void onSessionOpened(IoSession session) {

		ProtocolHandlerIoSession mySession = new ProtocolHandlerIoSession(
				session, protocol == nullProtocol ? null : protocol, buffPool);
		mySession.customAttachment = session.attachment();
		session.attach(mySession);
		protocol.init(mySession);
		onSessionOpen(session);
	}

	private void runOrWaitInQueue(Do jobDo, IoSession session) {

		if (!(session.attachment() instanceof ProtocolHandlerIoSession)) {// if
																			// connect
																			// refused,the
																			// session.attachment()
																			// is
																			// not
																			// a
																			// instance
																			// of
			try{															// ProtecolHandlerAttachment
				jobDo.doJob(session);
			}catch(Throwable e){
				session.closeSession();
				logger.error("", e);
			}
			return;
		}
		ProtocolHandlerIoSession attachment = (ProtocolHandlerIoSession) session
				.attachment();

		// spin to lock

		attachment.wantLock1 = 1;
		attachment.turn = 1;
		int spinCount = 0;
		while (attachment.turn == 1 && attachment.wantLock2 == 1){
			if(++spinCount == 500){
				spinCount = 0;
				try {
					Thread.sleep(0);//force to give up cpu
				} catch (InterruptedException e) {
				}
			}
		}
		try {
			if (attachment.isExecuting) {
				// add job to queue
				attachment.jobQueue.addLast(jobDo);
				if (attachment.jobQueue.size() == MAX_EVENT_QUEUE_SIZE) {
					session.suspendRead();// because too many read
				}
			} else {
				// add job to threadpool
				attachment.isExecuting = true;
				threadPool.execute(new JobThread(session, jobDo));
			}
		} finally {
			// release lock
			attachment.wantLock1 = 0;
		}

	}

	public void onSessionOpen(IoSession session) {
		
		Do jobDo = new OpenJob();
		runOrWaitInQueue(jobDo, session);
	}

	@Override
	public void onSessionClose(IoSession session) {

		// return CycleBuffer to BufferPool
		ProtocolHandlerIoSession mySession = (ProtocolHandlerIoSession) session
				.attachment();
		
		try {
			if (mySession != null) {
				mySession.writeLock.lock();
			}
			IoBuffer buffer;
			while((buffer = session.peekIoBuffer()) != null){
				if (buffer instanceof CycleBuff) {
					((CycleBuff) buffer).close();
				}
				session.truePollWaitSendBuff();
				
			}
		} finally {
			if (mySession != null) {
				mySession.writeLock.unlock();
			}
		}
		// activate close event
		Do jobDo = new CloseJob();
		runOrWaitInQueue(jobDo, session);
	}
	
	@Override
	public void onMessageRecieved(IoSession session,final IoBuffer message)
			throws IOException {
		/*
		ProtocolHandlerIoSession attachment = (ProtocolHandlerIoSession) session
				.attachment();
		Object obj = null;
		while (message.getByteBuffer().hasRemaining()
				&& (obj = protocol.decode(attachment, message)) != null) {
			Do<Object> jobDo = new Do<Object>() {
				@Override
				public void doJob(IoSession session) {
					handler.onOneThreadMessageRecieved(o,
							(ProtocolHandlerIoSession) session.attachment());
				}
			};
			jobDo.o = obj;
			runOrWaitInQueue(jobDo, session);
		}
		if (message.getByteBuffer().hasRemaining()) {
			attachment.unFinishedList.addLast(message);
		}else{
		if(message instanceof CycleBuff){
								((CycleBuff)message).close();
							}
							}
		// clear finished IoBuffer

		while (attachment.unFinishedList.size() > 0
				&& !attachment.unFinishedList.getFirst().getByteBuffer()
						.hasRemaining()) {
			if (attachment.unFinishedList.getFirst() instanceof CycleBuff) {
				((CycleBuff) attachment.unFinishedList.getFirst()).close();

			} else {
				// logger.debug("close new create mem");
			}
			attachment.unFinishedList.removeFirst();
		}*/
		final ProtocolHandlerIoSession attachment = (ProtocolHandlerIoSession) session
				.attachment();
		attachment.recivePackageCount = (attachment.recivePackageCount + 1) % Integer.MAX_VALUE;
		final int recivePackageCount = attachment.recivePackageCount;
			Do<IoBuffer> jobDo = new Do<IoBuffer>() {
				@Override
				public void doJob(IoSession session) {
					try {
						Object obj = null;
						attachment.targetRecivePackageCount = recivePackageCount;
								while (message.getByteBuffer().hasRemaining()
										&& (obj = protocol.decode(attachment, message)) != null) {
									handler.onOneThreadMessageRecieved(obj,
											attachment);
								}
							
							if (message.getByteBuffer().hasRemaining()) {
								attachment.unFinishedList.addLast(message);
							}else{
								if(message instanceof CycleBuff){
									((CycleBuff)message).close();
								}
							}
							// clear finished IoBuffer
							while (attachment.unFinishedList.size() > 0
									&& !attachment.unFinishedList.getFirst().getByteBuffer()
											.hasRemaining()) {
								if (attachment.unFinishedList.getFirst() instanceof CycleBuff) {
									((CycleBuff) attachment.unFinishedList.getFirst()).close();
		
								} else {
									// logger.debug("close new create mem");
								}
								attachment.unFinishedList.removeFirst();
							}
						
					} catch (Exception e) {
						logger.error("fatal error", e);
						session.closeSession();
					}
				}
			};
			runOrWaitInQueue(jobDo, session);
	}

	@Override
	public void onMessageSent(IoSession session, IoBuffer message) {
		SentJob jobDo = new SentJob();
		jobDo.o = message.getByteBuffer().limit();
		if (message instanceof CycleBuff) {
			((CycleBuff) message).close();
		}
		runOrWaitInQueue(jobDo, session);
	}

	@Override
	public void catchException(IoSession session, Throwable e) {
		ExceptionDo jobDo = new ExceptionDo();
		jobDo.o = e;
		runOrWaitInQueue(jobDo, session);
	}

	private static class JobThread implements Runnable {
		private Do jobDo;
		private IoSession session;

		public JobThread(IoSession session, Do jobDo) {
			this.jobDo = jobDo;
			this.session = session;
		}

		public void run() {
			ProtocolHandlerIoSession mySession = (ProtocolHandlerIoSession) session
					.attachment();
			while (true) {
				try{
					jobDo.doJob(session);
				}catch(Throwable e){
					session.closeSession();
					logger.error("", e);
				}
				// spin to lock
				mySession.wantLock2 = 1;
				mySession.turn = 2;
				int spinCount = 0;
				while (mySession.turn == 2 && mySession.wantLock1 == 1){
					if(++spinCount == 500){
						spinCount = 0;
						try {
							Thread.sleep(0);//force to give up cpu
						} catch (InterruptedException e) {
						}
					}
				}
					;
				try {
					if (!mySession.jobQueue.isEmpty()) {
						if (mySession.jobQueue.size() == CONTINUE_READ_THRESHOLD &&mySession.isSuspendRead()) {
							session.continueRead();
						}
//						System.out.println(mySession.jobQueue.size());
						try{
							jobDo = mySession.jobQueue.removeFirst();
						}catch(NoSuchElementException e){
							System.out.println(mySession.jobQueue.isEmpty());
							System.out.println(mySession.jobQueue.size());
							throw e;
						}
						
					} else {
						mySession.isExecuting = false;
						break;
					}
				} finally {
					// release lock
					mySession.wantLock2 = 0;
				}
			}
//			logger.debug( "finish event" + session.getRemoteAddress() + " mySession.job size:" + mySession.getJob().size() + " read state:" + mySession.isSuspendRead() + " runCount:" + runCount);
		}
	}

	public static class ProtocolHandlerIoSession extends IoSession {

		private LinkedList<Do<Object>> jobQueue = new LinkedList<Do<Object>>();
		private volatile byte turn;
		private byte wantLock1 = 0, wantLock2 = 0;
		private volatile boolean isExecuting = false;
		private LinkedList<IoBuffer> unFinishedList = new LinkedList<IoBuffer>();
		public Object protocolAttachment;
		public Object customAttachment;
		private volatile int recivePackageCount = 0;
		private int targetRecivePackageCount = 0;

		private ReentrantLock writeLock = new ReentrantLock();

		private IoSession session;
		private Protocol protocol;
		private SyncBuffPool buffPool;

		public ProtocolHandlerIoSession(IoSession session, Protocol protocol,
				SyncBuffPool buffPool) {
			super(null, null, null);
			this.session = session;
			this.protocol = protocol;
			this.buffPool = buffPool;
		}
		public LinkedList<Do<Object>> getJob(){
			return jobQueue;
		}
		@Override
		public boolean isSuspendRead() {
			return session.isSuspendRead();
		}
		@Override
		public IoHandler getHandler() {
			return session.getHandler();
		}

		@Override
		public long getLastActiveTime() {
			return session.getLastActiveTime();
		}

		@Override
		public SocketChannel getChannel() {
			return session.getChannel();
		}

		@Override
		public boolean isClose() {
			return session.isClose();
		}

		@Override
		public NioSocketProcessor getProcessor() {
			return session.getProcessor();
		}

		@Override
		public void suspendRead() {
			session.suspendRead();
		}

		@Override
		public void continueRead() {
			session.continueRead();
		}

		@Override
		public boolean tryWrite(IoBuffer message,boolean flush)
				throws SessionHavaClosedException {
			try {
				writeLock.lock();
				return session.tryWrite(message,flush);
			} catch (SessionHavaClosedException e) {
				if (message instanceof CycleBuff) {
					((CycleBuff) message).close();
				}
				throw e;
			} finally {
				writeLock.unlock();
			}
		}

		@Override
		public void write(IoBuffer message,boolean flush) throws SessionHavaClosedException {

			try {
				writeLock.lock();
				session.write(message,flush);
			} catch (SessionHavaClosedException e) {
				if (message instanceof CycleBuff) {
					((CycleBuff) message).close();
				}
				throw e;
			} finally {
				writeLock.unlock();
			}
		}
		@Override
		public void write(IoBuffer message) throws SessionHavaClosedException {

			try {
				writeLock.lock();
				session.write(message,targetRecivePackageCount == recivePackageCount);
			} catch (SessionHavaClosedException e) {
				if (message instanceof CycleBuff) {
					((CycleBuff) message).close();
				}
				throw e;
			} finally {
				writeLock.unlock();
			}
		}

		/**
		 * @param obj
		 * @return the size of bytes writed
		 * @throws SessionHavaClosedException
		 */

		public int writeObject(Object obj) throws SessionHavaClosedException {
			while (true) {
				try {
					int pos = 0;
					int totalSize = 0;
					IoBuffer buff = null;
					try {
						writeLock.lock();
						if (protocol == null) {
							throw new RuntimeException("not supported method");
						}

						buff = session.getLastWaitSendBuffer();
						IoBuffer[] buffs = null;
						try{
							if (buff != null) {
								pos = buff.getByteBuffer().position();
							}
							buffs = protocol.encode(buffPool, obj, buff);
							if (buff != null) {
								totalSize = buff.getByteBuffer().position() - pos;
							}
						}finally{
							if( buff!= null){
								session.flushLastWaitSendBuffer(buff);
							}
						}
						
						for (int i = 0; i < buffs.length; ++i) {
							try {
								totalSize += buffs[i].getByteBuffer().position();
								session.write(buffs[i],targetRecivePackageCount == recivePackageCount);
							} catch (SessionHavaClosedException e) {
								for (; i < buffs.length; ++i) {
									if (buffs[i] instanceof CycleBuff) {
										((CycleBuff) buffs[i]).close();
									}
								}
								throw e;
							}
						}
						return totalSize;
					} catch (IOException e) {
						logger.debug("", e);
						if (buff != null) {
							buff.getByteBuffer().position(pos);
						}
						throw e;
					}finally {
						writeLock.unlock();
					}
				} catch (PoolEmptyException e) {
					logger.info("maybe you need to incread the size of pool!");
					try {
						Thread.sleep(1);
					} catch (InterruptedException e1) {
						e1.printStackTrace();
					}
				} catch (IOException e) {
					logger.error("fatal error", e);
					throw new RuntimeException(e);
				}
			}

		}

		@Override
		public void attach(Object attachment) {
			session.attach(attachment);
		}

		@Override
		public IoBuffer getLastWaitSendBuffer() {
			return session.getLastWaitSendBuffer();
		}

		@Override
		public void flushLastWaitSendBuffer(IoBuffer buffer) {
			session.flushLastWaitSendBuffer(buffer);
		}
		
		@Override
		public IoBuffer peekIoBuffer() {
			return session.peekIoBuffer();
		}

		@Override
		public MaximumSizeArrayCycleQueue<ByteBuffer>.Bulk peekWaitSendBulk() {
			return session.peekWaitSendBulk();
		}

		@Override
		public boolean pollWaitSendBuff() {
			return session.pollWaitSendBuff();
		}

		@Override
		public Object attachment() {
			return session.attachment();
		}

		@Override
		public void closeSession() {
			session.closeSession();
		}
		@Override
		public Entity<IoSession> getLastAccessTimeLinkedListwrapSession() {
			return session.getLastAccessTimeLinkedListwrapSession();
		}
	}

}
