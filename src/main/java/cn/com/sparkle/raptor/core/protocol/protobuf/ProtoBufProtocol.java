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
	private GeneratedMessage generatedMessage;
//	private GeneratedMessage[] map = new GeneratedMessage[65536];
//	private HashMap<Class, Integer> classMap = new HashMap<Class, Integer>();
//	public void registerMessage(int id,GeneratedMessage message){
//		if(id > 65535 || id < 0){
//			throw new RuntimeException("id must be between [0,65535]!");
//		}
//		if(map[id] != null){
//			throw new RuntimeException("repeated id of the message!");
//		}
//		map[id] = message;
//		classMap.put(message.getClass(), id);
//	}
	
	public ProtoBufProtocol(GeneratedMessage generatedMessage){
		this.generatedMessage = generatedMessage;
	}
	
	@Override
	public void init(ProtocolHandlerIoSession session) {
		session.protocolAttachment = new ObjectProtocolCacheBean();
	}

	private static class ObjectProtocolCacheBean {
		private int curPackageSize = 0;
		private int recieveSize = 0;
		private byte checkPos = 0;
		private LinkedList<IoBuffer> buff = new LinkedList<IoBuffer>();
	}

//	byte tmp = readRawByte();
//    if (tmp >= 0) {
//      return tmp;
//    }
//    int result = tmp & 0x7f;
//    if ((tmp = readRawByte()) >= 0) {
//      result |= tmp << 7;
//    } else {
//      result |= (tmp & 0x7f) << 7;
//      if ((tmp = readRawByte()) >= 0) {
//        result |= tmp << 14;
//      } else {
//        result |= (tmp & 0x7f) << 14;
//        if ((tmp = readRawByte()) >= 0) {
//          result |= tmp << 21;
//        } else {
//          result |= (tmp & 0x7f) << 21;
//          result |= (tmp = readRawByte()) << 28;
//          if (tmp < 0) {
//            // Discard upper 32 bits.
//            for (int i = 0; i < 5; i++) {
//              if (readRawByte() >= 0) {
//                return result;
//              }
//            }
//            throw InvalidProtocolBufferException.malformedVarint();
//          }
//        }
//      }
//    }
//    return result;
	private void readSize(ObjectProtocolCacheBean bean) {
		IoBuffer buff = bean.buff.getFirst();
		for(int i = 1; i < 6 - bean.checkPos ; i++){
			if(bean.recieveSize > i){
				byte tmp = buff.getByteBuffer().get();
				--bean.recieveSize;
				bean.curPackageSize |= (tmp & 0x7f) << (7 * bean.checkPos);
				if(!buff.getByteBuffer().hasRemaining()){
					bean.buff.removeFirst();
					if(bean.buff.size() != 0){
						buff = bean.buff.getFirst();
					}
				}
				if(tmp >= 0){
					bean.checkPos = -1;
					return;
				}else{
					++bean.checkPos;
				}
			}
		}
	}
	
	
	@Override
	public Object decode(ProtocolHandlerIoSession session, IoBuffer buff) throws DecodeException{
		ObjectProtocolCacheBean bean = (ObjectProtocolCacheBean) session.protocolAttachment;
		if (bean.buff.size() == 0 || bean.buff.getLast() != buff) {
			bean.buff.addLast(buff);
			bean.recieveSize += buff.getByteBuffer().remaining();
		}
		
		if(bean.checkPos >= 0){
			readSize(bean);
			if(bean.checkPos>= 0){
				return null;
			}
		}
		if (bean.recieveSize >= bean.curPackageSize) {
//			int index = getIndex(bean);
//			if(map[index] == null){
//				throw new DecodeException("The message is not registered to protocol! id:" + index);
//			}
			try {
				Builder b = generatedMessage.newBuilderForType().mergeFrom(new IoBufferArrayInputStream(
						bean.buff.toArray(new IoBuffer[bean.buff.size()]),
						bean.curPackageSize));
				bean.recieveSize -= bean.curPackageSize;
				bean.curPackageSize = 0;
				bean.checkPos = 0;
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
		GeneratedMessage generatedMessage = (GeneratedMessage)message;
		int dataSize = generatedMessage.getSerializedSize();
			BufferPoolOutputStream bufferPoolOutputStream = new BufferPoolOutputStream(
					buffpool, 0,lastWaitSendBuff);
			// write count to buff
			while (true) {
			      if ((dataSize & ~0x7FL) == 0) {
			    	  bufferPoolOutputStream.write(dataSize);
			    	  break;
			      } else {
			    	  bufferPoolOutputStream.write((((int)dataSize & 0x7F) | 0x80));
			    	  dataSize >>>= 7;
			      }
			}
			generatedMessage.writeTo(bufferPoolOutputStream);
			List<IoBuffer> list = bufferPoolOutputStream.getBuffArray();
			if(lastWaitSendBuff != null){
				list.remove(0);
			}
			return list.toArray(new IoBuffer[list.size()]);
	}
	
}
