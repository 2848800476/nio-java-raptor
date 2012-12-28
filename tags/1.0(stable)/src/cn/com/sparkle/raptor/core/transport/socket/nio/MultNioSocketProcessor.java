package cn.com.sparkle.raptor.core.transport.socket.nio;

import java.io.IOException;
import java.nio.channels.SocketChannel;

import cn.com.sparkle.raptor.core.handler.IoHandler;
import cn.com.sparkle.raptor.core.session.IoSession;

public class MultNioSocketProcessor {
	NioSocketProcessor[] nioSocketProcessors;
	int currentFlag = 0;
    public MultNioSocketProcessor(NioSocketConfigure nscfg) throws IOException{
        nioSocketProcessors = new NioSocketProcessor[nscfg.getProcessorNum()];
        for(int i = 0 ; i < nscfg.getProcessorNum() ; i++){
            nioSocketProcessors[i] = new NioSocketProcessor(nscfg);
        }
	}
    public void addSession(IoHandler handler,SocketChannel sc){
        currentFlag = (currentFlag+1) % nioSocketProcessors.length;
        IoSession session = new IoSession(nioSocketProcessors[currentFlag],sc,handler);
        nioSocketProcessors[currentFlag].registerRead(session);
        handler.onSessionOpened(session);
    }
	
}
