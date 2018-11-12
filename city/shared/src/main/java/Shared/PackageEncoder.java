package Shared;

import gscode.GsCode;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.apache.log4j.Logger;

public class PackageEncoder extends MessageToByteEncoder<Package> {
    private static final Logger logger = Logger.getLogger(PackageEncoder.class);
    private boolean gs;
    public PackageEncoder(boolean gs) {
        this.gs = gs;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Package msg, ByteBuf out) {
        msg.toByteBuf(out);
        if(gs && msg.opcode != GsCode.OpCode.heartBeat_VALUE)
            logger.debug("server send -> " + GsCode.OpCode.valueOf(msg.opcode));
    }
}
