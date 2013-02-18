package cn.com.sparkle.raptor.core.buff;

import java.nio.ByteBuffer;

public class AllocateBytesBuff extends AbstractIoBuffer {
	public AllocateBytesBuff(int capacity) {
		bb = ByteBuffer.allocate(capacity);
	}
}
