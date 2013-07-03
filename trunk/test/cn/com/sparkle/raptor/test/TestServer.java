package cn.com.sparkle.raptor.test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import cn.com.sparkle.raptor.core.buff.AllocateBytesBuff;
import cn.com.sparkle.raptor.core.buff.IoBuffer;
import cn.com.sparkle.raptor.core.collections.MaximumSizeArrayCycleQueue.QueueFullException;
import cn.com.sparkle.raptor.core.handler.IoHandler;
import cn.com.sparkle.raptor.core.transport.socket.nio.IoSession;
import cn.com.sparkle.raptor.core.transport.socket.nio.NioSocketConfigure;
import cn.com.sparkle.raptor.core.transport.socket.nio.NioSocketServer;
import cn.com.sparkle.raptor.core.transport.socket.nio.exception.SessionHavaClosedException;

public class TestServer {

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
		nsc.setTcpNoDelay(true);
		nsc.setProcessorNum(2);
		
		NioSocketServer server = new NioSocketServer(nsc);
		server.bind(new InetSocketAddress(1234),new TestHandler());
//		server.bind(new InetSocketAddress(12345),new FilterChain(new TestHandler()));
	}
	
}

class TestHandler implements IoHandler{
	public static AtomicInteger i = new AtomicInteger(0);
	byte[] b = new byte[128];
	@Override
	public void onMessageRecieved(IoSession session, IoBuffer message) {
		
		
//		Integer size = (Integer)session.attachment();
//		if(size == null) size = 0;
//		size += message.getByteBuffer().remaining();
//		message.getByteBuffer().position(message.getByteBuffer().limit());
//		session.attach(size);
//		if(size != 128) return;
//		session.attach(0);
//		
//		IoBuffer temp = new AllocateBytesBuff(128,false);
//		temp.getByteBuffer().position(temp.getByteBuffer().limit());
		
		IoBuffer temp = new AllocateBytesBuff(message.getByteBuffer().remaining(),false);
		message.getByteBuffer().position(message.getByteBuffer().limit());
		temp.getByteBuffer().position(temp.getByteBuffer().limit());
		try {
			session.tryWrite(temp);
		} catch (SessionHavaClosedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
//		try {
//			session.getChannel().write(ByteBuffer.wrap(b));
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
	}

	@Override
	public void onMessageSent(IoSession session, IoBuffer message) {
		// TODO Auto-generated method stub
//		System.out.println("message sent");
	}

	@Override
	public void onSessionClose(IoSession session) {
		// TODO Auto-generated method stub
		int temp = i.addAndGet(-1);
		if(temp%1000 ==0) System.out.println("disconnected " + i);
//		System.out.println("session closed!!!");
	}

	
	private static long time ;
	@Override
	public void onSessionOpened(IoSession session) {
		// TODO Auto-generated method stub
		
		if(i.get()==0) time = System.currentTimeMillis();
		int temp = i.addAndGet(1);
//		System.out.println("session opend!!!" + temp);
		if(temp%1000 ==0){
			System.out.println("connected " + i + "  cost:" + (System.currentTimeMillis() - time));
		}
	}

	@Override
	public void catchException(IoSession session,Throwable e) {
//		 TODO Auto-generated method stub
		e.printStackTrace();
	}
}
