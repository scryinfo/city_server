package Shared;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

public class AutoReconnectHandler extends ChannelInboundHandlerAdapter {
    private Bootstrap bs;
    private Consumer<ChannelFuture> actionAfterConnect;
    private static final Logger LOGGER = LoggerFactory.getLogger(AutoReconnectHandler.class);
    public class ConnectionListener implements ChannelFutureListener {
        ConnectionListener() {
            LOGGER.info("Connection lost, try to reconnect");
        }
        @Override
        public void operationComplete(ChannelFuture channelFuture) {
            if (!channelFuture.isSuccess()) {
                bs.connect().addListener(new ConnectionListener());
            }
            else {
                AutoReconnectHandler.this.actionAfterConnect.accept(channelFuture);
                LOGGER.info("Reconnect success");
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
