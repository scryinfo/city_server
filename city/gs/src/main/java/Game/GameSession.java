package Game;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import Game.Contract.BuildingContract;
import Game.Contract.Contract;
import Game.Contract.ContractManager;
import Game.Contract.IBuildingContract;
import org.apache.log4j.Logger;

import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import com.google.protobuf.Message;

import Game.Contract.BuildingContract;
import Game.Contract.Contract;
import Game.Contract.ContractManager;
import Game.Contract.IBuildingContract;
import Game.Eva.Eva;
import Game.Eva.EvaManager;
import Game.Exceptions.GroundAlreadySoldException;
import Game.FriendManager.FriendManager;
import Game.FriendManager.FriendRequest;
import Game.FriendManager.ManagerCommunication;
import Game.FriendManager.OfflineMessage;
import Game.FriendManager.Society;
import Game.FriendManager.SocietyManager;
import Game.League.BrandLeague;
import Game.League.LeagueInfo;
import Game.League.LeagueManager;
import Game.Meta.MetaBuilding;
import Game.Meta.MetaData;
import Game.Meta.MetaExperiences;
import Game.Meta.MetaGood;
import Game.Meta.MetaItem;
import Game.Meta.MetaMaterial;
import Shared.GlobalConfig;
import Shared.LogDb;
import Shared.Package;
import Shared.RoleBriefInfo;
import Shared.Util;
import Shared.Validator;
import common.Common;
import gs.Gs;
import gs.Gs.BuildingInfo;
import gscode.GsCode;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.util.concurrent.ScheduledFuture;

public class GameSession {
	private ChannelHandlerContext ctx;
	private static final Logger logger = Logger.getLogger(GameSession.class);
	private final static int UPDATE_MS = 200;
	private Player player;
	private ChannelId channelId;
	private String accountName;
	private String token;
	private ScheduledFuture<?> updateScheduleFuture;
	private volatile boolean valid = false;
	private boolean loginFailed = false;
	private ArrayList<UUID> roleIds = new ArrayList<>();
	private HashSet<UUID> buildingDetail = new HashSet<>();
	private static final int MAX_DETAIL_BUILDING = 30;
	public Player getPlayer() {
		return player;
	}
	private enum LoginState {
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

