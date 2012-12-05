package cn.com.sparkle.raptor.core.buff;

import cn.com.sparkle.raptor.core.collections.MaximumSizeArrayCycleQueue;
import cn.com.sparkle.raptor.core.collections.MaximumSizeArrayCycleQueue.QueueFullException;

public class CycleAllocateBytesBuffPool implements BuffPool {
	public MaximumSizeArrayCycleQueue<CycleBuff> queue;
	private int cellCapacity;
	public CycleAllocateBytesBuffPool(int totalCellSize,int cellCapacity){
		this.cellCapacity =  cellCapacity;
		queue = new MaximumSizeArrayCycleQueue<CycleBuff>(totalCellSize);
		
		for(int i = 0 ; i < totalCellSize ; i++ ){
			try {
				queue.push(new CycleAllocateBuff(this,cellCapacity));
			} catch (QueueFullException e) {
				e.printStackTrace();
			}
		}
		
	}
	public void close(CycleBuff buff){
		try {
			if(buff.getPool() != this){
				return;
			}
			buff.getByteBuffer().clear();
				queue.push(buff);
		} catch (QueueFullException e) {
			e.printStackTrace();
		}
	}
	public CycleBuff get(){
		while(true){
			CycleBuff buff = queue.peek();
			if(buff != null){
				queue.poll();
				return buff;
			}else{
				try {
					Thread.sleep(1);
				} catch (InterruptedException e) {
				}
			}
		}
	}
	@Override
	public CycleBuffArray get(int byteSize) {
		int size = byteSize / cellCapacity + (byteSize % cellCapacity == 0 ? 0 :1);
		CycleBuff[] buff = new CycleBuff[size];
		for(int i = 0 ; i < size ; i++){
			while(true){
				buff[i] = queue.peek();
				if(buff[i] != null){
					queue.poll();
					break;
				}else{
					try {
						Thread.sleep(1);
					} catch (InterruptedException e) {
					}
				}
			}
		}
		return new CycleBuffArray(buff);
	}
}
