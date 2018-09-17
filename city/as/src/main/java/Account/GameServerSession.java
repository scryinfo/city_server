package Account;

import Shared.GameServerInfo;
import Shared.Package;
import Shared.ServerCfgDb;
import as.As;
import ascode.AsCode;
import com.google.protobuf.Message;
import ga.Ga;
import io.netty.channel.Channel;
import io.netty.channel.ChannelId;
import org.apache.log4j.Logger;

import io.netty.channel.ChannelHandlerContext;


public class GameServerSession {
	private ChannelHandlerContext ctx;
	public static class Info{
		int id;
		String ip;
		int port;
		int onlineCount;
		String playerDbHost;
		Info(int id, int port, int onlineCount, String ip, String playerDbHost){
			this.id = id;
			this.port = port;
			this.onlineCount = onlineCount;
			this.ip = new String(ip);
			this.playerDbHost = new String(playerDbHost);
		}
	}
	
	private static final Logger logger = Logger.getLogger(GameServerSession.class);
	private int id;
	private int port;
	private String ip;
	private String playerDbUri;
	private volatile int onlineCount;
	private boolean valid = false;

	public Info getInfo(){
		return new Info(id, port, onlineCount, ip, playerDbUri);
	}
	public int id() {
		return id;
	}
	public boolean valid() {
		return valid;
	}
	
	public GameServerSession(ChannelHandlerContext ctx){
		this.ctx = ctx;
	}
	
	public void login(short cmd, Message message) {
		Ga.Login c = (Ga.Login)message;
		this.id = c.getId();

		GameServerInfo gsInfo = ServerCfgDb.getGameServerInfo(id);
		if(gsInfo != null){
			if(AccountServer.gsIdToChannelId.containsKey(id))
			{
				ctx.writeAndFlush(Package.fail(cmd));
				System.out.println("duplicated game server " + id);
			}
			else {
				ip = gsInfo.getIp();
				port = gsInfo.getPort();
				playerDbUri = gsInfo.getGameDbUri();
				valid = true;

				AccountServer.allGsChannels.add(ctx.channel());
				AccountServer.gsIdToChannelId.put(id(), ctx.channel().id());
				ctx.writeAndFlush(Package.create(cmd));
				System.out.println("game server " + id + " connected");
			}
		}
		else{
			System.out.println("can't find game server " + id);
		}
	}
	public void logout() {
		AccountServer.gsIdToChannelId.remove(id());
		System.out.println("game server " + id + " disconnected");
	}
	public void stateReport(short cmd, Message message) {
		Ga.StateReport c = (Ga.StateReport)message;
		onlineCount = c.getOnlineCount();
	}
	
	public void validateAck(short cmd, Message message) {
		Ga.ValidationCode c = (Ga.ValidationCode)message;
		ChannelId id = AccountServer.clientAccountToChannelId.get(c.getAccountName());
		if(id == null)
			return;
		Channel ch = AccountServer.allClientChannels.find(id);
		if(ch != null){
			As.ChoseCameServerACK.Builder ack = As.ChoseCameServerACK.newBuilder();
			ack.setCode(c.getCode());
			logger.debug("send code to client " + c.getCode());
			ch.writeAndFlush(Package.create(AsCode.OpCode.chooseGameServer_VALUE, ack.build()));
		}
	}
}

