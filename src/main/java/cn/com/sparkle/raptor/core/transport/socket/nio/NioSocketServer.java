package cn.com.sparkle.raptor.core.transport.socket.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;

import cn.com.sparkle.raptor.core.collections.MaximumSizeArrayCycleQueue.QueueFullException;
import cn.com.sparkle.raptor.core.handler.IoHandler;

public class NioSocketServer {
	private NioSocketAccepter accepter;

	public NioSocketServer(NioSocketConfigure nsc,String name) throws IOException {
		accepter = new NioSocketAccepter(nsc,name);
	}
	public NioSocketServer(NioSocketConfigure nsc) throws IOException {
		this(nsc,"defaultserver");
	}

	public void bind(InetSocketAddress address, IoHandler handler)
			throws IOException, QueueFullException {
		if (handler == null)
			throw new IOException("filter / filter.handler is not exist");
		ServerSocketChannel server = ServerSocketChannel.open();
		server.socket().bind(address);
		server.configureBlocking(false);
		accepter.registerAccept(server, handler);
		System.out.println("raptor listening :" + address.toString());
	}

	public void waitToBind(InetSocketAddress address, IoHandler handler)
			throws IOException {
		while (true) {
			try {
				bind(address, handler);
			} catch (QueueFullException e) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e1) {
				}
			}
			break;
		}
	}
}
