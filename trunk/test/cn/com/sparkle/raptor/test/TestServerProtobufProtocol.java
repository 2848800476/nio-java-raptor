package cn.com.sparkle.raptor.test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import cn.com.sparkle.raptor.core.collections.MaximumSizeArrayCycleQueue.QueueFullException;
import cn.com.sparkle.raptor.core.protocol.MultiThreadProtecolHandler;
import cn.com.sparkle.raptor.core.protocol.MultiThreadProtecolHandler.ProtocolHandlerIoSession;
import cn.com.sparkle.raptor.core.protocol.ProtocolHandler;
import cn.com.sparkle.raptor.core.protocol.javaobject.ObjectProtocol;
import cn.com.sparkle.raptor.core.protocol.protobuf.ProtoBufProtocol;
import cn.com.sparkle.raptor.core.transport.socket.nio.IoSession;
import cn.com.sparkle.raptor.core.transport.socket.nio.NioSocketConfigure;
import cn.com.sparkle.raptor.core.transport.socket.nio.NioSocketServer;
import cn.com.sparkle.raptor.core.transport.socket.nio.exception.SessionHavaClosedException;
import cn.com.sparkle.raptor.test.model.protocolbuffer.PersonMessage;
import cn.com.sparkle.raptor.test.model.protocolbuffer.PersonMessage.AddressBook;
import cn.com.sparkle.raptor.test.model.protocolbuffer.PersonMessage.Person;

public class TestServerProtobufProtocol {

	/**
	 * @param args
	 * @throws IOException 
	 * @throws QueueFullException 
	 */
	public static void main(String[] args) throws IOException, QueueFullException {
		// TODO Auto-generated method stub
		NioSocketConfigure nsc = new NioSocketConfigure();
		nsc.setProcessorNum(2);
		nsc.setCycleRecieveBuffCellSize(10000);
		nsc.setTcpNoDelay(true);
		nsc.setReuseAddress(true);
		nsc.setAsyncTransportOptimize(true);
//		nsc.setRecieveBuffSize(32* 1024);
//		nsc.setSentBuffSize( 8 * 1024);
		//nsc.setRevieveBuffSize(1024 * 2048);
		//nsc.setTcpNoDelay(true);
		ProtoBufProtocol protocol = new ProtoBufProtocol();
		protocol.registerMessage(1, PersonMessage.AddressBook.getDefaultInstance());
		protocol.registerMessage(2, PersonMessage.Person.getDefaultInstance());
		NioSocketServer server = new NioSocketServer(nsc);
		server.bind(new InetSocketAddress(1234),new MultiThreadProtecolHandler(5000,16 * 1024, 20, 300, 60, TimeUnit.SECONDS,protocol, new ProtobufProtocolHandler()));
//		server.bind(new InetSocketAddress(12345),new FilterChain(new TestHandler()));
	}
	
}
class ProtobufProtocolHandler implements ProtocolHandler{
	private int i = 0;
	private String soure = "��ã�Mr server !This is client  cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc             !write package";

	@Override
	public void onOneThreadMessageRecieved(Object receiveObject,
			ProtocolHandlerIoSession session) {
		try {
			Person p = (Person)receiveObject;
//			System.out.println(p.getId());
			
			Person.Builder builder = Person.newBuilder().setId(p.getId()).setName(soure);
			AddressBook.Builder ab = AddressBook.newBuilder().addPerson(builder);
			session.writeObject( builder.build() );
//			session.writeObject("��ã�");
		} catch (SessionHavaClosedException e) {
		}
	}


	@Override
	public void onOneThreadSessionOpen(ProtocolHandlerIoSession session) {
		// TODO Auto-generated method stub
		
	}

	AtomicInteger ai = new AtomicInteger();
	@Override
	public void onOneThreadSessionClose(ProtocolHandlerIoSession session) {
		System.out.println("disconnect" + ai.addAndGet(1));
	}


	@Override
	public void onOneThreadCatchException(IoSession ioSession,
			ProtocolHandlerIoSession attachment, Throwable e) {
		e.printStackTrace();
		
	}




	@Override
	public void onOneThreadMessageSent(ProtocolHandlerIoSession session,int sendSize) {
		// TODO Auto-generated method stub
		
	}


	
	
}