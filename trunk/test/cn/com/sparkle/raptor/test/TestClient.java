package cn.com.sparkle.raptor.test;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import cn.com.sparkle.raptor.core.buff.AllocateBytesBuff;
import cn.com.sparkle.raptor.core.buff.IoBuffer;
import cn.com.sparkle.raptor.core.buff.QuoteBytesBuff;
import cn.com.sparkle.raptor.core.handler.IoHandler;
import cn.com.sparkle.raptor.core.session.IoSession;
import cn.com.sparkle.raptor.core.transport.socket.nio.NioSocketClient;
import cn.com.sparkle.raptor.core.transport.socket.nio.NioSocketConfigure;
import cn.com.sparkle.raptor.core.transport.socket.nio.exception.SessionHavaClosedException;

public class TestClient {
	public static void main(String[] args) throws Exception {
		NioSocketConfigure nsc = new NioSocketConfigure();
		NioSocketClient client = new NioSocketClient(nsc);
		for(int i = 0 ; i < 200 ; i++){
			Thread t = new ConnectThread(client);
			t.start();
		}
	}
}
class ConnectThread extends Thread{
	NioSocketClient nc;
	public ConnectThread(NioSocketClient nc){
		this.nc = nc;
	}
	public void run(){
		try{
			for(int i =0 ;i < 5000;i++){
				nc.connect(new InetSocketAddress("127.0.0.1",1234), new TestClientHandler());
//				client.connect(new InetSocketAddress("10.10.83.243",1234), new FilterChain(new TestClientHandler()));
//				client.connect(new InetSocketAddress("220.181.118.141",1234), new FilterChain(new TestClientHandler()));
				
//				client.connect(new InetSocketAddress("127.0.0.1",1234), new FilterChain(new TestClientHandler()));
			}
		}catch(Exception e){
//			e.printStackTrace();
		}
	}
}
class TestClientHandler implements IoHandler {
	public static AtomicInteger i = new AtomicInteger(0);
	public static long time = System.currentTimeMillis();
	@Override
	public void onMessageRecieved(IoSession session, IoBuffer message) {
		System.out.println("client recieve");
		IoBuffer iobuffer = new QuoteBytesBuff(buff, 0, buff.length);
		try {
			session.tryWrite(iobuffer);
		} catch (SessionHavaClosedException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onMessageSent(IoSession session, IoBuffer message) {
		// TODO Auto-generated method stub
		System.out.println("message sent");
	}
	@Override
	public void onSessionClose(IoSession session) {
		// TODO Auto-generated method stub
//		System.out.println("session closed!!!");
	}
	
	private byte[] buff = new byte[1024];
	@Override
	public void onSessionOpened(IoSession session) {
		int temp = i.addAndGet(1);
		if(temp % 100 ==0){
			System.out.println("connected:" + temp + " cost:" + (System.currentTimeMillis() - time));
		}
		/*
		IoBuffer iobuffer = new QuoteBytesBuff(buff, 0, buff.length);
		System.out.println("session open");
		try {
			session.tryWrite(iobuffer);
		} catch (SessionHavaClosedException e) {
			e.printStackTrace();
		}*/
//		A a = new A();
//		a.session = session;
//		a.start();
//		session.close();
		// TODO Auto-generated method stub
//		System.out.println("client session opend!!!" + (++i));
	}

	@Override
	public void catchException(IoSession session, Throwable e) {
		// TODO Auto-generated method stub
//		e.printStackTrace();
	}
}