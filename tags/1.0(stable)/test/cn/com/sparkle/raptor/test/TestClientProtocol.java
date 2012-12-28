package cn.com.sparkle.raptor.test;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import cn.com.sparkle.raptor.core.buff.IoBuffer;
import cn.com.sparkle.raptor.core.buff.SyncBuffPool;
import cn.com.sparkle.raptor.core.handler.IoHandler;
import cn.com.sparkle.raptor.core.protocol.MultiThreadProtecolHandler;
import cn.com.sparkle.raptor.core.protocol.MultiThreadProtecolHandler.ProtecolHandlerAttachment;
import cn.com.sparkle.raptor.core.protocol.Protocol;
import cn.com.sparkle.raptor.core.protocol.ProtocolHandler;
import cn.com.sparkle.raptor.core.protocol.textline.TextLineProtocol;
import cn.com.sparkle.raptor.core.protocol.textline.TextLineProtocol.DecodeCache;
import cn.com.sparkle.raptor.core.session.IoSession;
import cn.com.sparkle.raptor.core.transport.socket.nio.NioSocketClient;
import cn.com.sparkle.raptor.core.transport.socket.nio.NioSocketConfigure;
import cn.com.sparkle.raptor.core.transport.socket.nio.exception.SessionHavaClosedException;

public class TestClientProtocol {
	public static void main(String[] args) throws Exception {
		NioSocketConfigure nsc = new NioSocketConfigure();
		NioSocketClient client = new NioSocketClient(nsc);
		IoHandler handler = new MultiThreadProtecolHandler(1000, 1024, 20, 300, 60, TimeUnit.SECONDS,new TextLineProtocol(), new TestProtocolClientHandler());
		for(int i = 0 ; i < 10 ; i++){
			client.connect(new InetSocketAddress("10.10.83.243",1234), new MultiThreadProtecolHandler(1000, 1024, 20, 300, 60, TimeUnit.SECONDS,new TextLineProtocol(), new TestProtocolClientHandler()));
//			client.connect(new InetSocketAddress("127.0.0.1",1234),handler );
		}
	}

}
class TestProtocolClientHandler implements ProtocolHandler{
	private static AtomicInteger flag = new AtomicInteger(0);
	private int i = 0;
	@Override
	public void onOneThreadSessionOpen(SyncBuffPool buffPool,
			Protocol protocol, IoSession session) {
		try {
			
			ProtecolHandlerAttachment att = (ProtecolHandlerAttachment)session.attachment();
			DecodeCache decodeCache = (DecodeCache)att.customAttachment;
			decodeCache.customAttachment = Integer.valueOf(flag.addAndGet(1));
			System.out.println(decodeCache.customAttachment);
//			while(true){
				IoBuffer[] buff = protocol.encode(buffPool, "Hello,Mr server!");
				session.write(buff);
			
//			}
		} catch (SessionHavaClosedException e) {
			//only stop send data,because the onOneThreadSessionClose will be invoked subsequently. 
			return;
		}
	}

	@Override
	public void onOneThreadSessionClose(IoSession session) {
		ProtecolHandlerAttachment att = (ProtecolHandlerAttachment)session.attachment();
		DecodeCache decodeCache = (DecodeCache)att.customAttachment;
		System.out.println("close" + decodeCache.customAttachment);
	}

	@Override
	public void onOneThreadCatchException(IoSession session, Throwable e) {
		e.printStackTrace();
	}

	@Override
	public void onOneThreadMessageRecieved(SyncBuffPool buffPool,
			Protocol protocol, IoSession session, Object o) {
		System.out.println(o);
		
		ProtecolHandlerAttachment att = (ProtecolHandlerAttachment)session.attachment();
		DecodeCache decodeCache = (DecodeCache)att.customAttachment;
		try {
				IoBuffer[] buffa = protocol.encode(buffPool, "ÄãºÃ£¡Mr server!This is client" + decodeCache.customAttachment + "!write package" + (++i));
				session.write(buffa);
		} catch (SessionHavaClosedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void onOneThreadMessageSent(SyncBuffPool buffPool,
			Protocol protocol, IoSession session) {
		
	}
	
}