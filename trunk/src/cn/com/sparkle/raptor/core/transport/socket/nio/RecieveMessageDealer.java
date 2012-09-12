package cn.com.sparkle.raptor.core.transport.socket.nio;

import cn.com.sparkle.raptor.core.buff.CycleBuff;
import cn.com.sparkle.raptor.core.collections.MaximumSizeArrayCycleQueue;
import cn.com.sparkle.raptor.core.collections.MaximumSizeArrayCycleQueue.QueueFullException;
import cn.com.sparkle.raptor.core.session.IoSession;

public class RecieveMessageDealer extends Thread {
	private MaximumSizeArrayCycleQueue<DealerQueueBean> queue;
	public RecieveMessageDealer(int queueSize) {
		queue = new MaximumSizeArrayCycleQueue<RecieveMessageDealer.DealerQueueBean>(queueSize);
	}
	public void register(IoSession session,CycleBuff buff){
		try {
			queue.push(new DealerQueueBean(session, buff));
		} catch (QueueFullException e) {
			e.printStackTrace();
		}
	}
	@Override
	public void run() {
		DealerQueueBean bean;
		while(true){
			bean = queue.peek();
			if(bean != null){
				bean.session.getFilterChain().recieved(bean.session, bean.buff);
				bean.buff.close();
				queue.poll();
			}else{
				try {
					Thread.sleep(1);
				} catch (InterruptedException e) {
				}
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
