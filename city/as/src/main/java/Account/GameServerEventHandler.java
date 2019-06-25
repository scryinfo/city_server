package Account;

import Shared.Package;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class GameServerEventHandler extends SimpleChannelInboundHandler<Package> {
	private GameServerSession session;
	
	@Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
		session = new GameServerSession(ctx);
		super.channelActive(ctx);
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		if(session.valid())
			session.logout();
		super.channelInactive(ctx);
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		super.channelRead(ctx, msg);
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, Package msg) throws Exception {
		if(!GameServerEventDispatcher.getInstance().call(msg, session)) {
			ctx.close();
		}
	}
}
