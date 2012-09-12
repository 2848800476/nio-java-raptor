package cn.com.sparkle.raptor.core.buff;

import cn.com.sparkle.raptor.core.collections.MaximumSizeArrayCycleQueue;
import cn.com.sparkle.raptor.core.collections.MaximumSizeArrayCycleQueue.QueueFullException;

public final class CycleQuoteBytesBuffPool {
	public MaximumSizeArrayCycleQueue<CycleBuff> queue;
	public CycleQuoteBytesBuffPool(int totalCellSize,int cellCapacity){
		queue = new MaximumSizeArrayCycleQueue<CycleBuff>(totalCellSize);
		for(int i = 0 ; i < totalCellSize ; i++){
			try {
				queue.push(new CycleBuff(cellCapacity, this));
			} catch (QueueFullException e) {
				e.printStackTrace();
			}
		}
	}
	void close(CycleBuff buff){
		try {
			buff.getByteBuffer().clear();
			queue.push(buff);
		} catch (QueueFullException e) {
			e.printStackTrace();
		}
	}
	public CycleBuff get(){
		CycleBuff buff = queue.peek();
		if(buff != null){
			queue.poll();
		}
		return buff;
	}
}
