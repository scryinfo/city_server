package Game;

import Shared.AutoReconnectHandler;
import Shared.Package;
import Shared.Validator;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import common.Common;
import ga.Ga;
import gacode.GaCode;
import io.netty.channel.ChannelHandlerContext;
import org.apache.log4j.Logger;

public class AccountServerSession {
	private static final Logger logger = Logger.getLogger(AccountServerSession.class);
	private ChannelHandlerContext ctx;
	public void update(){
		Ga.StateReport.Builder c = Ga.StateReport.newBuilder();
		c.setOnlineCount(GameServer.allClientChannels.size());
		this.ctx.channel().writeAndFlush(Package.create(GaCode.OpCode.stateReport_VALUE,c.build()));
	}
	
	public AccountServerSession(ChannelHandlerContext ctx){
		this.ctx = ctx;
	}
	
	public void validationCode(short cmd, Message message) {
		Ga.ValidationCode c = (Ga.ValidationCode)message;
		Validator.getInstance().regist(c.getAccountName(), c.getCode());
		logger.debug("send back code to account server " + c.getAccountName() + " " + c.getCode());
		this.ctx.channel().writeAndFlush(Package.create(GaCode.OpCode.validateAck_VALUE, c));
	}
	public void handleError(short cmd, Message message) throws InvalidProtocolBufferException {
		Common.Fail c = (Common.Fail)message;
		switch (c.getOpcode())
		{
			case GaCode.OpCode.login_VALUE:
				logger.info("already has same id game server connected to account server!");
				//ctx.channel().pipeline().remove(AutoReconnectHandler.class);
				logger.error("game server Error==========================");
				System.exit(0);
				break;
		}
	}
	public void loginACK(short cmd) {
		logger.info("login to account server success!");
	}
	/*心跳检测*/
	public void heartInfo(short cmd){
		logger.info("heartConnect!");
	}
}
