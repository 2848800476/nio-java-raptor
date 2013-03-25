package cn.com.sparkle.raptor.test;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import cn.com.sparkle.raptor.core.buff.IoBuffer;
import cn.com.sparkle.raptor.core.buff.SyncBuffPool;
import cn.com.sparkle.raptor.core.handler.IoHandler;
import cn.com.sparkle.raptor.core.protocol.MultiThreadProtecolHandler;
import cn.com.sparkle.raptor.core.protocol.MultiThreadProtecolHandler.ProtecolHandlerAttachment;
import cn.com.sparkle.raptor.core.protocol.Protocol;
import cn.com.sparkle.raptor.core.protocol.ProtocolHandler;
import cn.com.sparkle.raptor.core.protocol.javaobject.ObjectProtocol;
import cn.com.sparkle.raptor.core.session.IoSession;
import cn.com.sparkle.raptor.core.transport.socket.nio.NioSocketClient;
import cn.com.sparkle.raptor.core.transport.socket.nio.NioSocketConfigure;
import cn.com.sparkle.raptor.core.transport.socket.nio.exception.SessionHavaClosedException;

public class TestClientObjectProtocol {
	public static void main(String[] args) throws Exception {
		NioSocketConfigure nsc = new NioSocketConfigure();
		NioSocketClient client = new NioSocketClient(nsc);
		nsc.setProcessorNum(1);
		nsc.setCycleRecieveBuffCellSize(10000);
		nsc.setCycleSendBuffCellSize(10000);
		IoHandler handler = new MultiThreadProtecolHandler(1000, 1024, 20, 300, 60, TimeUnit.SECONDS,new ObjectProtocol(), new TestProtocolObjetClientHandler());
		for(int i = 0 ; i < 100 ; i++){
//			client.connect(new InetSocketAddress("10.10.83.243",1234), handler,"aaa" + i);
			client.connect(new InetSocketAddress("127.0.0.1",1234),handler,"aaa" + i );
		}
	}

}
class TestProtocolObjetClientHandler implements ProtocolHandler{
	private static AtomicInteger flag = new AtomicInteger(0);
	private int i = 0;
	@Override
	public void onOneThreadSessionOpen(SyncBuffPool buffPool,
			Protocol protocol, IoSession session,ProtecolHandlerAttachment attachment) {
		try {
			System.out.println("init attachment:" + attachment.customAttachment);
//			ProtecolHandlerAttachment att = (ProtecolHandlerAttachment)session.attachment();
			attachment.customAttachment = Integer.valueOf(flag.addAndGet(1));
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
	public void onOneThreadSessionClose(IoSession session,ProtecolHandlerAttachment attachment) {
		System.out.println("close" + attachment.customAttachment);
	}

	@Override
	public void onOneThreadCatchException(IoSession session,ProtecolHandlerAttachment attachment, Throwable e) {
		e.printStackTrace();
	}
	private int cc = 0 ;
	private long ct = System.currentTimeMillis();
	private ReentrantLock lock = new ReentrantLock();
	@Override
	public void onOneThreadMessageRecieved(SyncBuffPool buffPool,
			Protocol protocol, IoSession session, Object o,ProtecolHandlerAttachment attachment) {
//		System.out.println(o);
		try {
			IoBuffer[] buffa = protocol.encode(buffPool, "ÄãºÃ£¡Mr server ccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc                                                                                                                                                                                                                                                    !This is client" + attachment.customAttachment + "!write package" + (++i));
//			System.out.println(buffa[0].getByteBuffer().capacity() - buffa[0].getByteBuffer().remaining());
			session.write(buffa);
	} catch (SessionHavaClosedException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
		try{
			lock.lock();
			++cc;
			if(cc%10000 == 0){
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
	public void onOneThreadMessageSent(SyncBuffPool buffPool,
			Protocol protocol, IoSession session,ProtecolHandlerAttachment attachment) {
		
	}
	
}