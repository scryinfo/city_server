package Account;

import Shared.Package;
import gacode.GaCode;
import io.netty.channel.*;
import io.netty.handler.timeout.IdleStateEvent;

import java.util.Collection;

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

	/*Heartbeat detection*/
	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
		if (evt instanceof IdleStateEvent) {
			ctx.writeAndFlush(Package.create(GaCode.OpCode.heartInfo_VALUE))
					.addListener(new ChannelFutureListener() {
						@Override
						public void operationComplete(ChannelFuture future) throws Exception {
							if (!future.isSuccess()) {
								if(session.valid())
									session.logout();
								future.channel().close();
							}
						}
					});
		} else {
			super.userEventTriggered(ctx, evt);
		}
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, Package msg) throws Exception {
		if(!GameServerEventDispatcher.getInstance().call(msg, session)) {
			ctx.close();
		}
	}
}
