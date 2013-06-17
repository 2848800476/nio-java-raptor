package cn.com.sparkle.raptor.core.protocol;

import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.log4j.Logger;

import cn.com.sparkle.raptor.core.buff.BuffPool;
import cn.com.sparkle.raptor.core.buff.CycleAllocateBuff;
import cn.com.sparkle.raptor.core.buff.CycleAllocateBytesBuffPool;
import cn.com.sparkle.raptor.core.buff.CycleBuff;
import cn.com.sparkle.raptor.core.buff.IoBuffer;
import cn.com.sparkle.raptor.core.buff.SyncBuffPool;
import cn.com.sparkle.raptor.core.collections.LastAccessTimeLinkedList.Entity;
import cn.com.sparkle.raptor.core.collections.Queue;
import cn.com.sparkle.raptor.core.handler.IoHandler;
import cn.com.sparkle.raptor.core.transport.socket.nio.IoSession;
import cn.com.sparkle.raptor.core.transport.socket.nio.NioSocketProcessor;
import cn.com.sparkle.raptor.core.transport.socket.nio.exception.SessionHavaClosedException;

public class MultiThreadProtecolHandler implements IoHandler {
	private final static Logger logger = Logger
			.getLogger(MultiThreadProtecolHandler.class);

	private final static int MAX_EVENT_QUEUE_SIZE = 100;

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

	public MultiThreadProtecolHandler(int sendBuffTotalCellSize,
			int sendBuffCellCapacity, int corePoolSize, int maximumPoolSize,
			long keepAliveTime, TimeUnit unit, Protocol protocol,
			ProtocolHandler handler) {
		buffPool = new SyncBuffPool(sendBuffTotalCellSize, sendBuffCellCapacity);
		threadPool = new ThreadPoolExecutor(corePoolSize, maximumPoolSize,
				keepAliveTime, unit, new LinkedBlockingQueue<Runnable>());
		this.protocol = protocol;
		this.handler = handler;
		if (protocol == null) {
			protocol = nullProtocol;
		}
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
																			// ProtecolHandlerAttachment
			jobDo.doJob(session);
			return;
		}
		ProtocolHandlerIoSession attachment = (ProtocolHandlerIoSession) session
				.attachment();

		// spin to lock

		attachment.wantLock1 = 1;
		attachment.turn = 1;
		while (attachment.turn == 1 && attachment.wantLock2 == 1)
			;
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

