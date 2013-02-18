package cn.com.sparkle.raptor.core.protocol;

import cn.com.sparkle.raptor.core.buff.SyncBuffPool;
import cn.com.sparkle.raptor.core.protocol.MultiThreadProtecolHandler.ProtecolHandlerAttachment;
import cn.com.sparkle.raptor.core.session.IoSession;

public interface ProtocolHandler {
	public void onOneThreadSessionOpen(SyncBuffPool buffPool,
			Protocol protocol, IoSession session,
			ProtecolHandlerAttachment attachment);

	public void onOneThreadSessionClose(IoSession session,
			ProtecolHandlerAttachment attachment);

	public void onOneThreadCatchException(IoSession session,
			ProtecolHandlerAttachment attachment, Throwable e);

	public void onOneThreadMessageRecieved(SyncBuffPool buffPool,
			Protocol protocol, IoSession session, Object receiveObject,
			ProtecolHandlerAttachment attachment);

	public void onOneThreadMessageSent(SyncBuffPool buffPool,
			Protocol protocol, IoSession session,
			ProtecolHandlerAttachment attachment);
}
