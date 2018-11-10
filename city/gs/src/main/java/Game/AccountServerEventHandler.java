package Game;

import Shared.Package;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;


public class AccountServerEventHandler extends SimpleChannelInboundHandler<Package> {
	private AccountServerSession session;

	public AccountServerEventHandler() {}

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Package msg) {
        AccountServerEventDispatcher.getInstance().process(msg, session);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        session = new AccountServerSession(ctx);
        super.channelActive(ctx);
    }
}
