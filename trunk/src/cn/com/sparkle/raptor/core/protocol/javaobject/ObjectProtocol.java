package cn.com.sparkle.raptor.core.protocol.javaobject;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.LinkedList;

import org.apache.log4j.Logger;

import cn.com.sparkle.raptor.core.buff.BuffPool;
import cn.com.sparkle.raptor.core.buff.CycleBuff;
import cn.com.sparkle.raptor.core.buff.IoBuffer;
import cn.com.sparkle.raptor.core.io.BufferPoolOutputStream;
import cn.com.sparkle.raptor.core.io.IoBufferArrayInputStream;
import cn.com.sparkle.raptor.core.protocol.DecodeException;
import cn.com.sparkle.raptor.core.protocol.EncodeException;
import cn.com.sparkle.raptor.core.protocol.MultiThreadProtecolHandler.ProtocolHandlerIoSession;
import cn.com.sparkle.raptor.core.protocol.Protocol;

public class ObjectProtocol implements Protocol {
	private Logger logger = Logger.getLogger(ObjectProtocol.class);
	@Override
	public void init(ProtocolHandlerIoSession session) {
		session.protocolAttachment = new ObjectProtocolCacheBean();
	}

	private static class ObjectProtocolCacheBean {
		private int curPackageSize = -1;
		private int recieveSize = 0;
		private LinkedList<IoBuffer> buff = new LinkedList<IoBuffer>();
	}

	private int readInt(ObjectProtocolCacheBean bean) {
		if (bean.recieveSize >= 4) {
			bean.recieveSize -= 4;
			IoBuffer buff = bean.buff.getFirst();
			int r = 0;
			for (int i = 0; i < 4; i++) {
				r = r | ((buff.getByteBuffer().get() & 0xff) << ((3 - i) * 8));
				while (!buff.getByteBuffer().hasRemaining()) {
					bean.buff.removeFirst();
					if(bean.buff.size() != 0){
						buff = bean.buff.getFirst();
					}else{
						break;
					}
				}
			}
			return r;
		} else
			return -1;
	}

	@Override
	public Object decode(ProtocolHandlerIoSession session, IoBuffer buff) {
		ObjectProtocolCacheBean bean = (ObjectProtocolCacheBean) session.protocolAttachment;
		if (bean.buff.size() == 0 || bean.buff.getLast() != buff) {
			bean.buff.addLast(buff);
			bean.recieveSize += buff.getByteBuffer().remaining();
		}
		if (bean.curPackageSize == -1) {
			bean.curPackageSize = readInt(bean);
			if (bean.curPackageSize == -1)
				return null;
		}
		if (bean.recieveSize >= bean.curPackageSize) {
			ObjectInputStream is = null;
			try {
//				int size = 0;
//				for(IoBuffer buffer:bean.buff){
//					size += buffer.getByteBuffer().remaining();
//				}
				is = new ObjectInputStream(new IoBufferArrayInputStream(
						bean.buff.toArray(new IoBuffer[bean.buff.size()]),
						bean.curPackageSize));
				Object o = is.readObject();
				bean.recieveSize -= bean.curPackageSize;
				bean.curPackageSize = -1;
				// remove and close IoBuffer that has been unuseful.
				while (bean.buff.size() > 0
						&& !bean.buff.getFirst().getByteBuffer().hasRemaining()) {
					bean.buff.removeFirst();
				}
				return o;
			} catch (IOException e) {
				logger.debug("bean.recieveSize" + bean.recieveSize + " bean.curPackageSize:" + bean.curPackageSize);
				throw new DecodeException(e);
			} catch (ClassNotFoundException e) {
				throw new DecodeException(e);
			} finally {
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
		return encode(buffpool,message,null);
	}

	@Override
	public IoBuffer[] encode(BuffPool buffpool, Object message,
			IoBuffer lastWaitSendBuff) {
		ObjectOutputStream out = null;
		BufferPoolOutputStream bufferPoolOutputStream = new BufferPoolOutputStream(
				buffpool, 4,lastWaitSendBuff);
		try {
			
			out = new ObjectOutputStream(bufferPoolOutputStream);
			out.writeObject(message);
			out.flush();
			IoBuffer[] result = bufferPoolOutputStream.getBuffArray();
			
			// write count to buff
			int writeCount = bufferPoolOutputStream.getWriteCount();
			byte[] size = {
					(byte) (writeCount >> 24),
					(byte) (writeCount >> 16),
					(byte) (writeCount >> 8),
					(byte) (writeCount)
			}; 
//			System.out.println(writeCount);
			bufferPoolOutputStream.writeReserve(size, 0, 4);
			
			return result;
		} catch (Exception e) {
			IoBuffer[] result = bufferPoolOutputStream.getBuffArray();
			for(IoBuffer buff : result){
				if (buff instanceof CycleBuff) {
					((CycleBuff) buff).close();
				}
			}
			throw new EncodeException(e);
		} finally {
			try {
				out.close();
			} catch (Exception e) {
			}
		}
	}
	
}
