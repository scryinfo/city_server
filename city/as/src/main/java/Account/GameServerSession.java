package Account;

import Shared.GameServerInfo;
import Shared.Package;
import Shared.ServerCfgDb;
import as.As;
import ascode.AsCode;
import com.google.protobuf.Message;
import ga.Ga;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;


public class GameServerSession {
	private ChannelHandlerContext ctx;
	private static final Logger logger = LoggerFactory.getLogger(GameServerSession.class);
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
			if(AccountServer.gsIdToChannelId.containsKey(id) &&
					AccountServer.allGsChannels.find(AccountServer.gsIdToChannelId.get(id)) != null)
			{
				Channel oldChannel = AccountServer.allGsChannels.find(AccountServer.gsIdToChannelId.get(id));
				InetSocketAddress oldAddress = (InetSocketAddress)oldChannel.remoteAddress();
				InetSocketAddress newAddress = (InetSocketAddress) ctx.channel().remoteAddress();
                logger.info("旧的ip"+oldAddress.getAddress());
                logger.info("新的ip"+newAddress.getAddress());
				if(oldAddress.getAddress().equals(newAddress.getAddress())){
					AccountServer.allGsChannels.add(ctx.channel());
					AccountServer.gsIdToChannelId.put(id(), ctx.channel().id());
					ip = gsInfo.getIp();
					port = gsInfo.getPort();
					playerDbUri = gsInfo.getGameDbUrl();
					valid = true;
					AccountServer.allGsChannels.add(ctx.channel());
					AccountServer.gsIdToChannelId.put(id(), ctx.channel().id());
					ctx.channel().writeAndFlush(Package.create(cmd));
					logger.info("ReConnected game server " + id + " success");

				}else {
					ctx.channel().writeAndFlush(Package.fail(cmd));
					logger.info("duplicated game server " + id);
				}
			}
			else {
				ip = gsInfo.getIp();
				port = gsInfo.getPort();
				playerDbUri = gsInfo.getGameDbUrl();
				valid = true;

				AccountServer.allGsChannels.add(ctx.channel());
				AccountServer.gsIdToChannelId.put(id(), ctx.channel().id());
				ctx.channel().writeAndFlush(Package.create(cmd));
				logger.info("game server " + id + " connected");
			}
		}
		else{
			logger.info("can't find game server " + id);
		}
	}
	public void logout() {
		AccountServer.gsIdToChannelId.remove(id());
		logger.info("game server " + id + " disconnected");
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

