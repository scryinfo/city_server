package Game;

import Shared.Package;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.apache.log4j.Logger;

public class GameEventHandler extends SimpleChannelInboundHandler<Package> {
	private GameSession session;
    private static final Logger logger = Logger.getLogger(GameEventHandler.class);
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
            City.instance().execute(()->session.logout(false));
        super.channelInactive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Package msg) {
        if(!GameEventDispatcher.getInstance().process(msg, session)) {
            logger.debug("incorrect request, server disconnect actively"+msg);
            System.err.println("发生协议号不匹配，协议是:"+msg.opcode);
            ctx.close();
        }
    }

//    @Override
//    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
//        cause.printStackTrace();
//        //ctx.close();
//    }
    
}
