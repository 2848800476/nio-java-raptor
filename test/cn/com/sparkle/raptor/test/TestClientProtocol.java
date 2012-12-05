package cn.com.sparkle.raptor.test;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import cn.com.sparkle.raptor.core.buff.IoBuffer;
import cn.com.sparkle.raptor.core.buff.SyncBuffPool;
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
		client.connect(new InetSocketAddress("127.0.0.1",1234), new MultiThreadProtecolHandler(1000, 1024, 20, 300, 60, TimeUnit.SECONDS,new TextLineProtocol(), new TestProtocolClientHandler()));
	}

}
class TestProtocolClientHandler implements ProtocolHandler{

	@Override
	public void onOneThreadSessionOpen(SyncBuffPool buffPool,
			Protocol protocol, IoSession session) {
		try {
			
			ProtecolHandlerAttachment att = (ProtecolHandlerAttachment)session.attachment();
			DecodeCache decodeCache = (DecodeCache)att.customAttachment;
//			while(true){
				IoBuffer[] buff = protocol.encode(buffPool, "Hello,Mr server!");
				decodeCache.customAttachment = Integer.valueOf(buff.length);//recode buff package number
				
				session.write(buff);
			
//			}
		} catch (SessionHavaClosedException e) {
			//only stop send data,because the onOneThreadSessionClose will be invoked subsequently. 
			return;
		}
	}

	@Override
	public void onOneThreadSessionClose(IoSession session) {
	
	}

	@Override
	public void onOneThreadCatchException(IoSession session, Throwable e) {
		// TODO Auto-generated method stub
		e.printStackTrace();
	}

	@Override
	public void onOneThreadMessageRecieved(SyncBuffPool buffPool,
			Protocol protocol, IoSession session, Object o) {
	}

	@Override
	public void onOneThreadMessageSent(SyncBuffPool buffPool,
			Protocol protocol, IoSession session) {
		System.out.println("sent");
//		/*
		try {
			ProtecolHandlerAttachment att = (ProtecolHandlerAttachment)session.attachment();
			DecodeCache decodeCache = (DecodeCache)att.customAttachment;
			Integer i = (Integer)decodeCache.customAttachment;
			if(i == 1){
				IoBuffer[] buffa = protocol.encode(buffPool, "This is client!");
				decodeCache.customAttachment = Integer.valueOf(buffa.length);
				session.write(buffa);
//				session.write(protocol.encode(buffPool, "This is client!"));
			}else{
				decodeCache.customAttachment = Integer.valueOf(i-1);
			}
			
		} catch (SessionHavaClosedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
}