package cn.com.sparkle.raptor.test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import cn.com.sparkle.raptor.core.buff.AllocateBytesBuff;
import cn.com.sparkle.raptor.core.buff.IoBuffer;
import cn.com.sparkle.raptor.core.buff.SyncBuffPool;
import cn.com.sparkle.raptor.core.collections.MaximumSizeArrayCycleQueue.QueueFullException;
import cn.com.sparkle.raptor.core.handler.IoHandler;
import cn.com.sparkle.raptor.core.protocol.MultiThreadProtecolHandler;
import cn.com.sparkle.raptor.core.protocol.Protocol;
import cn.com.sparkle.raptor.core.protocol.ProtocolHandler;
import cn.com.sparkle.raptor.core.protocol.MultiThreadProtecolHandler.ProtecolHandlerAttachment;
import cn.com.sparkle.raptor.core.protocol.textline.TextLineProtocol;
import cn.com.sparkle.raptor.core.session.IoSession;
import cn.com.sparkle.raptor.core.transport.socket.nio.NioSocketConfigure;
import cn.com.sparkle.raptor.core.transport.socket.nio.NioSocketServer;
import cn.com.sparkle.raptor.core.transport.socket.nio.exception.SessionHavaClosedException;

public class TestServerProtocol {

	/**
	 * @param args
	 * @throws IOException 
	 * @throws QueueFullException 
	 */
	public static void main(String[] args) throws IOException, QueueFullException {
		// TODO Auto-generated method stub
		NioSocketConfigure nsc = new NioSocketConfigure();
		//nsc.setSentBuffSize(1024);
		//nsc.setRevieveBuffSize(1024 * 2048);
		nsc.setReuseAddress(true);
		nsc.setProcessorNum(1);
		nsc.setTcpNoDelay(true);
		NioSocketServer server = new NioSocketServer(nsc);
		server.bind(new InetSocketAddress(1234),new MultiThreadProtecolHandler(100000, 1024, 20, 300, 60, TimeUnit.SECONDS,new TextLineProtocol(), new TestProtocolHandler()));
//		server.bind(new InetSocketAddress(12345),new FilterChain(new TestHandler()));
	}
	
}
class TestProtocolHandler implements ProtocolHandler{
	private int i = 0;
	@Override
	public void onOneThreadSessionOpen(SyncBuffPool buffPool,
			Protocol protocol, IoSession session,ProtecolHandlerAttachment attachment) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onOneThreadSessionClose(IoSession session,ProtecolHandlerAttachment attachment) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onOneThreadCatchException(IoSession session,ProtecolHandlerAttachment attachment, Throwable e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onOneThreadMessageRecieved(SyncBuffPool buffPool,
			Protocol protocol, IoSession session, Object recieveObject,ProtecolHandlerAttachment attachment) {
//		System.out.println(recieveObject);
		try {
			IoBuffer[] buffa = protocol.encode(buffPool, "��ã�Mr client!This is server!" + (++i));
			session.write(buffa);
	} catch (SessionHavaClosedException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
	}

	@Override
	public void onOneThreadMessageSent(SyncBuffPool buffPool,
			Protocol protocol, IoSession session,ProtecolHandlerAttachment attachment) {
		// TODO Auto-generated method stub
		
	}
	
	
}
