package cn.com.sparkle.raptor.core.buff;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class SyncBuffPool extends CycleAllocateBytesBuffPool{
	public SyncBuffPool(int totalCellSize, int cellCapacity) {
		super(totalCellSize, cellCapacity);
	}
	private ReentrantLock lock = new ReentrantLock();
	private Condition empty = lock.newCondition();
	@Override
	public void close(CycleBuff buff) {
		lock.lock();
		try{
			super.close(buff);
			empty.signal();
		}finally{
			lock.unlock();
		}

	}

	@Override
	public CycleBuff get() {
		lock.lock();
		try{
			CycleBuff buff = super.get();
			if(buff == null){
				try {
					empty.await();
				} catch (InterruptedException e) {
				}
				buff = super.get();
			}
			
			return buff;
		}finally{
			lock.unlock();
		}
	}
}
