package cn.com.sparkle.raptor.test.perfomance;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

public class TestBlockSocketClient {

	/**
	 * @param args
	 * @throws IOException 
	 * @throws ClassNotFoundException 
	 */
	public static void main(String[] args) throws IOException, ClassNotFoundException {
		Socket s = new Socket();
//		s.connect(new InetSocketAddress("127.0.0.1", 1234));
//		s.connect(new InetSocketAddress("192.168.3.100", 1234));
//		s.connect(new InetSocketAddress("10.10.83.243", 1234));
		
		s.connect(new InetSocketAddress("10.232.128.11",1234));
		s.setTcpNoDelay(true);
//		s.setReceiveBufferSize(2048);
//		s.setSendBufferSize(2048);
//		ObjectOutputStream oos = new ObjectOutputStream(s.getOutputStream());
//		ObjectInputStream ois = new ObjectInputStream(s.getInputStream());
		InputStream is = s.getInputStream();
		OutputStream os = s.getOutputStream();
		long ct = System.currentTimeMillis();
		int cc = 0;
		byte[] b = new byte[128 *8];
		while(true){
			os.write(b);
			os.flush();
			
			
			int size = 0;
			while(true){
			size += is.read(b,size,b.length - size);
			if(size == b.length) break;
			}
			++cc;
			
			if(cc%10000 == 0){
				long now = System.currentTimeMillis();
				long tt = now - ct;
				System.out.println((cc*1000/tt) + "/s");
				ct = now;
				cc = 0;
			}
			
		}
	}

}
