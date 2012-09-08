package cn.com.sparkle.raptor.core.handler;

import cn.com.sparkle.raptor.core.buff.IoBuffer;
import cn.com.sparkle.raptor.core.session.IoSession;

public interface IoHandler {
	public void onSessionOpened(IoSession session);
	public void onSessionClose(IoSession session);
	public void onMessageRecieved(IoSession session,IoBuffer message);
	public void onMessageSent(IoSession session,IoBuffer message);
	public void catchException(IoSession session,Throwable e);
}
