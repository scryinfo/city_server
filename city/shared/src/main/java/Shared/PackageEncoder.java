package Shared;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class PackageEncoder extends MessageToByteEncoder<Package> {

    @Override
    protected void encode(ChannelHandlerContext ctx, Package msg, ByteBuf out) {
        msg.toByteBuf(out);
    }
}
