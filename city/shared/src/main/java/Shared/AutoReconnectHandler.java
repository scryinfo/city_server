package Shared;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.util.function.Consumer;

public class AutoReconnectHandler extends ChannelInboundHandlerAdapter {
    private Bootstrap bs;
    private Consumer<ChannelFuture> actionAfterConnect;
    public class ConnectionListener implements ChannelFutureListener {
        ConnectionListener() {
            System.out.println("Connection lost, try to reconnect");
        }
        @Override
        public void operationComplete(ChannelFuture channelFuture) {
            if (!channelFuture.isSuccess()) {
                bs.connect().addListener(new ConnectionListener());
            }
            else {
                AutoReconnectHandler.this.actionAfterConnect.accept(channelFuture);
                System.out.println("Reconnect success");
            }
        }
    }
    public AutoReconnectHandler(Bootstrap bs, Consumer<ChannelFuture> actionAfterConnect) {
        this.bs = bs;
        this.actionAfterConnect = actionAfterConnect;
    }
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (ctx.channel().eventLoop().isShuttingDown())
            return; // this process is shutdown
        bs.connect().addListener(new ConnectionListener());
        super.channelInactive(ctx);
    }
}
