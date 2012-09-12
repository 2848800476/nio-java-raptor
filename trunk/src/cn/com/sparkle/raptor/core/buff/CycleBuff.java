package cn.com.sparkle.raptor.core.buff;

public class CycleBuff extends AllocateBytesBuff{
	private CycleQuoteBytesBuffPool pool;
	public CycleBuff(int capacity,CycleQuoteBytesBuffPool pool) {
		super(capacity);
		this.pool = pool;
	}
	public void close() {
		pool.close(this);
	}
}
