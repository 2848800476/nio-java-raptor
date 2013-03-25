package cn.com.sparkle.raptor.core.buff;

public class CycleAllocateBuff extends AllocateBytesBuff implements CycleBuff {
	BuffPool pool;

	public CycleAllocateBuff(BuffPool pool, int capacity ,boolean isDirectMem) {
		super(capacity,isDirectMem);
		this.pool = pool;
	}

	@Override
	public void close() {
		if(pool != null){
			pool.close(this);
		}
	}

	@Override
	public BuffPool getPool() {
		return pool;
	}

}
