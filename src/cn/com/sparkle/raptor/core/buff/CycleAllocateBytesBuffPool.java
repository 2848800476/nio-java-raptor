package cn.com.sparkle.raptor.core.buff;

import java.util.concurrent.locks.ReentrantLock;

import cn.com.sparkle.raptor.core.collections.MaximumSizeArrayCycleQueue;
import cn.com.sparkle.raptor.core.collections.MaximumSizeArrayCycleQueue.QueueFullException;

public class CycleAllocateBytesBuffPool implements BuffPool {
	protected MaximumSizeArrayCycleQueue<CycleBuff> queue;
	private int cellCapacity;
	private int totalCellSize;
	
	private ReentrantLock lock = new ReentrantLock();
	
	public CycleAllocateBytesBuffPool(int totalCellSize, int cellCapacity) {
		this.cellCapacity = cellCapacity;
		this.totalCellSize = totalCellSize;
		queue = new MaximumSizeArrayCycleQueue<CycleBuff>(totalCellSize);

		for (int i = 0; i < totalCellSize; i++) {
			try {
				queue.push(new CycleAllocateBuff(this, cellCapacity,true));
			} catch (QueueFullException e) {
				e.printStackTrace();
			}
		}

	}

	public void close(CycleBuff buff) {
		try {
			lock.lock();
			if (buff.getPool() != this) {
				return;
			}
			buff.getByteBuffer().clear();
			queue.push(buff);
		} catch (QueueFullException e) {
			e.printStackTrace();
		}finally{
			lock.unlock();
		}
	}

	public CycleBuff get() {
		while (true) {
			CycleBuff buff = tryGet();
			if (buff != null) {
				return buff;
			} else {
				try {
					Thread.sleep(1);
				} catch (InterruptedException e) {
				}
			}
		}
	}

	public CycleBuff tryGet() {
		CycleBuff buff = queue.peek();
		if (buff != null) {
			queue.poll();
		}
		return buff;
	}

	@Override
	public IoBufferArray get(int byteSize) {
		int size = byteSize / cellCapacity
				+ (byteSize % cellCapacity == 0 ? 0 : 1);
		if (totalCellSize < size)
			throw new RuntimeException(
					"this size of need is more than the capacity of pool!you need increase totalCellSize");
		CycleBuff[] buff = new CycleBuff[size];
		for (int i = 0; i < size; i++) {
			while (true) {
				buff[i] = queue.peek();
				if (buff[i] != null) {
					queue.poll();
					break;
				} else {
					try {
						Thread.sleep(1);
					} catch (InterruptedException e) {
					}
				}
			}
		}
		System.out.println("get size:" + queue.size());
		return new IoBufferArray(buff);
	}
	public int size(){
		return queue.size();
	}
	public int getCellCapacity() {
		return cellCapacity;
	}

	public int getTotalCellSize() {
		return totalCellSize;
	}

}
