package cn.com.sparkle.raptor.core.transport.socket.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;

import cn.com.sparkle.raptor.core.buff.CycleBuff;
import cn.com.sparkle.raptor.core.buff.IoBuffer;
import cn.com.sparkle.raptor.core.collections.LastAccessTimeLinkedList.Entity;
import cn.com.sparkle.raptor.core.collections.MaximumSizeArrayCycleQueue.Bulk;
import cn.com.sparkle.raptor.core.collections.MaximumSizeArrayCycleQueue;
import cn.com.sparkle.raptor.core.collections.MaximumSizeArrayCycleQueue.QueueFullException;
import cn.com.sparkle.raptor.core.collections.Queue;
import cn.com.sparkle.raptor.core.handler.IoHandler;
import cn.com.sparkle.raptor.core.transport.socket.nio.exception.SessionHavaClosedException;
import cn.com.sparkle.raptor.core.util.TimeUtil;

public class IoSession {
	private final static Logger logger = Logger.getLogger(IoSession.class);

	private long lastActiveTime;
	private SocketChannel channel;
	private MaximumSizeArrayCycleQueue<ByteBuffer> waitSendQueue = new MaximumSizeArrayCycleQueue<ByteBuffer>(
			ByteBuffer.class, 100);
	private MaximumSizeArrayCycleQueue<IoBuffer> waitSendQueueList = new MaximumSizeArrayCycleQueue<IoBuffer>(
			IoBuffer.class, 100);
	private NioSocketProcessor processor;
	private IoHandler handler;
	private Object attachment;
	private Entity<IoSession> lastAccessTimeLinkedListwrapSession = null;

	private volatile boolean isClose = false;

	private AtomicInteger isGetLast = new AtomicInteger(0);

	private AtomicInteger registerBarrier = new AtomicInteger(0);

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
	protected AtomicInteger getRegisterBarrier(){
		return registerBarrier;
	}
//	protected boolean unSetWriteRegisterBarrier(){
//		boolean isSuccess = registerBarrier.getAndSet(0) == 1;
//		return isSuccess;
//	}
//	protected boolean setWriteRegisterBarrier(){
//		boolean isSuccess = registerBarrier.getAndSet(1) == 0;
//		return isSuccess;
//		
//	}

	public boolean tryWrite(IoBuffer message) throws SessionHavaClosedException {
		// this progress of lock is necessary,because the method tryWrite will
		// be invoked in many different threads
		if (isClose) {
			throw new SessionHavaClosedException("IoSession have closed!");
		}
		if(!waitSendQueueList.hasRemain()){
			return false;
		}
		int flag = registerBarrier.getAndSet(2);
			ByteBuffer buffer = message.getByteBuffer().asReadOnlyBuffer();
			buffer.limit(buffer.position()).position(0);
			try {
				waitSendQueueList.push(message);
				waitSendQueue.push(buffer);
			} catch (QueueFullException e) {
				throw new RuntimeException("fatal error",e);
			}
			if(flag == 0){
				processor.registerWrite(this);
			}
		return true;
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

	// public IoBuffer getLastButOneSendBuff() {
	// IoBuffer buff = waitSendQueue.last();
	// if (waitSendQueueSize.decrementAndGet() > 0 &&
	// buff.getByteBuffer().limit() < buff.getByteBuffer().capacity()) {
	// waitSendQueue.pollLast();
	// buff.getByteBuffer().position(buff.getByteBuffer().limit()).limit(buff.getByteBuffer().capacity());
	// return buff;
	// }else {
	// waitSendQueueSize.addAndGet(1);//fix negative or zero size
	// return null;
	// }
	//
	// }
	public IoBuffer getLastWaitSendBuffer() {
		int is = isGetLast.addAndGet(1);
		IoBuffer buffer = waitSendQueueList.last();
		if (is > 0 && buffer != null && buffer.getByteBuffer().hasRemaining()) {
			return buffer;
		} else {
			isGetLast.decrementAndGet();
			return null;
		}
	}

	public void flushLastWaitSendBuffer(IoBuffer buffer) {
		ByteBuffer bb = waitSendQueue.last();
		int flag = registerBarrier.getAndSet(2);
		
		if (buffer.getByteBuffer().position() > bb.limit()) {
			bb.limit(buffer.getByteBuffer().position());
		}
		isGetLast.decrementAndGet();
		
		if (flag == 0) {
			// notify NioSocketProcessor to register a write action
			processor.registerWrite(this);
		}
	}

	public MaximumSizeArrayCycleQueue<ByteBuffer>.Bulk peekWaitSendBulk() {
		return waitSendQueue.getBulk();
	}

	public IoBuffer peekIoBuffer() {
		return waitSendQueueList.peek();
	}

	public boolean pollWaitSendBuff() {
		int is = isGetLast.decrementAndGet();
		if (is < 0 && !waitSendQueue.peek().hasRemaining()) {
			truePollWaitSendBuff();
			isGetLast.addAndGet(1);
			return true;
		} else {
			isGetLast.addAndGet(1);
			return false;
		}
	}

	public void truePollWaitSendBuff() {
		waitSendQueue.poll();
		waitSendQueueList.poll();
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
