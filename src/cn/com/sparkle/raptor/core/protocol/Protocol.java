package cn.com.sparkle.raptor.core.protocol;



import cn.com.sparkle.raptor.core.buff.BuffPool;
import cn.com.sparkle.raptor.core.buff.IoBuffer;
import cn.com.sparkle.raptor.core.protocol.MultiThreadProtecolHandler.ProtecolHandlerAttachment;

public interface Protocol {
	public void init(ProtecolHandlerAttachment attachment);
	/**
	 * Notification:
	 *  The method may be invoked twice with a single IoBuffer as the parameter.
	 * @param attachment
	 * @param buff
	 * @return
	 */
	public Object decode(ProtecolHandlerAttachment attachment,IoBuffer buff);
	public IoBuffer[] encode(BuffPool Buffpool,Object message);
	
}
