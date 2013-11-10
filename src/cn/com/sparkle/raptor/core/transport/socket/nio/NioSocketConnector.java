package cn.com.sparkle.raptor.core.transport.socket.nio;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

import cn.com.sparkle.raptor.core.collections.MaximumSizeArrayCycleQueue;
import cn.com.sparkle.raptor.core.delaycheck.DelayChecked;
import cn.com.sparkle.raptor.core.delaycheck.DelayCheckedTimer;
import cn.com.sparkle.raptor.core.handler.IoHandler;

public class NioSocketConnector {
	private Selector selector;
	private MultNioSocketProcessor multNioSocketProcessor;
	NioSocketConfigure nscfg;
	private MaximumSizeArrayCycleQueue<QueueBean> waitConnectQueue = new MaximumSizeArrayCycleQueue<NioSocketConnector.QueueBean>(
			NioSocketConnector.QueueBean.class, 100000);
	private DelayChecked checkRegisterConnecter;

	private class QueueBean {
		SocketChannel sc;
		IoHandler handler;
		Object attachment;
	}

	public NioSocketConnector(NioSocketConfigure nscfg) throws IOException {
		this.nscfg = nscfg;
		this.multNioSocketProcessor = new MultNioSocketProcessor(nscfg);
		selector = Selector.open();
		Thread t = new Thread(new Connector());
		t.setName("Raptor-Nio-Connector");
		t.setDaemon(nscfg.isDaemon());
		t.start();
		checkRegisterConnecter = new DelayChecked(
				nscfg.getRegisterConnecterDelay()) {
			@Override
			public void goToRun() {
				selector.wakeup();
			}
		};
		DelayCheckedTimer.addDelayCheck(checkRegisterConnecter);
	}

	public void registerConnector(SocketChannel sc, IoHandler handler)
			throws Exception {
		registerConnector(sc, handler, null);
	}

	public void registerConnector(SocketChannel sc, IoHandler handler,
			Object attachment) throws Exception {
		QueueBean a = new QueueBean();
		a.handler = handler;
		a.sc = sc;
		a.attachment = attachment;
		waitConnectQueue.push(a);
		checkRegisterConnecter.needRun();
	}

	class Connector implements Runnable {
		public void run() {
			while (true) {
				int i;
				try {
					i = selector.select(1);
				} catch (Throwable e) {
					throw new RuntimeException(e);
				}
				QueueBean qb;
				// long s = System.currentTimeMillis();
				while ((qb = waitConnectQueue.peek()) != null) {
					NioSocketProcessor processor = multNioSocketProcessor
							.getProcessor();
					IoSession session = new IoSession(processor, qb.sc,
							qb.handler);
					session.attach(qb.attachment);
					try {
						qb.sc.register(selector, SelectionKey.OP_CONNECT,
								session);
					} catch (ClosedChannelException e) {
						qb.handler.catchException(session, e);
					}
					waitConnectQueue.poll();
				}

				if (i > 0) {

					Iterator<SelectionKey> iter = selector.selectedKeys()
							.iterator();
					while (iter.hasNext()) {
						SelectionKey key = iter.next();
						iter.remove();
						if (key.isConnectable()) {
							key.cancel();
							SocketChannel sc = (SocketChannel) key.channel();
							IoSession session = (IoSession) key.attachment();
							try {
								if (sc.finishConnect()) {
									session.getProcessor()
											.registerRead(session);
									session.getHandler().onSessionOpened(
											session);
								}
							} catch (Exception e) {
								session.getHandler().catchException(session, e);
								try {
									sc.close();
								} catch (Exception ee) {
								}
							}
						}
					}
				}
				// System.out.println("cost:"+(System.currentTimeMillis() - s));

			}
		}
	}
}
