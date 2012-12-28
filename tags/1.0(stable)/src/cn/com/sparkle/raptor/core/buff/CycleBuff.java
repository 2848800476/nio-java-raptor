package cn.com.sparkle.raptor.core.buff;

public interface CycleBuff extends IoBuffer{
	public void close() ;
	public BuffPool getPool();
}
