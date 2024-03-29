package cn.com.sparkle.raptor.core.protocol;

import cn.com.sparkle.raptor.core.buff.IoBuffer;
import cn.com.sparkle.raptor.core.protocol.MultiThreadProtecolHandler.ProtocolHandlerIoSession;
import cn.com.sparkle.raptor.core.transport.socket.nio.IoSession;

public interface ProtocolHandler {
	public void onOneThreadSessionOpen(
			ProtocolHandlerIoSession session);

	public void onOneThreadSessionClose(
			ProtocolHandlerIoSession session);

	public void onOneThreadCatchException(IoSession ioSession,
			ProtocolHandlerIoSession attachment, Throwable e);

	public void onOneThreadMessageRecieved(Object receiveObject,
			ProtocolHandlerIoSession session);

	public void onOneThreadMessageSent(
			ProtocolHandlerIoSession session,int sendSize);
}