	public GameSession(ChannelHandlerContext ctx){
		this.ctx = ctx;
		this.channelId = ctx.channel().id();
		//updateScheduleFuture = ctx.channel().eventLoop().scheduleAtFixedRate(()->{this.saveOrUpdate();}, 0, UPDATE_MS, TimeUnit.MILLISECONDS);
	}
	public void write(Package pack) {
		ctx.channel().writeAndFlush(pack);
	}
	public void disconnect() {ctx.channel().disconnect();}
	public void asyncExecute(Method m, short cmd, Message message) {
		City.instance().execute(()->{
			try {
				if(message == null) {
					try {
						m.invoke(this, cmd);
					} catch (IllegalArgumentException e) {
						if(GlobalConfig.debug())
							logger.error(Throwables.getStackTraceAsString(e) + " " + Integer.toString(cmd));
						else
							this.close();
					}
				}
				else {
					try {
						m.invoke(this, cmd, message);
					} catch (IllegalArgumentException e) {
						if(GlobalConfig.debug())
							logger.error(Throwables.getStackTraceAsString(e));
						else
							this.close();
					}
				}
			} catch (Exception e) {
				if(GlobalConfig.debug())
					logger.fatal(Throwables.getStackTraceAsString(e));
				else
					this.close();
			}
		});
	}
	public void cheat(short cmd, Message message) {
		Gs.Str c = (Gs.Str)message;
		logger.debug("cheat command: " + c.getStr());
		Cheat cheat = _parseCheatString(c.getStr());
		if(cheat != null)
			_runCheat(cheat);
	}
	private static class Cheat {
		enum Type {
			addmoney,
			additem,
			addground,
			invent
		}
		Type cmd;
		int[] paras;
	}
	private void _runCheat(Cheat cheat) {
		switch (cheat.cmd) {
			case addmoney: {
				int n = Integer.valueOf(cheat.paras[0]);
				if(n <= 0)
					return;
				player.addMoney(n);
				GameDb.saveOrUpdate(player);
				break;
			}
			case additem: {
				int id = cheat.paras[0];
				int n = cheat.paras[1];
				MetaItem mi = MetaData.getItem(id);
				if (mi == null)
					return;
				if (n <= 0)
					return;
				if(player.getBag().reserve(mi, n)) {
					Item item;
					if(mi instanceof MetaMaterial)
						item = new Item(new ItemKey(mi), n);
					else
						item = new Item(new ItemKey(mi, player.id(), 0), n);
					player.getBag().consumeReserve(item.key, n, 1);
				}
                GameDb.saveOrUpdate(player);
                break;
			}
			case addground: {
				int x1 = cheat.paras[0];
				int y1 = cheat.paras[1];
				int x2 = cheat.paras[2];
				int y2 = cheat.paras[3];
				if(x1 > x2) {
					int z = x1;
					x1 = x2;
					x2 = z;
				}
				if(y1 > y2) {
					int z = y1;
					y1 = y2;
					y2 = z;
				}
				CoordPair cp = new CoordPair(new Coordinate(x1, y1), new Coordinate(x2, y2));
				try {
                    GroundManager.instance().addGround(id(), cp.toCoordinates(),0);
				} catch (GroundAlreadySoldException e) {
					e.printStackTrace();
				}
                GameDb.saveOrUpdate(GroundManager.instance());
                break;
			}
			case invent: {
				int mId = cheat.paras[0];
				int lv = cheat.paras[1];
				MetaItem mi = MetaData.getItem(mId);
				if(mi == null)
					return;
				if(mi instanceof MetaMaterial && lv != 0)
					return;
				if(lv < 0)
					return;
				player.addItem(mId, lv);
				TechTradeCenter.instance().techCompleteAction(mId, lv);
				GameDb.saveOrUpdate(Arrays.asList(player, TechTradeCenter.instance()));
				break;
			}
		}
	}
	private Cheat _parseCheatString(String str) {
		Cheat res = new Cheat();
		String[] sa = str.split(" ");
		if(sa.length == 0)
			return null;
		try {
			res.cmd = Cheat.Type.valueOf(sa[0]);
		}
		catch(IllegalArgumentException e) {
			return null;
		}
		res.paras = new int[sa.length-1];
		for(int i = 1; i < sa.length; ++i) {
			res.paras[i-1] = Integer.valueOf(sa[i]);
		}
		return res;
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
	private boolean kickOff = false;
	private Player kickOff() {
		assert player != null;
		kickOff = true;
		this.disconnect();
		return player;
	}
	public void logout(){
		if(!this.roleLogin()){
			return;
		}
		for (UUID uuid : buildingDetail) {
			Building building = City.instance().getBuilding(uuid);
			if(building != null)
				building.watchDetailInfoDel(this);
		}
		if (kickOff)
		{
			logger.debug("account: " + player.getAccount() + " be kick off");
		}
		else
		{
			player.offline();
			//GameDb.evict(player);
			logger.debug("account: " + player.getAccount() + " logout");

			//Notify friends
			FriendManager.getInstance().broadcastStatue(player.id(),false);
		}
		GameServer.allGameSessions.remove(id());
		if (player.getSocietyId() != null)
		{
			//必须在allGameSessions.remove(id())之后调用
			SocietyManager.broadOffline(player);
		}
		Validator.getInstance().unRegist(accountName, token);
	}
	public void roleLogin(short cmd, Message message) {
		// in city thread
		Gs.Id c = (Gs.Id)message;
		UUID roleId = Util.toUuid(c.getId().toByteArray());
		GameSession otherOne = GameServer.allGameSessions.get(roleId);
		if(otherOne != null) {
			player = otherOne.kickOff();
		}
		else {
			player = GameDb.getPlayer(roleId);
			if (player == null) {
				this.write(Package.fail(cmd));
				return;
			}
		}

		player.setSession(this);
		loginState = LoginState.ROLE_LOGIN;
		GameServer.allGameSessions.put(player.id(), this);

		player.setCity(City.instance()); // toProto will use Player.city
		logger.debug("account: " + this.accountName + " login");

		this.write(Package.create(cmd, player.toProto()));

		City.instance().add(player); // will send UnitCreate
		player.online();
		if (player.getSocietyId() != null)
		{
			SocietyManager.broadOnline(player);
		}
		sendSocialInfo();
	}

	public void createRole(short cmd, Message message) {
		Gs.CreateRole c = (Gs.CreateRole)message;
		if(c.getFaceId().length() > Player.MAX_FACE_ID_LEN)
			return;
		Player p = new Player(c.getName(), this.accountName, c.getMale(), c.getCompanyName(), c.getFaceId());
		p.addMoney(999999999);
		if(!GameDb.createPlayer(p)) {
			this.write(Package.fail(cmd, Common.Fail.Reason.roleNameDuplicated));
		}
		else {
			// this is fucking better, can keep the data consistency, however, it do update many times
			MetaData.getAllDefaultToUseItemId().forEach(id->{
				p.addItem(id, 0);
				TechTradeCenter.instance().techCompleteAction(id, 0);
			});
			GameDb.saveOrUpdate(Arrays.asList(p, TechTradeCenter.instance()));
			this.write(Package.create(cmd, playerToRoleInfo(p)));
			LogDb.insertPlayerInfo(p.id(),c.getMale());
			//复制eva元数据信息到数据库
			List<Eva> evaList=new ArrayList<Eva>();
			MetaData.getAllEva().forEach(m->{
				evaList.add(new Eva(p.id(),m.at,m.bt,m.lv,m.cexp,m.b));
			});
			EvaManager.getInstance().addEvaList(evaList);
		}
	}

	public void move(short cmd, Message message) {
		Gs.GridIndex c = (Gs.GridIndex)message;
		logger.debug("move " + c.getX() + " " + c.getY());
		if(c.getX() < 0 || c.getX() >= City.GridMaxX || c.getY() < 0 || c.getY() >= City.GridMaxY)
			return;
		GridIndex index = new GridIndex(c.getX(), c.getY());
		if(!this.player.setPosition(index))
			return;
	}
//	public void queryMetaGroundAuction(short cmd) {
//		Set<MetaGroundAuction> auctions = MetaData.getNonFinishedGroundAuction();
//		Gs.MetaGroundAuction.Builder builder = Gs.MetaGroundAuction.newBuilder();
//		for(MetaGroundAuction m : auctions) {
//			builder.addAuction(m.toProto());
//		}
//		this.write(Package.create(cmd, builder.build()));
//	}
	public void queryGroundAuction(short cmd) {
		this.write(Package.create(cmd, GroundAuction.instance().toProto()));
	}
	public void bidGround(short cmd, Message message) throws IllegalArgumentException {
		Gs.IntNum c = (Gs.IntNum)message;
		Optional<Common.Fail.Reason> err = GroundAuction.instance().bid(c.getId(), player, c.getNum());
		if(err.isPresent())
			this.write(Package.fail(cmd, err.get()));
		else
			this.write(Package.create(cmd, c));
	}

	public void startBusiness(short cmd, Message message) {
		Gs.Id c = (Gs.Id)message;
		UUID id = Util.toUuid(c.getId().toByteArray());
		Building b = City.instance().getBuilding(id);
		if(b == null || !b.ownerId().equals(player.id()))
			return;
		if(player.money() < b.allSalary()) {
			this.write(Package.fail(cmd, Common.Fail.Reason.moneyNotEnough));
			return;
		}
		if(b.startBusiness(player))
			this.write(Package.create(cmd,c));
	}
	public void shutdownBusiness(short cmd, Message message) {
		Gs.Id c = (Gs.Id)message;
		UUID id = Util.toUuid(c.getId().toByteArray());
		Building b = City.instance().getBuilding(id);
		if(b == null || !b.ownerId().equals(player.id()))
			return;
		b.shutdownBusiness();
		this.write(Package.create(cmd,c));
	}

	public void queryMarketSummary(short cmd, Message message) {
		Gs.Num c = (Gs.Num)message;
		MetaItem mi = MetaData.getItem(c.getNum()); //获取商品类型
		if(mi == null)
			return;
		Gs.MarketSummary.Builder builder = Gs.MarketSummary.newBuilder();
		City.instance().forAllGrid((grid)->{
			AtomicInteger n = new AtomicInteger(0);
			grid.forAllBuilding(building -> {
				if(building instanceof IShelf && !building.canUseBy(player.id())) {
					//如果是集散中心并且有租户，就还要从租户中获取上架信息
					if(building instanceof WareHouse &&((WareHouse) building).getRenters().size()>0){
						WareHouse wareHouse = (WareHouse) building;
						wareHouse.getRenters().forEach(r->{
							IShelf rs = (IShelf)building;
							if(rs.getSaleCount(mi.id) > 0)
								n.addAndGet(1);
						});
					}else {
						IShelf s = (IShelf) building;
						if (s.getSaleCount(mi.id) > 0)
							n.addAndGet(1);
					}
				}
			});
			builder.addInfoBuilder()
					.setIdx(Gs.GridIndex.newBuilder().setX(grid.getX()).setY(grid.getY()))
					.setItemId(mi.id)
					.setNum(n.intValue());
		});
		this.write(Package.create(cmd, builder.build()));
	}
	public void queryLabSummary(short cmd) {
		Gs.LabSummary.Builder builder = Gs.LabSummary.newBuilder();
		City.instance().forAllGrid(g->{
			Gs.LabSummary.Info.Builder b = builder.addInfoBuilder();
			GridIndex gi = new GridIndex(g.getX(),g.getY());
			b.setIdx(gi.toProto());
			AtomicInteger n = new AtomicInteger();
			g.forAllBuilding(building -> {
				if(building instanceof Laboratory && !building.outOfBusiness() && !((Laboratory)building).isExclusiveForOwner())
					n.incrementAndGet();
			});
			b.setCount(n.intValue());
		});
		this.write(Package.create(cmd, builder.build()));
	}
	public void setRoleFaceId(short cmd, Message message) {
		Gs.Str c = (Gs.Str) message;
		if(c.getStr().length() > Player.MAX_FACE_ID_LEN)
			return;
		this.player.setFaceId(c.getStr());
		GameDb.saveOrUpdate(player);
	}
	public void queryGroundSummary(short cmd) {
		this.write(Package.create(cmd, GroundManager.instance().getGroundSummaryProto()));
	}
	public void queryMarketDetail(short cmd, Message message) {
		Gs.QueryMarketDetail c = (Gs.QueryMarketDetail)message;
		GridIndex center = new GridIndex(c.getCenterIdx().getX(), c.getCenterIdx().getY());
		Gs.MarketDetail.Builder builder = Gs.MarketDetail.newBuilder();
		builder.setItemId(c.getItemId());
		City.instance().forEachGrid(center.toSyncRange(), (grid)->{
			Gs.MarketDetail.GridInfo.Builder gb = builder.addInfoBuilder();
			gb.getIdxBuilder().setX(grid.getX()).setY(grid.getY());
			grid.forAllBuilding(building->{
				//如果该建筑有货架并且并非当前玩家的
				if(building instanceof IShelf && !building.canUseBy(player.id())) {
					if(building instanceof  WareHouse&&((WareHouse) building).getRenters().size()>0){
						((WareHouse)building).getRenters().forEach(r->{
							if(r.getRenterId()!=player.id()){//排除玩家自己的数据信息
								IShelf s = (IShelf)building;
								Gs.MarketDetail.GridInfo.Building.Builder bb = gb.addBBuilder();
								bb.setId(Util.toByteString(building.id()));
								bb.setPos(building.coordinate().toProto());
								s.getSaleDetail(c.getItemId()).forEach((k,v)->{
									bb.addSaleBuilder().setItem(k.toProto()).setPrice(v).setRenterId(Util.toByteString(r.getRenterId()));
								});
								bb.setOwnerId(Util.toByteString(building.ownerId()));
								bb.setName(building.getName());
								bb.setMetaId(building.metaId());//建筑类型id
							}
						});
					}else {
						IShelf s = (IShelf) building;
						Gs.MarketDetail.GridInfo.Building.Builder bb = gb.addBBuilder();
						bb.setId(Util.toByteString(building.id()));
						bb.setPos(building.coordinate().toProto());
						s.getSaleDetail(c.getItemId()).forEach((k, v) -> {
							bb.addSaleBuilder().setItem(k.toProto()).setPrice(v);
						});
						bb.setOwnerId(Util.toByteString(building.ownerId()));
						bb.setName(building.getName());
						bb.setMetaId(building.metaId());//建筑类型id
					}
				}
			});
		});
		this.write(Package.create(cmd, builder.build()));
	}
	public void queryLabDetail(short cmd, Message message) {
		Gs.QueryLabDetail c = (Gs.QueryLabDetail)message;
		GridIndex center = new GridIndex(c.getCenterIdx().getX(), c.getCenterIdx().getY());
		Gs.LabDetail.Builder builder = Gs.LabDetail.newBuilder();
		City.instance().forEachGrid(center.toSyncRange(), (grid)->{
			Gs.LabDetail.GridInfo.Builder gb = builder.addInfoBuilder();
			gb.getIdxBuilder().setX(grid.getX()).setY(grid.getY());
			grid.forAllBuilding(building->{
				if(building instanceof Laboratory && !building.canUseBy(player.id())) {
					Laboratory s = (Laboratory)building;
					if(s.isExclusiveForOwner())
						return;
					Gs.LabDetail.GridInfo.Building.Builder bb = gb.addBBuilder();
					bb.setId(Util.toByteString(building.id()));
					bb.setPos(building.coordinate().toProto());
					bb.setEvaProb(s.getEvaProb());
					bb.setGoodProb(s.getGoodProb());
					bb.setPrice(s.getPricePreTime());
					bb.setAvailableTimes(s.getSellTimes());
					bb.setQueuedTimes(s.getQueuedTimes());
					bb.setOwnerId(Util.toByteString(building.ownerId()));
					bb.setName(building.getName());
				}
			});
		});
		this.write(Package.create(cmd, builder.build()));
	}
	public void queryPlayerInfo(short cmd, Message message) throws ExecutionException {
		Gs.Bytes c = (Gs.Bytes) message;
		if(c.getIdsCount() > 200 || c.getIdsCount() == 0) // attack
			return;
		List<UUID> ids = new ArrayList<>(c.getIdsCount());

		for(ByteString bs : c.getIdsList()) {
			ids.add(Util.toUuid(bs.toByteArray()));
		}
		List<Player.Info> infos = GameDb.getPlayerInfo(ids);
		if(ids.size() != infos.size()) {
			// send false data, kick this one ?
		}
		Gs.RoleInfos.Builder builder = Gs.RoleInfos.newBuilder();
		infos.forEach((i)->builder.addInfo(infoToRoleInfo(i)));
		this.write(Package.create(cmd, builder.build()));
	}
	public void addBuilding(short cmd, Message message) {
		Gs.AddBuilding c = (Gs.AddBuilding) message;
		int mid = c.getId();
		logger.debug("addBuilding " + c.getPos().getX() + " " + c.getPos().getY() + " player pos: " + player.getPosition().x + " " + player.getPosition().y);
		if(MetaBuilding.type(mid) == MetaBuilding.TRIVIAL)
			return;
		MetaBuilding m = MetaData.getBuilding(mid);
		if(m == null)
			return;
		Coordinate ul = new Coordinate(c.getPos());
		if(!GroundManager.instance().canBuild(player.id(), m.area(ul)))
			return;
		Building building = Building.create(mid, ul, player.id());
		building.setName(player.getCompanyName());
		boolean ok = City.instance().addBuilding(building);
		if(!ok)
			this.write(Package.fail(cmd));
		else
			this.write(Package.create(cmd, building.toProto()));
	}
	public void delBuilding(short cmd, Message message) {
		Gs.Id c = (Gs.Id) message;
		UUID id = Util.toUuid(c.getId().toByteArray());
		Building b = City.instance().getBuilding(id);
		if(b == null || b.type() == MetaBuilding.TRIVIAL || !b.canUseBy(player.id()))
			return;
		City.instance().delBuilding(b);
	}

	public void extendBag(short cmd) {
		if(player.extendBag())
			this.write(Package.create(cmd));
	}

	public void shelfAdd(short cmd, Message message) throws Exception {
		Gs.ShelfAdd c = (Gs.ShelfAdd)message;
		Item item = new Item(c.getItem());
		UUID id = Util.toUuid(c.getBuildingId().toByteArray());

		Building building = City.instance().getBuilding(id);
		if(building == null || !(building instanceof IShelf) || !building.canUseBy(player.id()) || building.outOfBusiness())
			return;
		if(building instanceof RetailShop && item.key.meta instanceof MetaMaterial)
			return;
		IShelf s = (IShelf)building;
		if(s.addshelf(item, c.getPrice(),c.getAutoRepOn())) {
			GameDb.saveOrUpdate(s);
			this.write(Package.create(cmd, c));
		}
		else
			this.write(Package.fail(cmd));
	}

	public void setAutoReplenish(short cmd, Message message) throws Exception {
		Gs.setAutoReplenish c = (Gs.setAutoReplenish)message;
		ItemKey itemKey = new ItemKey(c.getIKey());
		UUID id = Util.toUuid(c.getBuildingId().toByteArray());
		Building building = City.instance().getBuilding(id);
		if(building == null || !(building instanceof IShelf) || !(building instanceof IStorage) || !building.canUseBy(player.id()) || building.outOfBusiness())
			return;
		if(building instanceof RetailShop && itemKey.meta instanceof MetaMaterial)
			return;
		IShelf s = (IShelf)building;
		Shelf.Content i = s.getContent(itemKey);
		//int itemQuantityAll = i.n + storage.availableQuantity(itemKey.meta);
		if(s.setAutoReplenish(itemKey,c.getAutoRepOn())) {
			//处理自动补货
			if(i != null && i.autoReplenish){
				IShelf.updateAutoReplenish(s,itemKey);
			}
			GameDb.saveOrUpdate(s);
			this.write(Package.create(cmd, c));
		}
		else
			this.write(Package.fail(cmd));
	}
	public void shelfDel(short cmd, Message message) throws Exception {
		Gs.ShelfDel c = (Gs.ShelfDel)message;
		Item item = new Item(c.getItem());
		UUID bid = Util.toUuid(c.getBuildingId().toByteArray());
		Building building = City.instance().getBuilding(bid);
		if(building == null || !(building instanceof IShelf) || !building.canUseBy(player.id()) || building.outOfBusiness())
			return;
		if(building instanceof RetailShop && item.key.meta instanceof MetaMaterial)
			return;
		IShelf s = (IShelf)building;
		if(s.delshelf(item.key, item.n, true)) {
			GameDb.saveOrUpdate(s);
			this.write(Package.create(cmd, c));
		}
		else
			this.write(Package.fail(cmd));
	}
	public void shelfSet(short cmd, Message message) throws Exception {
		Gs.ShelfSet c = (Gs.ShelfSet)message;
		if(c.getPrice() <= 0)
			return;
		Item item = new Item(c.getItem());
		UUID bid = Util.toUuid(c.getBuildingId().toByteArray());
		Building building = City.instance().getBuilding(bid);
		if(building == null || !(building instanceof IShelf) || !building.canUseBy(player.id()) || building.outOfBusiness())
			return;
		if(building instanceof RetailShop && item.key.meta instanceof MetaMaterial)
			return;
		IShelf s = (IShelf)building;
		if(s.setPrice(item.key, c.getPrice())) {
			GameDb.saveOrUpdate(s);
			this.write(Package.create(cmd, c));
		}
		else
			this.write(Package.fail(cmd));
	}

	public void buyInShelf(short cmd, Message message) throws Exception {
		Gs.BuyInShelf c = (Gs.BuyInShelf)message;
		if(c.getPrice() <= 0)
			return;
		Item itemBuy = new Item(c.getItem());
		UUID bid = Util.toUuid(c.getBuildingId().toByteArray());
		UUID wid = Util.toUuid(c.getWareHouseId().toByteArray());
		Building sellBuilding = City.instance().getBuilding(bid);
		// player can not buy things in retail shop
		if(sellBuilding == null || !(sellBuilding instanceof IShelf) || !(sellBuilding instanceof IStorage) || sellBuilding instanceof RetailShop || sellBuilding.canUseBy(player.id()) || sellBuilding.outOfBusiness())
			return;
		IStorage buyStore = IStorage.get(wid, player);
		if(buyStore == null)
			return;
		IShelf sellShelf = (IShelf)sellBuilding;
		Shelf.Content i = sellShelf.getContent(itemBuy.key);
		if(i == null || i.price != c.getPrice() || i.n < itemBuy.n) {
			this.write(Package.fail(cmd));
			return;
		}
		long cost = itemBuy.n*c.getPrice();
		int freight = (int) (MetaData.getSysPara().transferChargeRatio * IStorage.distance(buyStore, (IStorage) sellBuilding));
		if(player.money() < cost + freight)
			return;

		// begin do modify
		if(!buyStore.reserve(itemBuy.key.meta, itemBuy.n))
			return;
		Player seller = GameDb.getPlayer(sellBuilding.ownerId());
		seller.addMoney(cost);
		GameServer.sendIncomeNotity(seller.id(),Gs.IncomeNotify.newBuilder()
				.setBuyer(Gs.IncomeNotify.Buyer.PLAYER)
				.setBuyerId(Util.toByteString(player.id()))
				.setFaceId(player.getFaceId())
				.setCost(cost)
				.setType(Gs.IncomeNotify.Type.INSHELF)
				.setBid(sellBuilding.metaBuilding.id)
				.setItemId(itemBuy.key.meta.id)
				.setCount(itemBuy.n)
				.build());
		player.decMoney(cost);
		if(cost>=10000000){//重大交易,交易额达到1000,广播信息给客户端,包括玩家ID，交易金额，时间
			GameServer.sendToAll(Package.create(GsCode.OpCode.cityBroadcast_VALUE,Gs.CityBroadcast.newBuilder()
					.setType(1)
					.setSellerId(Util.toByteString(seller.id()))
					.setBuyerId(Util.toByteString(player.id()))
					.setCost(cost)
					.setTs(System.currentTimeMillis())
					.build()));
			LogDb.cityBroadcast(seller.id(),player.id(),cost,0,1);
		}
		player.decMoney(freight);


		int itemId = itemBuy.key.meta.id;
		int type = MetaItem.type(itemBuy.key.meta.id);
		LogDb.payTransfer(player.id(), freight, bid, wid, itemBuy.key.producerId, itemBuy.n);

		LogDb.buyInShelf(player.id(), seller.id(), itemBuy.n, c.getPrice(),
				itemBuy.key.producerId, sellBuilding.id(),type,itemId);
		LogDb.buildingIncome(bid,player.id(),cost,type,itemId);

		sellShelf.delshelf(itemBuy.key, itemBuy.n, false);
		((IStorage)sellBuilding).consumeLock(itemBuy.key, itemBuy.n);
		sellBuilding.updateTodayIncome(cost);

		buyStore.consumeReserve(itemBuy.key, itemBuy.n, c.getPrice());

		GameDb.saveOrUpdate(Arrays.asList(player, seller, buyStore, sellBuilding));
		this.write(Package.create(cmd, c));
/*		//货架商品出售通知
		UUID[] sellBuildingAndSerller = {sellBuilding.id(),seller.id()};
		int[] itemIdAndNum = {itemId, itemBuy.n};
		MailBox.instance().sendMail(Mail.MailType.SHELF_SALE.getMailType(),seller.id(),null,sellBuildingAndSerller,itemIdAndNum);*/
	}
	public void exchangeItemList(short cmd) {
		this.write(Package.create(cmd, Exchange.instance().getItemList()));
	}

	public void exchangeGetItemDealHistory(short cmd, Message message) {
		Gs.Num c = (Gs.Num) message;
		Exchange.instance().getItemDealHistory(c.getNum());
	}
	public void exchangeBuy(short cmd, Message message) {
		Gs.ExchangeBuy c = (Gs.ExchangeBuy) message;
		if(c.getPrice() <= 0 || c.getNum() <= 0)
			return;
		MetaItem mi = MetaData.getItem(c.getItemId());
		if(mi == null)
			return;
		long cost = c.getPrice() * c.getNum();
		if(player.money() < cost)
			return;
		UUID bid = Util.toUuid(c.getBuildingId().toByteArray());
		IStorage s = IStorage.get(bid, player);
		if (s == null || !s.reserve(mi, c.getNum()))
			return;
		UUID orderId = Exchange.instance().addBuyOrder(player.id(), c.getItemId(), c.getPrice(), c.getNum(), bid);
		player.lockMoney(orderId, cost);
		s.markOrder(orderId);
		GameDb.saveOrUpdate(Arrays.asList(Exchange.instance(), player, s));
		this.write(Package.create(cmd, Gs.Id.newBuilder().setId(Util.toByteString(orderId)).build()));
	}
	public void exchangeSell(short cmd, Message message) {
		Gs.ExchangeSell c = (Gs.ExchangeSell) message;
		if(c.getPrice() <= 0 || c.getNum() <= 0)
			return;
		MetaItem mi = MetaData.getItem(c.getItemId());
		if(mi == null)
			return;
		UUID bid = Util.toUuid(c.getBuildingId().toByteArray());
		IStorage s = IStorage.get(bid, player);
		if (s == null || !s.lock(new ItemKey(mi), c.getNum()))
			return;
		UUID orderId = Exchange.instance().addSellOrder(player.id(), c.getItemId(), c.getPrice(), c.getNum(), bid);
		s.markOrder(orderId);
		GameDb.saveOrUpdate(Arrays.asList(Exchange.instance(), player, s));
		this.write(Package.create(cmd, Gs.Id.newBuilder().setId(Util.toByteString(orderId)).build()));
	}
	public void exchangeCancel(short cmd, Message message) {
		Gs.Id c = (Gs.Id) message;
		UUID id = Util.toUuid(c.getId().toByteArray());
		UUID bid = Exchange.instance().cancelOrder(player.id(), id);
		if(bid == null) {
			this.write(Package.fail(cmd));
			return;
		}
		IStorage s = IStorage.get(bid, player);
		if(s == null) {
			logger.fatal("building not exist" + bid);
			return;
		}
		s.clearOrder(id);
		GameDb.saveOrUpdate(Exchange.instance());
		this.write(Package.create(cmd));
	}
	public void exchangeStopWatch(short cmd) {
		Exchange.instance().stopWatch(this.channelId);
	}
	public void exchangeWatch(short cmd, Message message) {
		Gs.Num c = (Gs.Num) message;
		Gs.ExchangeItemDetail detail = Exchange.instance().watch(this.channelId, c.getNum());
		this.write(Package.create(cmd, detail));
	}
	public void exchangeMyOrder(short cmd) {
		this.write(Package.create(cmd, Exchange.instance().getOrder(player.id())));
	}
	public void exchangeMyDealLog(short cmd) {
		this.write(Package.create(cmd, GameDb.getExchangeDealLog(player.id())));
	}
	public void exchangeAllDealLog(short cmd, Message message) {
		Gs.Num c = (Gs.Num) message;
		int page = c.getNum();
		if(page < 0)
			return;
		this.write(Package.create(cmd, GameDb.getExchangeDealLog(page)));
	}
	public void exchangeCollect(short cmd, Message message) {
		Gs.Num c = (Gs.Num) message;
		int itemId = c.getNum();
		if(MetaData.getItem(itemId) == null)
			return;
		player.collectExchangeItem(itemId);
	}
	public void exchangeUnCollect(short cmd, Message message) {
		Gs.Num c = (Gs.Num) message;
		int itemId = c.getNum();
		if(MetaData.getItem(itemId) == null)
			return;
		player.unCollectExchangeItem(itemId);
	}
	public void stopListenBuildingDetailInform(short cmd, Message message) {
		Gs.Id c = (Gs.Id) message;
		UUID id = Util.toUuid(c.getId().toByteArray());
		Building b = City.instance().getBuilding(id);
		if(b == null)
			return;
		if(b.canUseBy(player.id()))
			b.watchDetailInfoDel(this);
	}
	public void setBuildingInfo(short cmd, Message message) {
		Gs.SetBuildingInfo c = (Gs.SetBuildingInfo) message;
		if(c.hasName() && (c.getName().length() == 0 || c.getName().length() >= 30))
			return;
		if(c.hasDes() && (c.getDes().length() == 0 || c.getDes().length() >= 30))
			return;
		UUID id = Util.toUuid(c.getId().toByteArray());
		Building b = City.instance().getBuilding(id);
		if(b == null)
			return;
		if (b.canUseBy(player.id()))
		{
			if(c.hasName())
				b.setName(c.getName());
			if(c.hasDes())
				b.setDes(c.getDes());
			if(c.hasEmoticon())
				b.setEmoticon(c.getEmoticon());
			if(c.hasShowBubble())
				b.setShowBubble(c.getShowBubble());
			b.broadcastChange();
		}
	}
	public void queryMoneyPoolInfo(short cmd) {
		this.write(Package.create(cmd, MoneyPool.instance().toProto()));
	}
	private void registBuildingDetail(Building building) {
		if(buildingDetail.size() < MAX_DETAIL_BUILDING && building.canUseBy(player.id())) {
			building.watchDetailInfoAdd(this);
			buildingDetail.add(building.id());
		}
	}
	public void detailApartment(short cmd, Message message) {
		Gs.Id c = (Gs.Id) message;
		UUID id = Util.toUuid(c.getId().toByteArray());
		Building b = City.instance().getBuilding(id);
		if(b == null || b.type() != MetaBuilding.APARTMENT)
			return;
		registBuildingDetail(b);
		this.write(Package.create(cmd, b.detailProto()));
	}
	public void detailMaterialFactory(short cmd, Message message) {
		Gs.Id c = (Gs.Id) message;
		UUID id = Util.toUuid(c.getId().toByteArray());
		Building b = City.instance().getBuilding(id);
		if(b == null || b.type() != MetaBuilding.MATERIAL)
			return;
		registBuildingDetail(b);
		this.write(Package.create(cmd, b.detailProto()));
	}

    public void detailProduceDepartment(short cmd, Message message) {
        Gs.Id c = (Gs.Id) message;
        UUID id = Util.toUuid(c.getId().toByteArray());
        Building b = City.instance().getBuilding(id);
        if(b == null || b.type() != MetaBuilding.PRODUCE)
            return;
		registBuildingDetail(b);
        this.write(Package.create(cmd, b.detailProto()));
    }
	public void detailPublicFacility(short cmd, Message message) {
		Gs.Id c = (Gs.Id) message;
		UUID id = Util.toUuid(c.getId().toByteArray());
		Building b = City.instance().getBuilding(id);
		if(b == null || b.type() != MetaBuilding.PUBLIC)
			return;
		registBuildingDetail(b);
		this.write(Package.create(cmd, b.detailProto()));
	}
    public void detailLaboratory(short cmd, Message message) {
        Gs.Id c = (Gs.Id) message;
        UUID id = Util.toUuid(c.getId().toByteArray());
        Building b = City.instance().getBuilding(id);
        if(b == null || b.type() != MetaBuilding.LAB)
            return;
		registBuildingDetail(b);
        this.write(Package.create(cmd, b.detailProto()));
    }
    public void detailRetailShop(short cmd, Message message) {
        Gs.Id c = (Gs.Id) message;
        UUID id = Util.toUuid(c.getId().toByteArray());
        Building b = City.instance().getBuilding(id);
        if(b == null || b.type() != MetaBuilding.RETAIL)
            return;
		registBuildingDetail(b);
        this.write(Package.create(cmd, b.detailProto()));
    }

    public void setSalaryRatio(short cmd, Message message) {
		Gs.SetSalary c = (Gs.SetSalary) message;
		UUID id = Util.toUuid(c.getBuildingId().toByteArray());
		Building b = City.instance().getBuilding(id);
		if(b == null || b.salaryRatioVerification(c.getSalary()) == false)
			return;
		long ts = System.currentTimeMillis();
		b.setSalaryRatio(c.getSalary(), ts);
		this.write(Package.create(cmd, c.toBuilder().setTs(ts).build()));
	}
	public void setRent(short cmd, Message message) {
		Gs.SetRent c = (Gs.SetRent) message;
		UUID id = Util.toUuid(c.getBuildingId().toByteArray());
		Building b = City.instance().getBuilding(id);
		if(b == null || b.type() != MetaBuilding.APARTMENT || !b.ownerId().equals(player.id()))
			return;
		Apartment a = (Apartment)b;
		a.setRent(c.getRent());
		this.write(Package.create(cmd, c));
	}
	public void queryPlayerBuildings(short cmd, Message message) {
		Gs.Id c = (Gs.Id)message;
		UUID id = Util.toUuid(c.getId().toByteArray());
		Gs.BuildingInfos.Builder builder = Gs.BuildingInfos.newBuilder();
		City.instance().forEachBuilding(id, b->builder.addInfo(b.toProto()));
		this.write(Package.create(cmd, builder.build()));
	}
	public void ftyAddLine(short cmd, Message message) {
		Gs.AddLine c = (Gs.AddLine) message;
		if(c.getTargetNum() <= 0 || c.getWorkerNum() <= 0)
			return;
		UUID id = Util.toUuid(c.getId().toByteArray());
		Building b = City.instance().getBuilding(id);
		if(b == null || b.outOfBusiness() || (b.type() != MetaBuilding.PRODUCE && b.type() != MetaBuilding.MATERIAL) || !b.ownerId().equals(player.id()))
			return;
		MetaItem m = MetaData.getItem(c.getItemId());
		if(m == null || (!m.useDirectly && !player.hasItem(m.id)))
			return;
		FactoryBase f = (FactoryBase) b;
		if (f.lineFull())
			return;
		int lv = 0;
		if(m instanceof MetaGood)
			lv = player.getGoodLevel(m.id);
		LineBase line = f.addLine(m, c.getWorkerNum(), c.getTargetNum(), lv);
		if(line != null)
			GameDb.saveOrUpdate(f);
	}

	public void ftyDelLine(short cmd, Message message) {
		Gs.DelLine c = (Gs.DelLine) message;
		UUID id = Util.toUuid(c.getBuildingId().toByteArray());
		Building b = City.instance().getBuilding(id);
		if (b == null || b.outOfBusiness() || (b.type() != MetaBuilding.PRODUCE && b.type() != MetaBuilding.MATERIAL) || !b.ownerId().equals(player.id()))
			return;
		UUID lineId = Util.toUuid(c.getLineId().toByteArray());
		FactoryBase f = (FactoryBase) b;
		if(f.delLine(lineId)) {
			GameDb.saveOrUpdate(f);
			if(f.lines.size() > 0){
				this.write(Package.create(cmd, c.toBuilder().setNextlineId(Util.toByteString(f.lines.get(0).id)).build()));
			}else{
				this.write(Package.create(cmd, c));
			}
		}
	}
	public void ftyChangeLine(short cmd, Message message) {
		Gs.ChangeLine c = (Gs.ChangeLine) message;
		UUID id = Util.toUuid(c.getBuildingId().toByteArray());
		Building b = City.instance().getBuilding(id);
		if (b == null || b.outOfBusiness() || (b.type() != MetaBuilding.PRODUCE && b.type() != MetaBuilding.MATERIAL) || !b.ownerId().equals(player.id()))
			return;
		UUID lineId = Util.toUuid(c.getLineId().toByteArray());
        FactoryBase f = (FactoryBase) b;
        OptionalInt tn = c.hasTargetNum()?OptionalInt.of(c.getTargetNum()):OptionalInt.empty();
        OptionalInt wn = c.hasWorkerNum()?OptionalInt.of(c.getWorkerNum()):OptionalInt.empty();
        boolean ok = f.changeLine(lineId, tn, wn);
        if(ok)
            this.write(Package.create(cmd, c));
        else
            this.write(Package.fail(cmd));
	}

	public void ftySetLineOrder(short cmd, Message message) {
		Gs.SetLineOrder c = (Gs.SetLineOrder) message;
		UUID id = Util.toUuid(c.getBuildingId().toByteArray());
		Building b = City.instance().getBuilding(id);
		if (b == null || b.outOfBusiness() || (b.type() != MetaBuilding.PRODUCE && b.type() != MetaBuilding.MATERIAL) || !b.ownerId().equals(player.id()))
			return;
		UUID lineId = Util.toUuid(c.getLineId().toByteArray());
	 	int pos = c.getLineOrder() - 1;

		FactoryBase f = (FactoryBase) b;
		if(pos >=0 && pos < f.lines.size()){
			for (int i = f.lines.size() -1; i >= 0 ; i--) {
				LineBase l = f.lines.get(i);
				if(l.id.equals(lineId)){
					f.lines.add(pos,f.lines.remove(i));
					break;
				}
			}
			this.write(Package.create(cmd, c));
		}else{
			this.write(Package.fail(cmd));
		}
	}

	public void adAddSlot(short cmd, Message message) {
		Gs.AddSlot c = (Gs.AddSlot)message;

		UUID bid = Util.toUuid(c.getBuildingId().toByteArray());
		Building building = City.instance().getBuilding(bid);
		if(building == null || building.outOfBusiness() || (!(building instanceof PublicFacility) && !(building instanceof RetailShop)) || !building.canUseBy(player.id()))
			return;
		PublicFacility pf = (PublicFacility)building;
		if(!isValidDayToRent(c.getMinDayToRent(), c.getMaxDayToRent(), pf) || !isValidRentPreDay(c.getRentPreDay(), pf))
			return;
		PublicFacility.Slot slot = pf.addSlot(c.getMaxDayToRent(), c.getMinDayToRent(), c.getRentPreDay());
		this.write(Package.create(cmd, Gs.AddSlotACK.newBuilder().setBuildingId(Util.toByteString(pf.id())).setS(slot.toProto()).build()));
	}
	public void adDelSlot(short cmd, Message message) {
		Gs.AdDelSlot c = (Gs.AdDelSlot)message;
		UUID bid = Util.toUuid(c.getBuildingId().toByteArray());
		Building building = City.instance().getBuilding(bid);
		if(building == null || building.outOfBusiness() || (!(building instanceof PublicFacility) && !(building instanceof RetailShop)) || !building.canUseBy(player.id()))
			return;
		UUID slotId = Util.toUuid(c.getSlotId().toByteArray());
		PublicFacility pf = (PublicFacility)building;
		if(pf.delSlot(slotId))
			this.write(Package.create(cmd, c));
		else
			this.write(Package.fail(cmd));
	}
	public void adSetTicket(short cmd, Message message) {
		Gs.AdSetTicket c = (Gs.AdSetTicket)message;
		if(c.getPrice() < 0)
			return;
		UUID bid = Util.toUuid(c.getBuildingId().toByteArray());
		Building building = City.instance().getBuilding(bid);
		if(building == null || building.outOfBusiness() || !(building instanceof PublicFacility) || !building.canUseBy(player.id()))
			return;
		PublicFacility pf = (PublicFacility)building;
		pf.setTickPrice(c.getPrice());
		this.write(Package.create(cmd, c));
	}
	public void adPutAdToSlot(short cmd, Message message) {
		Gs.AddAd c = (Gs.AddAd)message;
		UUID bid = Util.toUuid(c.getBuildingId().toByteArray());
		Building building = City.instance().getBuilding(bid);
		if(building == null || building.outOfBusiness() || (!(building instanceof PublicFacility) && !(building instanceof RetailShop)))
			return;
		if(c.getType() == Gs.Advertisement.Ad.Type.BUILDING) {
			if(!MetaBuilding.canAd(c.getMetaId()))
				return;
		}
		PublicFacility pf = (PublicFacility)building;
		PublicFacility.SlotRent sr = null;
		if(c.hasId()) {
			if(building.canUseBy(player.id()))
				return;
			UUID slotId = Util.toUuid(c.getId().toByteArray());
			sr = pf.getRentSlot(slotId);
			if(sr == null || !sr.renterId.equals(player.id()) || pf.hasAd(slotId))
				return;
		}
		else {
			if(!building.canUseBy(player.id()))
				return;
		}
		PublicFacility.Ad ad;
		if(c.getType() == Gs.Advertisement.Ad.Type.GOOD) {
			MetaItem m = MetaData.getItem(c.getMetaId());
			if(m == null)
				return;
			ad = pf.addAd(sr, m);
		}
		else if(c.getType() == Gs.Advertisement.Ad.Type.BUILDING) {
			ad = pf.addAd(sr, c.getMetaId());
		}
		else
			return;
		GameDb.saveOrUpdate(pf);
		this.write(Package.create(cmd, Gs.AddAdACK.newBuilder().setBuildingId(Util.toByteString(pf.id())).setA(ad.toProto()).build()));
	}
	private boolean isValidDayToRent(int min, int max, PublicFacility pf) {
		if(min <= 0 || min > pf.getMinDayToRent() || max <= 0 || max < min || max > pf.getMaxDayToRent())
			return false;
		return true;
	}
	private boolean isValidRentPreDay(int rent, PublicFacility pf) {
		if(rent <= 0 || rent > pf.getMaxRentPreDay())
			return false;
		return true;
	}

	public void adSetSlot(short cmd, Message message) {
		Gs.AdSetSlot c = (Gs.AdSetSlot)message;
		UUID bid = Util.toUuid(c.getBuildingId().toByteArray());
		Building building = City.instance().getBuilding(bid);
		if(building == null || building.outOfBusiness() || (!(building instanceof PublicFacility) && !(building instanceof RetailShop)))
			return;
		PublicFacility pf = (PublicFacility)building;
		if(!isValidDayToRent(c.getMinDayToRent(), c.getMaxDayToRent(), pf) || !isValidRentPreDay(c.getRentPreDay(), pf))
			return;
		UUID sid = Util.toUuid(c.getSlotId().toByteArray());
		PublicFacility.Slot slot = pf.getSlot(sid);
		if(slot == null)
			return;
		if(pf.isSlotRentOut(sid))
			return;
		slot.rentPreDay = c.getRentPreDay();
		slot.minDayToRent = c.getMinDayToRent();
		slot.maxDayToRent = c.getMaxDayToRent();
		this.write(Package.create(cmd, c));
	}

	public void adDelAdFromSlot(short cmd, Message message) {
		Gs.AdDelAdFromSlot c = (Gs.AdDelAdFromSlot)message;
		UUID bid = Util.toUuid(c.getBuildingId().toByteArray());
		Building building = City.instance().getBuilding(bid);
		if(building == null || building.outOfBusiness() || (!(building instanceof PublicFacility) && !(building instanceof RetailShop)))
			return;
		UUID adId = Util.toUuid(c.getAdId().toByteArray());
		PublicFacility pf = (PublicFacility)building;
		PublicFacility.Ad ad = pf.getAd(adId);
		if(ad == null)
			return;
		if(ad.sr == null && !pf.canUseBy(player.id()))
			return;
		if(ad.sr != null && !ad.sr.renterId.equals(player.id()))
			return;
		pf.delAd(adId);
		GameDb.saveOrUpdate(pf);
		this.write(Package.create(cmd, c));
	}
	public void adBuySlot(short cmd, Message message) {
		Gs.AdBuySlot c = (Gs.AdBuySlot)message;
		if(c.getDay() <= 0)
			return;
		UUID bid = Util.toUuid(c.getBuildingId().toByteArray());
		Building building = City.instance().getBuilding(bid);
		if(building == null || building.outOfBusiness() || (!(building instanceof PublicFacility) && !(building instanceof RetailShop)) || building.canUseBy(player.id()))
			return;
		UUID slotId = Util.toUuid(c.getSlotId().toByteArray());
		PublicFacility pf = (PublicFacility)building;
		if(!pf.slotCanBuy(slotId))
			return;
		PublicFacility.Slot slot = pf.getSlot(slotId);
		if(c.getDay() > slot.maxDayToRent || c.getDay() < slot.minDayToRent)
			return;
		int cost = slot.rentPreDay + slot.deposit;
		if(player.money() < cost)
			return;
		Player owner = GameDb.getPlayer(building.ownerId());
		owner.addMoney(slot.rentPreDay);
		player.decMoney(slot.rentPreDay);
		player.lockMoney(slot.id, slot.deposit);
		pf.buySlot(slotId, c.getDay(), player.id());
		GameDb.saveOrUpdate(Arrays.asList(pf, player, owner));
		this.write(Package.create(cmd, c));
	}
	public void getAllBuildingDetail(short cmd) {
		Gs.BuildingSet.Builder builder = Gs.BuildingSet.newBuilder();
		City.instance().forEachBuilding(player.id(), (Building b)->{
			b.appendDetailProto(builder);
		});
		this.write(Package.create(cmd, builder.build()));
	}

	public void delItem(short cmd, Message message) throws Exception {
		Gs.DelItem c = (Gs.DelItem)message;
		ItemKey k = new ItemKey(c.getItem());
		UUID id = Util.toUuid(c.getBuildingId().toByteArray());
		IStorage storage = IStorage.get(id, player);
		if(storage == null)
			return;
		if(storage.delItem(k))
		{
			GameDb.saveOrUpdate(storage);
			this.write(Package.create(cmd, c));
		}
		else
			this.write(Package.fail(cmd));
	}
	public void transferItem(short cmd, Message message) throws Exception {
		Gs.TransferItem c = (Gs.TransferItem)message;
		UUID srcId = Util.toUuid(c.getSrc().toByteArray());
		UUID dstId = Util.toUuid(c.getDst().toByteArray());
		IStorage src = IStorage.get(srcId, player);
		IStorage dst = IStorage.get(dstId, player);
		if(src == null || dst == null)
			return;
		int charge = (int) (MetaData.getSysPara().transferChargeRatio * IStorage.distance(src, dst));
		if(player.money() < charge)
			return;
		Item item = new Item(c.getItem());
		//如果运出的一方没有足够的存量进行锁定，那么操作失败
		if(!src.lock(item.key, item.n)) {
			this.write(Package.fail(cmd));
			return;
		}
		//如果运入的一方没有足够的预留空间，那么操作失败
		if(!dst.reserve(item.key.meta, item.n)) {
			src.unLock(item.key, item.n);
			this.write(Package.fail(cmd));
			return;
		}

		player.decMoney(charge);
		MoneyPool.instance().add(charge);
		LogDb.payTransfer(player.id(), charge, srcId, dstId, item.key.producerId, item.n);
		Storage.AvgPrice avg = src.consumeLock(item.key, item.n);
		dst.consumeReserve(item.key, item.n, (int) avg.avg);
		IShelf srcShelf = (IShelf)src;
		IShelf dstShelf = (IShelf)dst;
		{//处理自动补货
			Shelf.Content srcContent = srcShelf.getContent(item.key);
			Shelf.Content dstContent = dstShelf.getContent(item.key);
			if(srcContent != null && srcContent.autoReplenish){
				//更新自动补货的货架
				IShelf.updateAutoReplenish(srcShelf,item.key);
			}
			if(dstContent != null && dstContent.autoReplenish){
				//更新自动补货的货架
				IShelf.updateAutoReplenish(dstShelf,item.key);
			}
		}
		GameDb.saveOrUpdate(Arrays.asList(src, dst, player));
		this.write(Package.create(cmd, c));
	}
	public void rentOutGround(short cmd, Message message) {
		Gs.GroundRent c = (Gs.GroundRent)message;
		RentPara rentPara = new RentPara(c.getRentPreDay(),c.getRentDaysMin(), c.getRentDaysMax());
		if(!rentPara.valid())
			return;
		List<Coordinate> coordinates = new ArrayList<>(c.getCoordCount());
		for(Gs.MiniIndex i : c.getCoordList()) {
			coordinates.add(new Coordinate(i));
		}
		if(GroundManager.instance().rentOutGround(player.id(), coordinates, rentPara)) {
			this.write(Package.create(cmd, c));
		}
	}
	public void rentGround(short cmd, Message message) {
		Gs.RentGround c = (Gs.RentGround)message;
		RentPara rentPara = new RentPara(c.getInfo().getRentPreDay(),c.getInfo().getRentDaysMin(), c.getInfo().getRentDaysMax(), c.getDays());
		if(!rentPara.valid())
			return;
		List<Coordinate> coordinates = new ArrayList<>(c.getInfo().getCoordCount());
		for(Gs.MiniIndex i : c.getInfo().getCoordList()) {
			coordinates.add(new Coordinate(i));
		}
		if(GroundManager.instance().rentGround(player, coordinates, rentPara)) {
			this.write(Package.create(cmd, c));
		}
		else
			this.write(Package.fail(cmd));
	}
	public void sellGround(short cmd, Message message) {
		Gs.GroundSale c = (Gs.GroundSale)message;
		if(c.getPrice() <= 0)
			return;
		Set<Coordinate> coordinates = new HashSet<>();
		for(Gs.MiniIndex i : c.getCoordList()) {
			coordinates.add(new Coordinate(i));
		}
		if(coordinates.size() != c.getCoordCount())
			return;
		if(GroundManager.instance().sellGround(player.id(), coordinates, c.getPrice()))
			this.write(Package.create(cmd));
		else
			this.write(Package.fail(cmd));
	}

	public void setRoleDescription(short cmd, Message message) {
		Gs.Str c = (Gs.Str)message;
		player.setDes(c.getStr());
		GameDb.saveOrUpdate(player);
	}
	public void cancelRentGround(short cmd, Message message) {
		Gs.MiniIndexCollection c = (Gs.MiniIndexCollection)message;
		Set<Coordinate> coordinates = new HashSet<>();
		for(Gs.MiniIndex i : c.getCoordList()) {
			coordinates.add(new Coordinate(i));
		}
		if(GroundManager.instance().cancelRent(player.id(), coordinates))
			this.write(Package.create(cmd));
		else
			this.write(Package.fail(cmd));

	}
	public void cancelSellGround(short cmd, Message message) {
		Gs.MiniIndexCollection c = (Gs.MiniIndexCollection)message;
		Set<Coordinate> coordinates = new HashSet<>();
		for(Gs.MiniIndex i : c.getCoordList()) {
			coordinates.add(new Coordinate(i));
		}
		if(GroundManager.instance().cancelSell(player.id(), coordinates))
			this.write(Package.create(cmd));
		else
			this.write(Package.fail(cmd));
	}

	public void buyGround(short cmd, Message message) {
		Gs.GroundSale c = (Gs.GroundSale)message;
		if(c.getPrice() <= 0)
			return;
		Set<Coordinate> coordinates = new HashSet<>();
		for(Gs.MiniIndex i : c.getCoordList()) {
			coordinates.add(new Coordinate(i));
		}
		if(coordinates.size() != c.getCoordCount())
			return;
		if(GroundManager.instance().buyGround(player, coordinates, c.getPrice()))
			this.write(Package.create(cmd));

		else
			this.write(Package.fail(cmd));
	}
	public void labSetting(short cmd, Message message) {
		Gs.LabSetting c = (Gs.LabSetting)message;
		UUID bid = Util.toUuid(c.getBuildingId().toByteArray());
		Building building = City.instance().getBuilding(bid);
		if(building == null || building.outOfBusiness() || !(building instanceof Laboratory) || !building.canUseBy(player.id()))
			return;
		Laboratory lab = (Laboratory)building;
		if(c.getSellTimes() < 0 || c.getPricePreTime() < 0)
			return;
		lab.setting(c.getSellTimes(), c.getPricePreTime());
		GameDb.saveOrUpdate(lab);
		this.write(Package.create(cmd, c));
	}
	public void labLineAdd(short cmd, Message message) {
		Gs.LabAddLine c = (Gs.LabAddLine)message;
		UUID bid = Util.toUuid(c.getBuildingId().toByteArray());
		Building building = City.instance().getBuilding(bid);
		if(building == null || building.outOfBusiness() || !(building instanceof Laboratory))
			return;
		if(c.hasGoodCategory()) {
			if(!MetaGood.legalCategory(c.getGoodCategory()))
				return;
		}
		Laboratory lab = (Laboratory)building;
		if(!building.canUseBy(player.id())) {
			if(!c.hasTimes())
				return;
			if(c.getTimes() <= 0 || c.getTimes() > lab.getSellTimes())
				return;
			long cost = c.getTimes() * lab.getPricePreTime();
			if(!player.decMoney(cost))
				return;

			lab.updateTodayIncome(cost);
			if(c.hasGoodCategory())
				lab.updateTotalGoodIncome(cost, c.getTimes());
			else
				lab.updateTotalEvaIncome(cost, c.getTimes());
			LogDb.buildingIncome(lab.id(), player.id(), cost, 0, 0);
		}
		Laboratory.Line line = lab.addLine(c.hasGoodCategory()?c.getGoodCategory():0, c.getTimes(), player.id());
		if(null != line) {
			GameDb.saveOrUpdate(lab); // let hibernate generate the fucking line.id first
			this.write(Package.create(cmd, Gs.LabAddLineACK.newBuilder().setBuildingId(Util.toByteString(lab.id())).setLine(line.toProto()).build()));
		}
	}
	public void labLineCancel(short cmd, Message message) {
		Gs.LabCancelLine c = (Gs.LabCancelLine)message;
		UUID bid = Util.toUuid(c.getBuildingId().toByteArray());
		Building building = City.instance().getBuilding(bid);
		if(building == null || building.outOfBusiness() || !(building instanceof Laboratory) || !building.canUseBy(player.id()))
			return;
		UUID lineId = Util.toUuid(c.getLineId().toByteArray());
		Laboratory lab = (Laboratory)building;
		if(lab.delLine(lineId)) {
			GameDb.saveOrUpdate(lab);
			this.write(Package.create(cmd, c));
		}
		else
			this.write(Package.fail(cmd));
	}
	public void labExclusive(short cmd, Message message) {
		Gs.LabExclusive c = (Gs.LabExclusive)message;
		UUID bid = Util.toUuid(c.getBuildingId().toByteArray());
		Building building = City.instance().getBuilding(bid);
		if(building == null || building.outOfBusiness() || !(building instanceof Laboratory) || !building.canUseBy(player.id()))
			return;
		Laboratory lab = (Laboratory)building;
		lab.setExclusive(c.getExclusive());
		GameDb.saveOrUpdate(lab);
		this.write(Package.create(cmd, c));
	}

	public void labRoll(short cmd, Message message) {
		Gs.LabRoll c = (Gs.LabRoll)message;
		UUID bid = Util.toUuid(c.getBuildingId().toByteArray());
		Building building = City.instance().getBuilding(bid);
		if(building == null || building.outOfBusiness() || !(building instanceof Laboratory) || !building.canUseBy(player.id()))
			return;
		UUID lineId = Util.toUuid(c.getLineId().toByteArray());
		Laboratory lab = (Laboratory)building;
		Laboratory.RollResult r = lab.roll(lineId, player);
		if(r != null) {
			Gs.LabRollACK.Builder builder = Gs.LabRollACK.newBuilder();
			builder.setBuildingId(c.getBuildingId());
			builder.setLineId(c.getLineId());
			if(r.evaPoint > 0) {
				builder.setEvaPoint(r.evaPoint);
			}
			else {
				if(r.itemIds != null)
					builder.addAllItemId(r.itemIds);
			}
			GameDb.saveOrUpdate(Arrays.asList(lab, player));
			this.write(Package.create(cmd, builder.build()));
		}
	}
	public void techTradeAdd(short cmd, Message message) {
		Gs.TechTradeAdd c = (Gs.TechTradeAdd)message;
		MetaItem mi = MetaData.getItem(c.getItemId());
		if(mi == null || c.getLv() < 0 || c.getPrice() <= 0 || !player.hasItem(mi.id, c.getLv()))
			return;
		if(mi instanceof MetaMaterial)
			TechTradeCenter.instance().add(player.id(), (MetaMaterial)mi, c.getPrice());
		else
			TechTradeCenter.instance().add(player.id(), (MetaGood) mi, c.getLv(), c.getPrice());
		GameDb.saveOrUpdate(TechTradeCenter.instance());
		this.write(Package.create(cmd, c));
	}
	public void techTradeDel(short cmd, Message message) {
		Gs.Id c = (Gs.Id)message;
		UUID id = Util.toUuid(c.getId().toByteArray());
		TechTradeCenter.instance().del(player.id(), id);
		GameDb.saveOrUpdate(TechTradeCenter.instance());
	}

	public void techTradeBuy(short cmd, Message message) {
		Gs.Id c = (Gs.Id)message;
		UUID id = Util.toUuid(c.getId().toByteArray());
		TechTradeCenter.Sell sell = TechTradeCenter.instance().get(id);
		if(sell == null || sell.ownerId.equals(player.id()) || player.hasItem(sell.metaId, sell.lv))
			return;
		if(!player.decMoney(sell.price))
			return;
		Player seller = GameDb.getPlayer(sell.ownerId);
		seller.addMoney(sell.price);
		player.addItem(sell.metaId, sell.lv);
		TechTradeCenter.instance().techCompleteAction(sell.metaId, sell.lv);
		GameDb.saveOrUpdate(Arrays.asList(seller, player, TechTradeCenter.instance()));
		this.write(Package.create(cmd, c));
	}
	public void techTradeGetSummary(short cmd) {
		this.write(Package.create(cmd, TechTradeCenter.instance().getSummary()));
	}
	public void techTradeGetDetail(short cmd, Message message) {
		Gs.Num c = (Gs.Num)message;
		this.write(Package.create(cmd, TechTradeCenter.instance().getDetail(c.getNum())));
	}
	public void talentAddLine(short cmd, Message message) {
		Gs.TalentAddLine c = (Gs.TalentAddLine)message;
		if(!MetaBuilding.legalType(c.getType()) || TalentCenter.inapplicable(c.getType()) || c.getWorkerNum() <= 0)
			return;
		UUID id = Util.toUuid(c.getBuildingId().toByteArray());
		Building b = City.instance().getBuilding(id);
		if(b == null || b.outOfBusiness() || !(b instanceof TalentCenter) || !b.canUseBy(player.id()))
			return;
		TalentCenter tc = (TalentCenter)b;
		if(tc.addLine(c.getWorkerNum(), c.getType()))
			GameDb.saveOrUpdate(tc);
	}
	public void talentDelLine(short cmd, Message message) {
		Gs.TalentDelLine c = (Gs.TalentDelLine)message;
		UUID id = Util.toUuid(c.getBuildingId().toByteArray());
		Building b = City.instance().getBuilding(id);
		if(b == null || b.outOfBusiness() || !(b instanceof TalentCenter) || !b.canUseBy(player.id()))
			return;
		TalentCenter tc = (TalentCenter)b;
		if(tc.delLine(Util.toUuid(c.getLineId().toByteArray())))
			GameDb.saveOrUpdate(tc);
	}
	public void talentFinishLine(short cmd, Message message) {
		Gs.TalentFinishLine c = (Gs.TalentFinishLine)message;
		UUID id = Util.toUuid(c.getBuildingId().toByteArray());
		Building b = City.instance().getBuilding(id);
		if(b == null || b.outOfBusiness() || !(b instanceof TalentCenter) || !b.canUseBy(player.id()))
			return;
		TalentCenter tc = (TalentCenter)b;
		Talent t = tc.finishLine(Util.toUuid(c.getLineId().toByteArray()));
		if(t != null) {
			TalentManager.instance().add(t);
			this.write(Package.create(GsCode.OpCode.newTalentInform_VALUE, t.toProto()));
			GameDb.saveOrUpdate(Arrays.asList(player, tc));
			this.write(Package.create(cmd, c));
		}
	}
	public void allocTalent(short cmd, Message message) {
		Gs.AllocTalent c = (Gs.AllocTalent)message;
		UUID buildingId = Util.toUuid(c.getBuildingId().toByteArray());
		Building b = City.instance().getBuilding(buildingId);
		if(b == null || b.outOfBusiness() || !b.canUseBy(player.id()) || TalentCenter.inapplicable(b.type()))
			return;
		UUID talentId = Util.toUuid(c.getTalentId().toByteArray());
		if(!TalentManager.instance().hasTalent(player.id(), talentId))
			return;
		Talent talent = TalentManager.instance().get(talentId);
		if(talent == null || !talent.getOwnerId().equals(player.id()) || !b.canTake(talent))
			return;
		List<Object> updates;
		if(!talent.payed()) {
			int cost = b.singleSalary(talent) * talent.getWorkDays();
			if (player.money() < cost) // if the salary is dynamic, client need to know it
				return;
			talent.addMoney(cost);
			player.decMoney(cost);
			updates = Arrays.asList(talent, player);
		}
		else
			updates = Arrays.asList(talent);
		b.take(talent, updates);
		GameDb.saveOrUpdate(updates);
		this.write(Package.create(cmd, c));
	}
	public void unallocTalent(short cmd, Message message) {
		Gs.AllocTalent c = (Gs.AllocTalent)message;
		UUID buildingId = Util.toUuid(c.getBuildingId().toByteArray());
		Building b = City.instance().getBuilding(buildingId);
		if(b == null || b.outOfBusiness() || !b.canUseBy(player.id()) || TalentCenter.inapplicable(b.type()))
			return;
		UUID talentId = Util.toUuid(c.getTalentId().toByteArray());
		if(!b.hasTalent(talentId))
			return;
		Talent talent = TalentManager.instance().get(talentId);
		Npc npc = b.untake(talent);
		if(npc == null)
			GameDb.saveOrUpdate(talent);
		else
			GameDb.saveOrUpdateAndDelete(Arrays.asList(talent), Arrays.asList(npc));
	}
	//wxj========================================================
	private void sendSocialInfo()
	{
		//push blacklist
		Gs.RoleInfos.Builder builder1 = Gs.RoleInfos.newBuilder();
		try
		{
			GameDb.getPlayerInfo(player.getBlacklist()).forEach(info -> {
				builder1.addInfo(infoToRoleInfo(info));
			});
		}
		catch (ExecutionException e)
		{
			logger.fatal("GameSession.toDoOnline(): push blacklist failed.");
			e.printStackTrace();
		}
		this.write(Package.create(GsCode.OpCode.getBlacklist_VALUE, builder1.build()));

		//push friend addition request
		List<FriendRequest> list = GameDb.getFriendRequest(null, player.id());
		Gs.RequestFriend.Builder builder = Gs.RequestFriend.newBuilder();
		UUID from_id = null;
		List<FriendRequest> toBeDel = new ArrayList<>();
		List<FriendRequest> toBeUpdate = new ArrayList<>();
		for (FriendRequest fr : list)
		{
			//only send 10 times
			if (fr.getCount() > 10)
			{
				toBeDel.add(fr);
				continue;
			}
			from_id = fr.getFrom_id();
			String name = "";
			String companyName = "";
			String pic = "";
			try
			{
				for (Player.Info i :
						GameDb.getPlayerInfo(ImmutableList.of(from_id)))
				{
					name = i.getName();
					companyName = i.getCompanyName();
					pic = i.getFaceId();
				}
			}
			catch (ExecutionException e)
			{
				logger.fatal("get player name failed : id=" + from_id);
				e.printStackTrace();
			}
			builder.setId(Util.toByteString(from_id))
					.setName(name)
					.setDesc(fr.getDescp())
					.setFaceId(pic)
					.setCompanyName(companyName);
			this.write(Package.create(GsCode.OpCode.addFriendReq_VALUE, builder.build()));
			fr.setCount(fr.getCount() + 1);
			toBeUpdate.add(fr);
		}
		GameDb.newSessionSaveOrUpdateAndDelete(toBeUpdate,toBeDel);

		//push offline message
		List<OfflineMessage> lists = GameDb.getOfflineMsgAndDel(player.id());
		lists.forEach(message -> {
			ManagerCommunication.getInstance().sendMsgToPerson(this, message);
		});
		//notify friend online
		FriendManager.getInstance().broadcastStatue(player.id(), true);
	}
	public void searchPlayerByName(short cmd, Message message)
	{
		Gs.Str name = (Gs.Str) message;
		if (Strings.isNullOrEmpty(name.getStr())) return;
		if (name.getStr().trim().isEmpty()) return;
		List<Player.Info> infos= FriendManager.getInstance()
				.searchPlayByName(name.getStr());
		Gs.RoleInfos.Builder builder = Gs.RoleInfos.newBuilder();
		infos.forEach(info -> {
			if (!info.getId().equals(player.id())) {
				builder.addInfo(infoToRoleInfo(info));
			}
		});
		this.write(Package.create(cmd,builder.build()));
	}

	private Gs.RoleInfo infoToRoleInfo(Player.Info info)
	{
		return Gs.RoleInfo.newBuilder()
				.setId(Util.toByteString(info.getId()))
				.setName(info.getName())
				.setCompanyName(info.getCompanyName())
				.setDes(info.getDes())
				.setMale(info.isMale())
				.setFaceId(info.getFaceId())
				.setCreateTs(info.getCreateTs())
				.build();
	}

	private Gs.RoleInfo playerToRoleInfo(Player player)
	{
		return Gs.RoleInfo.newBuilder()
				.setId(Util.toByteString(player.id()))
				.setName(player.getName())
				.setCompanyName(player.getCompanyName())
				.setDes(player.getDes())
				.setMale(player.isMale())
				.setFaceId(player.getFaceId())
				.setCreateTs(player.getCreateTs())
				.build();
	}
	public void addFriend(short cmd, Message message)
	{
		Gs.ByteStr addMsg = (Gs.ByteStr) message;
		UUID targetId = Util.toUuid(addMsg.getId().toByteArray());
		if (!FriendManager.playerFriends.getUnchecked(id()).contains(targetId))
		{
			if (GameDb.getPlayer(targetId).getBlacklist().contains(player.id()))
			{
				//邮件通知黑名单拒绝添加
			}
			else
			{
				GameSession gs = GameServer.allGameSessions.get(targetId);
				if (gs != null)
				{
					Gs.RequestFriend.Builder builder = Gs.RequestFriend.newBuilder();
					builder.setId(Util.toByteString(player.id()))
							.setName(player.getName())
							.setDesc(addMsg.getDesc())
							.setCompanyName(player.getCompanyName())
							.setFaceId(player.getFaceId());
					gs.write(Package.create(GsCode.OpCode.addFriendReq_VALUE, builder.build()));

				}
				List<FriendRequest> list = GameDb.getFriendRequest(player.id(), targetId);
				if (list.isEmpty())
				{
					FriendRequest friendRequest = new FriendRequest(player.id(), targetId, addMsg.getDesc());
					GameDb.statelessInsert(friendRequest);
				}
			}
		}
	}

	public void addFriendResult(short cmd, Message message)
	{
		Gs.ByteBool result = (Gs.ByteBool) message;
		UUID sourceId = Util.toUuid(result.getId().toByteArray());
		boolean blacklistNotify = false;
		if (result.getB() &&
				!FriendManager.playerFriends.getUnchecked(player.id()).contains(sourceId))
		{
			FriendManager.getInstance().saveFriendship(sourceId, player.id());
			Player temp = GameDb.getPlayer(sourceId);
			if (temp.getBlacklist().contains(player.id()))
			{
				temp.getBlacklist().remove(player.id());
				GameDb.saveOrUpdate(temp);
				blacklistNotify = true;
			}
			Gs.RoleInfo firend = infoToRoleInfo(GameDb.getPlayerInfo(sourceId));
			this.write(
					Package.create(GsCode.OpCode.addFriendSucess_VALUE, firend));
			GameSession gameSession = GameServer.allGameSessions.get(sourceId);
			if (gameSession != null)
			{
				gameSession.write(
						Package.create(GsCode.OpCode.addFriendSucess_VALUE,
								playerToRoleInfo(player)));
				if (blacklistNotify) {
					gameSession.write(
							Package.create(GsCode.OpCode.deleteBlacklist_VALUE,
									Gs.Id.newBuilder().setId(Util.toByteString(player.id())).build()));
				}
			}
			//邮件通知添加好友成功
			UUID[] oppositeId = {player.id()};
			MailBox.instance().sendMail(Mail.MailType.ADD_FRIEND_SUCCESS.getMailType(),sourceId,null,oppositeId,null);
		}
		else
		{
			//refuse add
		}
		//remove record
		GameDb.deleteFriendRequest(sourceId,player.id());
	}

	public void deleteFriend(short cmd, Message message)
	{
		Gs.Id id = (Gs.Id) message;
		FriendManager.getInstance().deleteFriend(id(),Util.toUuid(id.getId().toByteArray()));
	}

	public void addBlacklist(short cmd, Message message)
	{
		Gs.Id id = (Gs.Id) message;
		if (!FriendManager.playerFriends.getUnchecked(player.id())
				.contains(Util.toUuid(id.getId().toByteArray())))
		{
			player.getBlacklist().add(Util.toUuid(id.getId().toByteArray()));
			GameDb.saveOrUpdate(player);
		}
		else
		{
			FriendManager.getInstance().deleteFriendWithBlacklist(player,
					Util.toUuid(id.getId().toByteArray()));
		}
		Player.Info info = GameDb.getPlayerInfo(Util.toUuid(id.getId().toByteArray()));
		if (info != null)
		{
			this.write(Package.create(cmd, infoToRoleInfo(info)));
		}
	}
	public void deleteBlacklist(short cmd, Message message)
	{
		Gs.Id id = (Gs.Id) message;
		player.getBlacklist().remove(Util.toUuid(id.getId().toByteArray()));
		this.write(Package.create(cmd, id));
		GameDb.saveOrUpdate(player);
	}

	public void communication(short cmd, Message message)
	{
		ManagerCommunication.getInstance().processing((Gs.CommunicationReq) message,player);
	}

    public void getGroundInfo(short cmd)
    {
        Gs.GroundChange.Builder builder = Gs.GroundChange.newBuilder();
        builder.addAllInfo(GroundManager.instance().getGroundProto(player.id()));
        this.write(Package.create(cmd, builder.build()));
    }

	public void createSociety(short cmd, Message message)
	{
		Gs.CreateSociety gsSociety = (Gs.CreateSociety) message;
		String name = gsSociety.getName();
		String declaration = gsSociety.getDeclaration();
		if (Strings.isNullOrEmpty(name) || Strings.isNullOrEmpty(declaration)
				|| player.getSocietyId() != null)
		{
			return;
		}
		Society society = SocietyManager.createSociety(player.id(), name, declaration);
		if (society == null)
		{
			this.write(Package.fail(cmd,Common.Fail.Reason.societyNameDuplicated));
		}
		else
		{
			player.setSocietyId(society.getId());
			GameDb.saveOrUpdate(player);
			this.write(Package.create(cmd, SocietyManager.toSocietyDetailProto(society,player)));
		}
	}

	public void modifySocietyName(short cmd, Message message)
	{
		Gs.BytesStrings params = (Gs.BytesStrings) message;
		String name = params.getStr();
		UUID societyId = Util.toUuid(params.getSocietyId().toByteArray());
		if (!Strings.isNullOrEmpty(name) && societyId.equals(player.getSocietyId()))
		{
			SocietyManager.modifySocietyName(societyId, name, this, cmd);
		}

	}
	public void modifyDeclaration(short cmd, Message message)
	{
		Gs.BytesStrings params = (Gs.BytesStrings) message;
		String declaration = params.getStr();
		UUID societyId = Util.toUuid(params.getSocietyId().toByteArray());
		if (!Strings.isNullOrEmpty(declaration)&& societyId.equals(player.getSocietyId()))
		{
			SocietyManager.modifyDeclaration(societyId, declaration, this, cmd);
		}
	}

	public void modifyIntroduction(short cmd, Message message)
	{
		Gs.BytesStrings params = (Gs.BytesStrings) message;
		String introduction = params.getStr();
		UUID societyId = Util.toUuid(params.getSocietyId().toByteArray());
		if (societyId.equals(player.getSocietyId()))
		{
			SocietyManager.modifyIntroduction(societyId, introduction, this, cmd);
		}
	}

	public void getSocietyInfo(short cmd, Message message)
	{
		UUID societyId = Util.toUuid(((Gs.Id) message).getId().toByteArray());
		if (societyId.equals(player.getSocietyId()))
		{
			Society society = SocietyManager.getSociety(societyId);
			if (society != null)
			{
				this.write(Package.create(cmd, SocietyManager.toSocietyDetailProto(society,player)));
			}

		}
	}

	public void getSocietyList(short cmd)
	{
		this.write(Package.create(cmd,
				Gs.SocietyList.newBuilder()
						.addAllListInfo(SocietyManager.getSocietyList())
						.build()));
	}

	public void joinSociety(short cmd, Message message)
	{
		String str = ((Gs.ByteStr) message).getDesc();
		UUID societyId = Util.toUuid(((Gs.ByteStr) message).getId().toByteArray());
		if (!Strings.isNullOrEmpty(str) && player.getSocietyId() == null)
		{
			if (SocietyManager.reqJoinSociety(societyId,player,str))
			{
				this.write(Package.create(cmd,message));
				return;
			}
			this.write(Package.fail(cmd));
		}
	}

	public void joinHandle(short cmd, Message message)
	{
		Gs.JoinHandle params = (Gs.JoinHandle) message;
		SocietyManager.handleReqJoin(params,player);
	}

	public void exitSociety(short cmd, Message message)
	{
		UUID societyId = Util.toUuid(((Gs.Id) message).getId().toByteArray());
		if (societyId.equals(player.getSocietyId()))
		{
			if (SocietyManager.exitSociety(societyId, player))
			{
				this.write(Package.create(cmd,
						Gs.ByteBool.newBuilder()
						.setB(true)
						.setId(Util.toByteString(societyId))
						.build()));
			}
		}
	}

	public void appointerPost(short cmd, Message message)
	{
		Gs.AppointerReq params = (Gs.AppointerReq) message;
		UUID societyId = Util.toUuid(params.getSocietyId().toByteArray());
		if (societyId.equals(player.getSocietyId()))
		{
			if (SocietyManager.appointerPost(player,params))
			{
				this.write(Package.create(cmd, message));
			}
		}
	}

	public void kickMember(short cmd, Message message)
	{
		Gs.Ids params = (Gs.Ids) message;
		UUID societyId = Util.toUuid(params.getSocietyId().toByteArray());
		if (societyId.equals(player.getSocietyId()))
		{
			if (SocietyManager.kickMember(societyId, player,
					Util.toUuid(params.getPlayerId().toByteArray())))
			{
				this.write(Package.create(cmd, message));
				GameSession session = GameServer.allGameSessions.get(Util.toUuid(params.getPlayerId().toByteArray()));
				if (session != null)
				{
					session.write(Package.create(GsCode.OpCode.exitSociety_VALUE,
							Gs.ByteBool.newBuilder()
									.setB(false)
									.setId(Util.toByteString(societyId))
									.build()));
				}
				/**
				 * TODO:
				 * 2019/2/25
				 * 邮件通知被踢出公会
				 */
			}
		}
	}

	public void getPrivateBuildingCommonInfo(short cmd,Message message)
	{
		Gs.Bytes ids = (Gs.Bytes) message;
		List<Building> buildings = new ArrayList<>(ids.getIdsCount());
		for (ByteString bs : ids.getIdsList())
		{
            Building building = City.instance().getBuilding(Util.toUuid(bs.toByteArray()));
            if (building != null && player.id().equals(building.ownerId()))
            {
                buildings.add(building);
            }
            else return;
		}
        Gs.PrivateBuildingInfos.Builder builder = Gs.PrivateBuildingInfos.newBuilder();
        buildings.forEach(building ->
        {
            builder.addInfos(building.getPrivateBuildingInfo());
        });
        this.write(Package.create(cmd, builder.build()));
	}

    public void closeContract(short cmd, Message message)
    {
        UUID bid = Util.toUuid(((Gs.Id) message).getId().toByteArray());
        Building building = City.instance().getBuilding(bid);
        if (building != null && player.id().equals(building.ownerId())
                && building instanceof IBuildingContract)
        {
			BuildingContract buildingContract = ((IBuildingContract) building).getBuildingContract();
            if (!buildingContract.isSign() && buildingContract.isOpen())
            {
				buildingContract.closeContract();
                GameDb.saveOrUpdate(building);
                this.write(Package.create(cmd, message));
            }
            else this.write(Package.fail(cmd));
        }
    }
    public void settingContract(short cmd, Message message)
    {
        Gs.ContractSetting setting = (Gs.ContractSetting) message;
        UUID bid = Util.toUuid(setting.getBuildingId().toByteArray());
        Building building = City.instance().getBuilding(bid);
        long price = setting.getPrice();
        int hours = setting.getHours();
        if (building != null && player.id().equals(building.ownerId())
                && building instanceof IBuildingContract)
        {
			BuildingContract buildingContract = ((IBuildingContract) building).getBuildingContract();
            if (hours > 0 && !buildingContract.isSign())
            {
				buildingContract.openOrSetContract(price, hours);
                GameDb.saveOrUpdate(building);
                this.write(Package.create(cmd, message));
            }
            else this.write(Package.fail(cmd));
        }
    }

	public void cancelContract(short cmd, Message message)
	{
		UUID bid = Util.toUuid(((Gs.Id) message).getId().toByteArray());
		Building building = City.instance().getBuilding(bid);
		if (building != null && player.id().equals(building.ownerId())
				&& building instanceof IBuildingContract)
		{
			BuildingContract buildingContract = ((IBuildingContract) building).getBuildingContract();
			if (buildingContract.isSign())
			{
				Contract contract = ContractManager.getInstance().getContractById(buildingContract.getContractId());
				if (contract.getSignId().equals(player.id()))
				{
					if (ContractManager.getInstance().deleteContract(contract, (IBuildingContract) building))
					{
						this.write(Package.create(cmd, message));
					}
					else this.write(Package.fail(cmd));
				}

			}
		}

	}

	public void signContract(short cmd, Message message)
	{
		Gs.SignContract signContract = (Gs.SignContract) message;
		UUID bid = Util.toUuid(signContract.getBuildingId().toByteArray());
		Building building = City.instance().getBuilding(bid);
		if (building instanceof IBuildingContract)
		{
			if (building.outOfBusiness())
			{
				this.write(Package.fail(cmd));
			}
			//本人签约
			if (player.id().equals(building.ownerId()))
			{
				BuildingContract buildingContract = ((IBuildingContract) building).getBuildingContract();
				if (buildingContract.isSign())
				{
					this.write(Package.fail(cmd));
					return;
				}
				buildingContract.openOrSetContract(0, 0);
				Contract contract = ContractManager.getInstance().signContract(player, (IBuildingContract) building);
				this.write(Package.create(cmd,contract.toProto()));
			}
			else
			{
				BuildingContract buildingContract = ((IBuildingContract) building).getBuildingContract();
				long price = signContract.getPrice();
				int hours = signContract.getHours();
				if (!buildingContract.isOpen() || buildingContract.isSign()
						|| price != buildingContract.getPrice()
						|| hours != buildingContract.getDurationHour()
						|| player.money() < price * hours)
				{
					this.write(Package.fail(cmd));
					return;
				}
				Contract contract = ContractManager.getInstance().signContract(player, (IBuildingContract) building);
				this.write(Package.create(cmd,contract.toProto()));
			}
		}
	}

	public void getCompanyContracts(short cmd)
	{
		Gs.Contracts.Builder builder = Gs.Contracts.newBuilder();
		builder.addAllContracts(ContractManager
				.getInstance()
				.getContractsBySignId(player.id())
				.stream()
				.map(Contract::toProto)
				.collect(Collectors.toList()));
		this.write(Package.create(cmd, builder.build()));
	}

	public void queryContractSummary(short cmd)
	{
		Gs.ContractSummary.Builder builder = Gs.ContractSummary.newBuilder();
		City.instance().forAllGrid(g->{
			Gs.ContractSummary.Info.Builder b = builder.addInfoBuilder();
			GridIndex gi = new GridIndex(g.getX(),g.getY());
			b.setIdx(gi.toProto());
			AtomicInteger n = new AtomicInteger();
			g.forAllBuilding(building -> {
				if (building instanceof IBuildingContract
						&& !building.outOfBusiness()
						&& ((IBuildingContract) building).getBuildingContract().isOpen()
						&& !((IBuildingContract) building).getBuildingContract().isSign())
				{
					n.incrementAndGet();
				}
			});
			b.setCount(n.intValue());
		});
		this.write(Package.create(cmd, builder.build()));
	}

	public void queryContractGridDetail(short cmd, Message message)
	{
		Gs.GridIndex gridIndex = (Gs.GridIndex) message;
		Gs.ContractGridDetail.Builder builder = Gs.ContractGridDetail.newBuilder();
		builder.setIdx(gridIndex);
		City.instance().forAllGrid(grid ->
		{
			if (grid.getX() == gridIndex.getX() && grid.getY() == gridIndex.getY())
			{

				grid.forAllBuilding(building ->
				{
					if (building instanceof IBuildingContract
							&& !building.outOfBusiness()
							&& ((IBuildingContract) building).getBuildingContract().isOpen()
							&& !((IBuildingContract) building).getBuildingContract().isSign())
					{
						Gs.ContractGridDetail.Info.Builder b = builder.addInfoBuilder();
						b.setOwnerId(Util.toByteString(building.ownerId()))
								.setBuildingName(building.getName())
								.setPos(building.coordinate().toProto())
								.setHours(((IBuildingContract) building).getBuildingContract().getDurationHour())
								.setPrice(((IBuildingContract) building).getBuildingContract().getPrice());
					}
				});
			}
		});
		this.write(Package.create(cmd, builder.build()));
	}

	public void getLeagueInfo(short cmd, Message message)
	{
		int techId = ((Gs.Num) message).getNum();
		Gs.LeagueInfo leagueInfo = LeagueManager.getInstance().queryProtoLeagueInfo(player.id(), techId);
		this.write(Package.create(cmd,leagueInfo));
	}

	public void setLeagueInfo(short cmd, Message message)
	{
		Gs.LeagueInfoSetting setting = (Gs.LeagueInfoSetting) message;
		if (LeagueManager.getInstance().settingLeagueInfo(player.id(), setting))
		{
			Gs.LeagueInfoSetting.Builder builder = Gs.LeagueInfoSetting.newBuilder()
					.setTechId(setting.getTechId())
					.setSetting(
							Gs.LeagueSetting.newBuilder()
									.setIsSettingOpen(true)
									.setPrice(setting.getSetting().getPrice())
									.setMaxHours(setting.getSetting().getMaxHours())
									.setMinHours(setting.getSetting().getMinHours())
									.build()
					);
			this.write(Package.create(cmd, builder.build()));
		}
		else
		{
			this.write(Package.fail(cmd));
		}
	}

	public void closeLeagueInfo(short cmd, Message message)
	{
		int techId = ((Gs.Num) message).getNum();
		if (LeagueManager.getInstance().closeLeagueInfo(player.id(), techId))
		{
			this.write(Package.create(cmd,message));
		}
		else
		{
			this.write(Package.fail(cmd));
		}
	}

	public void queryLeagueTechList(short cmd, Message message)
	{
		int techId = ((Gs.Num) message).getNum();
		Gs.LeagueTechList.Builder builder = Gs.LeagueTechList.newBuilder();
		builder.setTechId(techId);
		LeagueManager.getInstance().getOpenedLeagueInfoByTechId(techId)
				.forEach(leagueInfo -> {
					Gs.LeagueTech.Builder techBuilder = Gs.LeagueTech.newBuilder();
					techBuilder.setOwnerId(Util.toByteString(leagueInfo.getUid().getPlayerId()))
						.setSetting(leagueInfo.toSettingProto())
						.addAllTechInfo(LeagueManager.getInstance().getLeagueTechInfo(leagueInfo))
						.setJoinBCount(leagueInfo.getMemberSize())
						.setOwnerBcount(LeagueManager.getInstance().getLeagueInfoOwnerBcount(leagueInfo));
					builder.addTechList(techBuilder.build());
				});
		this.write(Package.create(cmd, builder.build()));
	}

	public void queryBuildingListByPlayerTech(short cmd, Message message)
	{
		Gs.ByteNum ident = (Gs.ByteNum) message;
		UUID pid = Util.toUuid(ident.getId().toByteArray());
		int techId = ident.getNum();
		Gs.TechBuildingPoss.Builder builder = Gs.TechBuildingPoss.newBuilder();
		builder.setIdentity(ident);
		LeagueManager.getInstance().getBuildingListByPlayerTech(pid, techId)
				.forEach(building -> builder.addPosInfo(building.toPositionProto()));
		this.write(Package.create(cmd, builder.build()));
	}

	public void joinLeague(short cmd, Message message)
	{
		Gs.JoinLeague joinLeague = (Gs.JoinLeague) message;
		if (LeagueManager.getInstance().joinLeague(player, joinLeague)) {
			this.write(Package.create(cmd,joinLeague));
		}
		else
		{
			this.write(Package.fail(cmd));
		}
	}

	//===========================================================

	//llb========================================================

	public void getAllMails(short cmd) {
		Collection<Mail> mails = MailBox.instance().getAllMails(player.id());
		Gs.Mails.Builder builder = Gs.Mails.newBuilder();
		mails.forEach(mail -> builder.addMail(mail.toProto()));
		this.write(Package.create(cmd, builder.build()));
	}

	public void delMail(short cmd, Message message)	{
		Gs.Id id = (Gs.Id) message;
		MailBox.instance().deleteMail(Util.toUuid(id.getId().toByteArray()));
		this.write(Package.create(cmd, id));
	}

	public void mailRead(short cmd, Message message) {
		Gs.Id id = (Gs.Id) message;
		MailBox.instance().mailRead(Util.toUuid(id.getId().toByteArray()));
		this.write(Package.create(cmd, id));
	}

	//===========================================================
	/**
	 * (市民需求)每种类型npc的数量
	 */
	public void eachTypeNpcNum(short cmd) {
		Gs.EachTypeNpcNum.Builder list = Gs.EachTypeNpcNum.newBuilder();
	    NpcManager.instance().countNpcByType().forEach((k,v)->{
	    	list.addCountNpcMap(Gs.CountNpcMap.newBuilder().setKey(k).setValue(v).build());
	    });
		this.write(Package.create(cmd,list.build()));
	}

	//查询行业平均工资
	public void QueryIndustryWages(short cmd, Message message)
	{
		Gs.QueryIndustryWages msg = (Gs.QueryIndustryWages)message;
		MetaBuilding buildingdata = null;
		int tp = msg.getType();
		switch(MetaBuilding.type(tp))
		{
			case MetaBuilding.TRIVIAL:
				buildingdata = MetaData.getTrivialBuilding(tp);
				break;
			case MetaBuilding.MATERIAL:
				buildingdata = MetaData.getMaterialFactory(tp);
				break;
			case MetaBuilding.PRODUCE:
				buildingdata = MetaData.getProduceDepartment(tp);
				break;
			case MetaBuilding.RETAIL:
				buildingdata = MetaData.getRetailShop(tp);
				break;
			case MetaBuilding.APARTMENT:
				buildingdata = MetaData.getApartment(tp);
				break;
			case MetaBuilding.LAB:
				buildingdata = MetaData.getLaboratory(tp);
				break;
			case MetaBuilding.PUBLIC:
				buildingdata = MetaData.getPublicFacility(tp);
				break;
			case MetaBuilding.TALENT:
				buildingdata = MetaData.getTalentCenter(tp);
				break;
		}
		if(buildingdata != null){
			this.write(Package.create(cmd, msg.toBuilder().setIndustryWages(buildingdata.salary).build()));
		}else{
			this.write(Package.fail(cmd));
		}
	}
	public void queryMyBuildings(short cmd, Message message) {
		Gs.QueryMyBuildings msg = (Gs.QueryMyBuildings)message;
		UUID id = Util.toUuid(msg.getId().toByteArray());
		Map<Integer,List<BuildingInfo>> map=new HashMap<Integer,List<BuildingInfo>>();
		
		Gs.MyBuildingInfos.Builder list = Gs.MyBuildingInfos.newBuilder();
		City.instance().forEachBuilding(id, b->{
			BuildingInfo buildingInfo=b.myProto(id);
			int type=buildingInfo.getType();
			map.computeIfAbsent(type,
                    k -> new ArrayList<BuildingInfo>()).add(buildingInfo);
		 }
		);
		map.forEach((k,v)->{
			Gs.MyBuildingInfo.Builder builder = Gs.MyBuildingInfo.newBuilder();
			builder.setType(k);
			v.forEach(buildingInfo->{
				builder.addInfo(buildingInfo);
			});
			list.addMyBuildingInfo(builder.build());
		});
		
		this.write(Package.create(cmd, list.build()));
	}
    public void queryMyEva(short cmd, Message message)
    {
        UUID pid = Util.toUuid(((Gs.Id) message).getId().toByteArray());
        
		Gs.Evas.Builder list = Gs.Evas.newBuilder();
		EvaManager.getInstance().getEvaList(pid).forEach(eva->{
			list.addEva(eva.toProto());
		});
		this.write(Package.create(cmd, list.build()));
    }
    public void updateMyEva(short cmd, Message message)
    {
    	Gs.Eva eva = (Gs.Eva)message;
		int level=eva.getLv();
		long cexp=eva.getCexp();
    	Map<Integer,MetaExperiences> map=MetaData.getAllExperiences();
    	
		if(level>=1){//计算等级
			long exp=0l;
			do{
				MetaExperiences obj=map.get(level);
				exp=obj.exp;
				if(cexp>=exp){
					cexp=cexp-exp; //减去升级需要的经验
					level++;  
				}
			}while(cexp>=exp);
		}
		
		Eva e=new Eva();
		e.setId(Util.toUuid(eva.getId().toByteArray()));
		e.setPid(Util.toUuid(eva.getPid().toByteArray()));
		e.setAt(eva.getAt());
		e.setBt(eva.getBt().getNumber());
		e.setLv(level);
		e.setCexp(cexp);
		e.setB(eva.getB());
    	EvaManager.getInstance().updateEva(e);
    	
    	Player player=GameDb.getPlayer(Util.toUuid(eva.getPid().toByteArray()));
    	player.decEva(eva.getDecEva());
       	GameDb.saveOrUpdate(player);
       	
    	this.write(Package.create(cmd, eva.toBuilder().setCexp(cexp).setLv(level).setDecEva(eva.getDecEva()).build()));
    }
    public void queryMyBrands(short cmd, Message message)
    {
    	Gs.QueryMyBrands msg = (Gs.QueryMyBrands)message;
    	int type=msg.getType();
    	UUID bId = Util.toUuid(msg.getBId().toByteArray());
		UUID pId = Util.toUuid(msg.getPId().toByteArray());

        Building build=City.instance().getBuilding(bId);

        Gs.BuildingInfo buildInfo = build.myProto(pId);

        Gs.MyBrands.Builder list = Gs.MyBrands.newBuilder();
		MetaData.getBuildingTech(type).forEach(itemId->{
			Gs.MyBrands.Brand.Builder band = Gs.MyBrands.Brand.newBuilder();
			band.setItemId(itemId).setBrand(buildInfo.getBrand());
    		GameDb.getEvaInfoList(pId,itemId).forEach(eva->{
    			//优先查询建筑正在使用的某项加盟技术
    			BrandLeague bl=LeagueManager.getInstance().getBrandLeague(bId,itemId);
    	        if(bl!=null){
    	            Eva e=EvaManager.getInstance().getEva(bl.getPlayerId(), eva.getAt(), eva.getBt());
    	 			band.addEva(e.toProto());
    	        }
     			band.addEva(eva.toProto());
    		});
    		list.addBrand(band.build());
		});
    	this.write(Package.create(cmd, list.build()));
    }

    public void queryMyBrandDetail(short cmd,Message message){
    	Gs.QueryMyBrandDetail msg = (Gs.QueryMyBrandDetail)message;
    	UUID bId = Util.toUuid(msg.getBId().toByteArray());
    	UUID pId = Util.toUuid(msg.getPId().toByteArray());
        int itemId=msg.getItemId();
        List<UUID> ls=new ArrayList<UUID>();
        ls.add(pId);

        Set<LeagueInfo.UID> set=LeagueManager.getInstance().getBuildingLeagueTech(bId); //加盟的技术
        for (LeagueInfo.UID info : set) {
    		int techId=info.getTechId();
			if(itemId==techId){
				ls.add(info.getPlayerId());//某项技术可能加盟多个，但是只能使用其中一个
			}
		}

        Gs.MyBrandDetail.Builder list = Gs.MyBrandDetail.newBuilder();
        ls.forEach(playerId->{
			Player player=GameDb.getPlayer(playerId);
		    Building build=City.instance().getBuilding(bId);
		    Gs.BuildingInfo buildInfo = build.myProto(playerId);
			long leaveTime=LeagueManager.getInstance().queryProtoLeagueMemberLeaveTime(playerId,itemId,bId);

			Gs.MyBrandDetail.BrandDetail.Builder detail = Gs.MyBrandDetail.BrandDetail.newBuilder();
			detail.setBId(Util.toByteString(bId))
			      .setTechId(itemId)
			      .setPId(Util.toByteString(playerId))
			      .setName(player.getName())
			      .setBrand(buildInfo.getBrand());

    		GameDb.getEvaInfoList(playerId,itemId).forEach(eva->{
    			detail.addEva(eva.toProto());
    		});
    		detail.setLeaveTime(leaveTime);
			list.addDetail(detail.build());
        });
		this.write(Package.create(cmd, list.build()));
    }

    public void updateMyBrandDetail(short cmd,Message message){
    	Gs.BrandLeague msg = (Gs.BrandLeague)message;
		UUID bId = Util.toUuid(msg.getBId().toByteArray());
		UUID pId = Util.toUuid(msg.getPId().toByteArray());
		int techId=msg.getTechId();
		LeagueManager.getInstance().addBrandLeague(bId,techId,pId);
        this.write(Package.create(cmd, msg));
    }


	//1.集散中心详情数据获取
	public void detailWareHouse(short cmd, Message message) {
		Gs.Id c = (Gs.Id) message;
		UUID id = Util.toUuid(c.getId().toByteArray());
		Building b = City.instance().getBuilding(id);
		if(b == null || b.type() != MetaBuilding.WAREHOUSE) {
			this.write(Package.fail(cmd));
			return;
		}
		registBuildingDetail(b);
		this.write(Package.create(cmd, b.detailProto()));
	}

	//2.获取玩家的建筑信息（包含、仓库、运费和距离）
	public void getPlayerBuildingDetail(short cmd,Message message){
		Gs.Id bid = (Gs.Id) message;//当前建筑的id
		Building srcBuilding = City.instance().getBuilding(Util.toUuid(bid.toByteArray()));
		Gs.BuildingSet.Builder builder = Gs.BuildingSet.newBuilder();
		City.instance().forEachBuilding(player.id(), (Building b)->{
			//计算距离(向上取整)
			b.distance = (int)Math.ceil(Building.distance(srcBuilding, b));
			//计算运费（距离x运费比例）
			b.charge=b.distance*(MetaData.getSysPara().transferChargeRatio);
			b.appendDetailProto(builder);
		});
		//根据玩家id获取租的仓库
		List<WareHouseRenter> renter = GameDb.getWareHouseRenterByPlayerId(player.id());
		renter.forEach(w->{
			//计算距离(向上取整)
			w.getWareHouse().distance = (int)Math.ceil(Building.distance(srcBuilding, w.getWareHouse()));
			//计算运费（距离x运费比例）
			w.getWareHouse().charge=w.getWareHouse().distance*(MetaData.getSysPara().transferChargeRatio);
			w.appendDetailProto(builder);
		});
		this.write(Package.create(cmd, builder.build()));
	}
	//3.设置仓库出租信息
	public void setWareHouseRent(short cmd, Message message){
		Gs.setWareHouseRent info = (Gs.setWareHouseRent) message;
		//建筑id
		UUID bid = Util.toUuid(info.getBuildingId().toByteArray());
		Building b = City.instance().getBuilding(bid);
		if(b == null || b.type() != MetaBuilding.WAREHOUSE)
			return;
		WareHouse wh= (WareHouse) b;
		if (wh.canUseBy(player.id())) {
			if (info.hasRentCapacity()) {
				//判断是不是有这么多容量可以出租
				if(wh.store.availableSize()<info.getRentCapacity()) {
					this.write(Package.fail(cmd));
					return;
				}
				//修改剩余容量，仓库剩余容量要减少,只需要增加仓库的使用大小
				wh.store.setOtherUseSize(info.getRentCapacity());
			}
			if(!info.hasRent())
				return;
			wh.setRent(info.getRent());
			if(!info.hasMinHourToRent()||info.getMinHourToRent()<wh.metaWarehouse.minHourToRent)
				return;
			wh.setMinHourToRent(info.getMinHourToRent());
			if(info.getMaxHourToRent()<wh.metaWarehouse.maxHourToRent&&info.getMaxHourToRent()>=info.getMinHourToRent())
				wh.setMaxHourToRent(info.getMaxHourToRent());
			else
				return;
		}
		GameDb.saveOrUpdate(wh);
		this.write(Package.create(cmd,info));
	}

	//4.删除指定个数的商品
	public void delItems(short cmd, Message message) throws Exception {
		Gs.ItemsInfo c = (Gs.ItemsInfo) message;
		Item item = new Item(c.getItem());
		UUID bid = Util.toUuid(c.getBuildingId().toByteArray());
		IStorage storage = IStorage.get(bid, player);//获取建筑仓库信息
		if(c.hasOrderId()){
			storage = WareHouseUtil.getWareRenter(bid, c.getOrderId());
		}
		if(storage == null)
			return;
		if(storage.delItem(item)){
			GameDb.saveOrUpdate(storage);//修改数据库
			this.write(Package.create(cmd,c));
		}else
			this.write(Package.fail(cmd));
	}
	//5.租用集散中心仓库
	public void rentWareHouse(short cmd, Message message){
		Gs.rentWareHouse rentInfo = (Gs.rentWareHouse) message;
		//订单编号
		long orderNumber = rentInfo.getOrderNumber();
		//建筑id
		UUID bid = Util.toUuid(rentInfo.getBid().toByteArray());
		//租户id
		UUID renterId = Util.toUuid(rentInfo.getRenterId().toByteArray());
		//租用时间
		int hourToRent = rentInfo.getHourToRent();
		//仓库容量
		int rentCapacity = rentInfo.getRentCapacity();
		//租金
		int rent = rentInfo.getRent();
		Long startTime = rentInfo.getStartTime();
		//1.判断建筑是不是集散中心，不是则return
		Building building = City.instance().getBuilding(bid);
		if(!(building instanceof WareHouse)||building==null){
			this.write(Package.fail(cmd));
			return;
		}
		//2.判断仓库容量是否充足
		WareHouse wareHouse= (WareHouse) building;
		if(wareHouse.getRentCapacity()-wareHouse.getRentUsedCapacity()<rentCapacity){
			this.write(Package.fail(cmd));
			return;
		}
		//3.判断玩家是否有足够的钱
		if(player.money()<rent){
			this.write(Package.fail(cmd));
			return;
		}
		//4.租户是否是当前玩家
		if(player.id()!=renterId){
			this.write(Package.fail(cmd));
			return;
		}
		//4.修改集散中心信息
		wareHouse.setRentUsedCapacity(wareHouse.getRentUsedCapacity()+rentCapacity);
		//4.1玩家开销
		player.decMoney(rent);
		MoneyPool.instance().add(rent);
		//4.2记录仓库出租日志
		LogDb.rentWarehouseIncome(orderNumber,bid,renterId,startTime,startTime+hourToRent*3600*1000,hourToRent,rent,rentCapacity);
		//4.3建筑主人获利
		UUID owner = building.ownerId();
		//4.4增加建筑主人的收入
		Player player = GameDb.getPlayer(owner);
		player.addMoney(rent);
		GameDb.saveOrUpdate(player);
		//4.创建租户对象
		WareHouseRenter wareHouseRenter = new WareHouseRenter(orderNumber, renterId, wareHouse, rentCapacity, startTime, hourToRent, rent);
		wareHouse.addRenter(wareHouseRenter);
		wareHouse.updateTodayRentIncome(rent);//修改今日货架收入
		wareHouseRenter.setWareHouse(wareHouse);
		GameDb.saveOrUpdate(wareHouse);
		WareHouseManager.wareHouseMap.put(wareHouse.id(),wareHouse);
		this.write(Package.create(cmd,rentInfo));
	}

	//6.获取所有上架的商品
	public void getAllShelf(short cmd){
		Gs.getAllShelf.Builder shelfInfo = Gs.getAllShelf.newBuilder();
		List<Gs.GoodInfo> shelfItemList = new ArrayList<>();//保存所有的商品信息
		List<Building> building=new ArrayList<>();
		City.instance().forEachBuilding(b->{
			if(b instanceof IShelf){
				building.add(b);
			}
		});
		for (Building bd : building) {
			int i = bd.metaId();//判断建筑的类型
			if(bd.ownerId() == player.id()||!(bd instanceof IShelf)){//不查询玩家的建筑和没有货架的建筑
				continue;
			}
			//判断类型
			switch (MetaBuilding.type(bd.metaId())){
				case MetaBuilding.MATERIAL://原料厂
					MaterialFactory mf = (MaterialFactory) bd;
					Gs.MaterialFactory m = mf.detailProto();
					List<Gs.Shelf.Content> goodList = m.getShelf().getGoodList();
					WareHouseUtil.addGoodInfo(goodList,shelfItemList,m.getInfo().getId(),null);
					break;
				case MetaBuilding.PRODUCE://加工厂
					ProduceDepartment pd = (ProduceDepartment) bd;
					Gs.ProduceDepartment p = pd.detailProto();
					List<Gs.Shelf.Content> goodList1 = p.getShelf().getGoodList();
					WareHouseUtil.addGoodInfo(goodList1,shelfItemList,p.getInfo().getId(),null);
					break;
				case MetaBuilding.RETAIL://零售店
					RetailShop rs = (RetailShop) bd;
					Gs.RetailShop r= (Gs.RetailShop) rs.detailProto();
					List<Gs.Shelf.Content> goodList2 = r.getShelf().getGoodList();
					WareHouseUtil.addGoodInfo(goodList2,shelfItemList,r.getInfo().getId(),null);
					break;
				case MetaBuilding.WAREHOUSE://集散中心
					WareHouse wh = (WareHouse) bd;
					Gs.WareHouse w=  wh.detailProto();
					List<Gs.Shelf.Content> goodList3 = w.getShelf().getGoodList();
					WareHouseUtil.addGoodInfo(goodList3,shelfItemList,w.getInfo().getId(),null);
					break;
			}
		}
		//从租户表中获取已上架的物品
		List<WareHouseRenter> renter = GameDb.getAllRenter();
		for (WareHouseRenter wt : renter) {
			if(wt.getRenterId()==player.id()){//跳过当前玩家的上架物品
				continue;
			}
			Gs.WareHouseRenter wareHouseRenter = wt.toProto();
			Gs.Shelf shelf = wareHouseRenter.getShelf();
			ByteString bid = Util.toByteString(wt.getWareHouse().id());
			WareHouseUtil.addGoodInfo(shelf.getGoodList(), shelfItemList,bid,wt.getOrderId());
		}
		shelfInfo.addAllShelfItem(shelfItemList);//完成商品信息的封装
		this.write(Package.create(cmd,shelfInfo.build()));
	}

	//7.购买上架商品
	public void buyInShelfGood(short cmd, Message message) throws Exception {
		Gs.BuyInShelfGood inShelf = (Gs.BuyInShelfGood) message;
		if(inShelf.getGood().getPrice()<0)
			return;
		//1.参数详情
		//1.1卖家建筑id
		UUID bid = Util.toUuid(inShelf.getGood().getBuildingId().toByteArray());
		//1.2买家仓库建筑id
		UUID wid = Util.toUuid(inShelf.getWareHouseId().toByteArray());
		//2.判断商品所属id建筑是不是租的仓库
		WareHouseRenter sellRenter=null;
		WareHouseRenter buyRenter=null;
		Building sellBuilding = City.instance().getBuilding(bid);
		IStorage buyStore = IStorage.get(wid, player);
		UUID sellOwnerId=sellBuilding.ownerId();
		if(inShelf.getGood().hasOrderid()){//判断商品所属建筑
			//表明是租的仓库
			sellRenter = WareHouseUtil.getWareRenter(bid, inShelf.getGood().getOrderid());
			if(sellRenter==null)
				return;
			sellOwnerId = sellRenter.getRenterId();
		}
		//3.判断要运送的仓库是不是也是租的
		if(inShelf.hasOrderid()){
			//是租的
			buyRenter= WareHouseUtil.getWareRenter(wid, inShelf.getOrderid());
			if(buyRenter==null){
				return;
			}
		}
		IShelf sellShelf = (IShelf) sellBuilding;
		if(sellRenter!=null)
			sellShelf = sellRenter;
		if(buyRenter!=null)
			buyStore = buyRenter;
		//商品信息
		Item itemBuy = new Item(inShelf.getGood().getItem());
		Shelf.Content i = sellShelf.getContent(itemBuy.key);
		//4.如果和上架的价格不对应或者上架数量小于要购买的数量，失败
		if(i == null || i.price != inShelf.getGood().getPrice() || i.n < itemBuy.n) {
			this.write(Package.fail(cmd));
			return;
		}
		//5.计算价格（运费+商品所需价值）
		//5.1.购买商品要花费钱
		long cost = itemBuy.n*inShelf.getGood().getPrice();//计算总价值
		//商品的运费
		int freight = (int) (MetaData.getSysPara().transferChargeRatio * Math.ceil(IStorage.distance(buyStore, (IStorage) sellBuilding)))*itemBuy.n;
		//6.如果玩家钱少于要支付的，交易失败
		if(player.money() < cost + freight)
			return;
		//7.仓库存放不下，失败
		if(!buyStore.reserve(itemBuy.key.meta, itemBuy.n))
			return;
		//========================
		//8.开始修改数据
		//8.1获取到商品主人的信息
		Player seller = GameDb.getPlayer(sellOwnerId);
		seller.addMoney(cost);//交易
		//8.2向出售方发送收入通知提示
		GameServer.sendIncomeNotity(seller.id(),Gs.IncomeNotify.newBuilder()
				.setBuyer(Gs.IncomeNotify.Buyer.PLAYER)
				.setBuyerId(Util.toByteString(player.id()))
				.setFaceId(player.getFaceId())
				.setCost(cost)
				.setType(Gs.IncomeNotify.Type.INSHELF)
				.setBid(sellBuilding.metaBuilding.id)
				.setItemId(itemBuy.key.meta.id)
				.setCount(itemBuy.n)
				.build());
		//8.3玩家扣钱
		player.decMoney(cost);
		//8.4 发送消息通知
		if(cost>=10000000){//重大交易,交易额达到1000,广播信息给客户端,包括玩家ID，交易金额，时间
			GameServer.sendToAll(Package.create(GsCode.OpCode.cityBroadcast_VALUE,Gs.CityBroadcast.newBuilder()
					.setType(1)
					.setSellerId(Util.toByteString(seller.id()))
					.setBuyerId(Util.toByteString(player.id()))
					.setCost(cost)
					.setTs(System.currentTimeMillis())
					.build()));
			LogDb.cityBroadcast(seller.id(),player.id(),cost,0,1);
		}
		//8.5玩家扣除运费
		player.decMoney(freight);
		int itemId = itemBuy.key.meta.id;
		int type = MetaItem.type(itemBuy.key.meta.id);//获取商品类型
		//8.6记录交易日志
		LogDb.payTransfer(player.id(), freight, bid, wid, itemBuy.key.producerId, itemBuy.n);
		if(!inShelf.getGood().hasOrderid()) { //商品不在租的仓库
			LogDb.buyInShelf(player.id(), seller.id(), itemBuy.n, inShelf.getGood().getPrice(),
					itemBuy.key.producerId, sellBuilding.id(), type, itemId);
			LogDb.buildingIncome(bid, player.id(), cost, type, itemId);
		}
		else{//否则是在租户货架上购买的（统计日志）
			/*LogDb.buyRenterInShelf(player.id(), seller.id(), itemBuy.n, inShelf.getGood().getPrice(),
					itemBuy.key.producerId,sellRenter.getOrderId(), type, itemId);*/
			//租户货架收入记录
			//LogDb.renterShelfIncome(inShelf.getGood().getOrderid(),player.id(), cost, type, itemId);
		}
		//8.7.销售方减少上架数量
		sellShelf.delshelf(itemBuy.key, itemBuy.n, false);
		IStorage sellStorage = (IStorage) sellBuilding;
		if(sellRenter!=null){
			sellStorage = sellRenter;
		}
		sellStorage.consumeLock(itemBuy.key, itemBuy.n);
		//更每每日的收入
		if(sellRenter!=null){
			sellRenter.updateTodayIncome(cost);//更新今日收入
		}else{
			sellBuilding.updateTodayIncome(cost);
		}
		buyStore.consumeReserve(itemBuy.key, itemBuy.n, inShelf.getGood().getPrice());
		GameDb.saveOrUpdate(Arrays.asList(player,seller,sellStorage));
		this.write(Package.create(cmd,inShelf));
	}

	//8.上架
	public void putAway(short cmd, Message message) throws Exception {
		Gs.PutAway c = (Gs.PutAway) message;
		Item item = new Item(c.getItem());
		UUID bid = Util.toUuid(c.getBuildingId().toByteArray());
		//1.：情况1：处理在租用仓库中上架的物品
		if(c.hasOrderId()){
			//从租的仓库中上架
			WareHouseRenter renter = WareHouseUtil.getWareRenter(bid, c.getOrderId());
			if(renter==null){
				return;
			}
			IShelf s = renter;
			if(s.addshelf(item, c.getPrice(),c.getAutoRepOn())) {
				GameDb.saveOrUpdate(s);
				this.write(Package.create(cmd, c));
				return;
			}
			else {
				this.write(Package.fail(cmd));
				return;
			}
		}
		//2.情况2，处理其他建筑上架信息
		Building building = City.instance().getBuilding(bid);
		//2.1 如果建筑无效
		if(building==null ||!(building instanceof IShelf) || !building.canUseBy(player.id())|| building.outOfBusiness())
			return;
		//2.2 如果是原料厂，但是货物是商品，不允许上架
		if(building instanceof  MaterialFactory &&item.key.meta instanceof MetaGood)
			return;
		//2.3 如果是加工厂或者零售店，但是，上架的是原料
		if(building instanceof ProduceDepartment &&item.key.meta instanceof MetaMaterial){
			return;
		}
		//2.4 如果是零售店，只能上架商品
		if(building instanceof RetailShop &&item.key.meta instanceof MetaMaterial){
			return;
		}
		IShelf s = (IShelf)building;
		if(s.addshelf(item,c.getPrice(),c.getAutoRepOn())){
			GameDb.saveOrUpdate(s);
			this.write(Package.create(cmd,c));
		}else{
			this.write(Package.fail(cmd));
		}
	}

	//9.修改租用仓库上架商品
	public void rentWarehouseShelfSet(short cmd, Message message) throws Exception {
		Gs.RentWarehouseShelfSet r = (Gs.RentWarehouseShelfSet) message;
		if(r.getPrice()<0||!r.hasOrderId())
			return;
		Item item = new Item(r.getItem());
		UUID bid = Util.toUuid(r.getBuildingId().toByteArray());
		WareHouseRenter wareRenter = WareHouseUtil.getWareRenter(bid, r.getOrderId());
		if(wareRenter==null)
			return;
		IShelf s = wareRenter;
		if (s.setPrice(item.key, r.getPrice())) {
			GameDb.saveOrUpdate(s);
			this.write(Package.create(cmd,r));
		}else
			this.write(Package.fail(cmd));
	}

	//10.下架（包含集散中心和租用仓库）
	public void soldOutShelf(short cmd, Message message) throws Exception {
		Gs.SoldOutShelf s = (Gs.SoldOutShelf) message;
		Item item = new Item(s.getItem());
		Building building = City.instance().getBuilding(Util.toUuid(s.getBuildingId().toByteArray()));
		//情况1，是租用的仓库下架
		if(s.hasOrderId()){
			//从租的仓库中下架
			WareHouseRenter renter = WareHouseUtil.getWareRenter(Util.toUuid(s.getBuildingId().toByteArray()),s.getOrderId());
			if(renter==null)
				return;

			IShelf shelf = renter;
			if(shelf.delshelf(item.key,item.n,true)){
				GameDb.saveOrUpdate(shelf);
				this.write(Package.create(cmd,s));
			}else
				this.write(Package.fail(cmd));
		}else {
			//普通建筑下架
			if (building == null || !(building instanceof IShelf) || !building.canUseBy(player.id()) || building.outOfBusiness())
				return;
			//2.2 如果是原料厂，货物是商品，
			if (building instanceof MaterialFactory && item.key.meta instanceof MetaGood)
				return;
			//2.3 如果是加工厂或者零售店，原料
			if (building instanceof ProduceDepartment && item.key.meta instanceof MetaMaterial) {
				return;
			}
			//2.4 如果是零售店，商品
			if (building instanceof RetailShop && item.key.meta instanceof MetaMaterial) {
				return;
			}
			IShelf sf = (IShelf) building;
			if (sf.delshelf(item.key, item.n, true)) {
				GameDb.saveOrUpdate(sf);
				this.write(Package.create(cmd, s));
			} else
				this.write(Package.fail(cmd));
		}
	}

	//11.设置租户仓库自动补货
	public void setRentAutoReplenish(short cmd, Message message) throws Exception {
		Gs.SetRentAutoReplenish c = (Gs.SetRentAutoReplenish) message;
		ItemKey itemKey = new ItemKey(c.getIKey());
		UUID id = Util.toUuid(c.getBuildingId().toByteArray());
		if(!c.hasOrderId())
			return;
		WareHouseRenter renter = WareHouseUtil.getWareRenter(id, c.getOrderId());
		if(renter==null)
			return;
		IShelf shelf = renter;
		Shelf.Content content = shelf.getContent(itemKey);
		if(shelf.setAutoReplenish(itemKey,c.getAutoRepOn())) {
			//处理自动补货
			if(content != null && content.autoReplenish){
				IShelf.updateAutoReplenish(shelf,itemKey);
			}
			GameDb.saveOrUpdate(shelf);
			this.write(Package.create(cmd, c));
		}
		else
			this.write(Package.fail(cmd));
	}

	//12.获取集散中心今日收入信息
	public void getWareHouseIncomeInfo(short cmd,Message message){
		Gs.Id id= (Gs.Id) message;
		UUID bid = Util.toUuid(id.toByteArray());
		Building building = City.instance().getBuilding(bid);
		if(!(building instanceof WareHouse)&&!player.id().equals(building.ownerId()))
			return;
		WareHouse wh = (WareHouse) building;
		Gs.GetWareHouseIncomeInfo info = wh.getPrivateWareHouseInfo();
		this.write(Package.create(cmd, info));
	}

	//13.detailWareHouse 租户详情，根据集散中心获取租户详情
	public void detailWareHouseRenter(short cmd,Message message){
		Gs.Id c = (Gs.Id) message;
		UUID bid = Util.toUuid(c.getId().toByteArray());
		Building building = City.instance().getBuilding(bid);
		if(building==null||!(building instanceof WareHouse)){
			this.write(Package.fail(cmd));
			return;
		}
		WareHouse wareHouse = (WareHouse) building;
		if(wareHouse.getRenters().size()<=0||wareHouse.getRenters()==null)
			return;
		Gs.detailWareHouseRenter.Builder builder = Gs.detailWareHouseRenter.newBuilder();
		wareHouse.getRenters().forEach(p->builder.addRenters(p.toProto()));
		this.write(Package.create(cmd,builder.build()));
	}

	//14.获取集散中心的数据摘要
	public void  queryWareHouseSummary(short cmd){
		Gs.WareHouseSummary.Builder builder = Gs.WareHouseSummary.newBuilder();
		//便利所有的坐标体系
		City.instance().forAllGrid(g -> {
			Gs.WareHouseSummary.Info.Builder info=builder.addInfoBuilder();
			GridIndex gi = new GridIndex(g.getX(), g.getY());
			info.setIdx(gi.toProto());
			AtomicInteger n = new AtomicInteger();
			//便利网格
			g.forAllBuilding(building->{
				if(building instanceof WareHouse&&!building.outOfBusiness())
					n.incrementAndGet();
			});
			info.setCount(n.intValue());
		});
		this.write(Package.create(cmd,builder.build()));
	}

	//15.获取集散中心数据详情摘要,客户端传递一个中心坐标
	public void queryWareHouseDetail(short cmd,Message message){
		Gs.QueryWareHouseDetail c = (Gs.QueryWareHouseDetail) message;
		GridIndex center = new GridIndex(c.getCenterIdx().getX(),c.getCenterIdx().getY());
		Gs.WareHouseDetail.Builder builder = Gs.WareHouseDetail.newBuilder();
		//遍历中心坐标周围城市的网格
		City.instance().forEachGrid(center.toSyncRange(), (grid)->{
			Gs.WareHouseDetail.GridInfo.Builder info = builder.addInfoBuilder();
			info.getIdxBuilder().setX(grid.getX()).setY(grid.getY());//参数1
			grid.forAllBuilding(building -> {
				if(building instanceof  WareHouse){
					WareHouse wareHouse = (WareHouse) building;
					if(wareHouse.getRent()!=0&&wareHouse.getRentCapacity()!=0){
						Gs.WareHouseDetail.GridInfo.Building.Builder bb = info.addBBuilder();
						bb.setId(Util.toByteString(wareHouse.id()))
								.setOwnerId(Util.toByteString(wareHouse.ownerId()))
								.setName(wareHouse.getName())
								.setPos(building.coordinate().toProto())
								.setMinHourToRent(wareHouse.getMinHourToRent())
								.setMaxHourToRent(wareHouse.getMaxHourToRent())
								.setRent(wareHouse.getRent())
								.setAvailableSize(wareHouse.getRentCapacity()-wareHouse.getRentUsedCapacity())
								.setMetaId(wareHouse.metaId());
					}
				}
			});
		});
		this.write(Package.create(cmd,builder.build()));
	}



	//未在公会中根据id查询公会信息
	public void getOneSocietyInfo(short cmd, Message message)
	{
		UUID societyId = Util.toUuid(((Gs.Id) message).getId().toByteArray());
		Society society = SocietyManager.getSociety(societyId);
		if (society != null)
		{
			this.write(Package.create(cmd, SocietyManager.toSocietyDetailProto(society)));

		}
	}
	//查询注册过的玩家数量
	public void getPlayerAmount(short cmd) {
		long playerAmount = GameDb.getPlayerAmount();
		this.write(Package.create(cmd, Gs.PlayerAmount.newBuilder().setPlayerAmount(playerAmount).build()));
	}

}
