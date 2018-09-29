package Game;

import Shared.*;
import Shared.Package;
import com.google.protobuf.Message;
import common.Common;
import gs.Gs;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.util.concurrent.ScheduledFuture;
import org.apache.log4j.Logger;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class GameSession {
	private ChannelHandlerContext ctx;
	private static final Logger logger = Logger.getLogger(GameSession.class);
	private final static int UPDATE_MS = 200;
	private Player player;
	private ChannelId channelId;
	private String accountName;
	private String token;
	private ScheduledFuture<?> updateScheduleFuture;
	private boolean valid = false;
	private boolean loginFailed = false;
	private ArrayList<UUID> roleIds = new ArrayList<>();

	public Player getPlayer() {
		return player;
	}
	private
	enum LoginState {
		ROLE_NO_LOGIN,
		ROLE_LOGIN
	}
	UUID id() {
		return player.id();
	}
	private volatile LoginState loginState = LoginState.ROLE_NO_LOGIN;
	public boolean roleLogin() {
		return loginState == LoginState.ROLE_LOGIN;
	}
	public ChannelFuture close() {
		updateScheduleFuture.cancel(false);
		return ctx.close();
	}
	public ChannelId channelId() {
		return channelId;
	}
	public boolean valid(){
		return valid;
	}
	public void update(long diffNano){
		
	}
	public void logout(){
		if(!this.roleLogin()){
			return;
		}
		GroundAuction.instance().unregist(this.channelId);
		player.offline();
		//GameDb.saveOrUpdate(player); // unnecessary in this game, and can not do this, due to current thread is not city thread
		//offline action of validate
		Validator.getInstance().unRegist(accountName, token);
		logger.debug("account: " + player.getAccount() + " logout");
	}

	public GameSession(Player p, ChannelHandlerContext ctx) {
		this.ctx = ctx;
		this.channelId = ctx.channel().id();
		this.player = p;

		//updateScheduleFuture = ctx.channel().eventLoop().scheduleAtFixedRate(()->{this.saveOrUpdate();}, 0, UPDATE_MS, TimeUnit.MILLISECONDS);
	}
	
	public GameSession(ChannelHandlerContext ctx){
		this.ctx = ctx;
		//updateScheduleFuture = ctx.channel().eventLoop().scheduleAtFixedRate(()->{this.saveOrUpdate();}, 0, UPDATE_MS, TimeUnit.MILLISECONDS);
	}
	public void write(Package pack) {
		ctx.writeAndFlush(pack);
	}
	public void pendingWrite(Package pack) {
		ctx.write(pack);
	}
	public void flush() {
		ctx.flush();
	}
	public void disconnect() {ctx.disconnect();}
	public void asyncExecute(Method m, short cmd, Message message) {
		City.instance().execute(()->{
			try {
				if(message == null) {
					try {
						m.invoke(this, cmd);
					} catch (IllegalArgumentException e) {
						if(GlobalConfig.debug())
							System.out.println("pb data is unwanted!!");
						else
							this.close();
					}
				}
				else {
					try {
						m.invoke(this, cmd, message);
					} catch (IllegalArgumentException e) {
						if(GlobalConfig.debug())
							System.out.println("no pb data!!");
						else
							this.close();
					}
				}
			} catch (Exception e) {
				if(GlobalConfig.debug())
					e.printStackTrace();
				else
					this.close();
			}
		});
	}
	public void login(short cmd, Message message) {
		if(this.loginFailed)
		{
			this.disconnect();
			return;
		}
		Gs.Login c = (Gs.Login)message;
		this.accountName = c.getAccount();
		this.token = c.getToken();
		int loginTimes = Validator.getInstance().validate(this.accountName, this.token);
		if(loginTimes > 0)
		{
			loginState = LoginState.ROLE_NO_LOGIN;
			Gs.LoginACK.Builder b = Gs.LoginACK.newBuilder();
			for(RoleBriefInfo i : GameDb.getPlayerInfo(this.accountName)) {
				roleIds.add(i.id);
				b.addInfoBuilder()
						.setId(Util.toByteString(i.id))
						.setName(i.name);
			}
			b.setTs(System.currentTimeMillis());
			this.write(Package.create(cmd, b.build()));
			GameServer.allClientChannels.add(ctx.channel()); // channel remove auto when its closed
			this.valid = true;
		}
		else
		{
			this.loginFailed = true;
			this.write(Package.fail(cmd));
		}
	}
	public void heartBeat(short cmd, Message message) {
		Gs.HeartBeat b = (Gs.HeartBeat)message;
		this.write(Package.create(cmd, Gs.HeartBeatACK.newBuilder().setClientTs(b.getTs()).setServerTs(System.currentTimeMillis()).build()));
	}
	public void roleLogin(short cmd, Message message) {
		// in city thread
		Gs.Id c = (Gs.Id)message;
		UUID roleId = Util.toUuid(c.getId().toByteArray());
		player = GameDb.getPlayer(roleId);
		if(player == null){
			this.write(Package.fail(cmd));
			return;
		}

		player.setSession(this);
		GameServer.allGameSessions.putIfAbsent(player.id(), this);
		this.write(Package.create(cmd, player.toProto()));
		loginState = LoginState.ROLE_LOGIN;
		logger.debug("account: " + this.accountName + " login");

		// due to almost all data of this player is keeping run, we need let
		// city to do data retrieve, and register this player then any data modification
		// will push to its client
		// REV seems nothing to assign?
		City.instance().add(player);
		player.online();
	}

	public void createRole(short cmd, Message message) {
		Gs.Str c = (Gs.Str)message;
		Player p = new Player(c.getStr(), this.accountName);
		if(!GameDb.createPlayer(p)) {
			this.write(Package.fail(cmd, Common.Fail.Reason.roleNameDuplicated));
		}
		else {
			this.write(Package.create(cmd, Gs.RoleInfo.newBuilder().setId(Util.toByteString(p.id())).setName(p.getName()).build()));
		}
	}

	public void move(short cmd, Message message) {
		Gs.GridIndex c = (Gs.GridIndex)message;
		GridIndex index = new GridIndex(c.getX(), c.getY());
		if(!this.player.setPosition(index))
			return;
	}
	public void queryMetaGroundAuction(short cmd) {
		Set<MetaGroundAuction> auctions = MetaData.getNonFinishedGroundAuction();
		Gs.MetaGroundAuction.Builder builder = Gs.MetaGroundAuction.newBuilder();
		for(MetaGroundAuction m : auctions) {
			builder.addAuction(m.toProto());
		}
		this.write(Package.create(cmd, builder.build()));
	}
	public void queryGroundAuction(short cmd) {
		this.write(Package.create(cmd, GroundAuction.instance().toProto()));
	}
	public void bidGround(short cmd, Message message) throws IllegalArgumentException {
		Gs.ByteNum c = (Gs.ByteNum)message;
		UUID id = Util.toUuid(c.getId().toByteArray());
		Optional<Common.Fail.Reason> err = GroundAuction.instance().bid(id, player, c.getNum());
		if(err.isPresent())
			this.write(Package.fail(cmd, err.get()));
		else
			this.write(Package.create(cmd, GroundAuction.instance().toProto()));
	}
	public void registGroundBidInform(short cmd) {
		GroundAuction.instance().regist(this.channelId);
	}
	public void unregistGroundBidInform(short cmd) {
		GroundAuction.instance().unregist(this.channelId);
	}

	public void addBuilding(short cmd, Message message) {
		Gs.AddBuilding c = (Gs.AddBuilding) message;
		int id = c.getId();
		boolean ok = City.instance().addBuilding(id, new Coord(c.getPos()), player);
		if(!ok)
			this.write(Package.fail(cmd));
		else
			this.write(Package.create(cmd));
	}
	public void delBuilding(short cmd, Message message) {
		Gs.Id c = (Gs.Id) message;
		UUID id = Util.toUuid(c.getId().toByteArray());
		boolean ok = City.instance().delBuilding(id, player);
		if(!ok)
			this.write(Package.fail(cmd));
		else
			this.write(Package.create(cmd));
	}
	public void detailApartment(short cmd, Message message) {
		Gs.Id c = (Gs.Id) message;
		UUID id = Util.toUuid(c.getId().toByteArray());
		Building b = City.instance().getBuilding(id);
		if(b == null || b.type() != MetaBuilding.APARTMENT)
			return;
		this.write(Package.create(cmd, ((Apartment)b).detailProto()));
	}
	public void detailMaterialFactory(short cmd, Message message) {
		Gs.Id c = (Gs.Id) message;
		UUID id = Util.toUuid(c.getId().toByteArray());
		Building b = City.instance().getBuilding(id);
		if(b == null || b.type() != MetaBuilding.MATERIAL)
			return;
		this.write(Package.create(cmd, ((MaterialFactory)b).detailProto()));
	}
	public void setSalary(short cmd, Message message) {
		Gs.ByteNum c = (Gs.ByteNum) message;
		UUID id = Util.toUuid(c.getId().toByteArray());
		Building b = City.instance().getBuilding(id);
		if(b == null)
			return;
		if(player.money() < c.getNum())
		{
			this.write(Package.fail(cmd, Common.Fail.Reason.moneyNotEnough));
			return;
		}
		b.setSalary(c.getNum());
		this.write(Package.create(cmd, c));
	}
	public void setRent(short cmd, Message message) {
		Gs.ByteNum c = (Gs.ByteNum) message;
		UUID id = Util.toUuid(c.getId().toByteArray());
		Building b = City.instance().getBuilding(id);
		if(b == null || b.type() != MetaBuilding.APARTMENT)
			return;
		Apartment a = (Apartment)b;
		a.setRent(c.getNum());
		this.write(Package.create(cmd, c));
	}
}
