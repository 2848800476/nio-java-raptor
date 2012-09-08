package cn.com.sparkle.raptor.core.session;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import cn.com.sparkle.raptor.core.buff.IoBuffer;
import cn.com.sparkle.raptor.core.collections.LastAccessTimeLinkedList.Entity;
import cn.com.sparkle.raptor.core.collections.MaximumSizeArrayCycleQueue;
import cn.com.sparkle.raptor.core.collections.Queue;
import cn.com.sparkle.raptor.core.filter.FilterChain;
import cn.com.sparkle.raptor.core.transport.socket.nio.NioSocketProcessor;
import cn.com.sparkle.raptor.core.transport.socket.nio.exception.SessionHavaClosedException;
import cn.com.sparkle.raptor.core.util.TimeUtil;

public class IoSession {
	private long lastActiveTime;
	private SocketChannel channel;
	private Queue<IoBuffer> waitSendQueue = new MaximumSizeArrayCycleQueue<IoBuffer>(100);
	private NioSocketProcessor processor;
	private FilterChain filterChain;
	private Object attachment;
	private Entity<IoSession> lastAccessTimeLinkedListwrapSession = null;
	
	private volatile boolean isClose = false;
	
	
	public IoSession(NioSocketProcessor processor,SocketChannel channel,FilterChain filterChain){
		this.processor = processor;
		this.channel = channel;
		this.filterChain = filterChain;
		this.lastAccessTimeLinkedListwrapSession = new Entity<IoSession>(this);
	}
	
	public FilterChain getFilterChain() {
		return filterChain;
	}
	public long getLastActiveTime() {
		return lastActiveTime;
	}
	public void setFilterChain(FilterChain filterChain) {
		this.filterChain = filterChain;
	}

	public SocketChannel getChannel() {
		return channel;
	}

	public boolean tryWrite(IoBuffer message) throws SessionHavaClosedException{
		if(isClose) throw new SessionHavaClosedException("IoSession have closed!");
		
		try{
			processor.getLock().lock();
			waitSendQueue.push(message);
			processor.registerWrite(this);
			return true;
		}catch(Exception e){
			return false;
		}finally{
			processor.getLock().unlock();
		}
	}
	public void write(IoBuffer message) throws SessionHavaClosedException{
		while(true){
			if(tryWrite(message)){
				break;
			}
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
			}
		}
	}
	public Queue<IoBuffer> getWaitSendQueue() {
		return waitSendQueue;
	}
	public void attach(Object attachment){
		this.attachment = attachment;
	}
	public Object attachment(){
		return attachment;
	}

	public void close(){
		if(!isClose){
			isClose = true;
			try {
				channel.close();
			} catch (IOException e) {
			}
			filterChain.getHandler().onSessionClose(this);
		}
	}

	public Entity<IoSession> getLastAccessTimeLinkedListwrapSession() {
		lastActiveTime = TimeUtil.currentTimeMillis();
		return lastAccessTimeLinkedListwrapSession;
	}
}
