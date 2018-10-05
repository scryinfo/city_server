package Game;

import Shared.Package;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class GameEventHandler extends SimpleChannelInboundHandler<Package> {
	private GameSession session;

//    @Override
//    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
//        super.userEventTriggered(ctx, evt);
//        if (evt instanceof IdleStateEvent) {
//            IdleStateEvent e = (IdleStateEvent) evt;
//            if (e.state() == IdleState.READER_IDLE) {
//                ctx.close();
//            } else if (e.state() == IdleState.WRITER_IDLE) {
//                ctx.writeAndFlush(Package.create(GsCode.OpCode.heartBeat_VALUE));
//            }
//        }
//    }
	
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        session = new GameSession(ctx);
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if(session.valid())
            session.logout();
        super.channelInactive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Package msg) throws Exception {
        if(!GameEventDispatcher.getInstance().process(msg, session))
            ctx.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        //ctx.close();
    }
    
}
