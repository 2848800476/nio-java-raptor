package cn.com.sparkle.raptor.core.protocol.javaobject;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.LinkedList;

import cn.com.sparkle.raptor.core.buff.BuffPool;
import cn.com.sparkle.raptor.core.buff.IoBuffer;
import cn.com.sparkle.raptor.core.io.BufferPoolOutputStream;
import cn.com.sparkle.raptor.core.io.IoBufferArrayInputStream;
import cn.com.sparkle.raptor.core.protocol.DecodeException;
import cn.com.sparkle.raptor.core.protocol.EncodeException;
import cn.com.sparkle.raptor.core.protocol.Protocol;
import cn.com.sparkle.raptor.core.protocol.MultiThreadProtecolHandler.ProtecolHandlerAttachment;

public class ObjectProtocol implements Protocol {

	@Override
	public void init(ProtecolHandlerAttachment attachment) {
		attachment.protocolAttachment = new ObjectProtocolCacheBean();
	}
	private static class ObjectProtocolCacheBean{
		private int curPackageSize = -1;
		private int recieveSize = 0;
		private LinkedList<IoBuffer> buff = new LinkedList<IoBuffer>();
	}
	private int readInt(ObjectProtocolCacheBean bean){
		if(bean.recieveSize >=4){
			bean.recieveSize -= 4;
			IoBuffer buff = bean.buff.getFirst();
			int r = 0;
			for(int i = 0 ; i < 4; i++){
				r =r | ((buff.getByteBuffer().get() & 0xff) << ((3-i)*8));
				while(!buff.getByteBuffer().hasRemaining()){
					bean.buff.removeFirst();
					buff = bean.buff.getFirst();
				}
			}
			return r;
		}else return -1;
	}
	@Override
	public Object decode(ProtecolHandlerAttachment attachment, IoBuffer buff) {
		ObjectProtocolCacheBean bean = (ObjectProtocolCacheBean)attachment.protocolAttachment;
		if(bean.buff.size() == 0 || bean.buff.getLast() != buff){
			bean.buff.addLast(buff);
			bean.recieveSize += buff.getByteBuffer().remaining();
		}
		if(bean.curPackageSize == -1){
			bean.curPackageSize = readInt(bean);
			if(bean.curPackageSize == -1) return null;
		}
		if(bean.recieveSize >= bean.curPackageSize){
			ObjectInputStream is = null;
			try {
				is = new ObjectInputStream(new IoBufferArrayInputStream(bean.buff.toArray(new IoBuffer[bean.buff.size()]),bean.curPackageSize));
				Object o = is.readObject();
				bean.recieveSize -= bean.curPackageSize;
				bean.curPackageSize = -1;
				//remove and close IoBuffer that has been unuseful.
				while(bean.buff.size() > 0 && !bean.buff.getFirst().getByteBuffer().hasRemaining()){
					bean.buff.removeFirst();
				}
				return o;
			} catch (IOException e) {
				throw new DecodeException(e);
			} catch (ClassNotFoundException e) {
				throw new DecodeException(e);
			}finally{
				try {
					is.close();
				} catch (Exception e) {
				}
			}
		}
		return null;
	}

	@Override
	public IoBuffer[] encode(BuffPool buffpool, Object message) {
		ObjectOutputStream out = null;
		try{
			BufferPoolOutputStream bufferPoolOutputStream = new BufferPoolOutputStream(buffpool,4);
			out = new ObjectOutputStream( bufferPoolOutputStream);
			out.writeObject(message);
			out.flush();
			IoBuffer[] result = bufferPoolOutputStream.getCycleBuffArray();
			//write count to buff
			int writeCount = bufferPoolOutputStream.getCount();
			result[0].getByteBuffer().put(0, (byte)( writeCount >> 24));
			result[0].getByteBuffer().put(1, (byte)( writeCount >> 16));
			result[0].getByteBuffer().put(2, (byte)( writeCount >> 8));
			result[0].getByteBuffer().put(3, (byte)( writeCount ));
			return result;
		} catch (IOException e) {
			throw new EncodeException(e);
		}finally{
			try {
				out.close();
			} catch (Exception e) {
			}
		}
	}
}
