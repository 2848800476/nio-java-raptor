package cn.com.sparkle.raptor.core.filter;

import cn.com.sparkle.raptor.core.buff.IoBuffer;
import cn.com.sparkle.raptor.core.handler.IoHandler;
import cn.com.sparkle.raptor.core.session.IoSession;

public final class FilterChain{
	private IoHandler handler;
	
	private NextFilter firstFilter = new NextFilter(new IoFilter() {
		@Override
		public void sent(IoSession session, IoBuffer message) {
		}
		@Override
		public void recieved(IoSession session, IoBuffer message) {
		}
	});
	private NextFilter beforeLast;
	private NextFilter endLast;
	public FilterChain(IoHandler handler){
		this.handler = handler;
		beforeLast = firstFilter;
		endLast = new NextFilter(null){
			@Override
			public void sent(IoSession session, IoBuffer message) {
				FilterChain.this.handler.onMessageSent(session, message);
			}
			@Override
			public void recieved(IoSession session, IoBuffer message) {
				FilterChain.this.handler.onMessageRecieved(session, message);
			}
		};
		beforeLast.setNextFilter(endLast);
	}
	public void addFilter(IoFilter filter){
		NextFilter temp = new NextFilter(filter);
		beforeLast.setNextFilter(temp);
		temp.setNextFilter(endLast);
		beforeLast = temp;
	}
	public void recieved(IoSession session,
			IoBuffer message) {
		firstFilter.recieved(session, message);
	}
	public void sent(IoSession session, IoBuffer message) {
		firstFilter.sent(session, message);
	}
	public IoHandler getHandler() {
		return handler;
	}
	public class NextFilter{
		private IoFilter wrapFilter;
		private NextFilter nextFilter;
		public NextFilter(IoFilter wrapFilter) {
			this.wrapFilter = wrapFilter;
		}
		public void recieved(IoSession session,
				IoBuffer message) {
			wrapFilter.recieved(session, message);
			nextFilter.recieved(session, message);
		}
		public void sent(IoSession session, IoBuffer message) {
			wrapFilter.sent(session, message);
			nextFilter.sent(session, message);
		}
		public void setNextFilter(NextFilter nextFilter){
			this.nextFilter = nextFilter;
		}
	}
}
