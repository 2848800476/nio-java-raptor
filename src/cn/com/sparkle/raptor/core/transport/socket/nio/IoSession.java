package cn.com.sparkle.raptor.core.transport.socket.nio;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;

import cn.com.sparkle.raptor.core.buff.CycleBuff;
import cn.com.sparkle.raptor.core.buff.IoBuffer;
import cn.com.sparkle.raptor.core.collections.LastAccessTimeLinkedList.Entity;
import cn.com.sparkle.raptor.core.collections.MaximumSizeArrayCycleQueue;
import cn.com.sparkle.raptor.core.collections.Queue;
import cn.com.sparkle.raptor.core.handler.IoHandler;
import cn.com.sparkle.raptor.core.transport.socket.nio.exception.SessionHavaClosedException;
import cn.com.sparkle.raptor.core.util.TimeUtil;

public class IoSession {
	private final static Logger logger = Logger.getLogger(IoSession.class);
	
	private long lastActiveTime;
	private SocketChannel channel;
	private MaximumSizeArrayCycleQueue<IoBuffer> waitSendQueue = new MaximumSizeArrayCycleQueue<IoBuffer>(
			100);
	private NioSocketProcessor processor;
	private IoHandler handler;
	private Object attachment;
	private Entity<IoSession> lastAccessTimeLinkedListwrapSession = null;

	private volatile boolean isClose = false;

	private AtomicInteger waitSendQueueSize = new AtomicInteger(0);

	public IoSession(NioSocketProcessor processor, SocketChannel channel,
			IoHandler handler) {
		this.processor = processor;
		this.channel = channel;
		this.handler = handler;
		this.lastAccessTimeLinkedListwrapSession = new Entity<IoSession>(this);
	}

	public IoHandler getHandler() {
		return handler;
	}

	public long getLastActiveTime() {
		return lastActiveTime;
	}

	public SocketChannel getChannel() {
		return channel;
	}

	public boolean isClose() {
		return isClose;
	}

	public NioSocketProcessor getProcessor() {
		return processor;
	}

	public void suspendRead() {
		processor.unRegisterRead(this);
	}

	public void continueRead() {
		processor.registerRead(this);
	}

	public boolean tryWrite(IoBuffer message) throws SessionHavaClosedException {
		// this progress of lock is necessary,because the method tryWrite will
		// be invoked in many different threads
		if (isClose) {
			throw new SessionHavaClosedException("IoSession have closed!");
		}
		message.getByteBuffer().limit(message.getByteBuffer().position())
				.position(0);
		try {
			waitSendQueue.push(message);
			waitSendQueueSize.addAndGet(1);
		} catch (Exception e) {
			message.getByteBuffer().position(message.getByteBuffer().limit());// if
																				// tryWrite
																				// false,fix
																				// position
																				// to
																				// next
																				// invoking
			return false;
		}
		// notify NioSocketProcessor to register a write action
		try {
			processor.getLock().lock();
			processor.registerWrite(this);
			return true;
		} finally {
			processor.getLock().unlock();
		}
	}

	public void write(IoBuffer message) throws SessionHavaClosedException {
		while (true) {
			if (tryWrite(message)) {
				break;
			}
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
			}
		}
	}

	public IoBuffer getLastButOneSendBuff() {
		IoBuffer buff = waitSendQueue.last();
		if (waitSendQueueSize.decrementAndGet() > 0 && buff.getByteBuffer().limit() < buff.getByteBuffer().capacity()) {
			waitSendQueue.pollLast();
			buff.getByteBuffer().position(buff.getByteBuffer().limit()).limit(buff.getByteBuffer().capacity());
			return buff;
		}else {
			waitSendQueueSize.addAndGet(1);//fix negative or zero size
			return null;
		}
		
	}

	public IoBuffer peekWaitSendBuff() {
		try{
			int size = waitSendQueueSize.decrementAndGet();
			if(size <0){
				return null;
			}else{
				return waitSendQueue.peek();
			}
		}finally{
			waitSendQueueSize.addAndGet(1);
		}
	}

	public void pollWaitSendBuff() {
		waitSendQueueSize.decrementAndGet();
		waitSendQueue.poll();
	}

	public void attach(Object attachment) {
		this.attachment = attachment;
	}

	public Object attachment() {
		return attachment;
	}

	void closeSession() {
		if (!isClose) {
			isClose = true;
			closeSocketChannel();
			handler.onSessionClose(this);
		}
	}

	public void closeSocketChannel() {
		try {
			channel.close();
		} catch (IOException e) {
		}
	}

	public Entity<IoSession> getLastAccessTimeLinkedListwrapSession() {
		lastActiveTime = TimeUtil.currentTimeMillis();
		return lastAccessTimeLinkedListwrapSession;
	}
}
