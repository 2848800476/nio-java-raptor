package cn.com.sparkle.raptor.core.session;

import java.io.IOException;
import java.nio.channels.SocketChannel;

import cn.com.sparkle.raptor.core.buff.IoBuffer;
import cn.com.sparkle.raptor.core.collections.LastAccessTimeLinkedList.Entity;
import cn.com.sparkle.raptor.core.collections.MaximumSizeArrayCycleQueue;
import cn.com.sparkle.raptor.core.collections.Queue;
import cn.com.sparkle.raptor.core.handler.IoHandler;
import cn.com.sparkle.raptor.core.transport.socket.nio.NioSocketProcessor;
import cn.com.sparkle.raptor.core.transport.socket.nio.exception.SessionHavaClosedException;
import cn.com.sparkle.raptor.core.util.TimeUtil;

public class IoSession {
	private long lastActiveTime;
	private SocketChannel channel;
	private Queue<IoBuffer> waitSendQueue = new MaximumSizeArrayCycleQueue<IoBuffer>(
			100);
	private NioSocketProcessor processor;
	private IoHandler handler;
	private Object attachment;
	private Entity<IoSession> lastAccessTimeLinkedListwrapSession = null;

	private volatile boolean isClose = false;

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
	public boolean isClose(){
		return isClose;
	}
	public NioSocketProcessor getProcessor(){
		return processor;
	}
	public boolean tryWrite(IoBuffer message) throws SessionHavaClosedException {
		// this progress of lock is necessary,because the method tryWrite will
		// be invoked in many different threads

		if (isClose)
			throw new SessionHavaClosedException("IoSession have closed!");

		message.getByteBuffer().limit(message.getByteBuffer().position()).position(0);
		
		try {
			waitSendQueue.push(message);
		} catch (Exception e) {
			message.getByteBuffer().position(message.getByteBuffer().limit());//if tryWrite false,fix position to next invoking
			return false;
		}
		//notify NioSocketProcessor to register a write action
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
				Thread.sleep(10);
			} catch (InterruptedException e) {
			}
		}
	}

	public void write(IoBuffer[] message) throws SessionHavaClosedException {
		for (int i = 0; i < message.length; i++) {
			write(message[i]);
		}
	}

	public Queue<IoBuffer> getWaitSendQueue() {
		return waitSendQueue;
	}

	public void attach(Object attachment) {
		this.attachment = attachment;
	}

	public Object attachment() {
		return attachment;
	}

	public void close() {
		if (!isClose) {
			isClose = true;
			try {
				channel.close();
			} catch (IOException e) {
			}
			handler.onSessionClose(this);
		}
	}

	public Entity<IoSession> getLastAccessTimeLinkedListwrapSession() {
		lastActiveTime = TimeUtil.currentTimeMillis();
		return lastAccessTimeLinkedListwrapSession;
	}
}
