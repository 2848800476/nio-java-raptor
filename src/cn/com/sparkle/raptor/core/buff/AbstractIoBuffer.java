package cn.com.sparkle.raptor.core.buff;

import java.nio.ByteBuffer;

public abstract class AbstractIoBuffer implements IoBuffer {
	protected ByteBuffer bb;

	private Thread t;
//	private StackTraceElement[] stacks;
	
	public ByteBuffer getByteBuffer() {
		t = Thread.currentThread();
//		stacks = t.getStackTrace();
		return bb;
	};
	public Thread getLastGetThread(){
		return t;
	}
//	public StackTraceElement[] getStacks(){
//		return stacks;
//	}
}