		Do jobDo = new Do() {
			@Override
			public void doJob(IoSession session) {
				handler.onOneThreadSessionOpen((ProtocolHandlerIoSession) session
						.attachment());
			}
		};
		runOrWaitInQueue(jobDo, session);
	}

	@Override
	public void onSessionClose(IoSession session) {

		// return CycleBuffer to BufferPool
		IoBuffer buff;
		while ((buff = session.peekWaitSendBuff()) != null) {
			if (buff instanceof CycleBuff) {
				((CycleBuff) buff).close();
			}
			session.pollWaitSendBuff();
		}
		ProtocolHandlerIoSession mySession = (ProtocolHandlerIoSession) session
				.attachment();
		if (mySession != null) {
			while (mySession.unFinishedList.size() > 0) {
				if (mySession.unFinishedList.getFirst() instanceof CycleBuff) {
					((CycleBuff) mySession.unFinishedList.getFirst()).close();
					// logger.debug("close " +
					// ((CycleAllocateBytesBuffPool)((CycleAllocateBuff)
					// mySession.unFinishedList.getFirst()).getPool()).size());
				} else {
					// logger.debug("close new create mem");
				}
				mySession.unFinishedList.removeFirst();

			}
		}
		// activate close event
		Do jobDo = new Do() {
			@Override
			public void doJob(IoSession session) {

				handler.onOneThreadSessionClose((ProtocolHandlerIoSession) session
						.attachment());
			}
		};
		runOrWaitInQueue(jobDo, session);
	}

	@Override
	public void onMessageRecieved(IoSession session, IoBuffer message) {
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
		}
		// clear finished IoBuffer

		while (attachment.unFinishedList.size() > 0
				&& !attachment.unFinishedList.getFirst().getByteBuffer()
						.hasRemaining()) {
			if (attachment.unFinishedList.getFirst() instanceof CycleBuff) {
				((CycleBuff) attachment.unFinishedList.getFirst()).close();

				// logger.debug("recieve close " +
				// ((CycleAllocateBytesBuffPool)((CycleAllocateBuff)
				// attachment.unFinishedList.getFirst()).getPool()).size());

			} else {
				// logger.debug("close new create mem");
			}
			attachment.unFinishedList.removeFirst();
		}
	}

	@Override
	public void onMessageSent(IoSession session, IoBuffer message) {
		if (message instanceof CycleBuff) {
			((CycleBuff) message).close();
		}
		Do<IoBuffer> jobDo = new Do<IoBuffer>() {
			@Override
			public void doJob(IoSession session) {
				handler.onOneThreadMessageSent((ProtocolHandlerIoSession) session
						.attachment());
			}
		};
		runOrWaitInQueue(jobDo, session);
	}

	@Override
	public void catchException(IoSession session, Throwable e) {
		Do<Throwable> jobDo = new Do<Throwable>() {
			@Override
			public void doJob(IoSession session) {
				handler.onOneThreadCatchException(
						session,
						session.attachment() instanceof ProtocolHandlerIoSession ? (ProtocolHandlerIoSession) session
								.attachment() : null, o);
			}
		};
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
				jobDo.doJob(session);
				// spin to lock
				mySession.wantLock2 = 1;
				mySession.turn = 2;
				while (mySession.turn == 2 && mySession.wantLock1 == 1)
					;
				try {
					if (!mySession.jobQueue.isEmpty()) {
						if (mySession.jobQueue.size() == MAX_EVENT_QUEUE_SIZE) {
							session.continueRead();
						}
						jobDo = mySession.jobQueue.removeFirst();
					} else {
						mySession.isExecuting = false;
						break;
					}
				} finally {
					// release lock
					mySession.wantLock2 = 0;
				}
			}
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
		public boolean tryWrite(IoBuffer message)
				throws SessionHavaClosedException {
			try {
				return session.tryWrite(message);
			} catch (SessionHavaClosedException e) {
				if (message instanceof CycleBuff) {
					((CycleBuff) message).close();
				}
				throw e;
			}
		}

		@Override
		public void write(IoBuffer message) throws SessionHavaClosedException {

			try {
				session.write(message);
			} catch (SessionHavaClosedException e) {
				if (message instanceof CycleBuff) {
					((CycleBuff) message).close();
				}
				throw e;
			}
		}


		public void writeObject(Object obj) throws SessionHavaClosedException {
			if (protocol == null) {
				throw new RuntimeException("not supported method");
			}
			IoBuffer buff = session.getLastButOneSendBuff();
			IoBuffer[] buffs = protocol.encode(buffPool, obj, buff);
			for(int i = 0 ; i < buffs.length ; ++i){
				try{
					session.write(buffs[i]);
				} catch (SessionHavaClosedException e) {
					for(;i<buffs.length; ++i){
						if (buffs[i] instanceof CycleBuff) {
							((CycleBuff) buffs[i]).close();
						}
					}
				}
				
			}
			
			
//			if(buff == null){
//				logger.debug("no hit not full buff");
//			}else{
//				logger.debug("hit not full buff");
//			}
			
		}

		@Override
		public IoBuffer getLastButOneSendBuff() {
			return session.getLastButOneSendBuff();
		}

		@Override
		public IoBuffer peekWaitSendBuff() {
			return session.peekWaitSendBuff();
		}

		@Override
		public void pollWaitSendBuff() {
			session.pollWaitSendBuff();
		}

		@Override
		public void attach(Object attachment) {
			session.attach(attachment);
		}

		@Override
		public Object attachment() {
			return session.attachment();
		}

		@Override
		public void closeSocketChannel() {
			session.closeSocketChannel();
		}

		@Override
		public Entity<IoSession> getLastAccessTimeLinkedListwrapSession() {
			return session.getLastAccessTimeLinkedListwrapSession();
		}
	}

	// public static class ProtecolHandlerAttachment {
	//
	// }

	public static abstract class Do<T> {
		public T o;

		public abstract void doJob(IoSession session);
	}

}
