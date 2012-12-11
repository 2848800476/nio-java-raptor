package cn.com.sparkle.raptor.core.protocol;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import cn.com.sparkle.raptor.core.buff.BuffPool;
import cn.com.sparkle.raptor.core.buff.CycleBuff;
import cn.com.sparkle.raptor.core.buff.IoBuffer;
import cn.com.sparkle.raptor.core.buff.SyncBuffPool;
import cn.com.sparkle.raptor.core.handler.IoHandler;
import cn.com.sparkle.raptor.core.session.IoSession;

public class MultiThreadProtecolHandler implements IoHandler {
	private SyncBuffPool buffPool;
	private ThreadPoolExecutor threadPool;
	private Protocol protocol;
	private ProtocolHandler handler;
	
	public MultiThreadProtecolHandler(int totalCellSize,
			int cellCapacity, int corePoolSize, int maximumPoolSize,
			long keepAliveTime, TimeUnit unit,Protocol protocol,ProtocolHandler handler) {
		buffPool = new SyncBuffPool(totalCellSize, cellCapacity);
		threadPool = new ThreadPoolExecutor(corePoolSize, maximumPoolSize,
				keepAliveTime, unit, new LinkedBlockingQueue<Runnable>());
		this.protocol = protocol;
		this.handler = handler;
		if(protocol == null){
			protocol = new Protocol() {
				@Override
				public Object decode(ProtecolHandlerAttachment attachment,IoBuffer buff) {
					return buff;
				}
				@Override
				public IoBuffer[] encode(BuffPool Buffpool, Object obj) {
					return null;
				}
				@Override
				public void init(ProtecolHandlerAttachment attachment) {
					
				}
			};
		}
	}

	@Override
	public final void onSessionOpened(IoSession session) {
		
		ProtecolHandlerAttachment attachment = new ProtecolHandlerAttachment();
		session.attach(attachment);
		protocol.init(attachment);
		onSessionOpen(session);
	}
	private void runOrWaitInQueue(Do jobDo,IoSession session){
		if(session == null){//if connect refused,the session is null
			jobDo.doJob(session);
			return;
		}
		ProtecolHandlerAttachment attachment = (ProtecolHandlerAttachment) session.attachment();
		//spin to lock
				attachment.wantLock1 = 1;
				attachment.turn = 1;
				while (attachment.turn == 1 && attachment.wantLock2 == 1)
					;
				try {
					if (attachment.isExecuting) {
						// add job to queue
						attachment.jobQueue.addLast(jobDo);
					} else {
						// add job to threadpool
						attachment.isExecuting = true;
						threadPool.execute(new JobThread(session, jobDo));
					}
				} finally {
					//release lock
					attachment.wantLock1 = 0;
				}
	}
	public void onSessionOpen(IoSession session) {
		
		Do jobDo = new Do() {
			@Override
			public void doJob(IoSession session) {
				handler.onOneThreadSessionOpen(buffPool,protocol,session);
			}
		};
		runOrWaitInQueue(jobDo,session);
	}
	
	@Override
	public void onSessionClose(IoSession session) {
		Do jobDo = new Do() {
			@Override
			public void doJob(IoSession session) {
				handler.onOneThreadSessionClose(session);
			}
		};
		runOrWaitInQueue(jobDo,session);
	}
	@Override
	public void onMessageRecieved(IoSession session, IoBuffer message) {
		ProtecolHandlerAttachment attachment = (ProtecolHandlerAttachment) session.attachment();
		Object obj = null;
		while((obj = protocol.decode(attachment,message)) != null){
			Do<Object> jobDo = new Do<Object>() {
				@Override
				public void doJob(IoSession session) {
					handler.onOneThreadMessageRecieved(buffPool,protocol,session,o);
				}
			};
			jobDo.o = obj;
			runOrWaitInQueue(jobDo,session);
		}
	}
	@Override
	public void onMessageSent(IoSession session, IoBuffer message) {
		if(message instanceof CycleBuff){
			((CycleBuff) message).close();
		}
		Do<IoBuffer> jobDo = new Do<IoBuffer>() {
			@Override
			public void doJob(IoSession session) {
//				if(o instanceof CycleBuff){
//					buffPool.close((CycleBuff)o);
//				}
				handler.onOneThreadMessageSent(buffPool,protocol,session);
			}
		};
//		jobDo.o = message;
		runOrWaitInQueue(jobDo,session);
	}
	@Override
	public void catchException(IoSession session, Throwable e) {
		Do<Throwable> jobDo = new Do<Throwable>(){
			@Override
			public void doJob(IoSession session) {
				handler.onOneThreadCatchException(session,o);
			}
		};
		jobDo.o = e;
		runOrWaitInQueue(jobDo,session);
	}

	

	private static class JobThread implements Runnable {
		private Do jobDo;
		private IoSession session;

		public JobThread(IoSession session, Do jobDo) {
			this.jobDo = jobDo;
			this.session = session;
		}

		public void run() {
			ProtecolHandlerAttachment attachment = (ProtecolHandlerAttachment) session.attachment();
			while(true){
				jobDo.doJob(session);
				//spin to lock
				attachment.wantLock2 = 1;
				attachment.turn = 2;
				while (attachment.turn == 2 && attachment.wantLock1 == 1)
					;
				try{
					if(!attachment.jobQueue.isEmpty()){
						jobDo = attachment.jobQueue.removeFirst();
					}else{
						attachment.isExecuting = false;
						break;
					}
				}finally{
					//release lock
					attachment.wantLock2 = 0;
				}
			}
		}
	}

	private static abstract class Do<T> {
		public T o;
		public abstract void doJob(IoSession session);
	}
	public static class ProtecolHandlerAttachment {
		private LinkedList<Do> jobQueue = new LinkedList<Do>();
		private volatile byte turn;
		private byte wantLock1 = 0, wantLock2 = 0;
		private volatile boolean isExecuting = false;
		public Object customAttachment;
	}
}
