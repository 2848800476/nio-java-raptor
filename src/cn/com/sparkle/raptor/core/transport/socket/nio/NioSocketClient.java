package cn.com.sparkle.raptor.core.transport.socket.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import cn.com.sparkle.raptor.core.filter.FilterChain;

public class NioSocketClient {
	private NioSocketConfigure nscfg;
	private NioSocketConnector connector;
	private Lock lock = new ReentrantLock();

	public NioSocketClient(NioSocketConfigure nscfg) throws IOException {
		this.nscfg = nscfg;
		connector = new NioSocketConnector(nscfg);
	}
	private SocketChannel getSocketChannel() throws IOException{
		SocketChannel sc = SocketChannel.open();
		sc.configureBlocking(false);
		if(nscfg.getKeepAlive() != null) sc.socket().setKeepAlive(nscfg.getKeepAlive().booleanValue());
		if(nscfg.getOobInline() != null) sc.socket().setOOBInline(nscfg.getOobInline().booleanValue());
		if(nscfg.getReuseAddress() != null) sc.socket().setReuseAddress(nscfg.getReuseAddress().booleanValue());
		if(nscfg.getRevieveBuffSize() != null) sc.socket().setReceiveBufferSize(nscfg.getRevieveBuffSize().intValue());
		if(nscfg.getSentBuffSize() != null) sc.socket().setSendBufferSize(nscfg.getSentBuffSize().intValue());
		if(nscfg.getSoLinger() != null) sc.socket().setSoLinger(true, nscfg.getSoLinger().intValue());
		if(nscfg.getTcpNoDelay() != null) sc.socket().setTcpNoDelay(nscfg.getTcpNoDelay().booleanValue());
		if(nscfg.getTrafficClass() != null) sc.socket().setTrafficClass(nscfg.getTrafficClass().intValue());
		sc.socket().setSoTimeout(200);
		return sc;
	}
	public void connect(InetSocketAddress address,FilterChain filterChain) throws Exception{
		if(filterChain == null || filterChain.getHandler() == null) throw new IOException("filter / filter.handler is not exist");
		SocketChannel sc;
		try{
			lock.lock();	
			sc = getSocketChannel();
			connector.registerConnector(sc, filterChain);
			
		}finally{
			lock.unlock();
		}
		sc.connect(address);
	}
}
