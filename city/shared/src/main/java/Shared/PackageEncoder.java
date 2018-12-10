package Shared;

import gacode.GaCode;
import gscode.GsCode;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.apache.log4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class PackageEncoder extends MessageToByteEncoder<Package> {
    private static final Logger logger = Logger.getLogger(PackageEncoder.class);
    private Method method;

    public PackageEncoder(Class<? extends com.google.protobuf.ProtocolMessageEnum> clazz) throws NoSuchMethodException {
        this.method = clazz.getDeclaredMethod("valueOf", Integer.TYPE);
    }
    @Override
    protected void encode(ChannelHandlerContext ctx, Package msg, ByteBuf out) throws InvocationTargetException, IllegalAccessException {
        msg.toByteBuf(out);
        Object o = this.method.invoke(null, msg.opcode);
        int bytes = msg.body==null?0:msg.body.length;
        if (o instanceof GsCode.OpCode) {
            if (o != GsCode.OpCode.heartBeat)
                logger.debug("send to client -> " + o + ", bytes: " + bytes);
        } else if (o instanceof GaCode.OpCode)
            logger.debug("send to server -> " + o + ", bytes: " + bytes);
    }
}
