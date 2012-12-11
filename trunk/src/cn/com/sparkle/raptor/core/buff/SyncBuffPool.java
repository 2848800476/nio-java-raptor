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
			CycleBuff buff = super.tryGet();
			if(buff == null){
				try {
					empty.await();
				} catch (InterruptedException e) {
				}
				buff = super.tryGet();
			}
			
			return buff;
		}finally{
			lock.unlock();
		}
	}
	@Override
	public CycleBuffArray get(int byteSize) {
		lock.lock();
		try{
			int size = byteSize / this.getCellCapacity() + (byteSize % this.getCellCapacity() == 0 ? 0 :1);
			if(this.getTotalCellSize() < size) throw new RuntimeException("this size of need is more than the capacity of pool!you need increase totalCellSize");
			while(true){
				if(this.queue.size() < size){
					try {
						empty.await();
					} catch (InterruptedException e) {
					}
				}else{
					return super.get(byteSize);
				}
			}
		}finally{
			lock.unlock();
		}
		
	}
}
