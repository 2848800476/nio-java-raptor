package cn.com.sparkle.raptor.core.transport.socket.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;

import cn.com.sparkle.raptor.core.collections.MaximumSizeArrayCycleQueue.QueueFullException;
import cn.com.sparkle.raptor.core.filter.FilterChain;


public class NioSocketServer {
	private NioSocketAccepter accepter;
	
	public NioSocketServer(NioSocketConfigure nsc) throws IOException{
		accepter = new NioSocketAccepter(nsc);
	}
	public void bind(InetSocketAddress address,FilterChain filterChain) throws IOException, QueueFullException{
		if(filterChain == null || filterChain.getHandler() == null) throw new IOException("filter / filter.handler is not exist");
		ServerSocketChannel server = ServerSocketChannel.open();
		server.socket().bind(address);
		server.configureBlocking(false);
		accepter.registerAccept(server,filterChain);
		System.out.println("raptor listening :" + address.toString());
	}
}
