//import org.apache.log4j.Logger;
//
//import io.netty.channel.ChannelFuture;
//import io.netty.channel.ChannelHandlerContext;
//import proto.GsCode;
//
//public abstract class ISession {
//
//	private static final Logger logger = Logger.getLogger(ISession.class);
//	protected ChannelHandlerContext ctx;
//
//	public ChannelFuture send(Package pack){
//		return ctx.writeAndFlush(pack);
//	}
//
//	public ChannelFuture close(){
//		return ctx.close();
//	}
//
//	public void execute(Runnable runable) {
//		ctx.channel().eventLoop().execute(runable);
//	}
//
//	public void update(){
//
//	}
//
//	public abstract boolean valid();
//
//	public abstract void logout();
//
//
//}
