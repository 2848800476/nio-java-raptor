package cn.com.sparkle.raptor.core.transport.socket.nio;

import java.io.IOException;
import java.nio.channels.SocketChannel;

import cn.com.sparkle.raptor.core.filter.FilterChain;
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
    public void addSession(FilterChain filterChain,SocketChannel sc){
        currentFlag = (currentFlag+1) % nioSocketProcessors.length;
        IoSession session = new IoSession(nioSocketProcessors[currentFlag],sc,filterChain);
        nioSocketProcessors[currentFlag].registerRead(session);
        filterChain.getHandler().onSessionOpened(session);
    }
	
}
