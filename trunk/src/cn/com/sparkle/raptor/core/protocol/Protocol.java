package cn.com.sparkle.raptor.core.protocol;

import cn.com.sparkle.raptor.core.buff.BuffPool;
import cn.com.sparkle.raptor.core.buff.IoBuffer;
import cn.com.sparkle.raptor.core.protocol.MultiThreadProtecolHandler.ProtocolHandlerIoSession;

public interface Protocol {
	public void init(ProtocolHandlerIoSession mySession);

	/**
	 * Notification: The method may be invoked twice with a single IoBuffer as
	 * the parameter.
	 * 
	 * @param attachment
	 * @param buff
	 * @return
	 */
	public Object decode(ProtocolHandlerIoSession mySession, IoBuffer buff);

	public IoBuffer[] encode(BuffPool buffpool, Object message);
	
	public IoBuffer[] encode(BuffPool buffpool,Object message,IoBuffer lastWaitSendBuff);

}
