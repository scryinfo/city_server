package Shared;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

public class PackageDecoder extends ByteToMessageDecoder {
	@Override
	protected void decode(ChannelHandlerContext arg0, ByteBuf arg1, List<Object> arg2) {
		Package pack = new Package(arg1);
		arg2.add(pack);
		// this decode method is a template method which called in super class channelRead, the arg1.release()
		// was called in channelRead
	}
}
