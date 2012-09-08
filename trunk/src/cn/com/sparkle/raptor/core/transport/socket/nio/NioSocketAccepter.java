package cn.com.sparkle.raptor.core.transport.socket.nio;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

import cn.com.sparkle.raptor.core.collections.MaximumSizeArrayCycleQueue;
import cn.com.sparkle.raptor.core.collections.MaximumSizeArrayCycleQueue.QueueFullException;
import cn.com.sparkle.raptor.core.delaycheck.DelayChecked;
import cn.com.sparkle.raptor.core.delaycheck.DelayCheckedTimer;
import cn.com.sparkle.raptor.core.filter.FilterChain;
import cn.com.sparkle.raptor.core.handler.IoHandler;

public class NioSocketAccepter {
	private Selector selector;
	IoHandler handler;
	private NioSocketConfigure nscfg;
	private MultNioSocketProcessor multNioSocketProcessor;
	private MaximumSizeArrayCycleQueue<QueueBean> waitRegisterQueue = new MaximumSizeArrayCycleQueue<NioSocketAccepter.QueueBean>(100);
	
	private class QueueBean{
		ServerSocketChannel ssc;
		FilterChain filterChain;
	}
	public NioSocketAccepter(NioSocketConfigure nscfg) throws IOException{
		this.nscfg = nscfg;
		this.multNioSocketProcessor = new MultNioSocketProcessor( nscfg);
		selector = Selector.open();
		Thread t = new Thread(new Accepter());
		t.setDaemon(nscfg.isDaemon());
		t.start();
	}
	public void registerAccept(ServerSocketChannel ssc,FilterChain f) throws IOException, QueueFullException{
		if(f == null) throw new IOException("FilterChain can't be null");
		QueueBean qb = new QueueBean();
		qb.ssc = ssc;
		qb.filterChain = f;
		waitRegisterQueue.push(qb);
		selector.wakeup();
	}
	class Accepter implements Runnable{
		public void run(){
			while(true){
				try{
					int i = selector.select();
					
					QueueBean qb;
					while((qb = waitRegisterQueue.peek()) != null){
						qb.ssc.register(selector, SelectionKey.OP_ACCEPT,qb.filterChain);
						waitRegisterQueue.poll();
					}
					if(i > 0){
						
						Iterator<SelectionKey> iter = selector.selectedKeys().iterator();
						while(iter.hasNext()){
							SelectionKey key = iter.next();
							iter.remove();
							if(key.isAcceptable()){
								
								SocketChannel sc = null;
								try {
									FilterChain filterChain = (FilterChain)key.attachment();
									sc = ((ServerSocketChannel)key.channel()).accept();
									sc.configureBlocking(false);
									if(nscfg.getKeepAlive() != null) sc.socket().setKeepAlive(nscfg.getKeepAlive().booleanValue());
									if(nscfg.getOobInline() != null) sc.socket().setOOBInline(nscfg.getOobInline().booleanValue());
									if(nscfg.getReuseAddress() != null) sc.socket().setReuseAddress(nscfg.getReuseAddress().booleanValue());
									if(nscfg.getRevieveBuffSize() != null) sc.socket().setReceiveBufferSize(nscfg.getRevieveBuffSize().intValue());
									if(nscfg.getSentBuffSize() != null) sc.socket().setSendBufferSize(nscfg.getSentBuffSize().intValue());
									if(nscfg.getSoLinger() != null) sc.socket().setSoLinger(true, nscfg.getSoLinger().intValue());
									if(nscfg.getTcpNoDelay() != null) sc.socket().setTcpNoDelay(nscfg.getTcpNoDelay().booleanValue());
									if(nscfg.getTrafficClass() != null) sc.socket().setTrafficClass(nscfg.getTrafficClass().intValue());
									multNioSocketProcessor.addSession(filterChain, sc);
								} catch (IOException e) {
									if(sc != null){
										try{
											sc.close();
										}catch(Exception ee){}
									}
								}
							}
							
						}
					}
				}catch(Throwable e){
					throw new RuntimeException(e);
				}
			}
		}
	}
}
