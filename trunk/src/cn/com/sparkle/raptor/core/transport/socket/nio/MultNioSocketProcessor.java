package cn.com.sparkle.raptor.core.transport.socket.nio;

import java.io.IOException;
import java.nio.channels.SocketChannel;

import cn.com.sparkle.raptor.core.buff.SyncBuffPool;
import cn.com.sparkle.raptor.core.handler.IoHandler;

public class MultNioSocketProcessor {
	NioSocketProcessor[] nioSocketProcessors;
	int currentFlag = 0;

	public MultNioSocketProcessor(NioSocketConfigure nscfg) throws IOException {
		nioSocketProcessors = new NioSocketProcessor[nscfg.getProcessorNum()];
		SyncBuffPool mempool = new SyncBuffPool(nscfg.getCycleRecieveBuffCellSize(), nscfg.getRecieveBuffSize() * 2 / 3);
		for (int i = 0; i < nscfg.getProcessorNum(); i++) {
			nioSocketProcessors[i] = new NioSocketProcessor(nscfg,mempool);
		}
	}

	public NioSocketProcessor getProcessor() {
		currentFlag = (currentFlag + 1) % nioSocketProcessors.length;
		return nioSocketProcessors[currentFlag];
	}

}
