package cn.com.sparkle.raptor.core.buff;

import java.nio.ByteBuffer;

public class CycleBuffArray {
	private CycleBuff[] cycleBuffArray;
	private int flag = 0;
	public CycleBuffArray(CycleBuff[] cycleBuffArray) {
		this.cycleBuffArray = cycleBuffArray;
	}
	public CycleBuff[] getCycleBuffArray() {
		return cycleBuffArray;
	}
	public void put(byte[] src,int offset,int length){
		for(flag = 0 ; flag < cycleBuffArray.length ;flag++){
			ByteBuffer byteBuffer = cycleBuffArray[flag].getByteBuffer();
			int canWriteLength = length > byteBuffer.remaining() ? byteBuffer.remaining() : length;
			try{
			byteBuffer.put(src, offset, canWriteLength);
			}catch(java.lang.RuntimeException e){
				System.out.println(src.length + "  " + offset + "   "+canWriteLength + "   " + byteBuffer.remaining() );
				throw e;
			}
			length -= canWriteLength;
			if(length == 0) return;
			offset += canWriteLength;
		}
	}
	public void put(byte b){
		byte[] bs = new byte[1];
		bs[0] = b;
		put(bs, 0, 1);
	}
	public void put(byte[] b){
		put(b,0,b.length);
	}
}
