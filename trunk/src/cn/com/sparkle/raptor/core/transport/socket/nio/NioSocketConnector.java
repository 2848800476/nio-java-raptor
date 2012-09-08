package cn.com.sparkle.raptor.core.transport.socket.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

import cn.com.sparkle.raptor.core.collections.MaximumSizeArrayCycleQueue;
import cn.com.sparkle.raptor.core.delaycheck.DelayChecked;
import cn.com.sparkle.raptor.core.delaycheck.DelayCheckedTimer;
import cn.com.sparkle.raptor.core.filter.FilterChain;
import cn.com.sparkle.raptor.core.handler.IoHandler;

public class NioSocketConnector {
	private Selector selector;
	IoHandler handler;
	private MultNioSocketProcessor multNioSocketProcessor;
	NioSocketConfigure nscfg;
	private MaximumSizeArrayCycleQueue<QueueBean> waitConnectQueue = new MaximumSizeArrayCycleQueue<NioSocketConnector.QueueBean>(100000);
	private DelayChecked checkRegisterConnecter;
	private class QueueBean{
		SocketChannel sc;
		FilterChain filterChain;
	}
	public NioSocketConnector(NioSocketConfigure nscfg) throws IOException{
		this.nscfg = nscfg;
		this.multNioSocketProcessor = new MultNioSocketProcessor(nscfg);
		selector = Selector.open();
		Thread t = new Thread(new Connector());
		t.setDaemon(nscfg.isDaemon());
		t.start();
		checkRegisterConnecter = new DelayChecked(nscfg.getRegisterConnecterDelay()) {
			@Override
			public void goToRun() {
				selector.wakeup();
			}
		};
		DelayCheckedTimer.addDelayCheck(checkRegisterConnecter);
	}
	public void registerConnector(SocketChannel sc,FilterChain filterChain) throws Exception{
		QueueBean a = new QueueBean();
		a.filterChain = filterChain;
		a.sc = sc;
		waitConnectQueue.push(a);
		checkRegisterConnecter.needRun();
	}
	
	class Connector implements Runnable{
		public void run(){
			while(true){
				int i;
				try{
					i = selector.select();
				}catch(Throwable e){
					throw new RuntimeException(e);
				}
					QueueBean qb;
//					long s = System.currentTimeMillis();
					while((qb = waitConnectQueue.peek()) != null){
						try {
							qb.sc.register(selector, SelectionKey.OP_CONNECT,qb.filterChain);
						} catch (ClosedChannelException e) {
							qb.filterChain.getHandler().catchException(null, e);
						}
						waitConnectQueue.poll();
					}
					
					if(i > 0){
						
						Iterator<SelectionKey> iter = selector.selectedKeys().iterator();
						while(iter.hasNext()){
							SelectionKey key = iter.next();
							iter.remove();
							if (key.isConnectable()) {
								key.cancel();
								SocketChannel sc = (SocketChannel) key
										.channel();
								FilterChain filterChain = (FilterChain)key.attachment();
								try {
									if(sc.finishConnect()){
										multNioSocketProcessor.addSession(filterChain, sc);
									}
								} catch (Exception e) {
									filterChain.getHandler().catchException(null, e);
									try{
										sc.close();
									}catch(Exception ee){}
								}
							}
						}
					}
//					System.out.println("cost:"+(System.currentTimeMillis() - s));
				
			}
		}
	}
}
