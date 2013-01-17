package cn.com.sparkle.raptor.core.protocol.textline;


import java.nio.ByteBuffer;


import cn.com.sparkle.raptor.core.buff.BuffPool;
import cn.com.sparkle.raptor.core.buff.IoBuffer;
import cn.com.sparkle.raptor.core.buff.IoBufferArray;
import cn.com.sparkle.raptor.core.protocol.MultiThreadProtecolHandler.ProtecolHandlerAttachment;
import cn.com.sparkle.raptor.core.protocol.Protocol;

public class TextLineProtocol implements Protocol{
	
	@Override
	public Object decode(ProtecolHandlerAttachment attachment, IoBuffer buff) {
		
		if(!buff.getByteBuffer().hasRemaining()) return null;//check buff
		
		DecodeCache decodeCache = (DecodeCache)attachment.protocolAttachment;
		
		char c;
		//deal cached byte
		if(decodeCache.b != null){
			c = (char) (decodeCache.b << 8 | buff.getByteBuffer().get());
			String r = readChar(c,buff.getByteBuffer(),decodeCache);
			if(r != null) return r;
		}
		
		//deal remaining byte in IoBuff
		int remaining = buff.getByteBuffer().remaining();
		int off = remaining % 2;
		for(int i = 0 ; i < remaining - off ;i = i + 2){
			c = buff.getByteBuffer().getChar();
			String r = readChar(c,buff.getByteBuffer(),decodeCache);
			if(r != null) return r;
		}
		//keep the last odd byte
		if(off == 1){
			decodeCache.b = buff.getByteBuffer().get();
		}else{
			decodeCache.b = null;
		}
		return null;
	}
	private String readChar(char c,ByteBuffer b,DecodeCache decodeCache){
		if(c == '\r' || c == '\n'){
			if(decodeCache.sb.length() != 0){
				String result = decodeCache.sb.toString();
				decodeCache.sb = new StringBuilder();
				return result;
			}
		}else{
			decodeCache.sb.append(c);
		}
		return null;
	}
	public static class DecodeCache{
		private StringBuilder sb = new StringBuilder();
		private Byte b;
	} 
	@Override
	public IoBuffer[] encode(BuffPool buffpool, Object obj) {
		String s = (String)obj;
		IoBufferArray ioBuffArray = buffpool.get(s.length() * 2 + 2);
		for(int i = 0; i < s.length() ; i++){
			char c = s.charAt(i);
			ioBuffArray.put((byte)(c >> 8));
			ioBuffArray.put((byte)(c & 0xff));
		}
		ioBuffArray.put((byte)0);
		ioBuffArray.put((byte)'\r');
		return ioBuffArray.getIoBuffArray();
		/*
		IoBuffer[] buff = new IoBuffer[1];
		buff[0] = new AllocateBytesBuff(1024);
		for(int i = 0; i < s.length() ; i++){
			char c = s.charAt(i);
			buff[0].getByteBuffer().put((byte)(c >> 8));
			buff[0].getByteBuffer().put((byte)(c & 0xff));
		}
		buff[0].getByteBuffer().put((byte)0);
		buff[0].getByteBuffer().put((byte)'\r');
		return buff;*/
	}
	@Override
	public void init(ProtecolHandlerAttachment attachment) {
			attachment.protocolAttachment = new DecodeCache();
	}
}