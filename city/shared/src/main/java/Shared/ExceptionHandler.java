package Shared;

import com.google.common.base.Throwables;
import io.netty.channel.*;
import org.apache.log4j.Logger;

import java.io.IOException;

public class ExceptionHandler extends ChannelDuplexHandler {
    private static final Logger logger = Logger.getLogger(ExceptionHandler.class);
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if(cause instanceof IOException)
            logger.debug(cause);
        else
            logger.fatal(Throwables.getStackTraceAsString(cause));
    }
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        ctx.write(msg, promise.addListener((ChannelFutureListener) future -> {
            if (!future.isSuccess()) {
                logger.fatal(Throwables.getStackTraceAsString(future.cause()));
            }
        }));
    }
}
