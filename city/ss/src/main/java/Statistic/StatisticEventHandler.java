package Statistic;

import Shared.Package;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class StatisticEventHandler extends SimpleChannelInboundHandler<Package> {
    private StatisticSession session;

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        session = new StatisticSession(ctx);
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        session.logout();
        super.channelInactive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Package msg) {
        if(!StatisticEventDispatcher.getInstance().call(msg, session))
            ctx.close();
    }
}

