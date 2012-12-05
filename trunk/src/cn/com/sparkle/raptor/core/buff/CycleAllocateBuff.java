package cn.com.sparkle.raptor.core.buff;

public class CycleAllocateBuff extends AllocateBytesBuff implements CycleBuff {
	BuffPool pool;
	public CycleAllocateBuff(BuffPool pool,int capacity) {
		super(capacity);
		this.pool = pool;
	}
	@Override
	public void close() {
		pool.close(this);
	}

	@Override
	public BuffPool getPool() {
		return pool;
	}

}
