package cn.com.sparkle.raptor.core.filter;

import cn.com.sparkle.raptor.core.buff.IoBuffer;
import cn.com.sparkle.raptor.core.session.IoSession;

public interface IoFilter {
	public void recieved(IoSession session , IoBuffer message);
	public void sent(IoSession session , IoBuffer message);
}
