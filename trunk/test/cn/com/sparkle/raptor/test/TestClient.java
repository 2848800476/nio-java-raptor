package cn.com.sparkle.raptor.test;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

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
		nsc.setTcpNoDelay(true);
		NioSocketClient client = new NioSocketClient(nsc);
		for(int i = 0 ; i < 1 ; i++){
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
			for(int i =0 ;i < 1;i++){
				nc.connect(new InetSocketAddress("127.0.0.1",1234), new TestClientHandler());
//				nc.connect(new InetSocketAddress("10.10.83.243",1234), new TestClientHandler());
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
	private ReentrantLock lock = new ReentrantLock();
	long ct = System.currentTimeMillis();
	int cc = 0;
	@Override
	public void onMessageRecieved(IoSession session, IoBuffer message) {
//		System.out.println("client recieve");
		Integer size = (Integer)session.attachment();
		if(size == null) size = 0;
		size += message.getByteBuffer().remaining();
		message.getByteBuffer().position(message.getByteBuffer().limit());
		session.attach(size);
		if(size != 1024) return;
		
		session.attach(0);
		IoBuffer temp = new AllocateBytesBuff(1024);
		temp.getByteBuffer().position(temp.getByteBuffer().limit());
		try {
			session.tryWrite(temp);
		} catch (SessionHavaClosedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		try{
			lock.lock();
			++cc;
			if(cc%1000 == 0){
				long tt = System.currentTimeMillis() - ct;
				System.out.println((cc*1000/tt) + "/s");
				ct = System.currentTimeMillis();
				cc = 1;
			}
		}finally{
			lock.unlock();
		}
		
	}

	@Override
	public void onMessageSent(IoSession session, IoBuffer message) {
		// TODO Auto-generated method stub
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
		
		IoBuffer iobuffer = new QuoteBytesBuff(buff, 0, buff.length);
		System.out.println("session open");
		iobuffer.getByteBuffer().position(iobuffer.getByteBuffer().limit());
		System.out.println(iobuffer.getByteBuffer().remaining());
		
		try {
			session.tryWrite(iobuffer);
		} catch (SessionHavaClosedException e) {
			e.printStackTrace();
		}
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
		e.printStackTrace();
	}
}