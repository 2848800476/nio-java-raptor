package cn.com.sparkle.raptor.core.protocol;

import cn.com.sparkle.raptor.core.buff.SyncBuffPool;
import cn.com.sparkle.raptor.core.session.IoSession;

public interface ProtocolHandler {
	public void onOneThreadSessionOpen(SyncBuffPool buffPool,Protocol protocol,IoSession session);

	public void onOneThreadSessionClose(IoSession session);

	public void onOneThreadCatchException(IoSession session, Throwable e);

	public void onOneThreadMessageRecieved(SyncBuffPool buffPool,Protocol protocol,
			IoSession session, Object o);

	public void onOneThreadMessageSent(SyncBuffPool buffPool,Protocol protocol,
			IoSession session);
}
