package cn.com.sparkle.raptor.core.transport.socket.nio;

import java.io.IOException;
import java.nio.channels.Selector;

import cn.com.sparkle.raptor.core.buff.CycleBuff;
import cn.com.sparkle.raptor.core.collections.MaximumSizeArrayCycleQueue;
import cn.com.sparkle.raptor.core.collections.MaximumSizeArrayCycleQueue.QueueFullException;
import cn.com.sparkle.raptor.core.session.IoSession;

public class RecieveMessageDealer extends Thread {
	private MaximumSizeArrayCycleQueue<DealerQueueBean> queue;
	Selector selector = null;
	public RecieveMessageDealer(int queueSize) {
		queue = new MaximumSizeArrayCycleQueue<RecieveMessageDealer.DealerQueueBean>(queueSize);
		try {
			selector = Selector.open();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}
	public void register(IoSession session,CycleBuff buff){
		try {
			queue.push(new DealerQueueBean(session, buff));
			selector.wakeup();
		} catch (QueueFullException e) {
			e.printStackTrace();
		}
	}
	@Override
	public void run() {
		DealerQueueBean bean;
		while(true){
			try {
				selector.select(1);
				bean = queue.peek();
				
				if(bean != null){
					bean.session.getHandler().onMessageRecieved(bean.session, bean.buff);
					if(!bean.buff.getByteBuffer().hasRemaining()){
						bean.buff.close();
					}
					queue.poll();
				}
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}
	}
	public static class DealerQueueBean{
		private IoSession session;
		private CycleBuff buff;
		public DealerQueueBean(IoSession session, CycleBuff buff) {
			this.session = session;
			this.buff = buff;
		}
	}
}
