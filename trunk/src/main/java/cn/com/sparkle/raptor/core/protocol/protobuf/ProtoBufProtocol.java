package cn.com.sparkle.raptor.core.protocol.protobuf;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.Logger;

import com.google.protobuf.GeneratedMessage;
import com.google.protobuf.Message.Builder;

import cn.com.sparkle.raptor.core.buff.BuffPool;
import cn.com.sparkle.raptor.core.buff.BuffPool.PoolEmptyException;
import cn.com.sparkle.raptor.core.buff.CycleBuff;
import cn.com.sparkle.raptor.core.buff.IoBuffer;
import cn.com.sparkle.raptor.core.buff.IoBufferArray;
import cn.com.sparkle.raptor.core.io.BufferPoolOutputStream;
import cn.com.sparkle.raptor.core.io.IoBufferArrayInputStream;
import cn.com.sparkle.raptor.core.protocol.DecodeException;
import cn.com.sparkle.raptor.core.protocol.EncodeException;
import cn.com.sparkle.raptor.core.protocol.MultiThreadProtecolHandler.ProtocolHandlerIoSession;
import cn.com.sparkle.raptor.core.protocol.Protocol;

public class ProtoBufProtocol implements Protocol {
	private Logger logger = Logger.getLogger(ProtoBufProtocol.class);
	private GeneratedMessage[] map = new GeneratedMessage[65536];
	private HashMap<Class, Integer> classMap = new HashMap<Class, Integer>();
	public void registerMessage(int id,GeneratedMessage message){
		if(id > 65535 || id < 0){
			throw new RuntimeException("id must be between [0,65535]!");
		}
		if(map[id] != null){
			throw new RuntimeException("repeated id of the message!");
		}
		map[id] = message;
		classMap.put(message.getClass(), id);
	}
	

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
	
	private int getIndex(ObjectProtocolCacheBean bean){
		IoBuffer buff = bean.buff.getFirst();
		int r = 0;
		for (int i = 0; i < 2; i++) {
			r = r | ((buff.getByteBuffer().get() & 0xff) << ((1 - i) * 8));
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
		
//		return ((bs[0] & 0xff) << 8) | (bs[1] & 0xff);
	}
	@Override
	public Object decode(ProtocolHandlerIoSession session, IoBuffer buff) throws DecodeException{
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
			int index = getIndex(bean);
			if(map[index] == null){
				throw new DecodeException("The message is not registered to protocol! id:" + index);
			}
			try {
				Builder b = map[index].newBuilderForType().mergeFrom(new IoBufferArrayInputStream(
						bean.buff.toArray(new IoBuffer[bean.buff.size()]),
						bean.curPackageSize-2));
				bean.recieveSize -= bean.curPackageSize;
				bean.curPackageSize = -1;
				// remove and close IoBuffer that has been unuseful.
				while (bean.buff.size() > 0
						&& !bean.buff.getFirst().getByteBuffer().hasRemaining()) {
					bean.buff.removeFirst();
				}
				return b.build();
			} catch (Exception e) {
				logger.debug("bean.recieveSize" + bean.recieveSize + " bean.curPackageSize:" + bean.curPackageSize);
				throw new DecodeException(e);
			} 
		}
		return null;
	}

	@Override
	public IoBuffer[] encode(BuffPool buffpool, Object message) throws IOException{
		return encode(buffpool,message,null);
	}

	@Override
	public IoBuffer[] encode(BuffPool buffpool, Object message,
			IoBuffer lastWaitSendBuff) throws IOException {
		Integer index = classMap.get(message.getClass());
		if(index == null){
			throw new RuntimeException("The message is not registered to protocol! class:" + message.getClass());
		}
			GeneratedMessage generatedMessage = (GeneratedMessage)message;
			BufferPoolOutputStream bufferPoolOutputStream = new BufferPoolOutputStream(
					buffpool, 6,lastWaitSendBuff);
			generatedMessage.writeTo(bufferPoolOutputStream);
			List<IoBuffer> list = bufferPoolOutputStream.getBuffArray();
			// write count to buff
			int writeCount = bufferPoolOutputStream.getWriteCount() + 2;
			byte[] size = {
					(byte) (writeCount >> 24),
					(byte) (writeCount >> 16),
					(byte) (writeCount >> 8),
					(byte) (writeCount),
					(byte)(index >> 8 & 0xff),
					(byte)(index & 0xff)
			}; 
//			System.out.println(writeCount + 2);
			bufferPoolOutputStream.writeReserve(size, 0, 6);
			if(lastWaitSendBuff != null){
				list.remove(0);
			}
			return list.toArray(new IoBuffer[list.size()]);
	}
	
}
