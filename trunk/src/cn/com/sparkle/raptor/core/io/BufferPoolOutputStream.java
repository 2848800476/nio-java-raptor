package cn.com.sparkle.raptor.core.io;

import java.io.OutputStream;
import java.util.LinkedList;

import cn.com.sparkle.raptor.core.buff.BuffPool;
import cn.com.sparkle.raptor.core.buff.CycleBuff;

/**
 * This outputStream is not thread's safe.
 */
public class BufferPoolOutputStream extends OutputStream {

	private int count = 0;
	private BuffPool pool;
	private CycleBuff cycleBuff;
	private LinkedList<CycleBuff> arrayList = new LinkedList<CycleBuff>();

	public BufferPoolOutputStream(BuffPool pool) {
		this(pool, 0);
	}

	public BufferPoolOutputStream(BuffPool pool, int reserveSize) {
		this.pool = pool;
		cycleBuff = pool.get();
		arrayList.add(cycleBuff);
		while (reserveSize > cycleBuff.getByteBuffer().remaining()) {
			reserveSize -= cycleBuff.getByteBuffer().remaining();
			cycleBuff = pool.get();
			arrayList.add(cycleBuff);
		}
		cycleBuff.getByteBuffer().position(
				cycleBuff.getByteBuffer().position() + reserveSize);
	}

	@Override
	public void write(int b) {
		cycleBuff.getByteBuffer().put((byte) b);
		++count;
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
			count += canWrite;
			if (cycleBuff.getByteBuffer().remaining() == 0) {
				cycleBuff = pool.get();
				arrayList.add(cycleBuff);
			}
		}
	}

	public CycleBuff[] getCycleBuffArray() {
		return arrayList.toArray(new CycleBuff[arrayList.size()]);
	}

	public int getCount() {
		return count;
	}
}
