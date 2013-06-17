package cn.com.sparkle.raptor.core.io;

import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedList;

import cn.com.sparkle.raptor.core.buff.BuffPool;
import cn.com.sparkle.raptor.core.buff.CycleBuff;
import cn.com.sparkle.raptor.core.buff.IoBuffer;

/**
 * This outputStream is not thread's safe.
 */
public class BufferPoolOutputStream extends OutputStream {

	private int writeCount = 0;
	private BuffPool pool;
	private IoBuffer cycleBuff;
	private LinkedList<IoBuffer> arrayList = new LinkedList<IoBuffer>();
	private int reserverPosition = -1;
	private int reserveSize;
	
	public BufferPoolOutputStream(BuffPool pool) {
		this(pool, 0, null);
	}

	public BufferPoolOutputStream(BuffPool pool, int reserveSize,
			IoBuffer ioBuffer) {
		this.reserveSize = reserveSize;
		this.pool = pool;
		cycleBuff = ioBuffer == null ? pool.get() : ioBuffer;
		arrayList.add(cycleBuff);
		while (reserveSize > cycleBuff.getByteBuffer().remaining()) {
			reserveSize -= cycleBuff.getByteBuffer().remaining();
			reserverPosition = cycleBuff.getByteBuffer().position();
			cycleBuff.getByteBuffer().position(cycleBuff.getByteBuffer().capacity());
			cycleBuff = pool.get();
			arrayList.add(cycleBuff);
		}
		if(reserverPosition == -1){
			reserverPosition = cycleBuff.getByteBuffer().position();
		}
		cycleBuff.getByteBuffer().position(cycleBuff.getByteBuffer().position() + reserveSize);
	}

	@Override
	public void write(int b) {
		cycleBuff.getByteBuffer().put((byte) b);
		++writeCount;
		if (cycleBuff.getByteBuffer().remaining() == 0) {
			cycleBuff = pool.get();
			arrayList.add(cycleBuff);
		}
	}

	@Override
	public void write(byte[] b, int off, int len) {
		while (len > 0) {
			int canWrite = Math.min(cycleBuff.getByteBuffer().remaining(), len);
			cycleBuff.getByteBuffer().put(b, off, canWrite);
			len -= canWrite;
			off += canWrite;
			writeCount += canWrite;
			if (cycleBuff.getByteBuffer().remaining() == 0) {
				cycleBuff = pool.get();
				arrayList.add(cycleBuff);
			}
		}
	}
	public void writeReserve(byte[] b,int off,int len){
		int writeLength = len > b.length - off ? b.length - off :len;
		if(writeLength > reserveSize){
			throw new RuntimeException("write count of byte more than the size of reservation");
		}
		boolean isFirst = true;
		int pos = reserverPosition;
		for(IoBuffer buff : arrayList){
			int canWrite;
			canWrite = Math.min(buff.getByteBuffer().capacity() - pos, writeLength);
			writeLength -= canWrite;
			for(int i = 0 ; i < canWrite ; i++){
				buff.getByteBuffer().put(pos + i, b[off]);
				++off;
			}
			pos = 0;
			if(writeLength == 0){
				break;
			}
		}
	}
	public IoBuffer[] getBuffArray() {
		return arrayList.toArray(new IoBuffer[arrayList.size()]);
	}

	public int getWriteCount() {
		return writeCount;
	}
}
