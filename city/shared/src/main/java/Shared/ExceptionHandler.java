package Shared;

import io.netty.channel.*;

import java.io.IOException;

public class ExceptionHandler extends ChannelDuplexHandler {
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if(cause instanceof IOException)
            System.out.println("connection disconnected by client");
        else
            cause.printStackTrace();
    }
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        ctx.write(msg, promise.addListener((ChannelFutureListener) future -> {
            if (!future.isSuccess()) {
                future.cause().printStackTrace();
            }
        }));
    }
}
