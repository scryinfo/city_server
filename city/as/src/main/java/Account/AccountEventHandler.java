package Account;

import Shared.Package;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class AccountEventHandler extends SimpleChannelInboundHandler<Package> {

	private AccountSession session;
	
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
       session = new AccountSession(ctx);
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (session.valid())
            session.logout();
        super.channelInactive(ctx);
    }

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, Package msg) throws Exception {
		if(!AccountEventDispatcher.getInstance().call(msg, session))
			ctx.close();
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		cause.printStackTrace();
		//ctx.close();
	}

}
