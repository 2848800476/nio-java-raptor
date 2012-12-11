package cn.com.sparkle.raptor.core.buff;

public interface BuffPool {
	public void close(CycleBuff buff);
	public CycleBuffArray get(int byteSize);
	public CycleBuff get();
	public CycleBuff tryGet();
}
