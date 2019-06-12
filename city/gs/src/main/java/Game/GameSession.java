package Game;

import Game.Contract.BuildingContract;
import Game.Contract.Contract;
import Game.Contract.ContractManager;
import Game.Contract.IBuildingContract;
import Game.Eva.Eva;
import Game.Eva.EvaManager;
import Game.Exceptions.GroundAlreadySoldException;
import Game.FriendManager.*;
import Game.Gambling.FlightManager;
import Game.League.LeagueInfo;
import Game.League.LeagueManager;
import Game.Meta.*;
import Game.Util.*;
import Game.security.Bouncycastle_Secp256k1;
import Shared.*;
import Shared.Package;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import common.Common;
import gs.Gs;
import gs.Gs.BuildingInfo;
import gscode.GsCode;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.util.concurrent.ScheduledFuture;
import org.apache.log4j.Logger;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class GameSession {

	public static byte[] signData(String algorithm, byte[] data, PrivateKey key) throws Exception {
		Signature signer = Signature.getInstance(algorithm);
		signer.initSign(key);
		signer.update(data);
		return (signer.sign());
	}

	public static boolean verifySign(String algorithm, byte[] data, PublicKey key, byte[] sig) throws Exception {
		Signature signer = Signature.getInstance(algorithm);
		signer.initVerify(key);
		signer.update(data);
		return (signer.verify(sig));
	}

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
					GlobalConfig.cityError(Throwables.getStackTraceAsString(e));
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
				LogDb.playerIncome(player.id(), n);
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
						item = new Item(new ItemKey(mi,player.id()), n);
					else
						item = new Item(new ItemKey(mi, player.id()), n);
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
		//添加矿工费用（系统参数）
		Gs.Role.Builder role = player.toProto().toBuilder();
		role.setMinersCostRatio(MetaData.getSysPara().minersCostRatio);
		this.write(Package.create(cmd,role.build()));
		City.instance().add(player); // will send UnitCreate
		player.online();
		if (player.getSocietyId() != null)
		{
			SocietyManager.broadOnline(player);
		}
		sendSocialInfo();
	}
	private static final int MAX_PLAYER_NAME_LEN = 20;
	public void createRole(short cmd, Message message) {
		Gs.CreateRole c = (Gs.CreateRole)message;
		if(c.getFaceId().length() > Player.MAX_FACE_ID_LEN || c.getName().isEmpty() || c.getName().length() > MAX_PLAYER_NAME_LEN)
			return;
		//如果公司名存在，return
		if(GameDb.companyNameIsInUsed(c.getCompanyName())){
			this.write(Package.fail(cmd, Common.Fail.Reason.notAllow));
			return;
		}
		Player p = new Player(c.getName(), this.accountName, c.getMale(), c.getCompanyName(), c.getFaceId());
		p.addMoney(999999999);
		LogDb.playerIncome(p.id(), 999999999);
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
		b.createNpc();//产生npc
		if(b.startBusiness(player)){
			this.write(Package.create(cmd,c));
			GameDb.saveOrUpdate(Arrays.asList(b,player));
		}
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
				if(building instanceof IShelf && !building.outOfBusiness()&&building instanceof FactoryBase) {
					//如果是集散中心并且有租户，就还要从租户中获取上架信息
					/*if(building instanceof WareHouse &&((WareHouse) building).getRenters().size()>0){
						WareHouse wareHouse = (WareHouse) building;
						wareHouse.getRenters().forEach(r->{
							IShelf rs = (IShelf)building;
							if(rs.getSaleCount(mi.id) > 0)
								n.addAndGet(1);
						});
					}*/
					IShelf s = (IShelf) building;
					if (s.getSaleCount(mi.id) > 0)
						n.addAndGet(1);
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

	public void queryPromoSummary(short cmd) {
		Gs.PromoSummary.Builder builder = Gs.PromoSummary.newBuilder();
		City.instance().forAllGrid(g->{
			Gs.PromoSummary.Info.Builder b = builder.addInfoBuilder();
			GridIndex gi = new GridIndex(g.getX(),g.getY());
			b.setIdx(gi.toProto());
			AtomicInteger n = new AtomicInteger();
			g.forAllBuilding(building -> {
				if(building instanceof PublicFacility && !building.outOfBusiness() &&  ((PublicFacility)building).isTakeOnNewOrder())
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
				if(building instanceof IShelf && !building.outOfBusiness()&&building instanceof FactoryBase) {
					/*if(building instanceof  WareHouse&&((WareHouse) building).getRenters().size()>0){
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
					}*/
					IShelf s = (IShelf) building;
					if(s.getSaleCount(c.getItemId())>0){
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
				if(!building.outOfBusiness() && building instanceof Laboratory) {
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
					bb.setMetaId(building.metaId());
				}
			});
		});
		this.write(Package.create(cmd, builder.build()));
	}

	public void queryPromoDetail(short cmd, Message message) {
		Gs.QueryPromoDetail c = (Gs.QueryPromoDetail)message;
		GridIndex center = new GridIndex(c.getCenterIdx().getX(), c.getCenterIdx().getY());
		Gs.PromoDetail.Builder builder = Gs.PromoDetail.newBuilder();
		City.instance().forEachGrid(center.toSyncRange(), (grid)->{
			Gs.PromoDetail.GridInfo.Builder gb = builder.addInfoBuilder();
			gb.getIdxBuilder().setX(grid.getX()).setY(grid.getY());
			gb.addAllTypeIds(c.getTypeIdsList());
			grid.forAllBuilding(building->{
				if(!building.outOfBusiness() && building instanceof PublicFacility
						&& ((PublicFacility)building).isTakeOnNewOrder()) {
					PublicFacility s = (PublicFacility)building;
					if(!s.isTakeOnNewOrder())
						return;
					Gs.PromoDetail.GridInfo.Building.Builder bb = gb.addBBuilder();
					bb.setId(Util.toByteString(building.id()));
					bb.setPos(building.coordinate().toProto());

					for (int tp : c.getTypeIdsList())
					{
						int bsTp = tp/100;
						int subTp = tp % 100;
						Integer value = (int)s.getAllPromoTypeAbility(tp);
						bb.addCurAbilitys(value);
					}
					bb.setPricePerHour(s.getCurPromPricePerHour());
					bb.setRemainTime(s.getPromRemainTime());
					bb.setQueuedTimes(s.getNewPromoStartTs());
					bb.setOwnerId(Util.toByteString(building.ownerId()));
					bb.setName(building.getName());
					bb.setMetaId(building.metaId());
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
		if(!ok){
			this.write(Package.fail(cmd));
		}
		else{
			building.postAddToWorld();
			this.write(Package.create(cmd, building.toProto()));
		}
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
		else{
			//this.write(Package.fail(cmd));
			this.write(Package.create(cmd, c.toBuilder().setCurCount(s.getContent(item.key).getCount()).build()));
		}
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
		if(s.shelfSet(item, c.getPrice(),c.getAutoRepOn())){
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

		 //TODO:矿工费用（商品基本费用*矿工费用比例）(向下取整),
		double minersRatio = MetaData.getSysPara().minersCostRatio/10000;
		long minerCost = (long) Math.floor(cost * minersRatio);
		long income =cost - minerCost;//收入（扣除矿工费后）
		long pay=cost+minerCost;
		if(player.money() < cost + freight+minerCost)
			return;
		// begin do modify
		if(!buyStore.reserve(itemBuy.key.meta, itemBuy.n))
			return;
		Player seller = GameDb.getPlayer(sellBuilding.ownerId());
		seller.addMoney(income);
		Gs.IncomeNotify notify = Gs.IncomeNotify.newBuilder()
				.setBuyer(Gs.IncomeNotify.Buyer.PLAYER)
				.setBuyerId(Util.toByteString(player.id()))
				.setFaceId(player.getFaceId())
				.setCost(cost)
				.setType(Gs.IncomeNotify.Type.INSHELF)
				.setBid(sellBuilding.metaBuilding.id)
				.setItemId(itemBuy.key.meta.id)
				.setCount(itemBuy.n)
				.build();
		GameServer.sendIncomeNotity(seller.id(),notify);
		player.decMoney(pay);
		LogDb.playerPay(player.id(),pay);
		LogDb.playerIncome(seller.id(),income);
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
		LogDb.playerPay(player.id(), freight);

		int itemId = itemBuy.key.meta.id;
		int type = MetaItem.type(itemBuy.key.meta.id);
		LogDb.payTransfer(player.id(), freight, bid, wid, itemBuy.key.producerId, itemBuy.n);
		LogDb.payTransfer(player.id(), freight, bid, wid, itemBuy.key.producerId, itemBuy.n);
		LogDb.buyInShelf(player.id(), seller.id(), itemBuy.n, c.getPrice(),
				itemBuy.key.producerId, sellBuilding.id(), type, itemId);
		LogDb.buildingIncome(bid,player.id(),cost,type,itemId);//商品支出记录不包含运费
		//矿工费用日志记录(需调整)
		LogDb.minersCost(player.id(),minerCost,minersRatio);
		LogDb.minersCost(seller.id(),minerCost,minersRatio);
		sellShelf.delshelf(itemBuy.key, itemBuy.n, false);
		((IStorage)sellBuilding).consumeLock(itemBuy.key, itemBuy.n);
		sellBuilding.updateTodayIncome(income);

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
//		if (s == null || !s.lock(new ItemKey(mi), c.getNum()))
//			return;
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
			GlobalConfig.cityError("building not exist" + bid);
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
		updateBuildingVisitor(b);
		this.write(Package.create(cmd, b.detailProto()));
	}

	private void updateBuildingVisitor(Building building)
	{
		if (!building.ownerId().equals(player.id())) {
			building.increaseTodayVisit();
		}
	}
	public void detailMaterialFactory(short cmd, Message message) {
		Gs.Id c = (Gs.Id) message;
		UUID id = Util.toUuid(c.getId().toByteArray());
		Building b = City.instance().getBuilding(id);
		if(b == null || b.type() != MetaBuilding.MATERIAL)
			return;
		registBuildingDetail(b);
		updateBuildingVisitor(b);
		this.write(Package.create(cmd, b.detailProto()));
	}

	public void detailProduceDepartment(short cmd, Message message) {
		Gs.Id c = (Gs.Id) message;
		UUID id = Util.toUuid(c.getId().toByteArray());
		Building b = City.instance().getBuilding(id);
		if(b == null || b.type() != MetaBuilding.PRODUCE)
			return;
		registBuildingDetail(b);
		updateBuildingVisitor(b);
		this.write(Package.create(cmd, b.detailProto()));
	}
	public void detailPublicFacility(short cmd, Message message) {
		Gs.Id c = (Gs.Id) message;
		UUID id = Util.toUuid(c.getId().toByteArray());
		Building b = City.instance().getBuilding(id);
		if(b == null || b.type() != MetaBuilding.PUBLIC)
			return;
		registBuildingDetail(b);
		updateBuildingVisitor(b);
		this.write(Package.create(cmd, b.detailProto()));
	}
	public void detailLaboratory(short cmd, Message message) {
		Gs.Id c = (Gs.Id) message;
		UUID id = Util.toUuid(c.getId().toByteArray());
		Building b = City.instance().getBuilding(id);
		if(b == null || b.type() != MetaBuilding.LAB)
			return;
		registBuildingDetail(b);
		updateBuildingVisitor(b);
		this.write(Package.create(cmd, b.detailProto()));
	}
	public void detailRetailShop(short cmd, Message message) {
		Gs.Id c = (Gs.Id) message;
		UUID id = Util.toUuid(c.getId().toByteArray());
		Building b = City.instance().getBuilding(id);
		if(b == null || b.type() != MetaBuilding.RETAIL)
			return;
		registBuildingDetail(b);
		updateBuildingVisitor(b);
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

	//adQueryPromoCurAbilitys
	public void adQueryPromoCurAbilitys(short cmd, Message message) {
		Gs.AdQueryPromoCurAbilitys gs_queryPromoCurAbility = (Gs.AdQueryPromoCurAbilitys) message;
		UUID sellerBuildingId = Util.toUuid(gs_queryPromoCurAbility.getSellerBuildingId().toByteArray());
		//检查是否是推广公司
		Building sellerBuilding = City.instance().getBuilding(sellerBuildingId);
		PublicFacility fcySeller = (PublicFacility) sellerBuilding ;

		if(sellerBuilding == null || sellerBuilding.outOfBusiness() || sellerBuilding.type() != MetaBuilding.PUBLIC){
			if(GlobalConfig.DEBUGLOG){
				GlobalConfig.cityError("GameSession.adQueryPromoCurAbilitys: building type of seller is not PublicFacility!");
			}
			return;
		}

		List<Integer> types = new ArrayList<>(gs_queryPromoCurAbility.getTypeIdsCount());
		Gs.AdQueryPromoCurAbilitys.Builder builder = gs_queryPromoCurAbility.toBuilder();
		for (int tp : gs_queryPromoCurAbility.getTypeIdsList())
		{
			int bsTp = tp/100;
			int subTp = tp % 100;
			Integer value = (int)fcySeller.getAllPromoTypeAbility(tp);
			builder.addCurAbilitys(value);
		}
		this.write(Package.create(cmd, builder.build()));
	}

	//adGetAllMyFlowSign
	public void GetAllMyFlowSign(short cmd, Message message) {
		Gs.GetAllMyFlowSign reqMsg = (Gs.GetAllMyFlowSign) message;
		UUID buildingId = Util.toUuid(reqMsg.getBuildingId().toByteArray());
		List<UUID> promoIDs = new ArrayList<>();
		Building bd = City.instance().getBuilding(buildingId);
		if(bd == null || !(bd instanceof PublicFacility)){
			if(GlobalConfig.DEBUGLOG){
				GlobalConfig.cityError("GetAllMyFlowSign: Invalid buildingId ="+buildingId.toString());
			}
			return;
		}
		PublicFacility fcySeller = (PublicFacility) bd ;
		Player player =  GameDb.getPlayer(fcySeller.ownerId());
		List<Contract> clist = ContractManager.getInstance().getAllMySign(player.id());
		Gs.GetAllMyFlowSign.Builder newPromotions = reqMsg.toBuilder();
		for (Contract contract : clist) {
			Building sbd = City.instance().getBuilding(contract.getSellerBuildingId());
			Gs.GetAllMyFlowSign.flowInfo.Builder flowInfobd =  Gs.GetAllMyFlowSign.flowInfo.newBuilder();
			flowInfobd.setSellerBuildingId(Util.toByteString(contract.getSellerBuildingId()))
					.setBuildingName(sbd.getName())
					.setStartTs(contract.getStartTs())
					.setSigningHours(contract.getSigningHours())
					.setLift(contract.getLift())
					.setSellerPlayerId(Util.toByteString(sbd.ownerId()))
					.setPricePerHour((int)(contract.getPrice()))
					.setTypeId(sbd.type());
			newPromotions.addInfo(flowInfobd);
		}
		//返回给客户端
		this.write(Package.create(cmd, newPromotions.build()));
	}
	//adQueryPromotion
	public void AdQueryPromotion(short cmd, Message message) {
		/*
				查询广告列表，分两种情况
				1、 广告商（卖家）查询
				2、 广告主（买家）查询
				方式： 都是通过 promotionId 查询
			*/
		Gs.AdQueryPromotion AdQueryPromotion = (Gs.AdQueryPromotion) message;
		boolean isSeller = AdQueryPromotion.getIsSeller();
		UUID buyerId = Util.toUuid(AdQueryPromotion.getPlayerId().toByteArray());
		Player player =  GameDb.getPlayer(buyerId);
		//获取 payedPromotion
		if(player == null){
			if(GlobalConfig.DEBUGLOG){
				GlobalConfig.cityError("PromotionMgr.AdQueryPromotion: isSeller is false but player not exist!");
			}
			return;
		}

		List<UUID> promoIDs = new ArrayList<>();
		if( isSeller || AdQueryPromotion.hasSellerBuildingId()){
			//获取 selledPromotion
			if(!AdQueryPromotion.hasSellerBuildingId()){
				if(GlobalConfig.DEBUGLOG){
					GlobalConfig.cityError("PromotionMgr.AdQueryPromotion: isSeller is true but SellerBuildingId is null!");
				}
				return;
			}
			Building bd = City.instance().getBuilding(Util.toUuid(AdQueryPromotion.getSellerBuildingId().toByteArray()));

			if(bd == null){
				if(GlobalConfig.DEBUGLOG){
					GlobalConfig.cityError("PromotionMgr.AdQueryPromotion: isSeller is true but building not exist!");
				}
				return;
			}

			PublicFacility fcySeller = (PublicFacility) bd ;
			promoIDs = fcySeller.getSelledPromotions();
		}else{
			promoIDs = player.getPayedPromotions();
		}
		List<PromoOrder> promotions = new ArrayList<>() ;
		PromotionMgr.instance().getPromotins(promoIDs,promotions);
		Gs.AdQueryPromotion.Builder newPromotions = AdQueryPromotion.toBuilder();

		for (int i = 0; i < promotions.size(); i++) {
			newPromotions.addPromotions(promotions.get(i).toProto());
		}
		//返回给客户端
		this.write(Package.create(cmd, newPromotions.build()));
	}

	//adRemovePromoOrder
	public void AdRemovePromoOrder(short cmd, Message message) {
		Gs.AdRemovePromoOrder gs_AdRemovePromoOrder = (Gs.AdRemovePromoOrder) message;
		UUID promoId = Util.toUuid(gs_AdRemovePromoOrder.getPromotionId().toByteArray());
		PromoOrder promoOrder = PromotionMgr.instance().getPromotion(promoId);
		if(promoOrder == null){
			if(GlobalConfig.DEBUGLOG){
				GlobalConfig.cityError("GameSession.AdRemovePromoOrder(): PromoOrder which Id equals "+promoId+" not find!");
			}
			return;
		}
		//更新广告商玩家信息中广告列表
		Building sellerBuilding = City.instance().getBuilding(promoOrder.sellerBuildingId);
		PublicFacility fcySeller = (PublicFacility) sellerBuilding ;
		List<PromoOdTs> tslist = fcySeller.delSelledPromotion(promoId,true);
		Gs.AdRemovePromoOrder.Builder newMsg = gs_AdRemovePromoOrder.toBuilder();
		tslist.forEach(ts->newMsg.addPromoTsChanged(ts.toProto()));
		GameDb.saveOrUpdate(fcySeller);
		Player seller = GameDb.getPlayer(sellerBuilding.ownerId());
		seller.delpayedPromotion(promoId);

		//发送客户端通知
		this.write(Package.create(cmd, newMsg.build()));
	}

	//adjustPromoSellingSetting
	public void AdjustPromoSellingSetting(short cmd, Message message) {
		Gs.AdjustPromoSellingSetting adjustPromo = (Gs.AdjustPromoSellingSetting) message;
		UUID sellerBuildingId = Util.toUuid(adjustPromo.getSellerBuildingId().toByteArray());
		//检查是否是推广公司
		Building b = City.instance().getBuilding(sellerBuildingId);
		Building sellerBuilding = City.instance().getBuilding(sellerBuildingId);
		PublicFacility fcySeller = (PublicFacility) sellerBuilding ;

		if(b == null || fcySeller == null){
			if(GlobalConfig.DEBUGLOG){
				GlobalConfig.cityError("GameSession.AdjustPromoSellingSetting(): can't find the building instance which id equals to "+sellerBuildingId);
			}
			return;
		}
		fcySeller.setCurPromPricePerHour((int) adjustPromo.getPricePerHour());
		fcySeller.setPromRemainTime(adjustPromo.getRemainTime());
		fcySeller.setTakeOnNewOrder(adjustPromo.getTakeOnNewOrder());
		GameDb.saveOrUpdate(fcySeller);

		//发送客户端通知
		this.write(Package.create(cmd, adjustPromo));
	}

	//adAddNewPromoOrder
	public void AdAddNewPromoOrder(short cmd, Message message) {
		Gs.AdAddNewPromoOrder gs_AdAddNewPromoOrder = (Gs.AdAddNewPromoOrder) message;
		//UUID id = Util.toUuid(gs_AdAddNewPromoOrder.getSellerBuildingId().toByteArray());
		UUID sellerBuildingId = Util.toUuid(gs_AdAddNewPromoOrder.getSellerBuildingId().toByteArray());
		UUID buyerPlayerId = Util.toUuid(gs_AdAddNewPromoOrder.getBuyerPlayerId().toByteArray());
		//检查是否是推广公司
		Building b = City.instance().getBuilding(sellerBuildingId);
		if(b == null || b.outOfBusiness()){
			if(GlobalConfig.DEBUGLOG){
				GlobalConfig.cityError("GameSession.AdAddNewPromoOrder(): can't find the building instance which id equals to "+sellerBuildingId);
			}
			return;
		}
		//检查推广的目标类型
		//1、建筑类型，包括零售店（RETAIL）、 住宅（APARTMENT）
		//2、商品类型：服装、食品
		//客户端及填写了商品类型又填写了建筑类型时，优先级怎么处理？
		boolean hasBuildingType = gs_AdAddNewPromoOrder.hasBuildingType();
		boolean hasProducerId = gs_AdAddNewPromoOrder.hasProductionType();

		if(hasProducerId && hasBuildingType){
			if(GlobalConfig.DEBUGLOG){
				GlobalConfig.cityError("GameSession.AdAddNewPromoOrder(): buildingType and productionType can't be avaliable at the same time.");
			}
			return;
		}

		Building sellerBuilding = City.instance().getBuilding(sellerBuildingId);
		PublicFacility fcySeller = (PublicFacility) sellerBuilding ;
		Player seller = GameDb.getPlayer(sellerBuilding.ownerId());
		Player buyer =  GameDb.getPlayer(buyerPlayerId);
		boolean selfPromo = buyerPlayerId.equals(sellerBuilding.ownerId()) ;
		//boolean selfPromo = buyerPlayerId == sellerBuilding.ownerId();

		PromoOrder newOrder = new PromoOrder();
		//订单记录该价格（ 需要记录吗？）
		newOrder.setTransactionPrice(fcySeller.getCurPromPricePerHour());

		//购买的时长是否合法
		//广告商给自添加推广不用考虑可用时间
		if(!selfPromo && gs_AdAddNewPromoOrder.getPromDuration() > fcySeller.getPromRemainTime()){
			if(GlobalConfig.DEBUGLOG){
				GlobalConfig.cityError("GameSession.AdAddNewPromoOrder(): PromDuration required by client greater than sellerBuilding's remained.");
			}
			return;
		}

		if(sellerBuilding == null){
			if(GlobalConfig.DEBUGLOG){
				GlobalConfig.cityError("GameSession.AdAddNewPromoOrder(): building instance of sellerBuilding not find which Id equals to"+newOrder.sellerBuildingId);
			}
			return;
		}
		if(buyer == null){
			if(GlobalConfig.DEBUGLOG){
				GlobalConfig.cityError("GameSession.AdAddNewPromoOrder(): Buyer not find which Id equals to"+newOrder.sellerBuildingId);
			}
			return;
		}

		//判断买家资金是否足够，如果够，扣取对应资金，否则返回资金不足的错误
		int fee = selfPromo? 0 : (fcySeller.getCurPromPricePerS()) * (int)gs_AdAddNewPromoOrder.getPromDuration()/1000;
		//TODO:矿工费用(向下取整)
		double minersRatio = MetaData.getSysPara().minersCostRatio/10000;
		long minerCost = (long) Math.floor(fee * minersRatio);
		if(buyer.money() < fee+minerCost){
			if(GlobalConfig.DEBUGLOG){
				GlobalConfig.cityError("GameSession.AdAddNewPromoOrder(): PromDuration required by client greater than sellerBuilding's remained.");
			}
			this.write(Package.fail(cmd, Common.Fail.Reason.moneyNotEnough));
			return;
		}

		//添加订单
			/*
				订单ID： promotionId
				* 广告推广能力和品牌值增加的计算
					* 当前广告执行期间，每小时计算一次，广告主品牌值根据当前广告公司推广能力进行累计，
						比如：广告主品牌值为 10000， 当前广告公司的推广能力为1000，那么计算之后为 11000；
						下一次整点为 12000 ，如此累计
					* 广告推广力计算为整点全局更新，即便是 1点59打的广告，2点整也会执行一次广告计算。
					* 每次更新增加的品牌值增量为：
						* 当前广告公司推广能力 * 更新时长/1小时
						* 更新时长 ：5
							* 如果该广告第一次执行整点更新，那么“更新时长”为该该广告开始时间起计算到这个整点的时长
							* 非第一次整点更新，更新时长 为1个小时
				* promotionId 可以使用索引作为ID吗（需要全局唯一吗）？
					* 哪些地方会依赖到这个数据？
						* 广告主会记录这个ID，Npc消费决策时会访问该广告主的品牌值
							* 因为品牌值整点已经更新，所以消费决策只需要直接使用该广告主的品牌值，不需要单独计算。
							* 需要考察现在npc的消费决策，看看原来如何计算品牌值的
						* 这个更新放在哪儿比较好？
							* 放在推广公司建筑中
							* 放在全局的广告管理器中
					* 一旦 promoOrderQueue 因为广告商撤销为自己打的广告订单，那么整个
				* promotionId 放在哪儿好？
					* 放在广告公司实例中？
						* 消费决策要会先找出广告公司
							* 整点更新品牌值，消费决策直接读取
					* 所以 promotionId 要放在建筑本身是可以的
						* 但是如果要考虑到后边统计方便，还是拧出来，放到一个管理器中，那么就必须使用 UUID
							*
			*/
			/*
				UUID 生成：
					this.id = UUID.randomUUID();
				UUID 转换传输时需转换成 byte[]
					startBusiness Util.toUuid(gs_AdAddNewPromoOrder.getId().toByteArray());
					也就是说，目前传输是使用的 byte 数组来存放的，服务使用的UUID是从byte数组转换过来的，
					转换使用的方法是 UUID toUuid(byte[] bytes)
			*/

		//获取该广告公司最后一个广告
		UUID lastPromotion = fcySeller.getLastPromotion();

		PromoOrder lastOrder = null;
		if(lastPromotion == null){
			lastOrder = new PromoOrder();
			lastOrder.promStartTs = System.currentTimeMillis();
			lastOrder.promDuration = 0;
		}else{
			lastOrder = PromotionMgr.instance().getPromotion(lastPromotion);
		}

		if(!fcySeller.comsumeAvaliableTime(newOrder.promDuration)){
			if(GlobalConfig.DEBUGLOG){
				GlobalConfig.cityError("GameSession.AdAddNewPromoOrder(): fcySeller.comsumeAvaliableTime return false");
			}
			return;
		}

		//临时处理不匹配的情况,正常情况下不会出现这种情况
		if(lastOrder == null && !lastPromotion.equals(null)){
			fcySeller.delSelledPromotion(lastPromotion,true);
			if(GlobalConfig.DEBUGLOG) {
				GlobalConfig.cityError("GameSession.AdAddNewPromoOrder(): lastOrder == null && lastPromotion != null");
			}
			//return;
		}

		newOrder.promotionId = UUID.randomUUID();
		newOrder.buyerId = buyerPlayerId;
		newOrder.sellerBuildingId = sellerBuildingId;
		newOrder.sellerId = seller.id();

		//计算 promStartTs， 先取出广告公司中的所有广告promotionId 列表，计算新广告的起点
		newOrder.promStartTs = lastOrder.promStartTs + lastOrder.promDuration;
		newOrder.promProgress = 0;
		//客户端发过来的时间单位是毫秒
		newOrder.promDuration = gs_AdAddNewPromoOrder.getPromDuration();
		fcySeller.setNewPromoStartTs(newOrder.promStartTs+gs_AdAddNewPromoOrder.getPromDuration());
		fcySeller.setPromRemainTime(fcySeller.getPromRemainTime() - (selfPromo ? 0: gs_AdAddNewPromoOrder.getPromDuration()));

		if(hasBuildingType){
				/*
				由 PromotionMgr 统一维护所有广告的增删改查比较好。集中更新、方便统计。让建筑中维护的话，
				还得给每个建筑单独增加一个update。
				维护策略
				增： PromotionMgr 维护，建筑只更新对应的 promotionId 列表
				删： PromotionMgr 维护，建筑只更新对应的 promotionId 列表
					* 目前只有广告商可以删除自己的PromoOrder，这种情况是频率非常低的。
				改： PromotionMgr 维护，结果值更新到建筑的品牌值
				查： PromotionMgr 维护
				*/
			int buildingType = gs_AdAddNewPromoOrder.getBuildingType(); // 四位建筑id
			newOrder.buildingType = buildingType;
			LogDb.promotionRecord(seller.id(), buyer.id(), sellerBuildingId, selfPromo ? 0 : fcySeller.getCurPromPricePerMs(), fee, buildingType, buildingType / 100, true);
		}else{
			int productionType = gs_AdAddNewPromoOrder.getProductionType(); //七位商品id
			newOrder.productionType = productionType;
            LogDb.promotionRecord(seller.id(), buyer.id(), sellerBuildingId, selfPromo ? 0 : fcySeller.getCurPromPricePerMs(), fee, productionType,MetaGood.category(productionType),false);
		}
		PromotionMgr.instance().AdAddNewPromoOrder(newOrder);
		GameDb.saveOrUpdate(PromotionMgr.instance());
		//结账
		buyer.decMoney(fee+minerCost);
		seller.addMoney(fee-minerCost);
        LogDb.playerPay(buyer.id(), fee+minerCost);
        LogDb.playerIncome(seller.id(), fee-minerCost);
        //矿工费用记录
		LogDb.minersCost(buyer.id(),minerCost,MetaData.getSysPara().minersCostRatio);
		LogDb.minersCost(seller.id(),minerCost,MetaData.getSysPara().minersCostRatio);

		//更新买家玩家信息中的广告缓存
		buyer.addPayedPromotion(newOrder.promotionId);
		GameDb.saveOrUpdate(buyer);
		//更新广告商广告列表
		fcySeller.addSelledPromotion(newOrder.promotionId);
		sellerBuilding.updateTodayIncome(fee);
		LogDb.buildingIncome(sellerBuildingId, buyer.id(), fee, 0, 0);//不含矿工费
		GameDb.saveOrUpdate(Arrays.asList(fcySeller,sellerBuilding));
		//如果是在自己公司推广不发通知
		if (!selfPromo) {
			//推广公司预约通知
			long newPromoStartTs = newOrder.promStartTs; //预计开始时间
			long promDuration = newOrder.promDuration; //广告时长
			UUID[] buildingId = {sellerBuilding.id()};
			StringBuilder sb = new StringBuilder().append(fee - minerCost + ",").append(promDuration + ",").append(newPromoStartTs);
			MailBox.instance().sendMail(Mail.MailType.PUBLICFACILITY_APPOINTMENT.getMailType(), sellerBuilding.ownerId(), null, buildingId, null, sb.toString());
		}
		//发送客户端通知
		this.write(Package.create(cmd, gs_AdAddNewPromoOrder.toBuilder().setRemainTime(fcySeller.getPromRemainTime()).build()));
		//能否在Fail中添加一个表示成功的枚举值 noFail ，直接把收到的包返回给客户端太浪费服务器带宽了
		if (!buyerPlayerId.equals(seller.id()))
		{
			Gs.IncomeNotify incomeNotify = Gs.IncomeNotify.newBuilder()
					.setBuyer(Gs.IncomeNotify.Buyer.PLAYER)
					.setBuyerId(Util.toByteString(buyerPlayerId))
					.setFaceId(buyer.getFaceId())
					.setCost(fee - minerCost)
					.setType(Gs.IncomeNotify.Type.PROMO)
					.setBid(sellerBuilding.metaId())
					.setItemId(gs_AdAddNewPromoOrder.hasBuildingType() ? gs_AdAddNewPromoOrder.getBuildingType() : gs_AdAddNewPromoOrder.getProductionType())
					.setDuration((int) (gs_AdAddNewPromoOrder.getPromDuration() / 3600000))
					.build();
			GameServer.sendIncomeNotity(seller.id(),incomeNotify);
		}
	}

	public void AdGetPromoAbilityHistory(short cmd, Message message) {
		Gs.AdGetPromoAbilityHistory GetRds = (Gs.AdGetPromoAbilityHistory) message;
		Gs.AdGetPromoAbilityHistory.Builder newbuilder = GetRds.toBuilder();
		UUID sellerBuildingId = Util.toUuid(GetRds.getSellerBuildingId().toByteArray());
		//开始时间，以小时为单位
		int tsSart = (int)(GetRds.getStartTs()/PromotionMgr._upDeltaMs) - 1;
		List userList = null;
		int RecordsCount = GetRds.hasRecordsCount() ? (GetRds.getRecordsCount() < 30 ? GetRds.getRecordsCount(): 30)  :30;
		for (int i = 0; i < GetRds.getTypeIdsList().size(); i++) {
			int tpid = GetRds.getTypeIdsList().get(i);
			if(tpid > 0){
				//eva
				userList = GameDb.getEva_records(tsSart,sellerBuildingId,tpid, RecordsCount);
			}
			else{
				//人流量
				Building building = City.instance().getBuilding(sellerBuildingId);
				userList.addAll(GameDb.getFlow_records(tsSart, building.ownerId(),RecordsCount)) ;
			}
			Gs.Records.Builder rds = Gs.Records.newBuilder();
			rds.setBuildingId(Util.toByteString(sellerBuildingId));
			rds.setTypeId(tpid);
			userList.forEach(record->{
				Record rd = (Record) record;
				rds.addList(rd.toproto());
			});
			newbuilder.addRecordsList(rds.build());
		}
		this.write(Package.create(cmd, newbuilder.build()));
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
	    LogDb.playerPay(player.id(), slot.rentPreDay);
	    LogDb.playerIncome(owner.id(), slot.rentPreDay);
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
		Item it = new Item(c.getItem());
		ItemKey k = it.key;
		UUID id = Util.toUuid(c.getBuildingId().toByteArray());
		IStorage storage = IStorage.get(id, player);
		if(storage == null)
			return;
		if(storage.delItem(it))
		{
			//同步数据
			if(storage instanceof  WareHouse){
				WareHouse wareHouse= (WareHouse) storage;
				WareHouseManager.updateWareHouseMap(wareHouse);
			}
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
		LogDb.playerPay(player.id(), charge);
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
		Gs.LabAddLine c = (Gs.LabAddLine) message;
		UUID bid = Util.toUuid(c.getBuildingId().toByteArray());
		Building building = City.instance().getBuilding(bid);
		if (building == null || building.outOfBusiness() || !(building instanceof Laboratory))
			return;
		if (c.getTimes() <= 0)
			return;
		if (c.hasGoodCategory()) {
			if (!MetaGood.legalCategory(c.getGoodCategory()))
				return;
		}
		Laboratory lab = (Laboratory) building;
		long cost = 0;
		Player seller = GameDb.getPlayer(lab.ownerId());
		if (!building.canUseBy(this.player.id()) && !lab.isExclusiveForOwner()) {//如果不是建筑主任，同时要求开放研究所
			if (!c.hasTimes())
				return;
			if (c.getTimes() > lab.getSellTimes())
				return;
			cost = c.getTimes() * lab.getPricePreTime();
			//TODO:矿工费用
			double minersRatio = MetaData.getSysPara().minersCostRatio/10000;
			long minerCost = (long) Math.floor(cost * minersRatio);
			if (!player.decMoney(cost + minerCost))
				return;
			seller.addMoney(cost - minerCost);
			LogDb.playerPay(this.player.id(), cost + minerCost);
			LogDb.playerIncome(seller.id(), cost - minerCost);
			//矿工费用记录
			LogDb.minersCost(this.player.id(), minerCost, MetaData.getSysPara().minersCostRatio);
			LogDb.minersCost(seller.id(), minerCost, MetaData.getSysPara().minersCostRatio);
			lab.updateTodayIncome(cost - minerCost);
			if (c.hasGoodCategory()) {
				lab.updateTotalGoodIncome(cost - minerCost, c.getTimes());
			} else {
				lab.updateTotalEvaIncome(cost - minerCost, c.getTimes());
			}
			LogDb.buildingIncome(lab.id(), this.player.id(), cost, 0, 0);//不包含矿工费用
		}
		LogDb.laboratoryRecord(lab.ownerId(), player.id(), lab.id(), lab.getPricePreTime(), cost, c.hasGoodCategory() ? c.getGoodCategory() : 0, c.hasGoodCategory() ? true : false);
		Laboratory.Line line = lab.addLine(c.hasGoodCategory() ? c.getGoodCategory() : 0, c.getTimes(), this.player.id(), cost);
		if (null != line) {
			GameDb.saveOrUpdate(Arrays.asList(lab, player, seller)); // let hibernate generate the fucking line.id first
			// 研究所预约通知(如果在自己公司研究不发通知)
			boolean flag = this.player.id().equals(building.ownerId()) ;
			if (!flag) {
				long beginProcessTs = line.beginProcessTs;//预计开始时间
				int times = c.getTimes();//研究时长
				UUID[] buildingId = {lab.id()};
				StringBuilder sb = new StringBuilder().append(cost+",").append(times+",").append(beginProcessTs);
				MailBox.instance().sendMail(Mail.MailType.LABORATORY_APPOINTMENT.getMailType(), lab.ownerId(), null, buildingId, null, sb.toString());
			}
			this.write(Package.create(cmd, Gs.LabAddLineACK.newBuilder().setBuildingId(Util.toByteString(lab.id())).setLine(line.toProto()).build()));
		}
        Gs.IncomeNotify incomeNotify = Gs.IncomeNotify.newBuilder()
                .setBuyer(Gs.IncomeNotify.Buyer.PLAYER)
                .setBuyerId(Util.toByteString(player.id()))
                .setFaceId(player.getFaceId())
                .setCost(cost)
                .setType(Gs.IncomeNotify.Type.LAB)
                .setBid(building.metaId())
                .setItemId(c.hasGoodCategory() ? c.getGoodCategory() : 0)
                .setDuration(c.getTimes())
                .build();
		GameServer.sendIncomeNotity(seller.id(),incomeNotify);
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
		Laboratory lab = (Laboratory)building;
		UUID lineId = Util.toUuid(c.getLineId().toByteArray());
		Laboratory.RollResult r = lab.roll(lineId, player);
		if(r != null) {
			Gs.LabRollACK.Builder builder = Gs.LabRollACK.newBuilder();
			builder.setBuildingId(c.getBuildingId());
			builder.setLineId(c.getLineId());
			if(r.evaPoint > 0) {
				builder.setEvaPoint(r.evaPoint);
                builder.addAllLabResult(r.labResult);//开启的5个结果集（新增）
			}
			else {
				if(r.itemIds != null)
					builder.addAllItemId(r.itemIds);
				else{
					builder.addAllLabResult(r.labResult);
				}
			}
			GameDb.saveOrUpdate(Arrays.asList(lab, player));
			this.write(Package.create(cmd, builder.build()));
		}
		else
			this.write(Package.fail(cmd));
	}

	public void setPlayerName(short cmd, Message message) {
		Gs.Str c = (Gs.Str)message;
		if(c.getStr().isEmpty() || c.getStr().length() > MAX_PLAYER_NAME_LEN)
			return;
		if(!player.canSetName()) {
			this.write(Package.fail(cmd, Common.Fail.Reason.roleNameSetInCd));
			return;
		}
		String oldName = player.getName();
		player.setName(c.getStr());
		if(!GameDb.setPlayerName(player)) {
			player.setName(oldName);
			this.write(Package.fail(cmd, Common.Fail.Reason.roleNameDuplicated));
		}
		else {
			player.updateNameSetTs();
			GameDb.saveOrUpdate(player);
			this.write(Package.create(cmd, c));
		}
	}
	public void betFlight(short cmd, Message message) {
		Gs.BetFlight c = (Gs.BetFlight)message;
		if(c.getScore() > player.score())
			return;

		if(FlightManager.instance().betFlight(player.id(), c.getId(), c.getDelay(), c.getScore())) {
			player.offsetScore(-c.getScore());
			GameDb.saveOrUpdate(Arrays.asList(player, FlightManager.instance()));
			this.write(Package.create(cmd, c));
		}
	}

	public void getFlightBetHistory(short cmd) {
		Gs.FlightBetHistory.Builder builder = Gs.FlightBetHistory.newBuilder();
		for(LogDb.FlightBetRecord r : LogDb.getFlightBetRecord(player.id())) {
			builder.addInfoBuilder().setAmount(r.amount).setDelay(r.delay).setData(r.data);
		}
		this.write(Package.create(cmd, builder.build()));
	}

	public void getAllFlight(short cmd) {
		this.write(Package.create(cmd, FlightManager.instance().toProto(player.id())));
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
		LogDb.playerPay(player.id(), sell.price);
	    LogDb.playerIncome(seller.id(), sell.price);
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
		    LogDb.playerPay(player.id(), cost);
		    LogDb.playerIncome(talent.id(), cost);
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
			GlobalConfig.cityError("GameSession.toDoOnline(): push blacklist failed.");
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
				GlobalConfig.cityError("get player name failed : id=" + from_id);
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
			MailBox.instance().sendMail(Mail.MailType.ADD_FRIEND_SUCCESS.getMailType(),sourceId, null,oppositeId,null);
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

	public void getGroundInfo(short cmd,Message message)
	{
		Gs.Id id= (Gs.Id) message;
		UUID pid = Util.toUuid(id.getId().toByteArray());
		Gs.GroundChange.Builder builder = Gs.GroundChange.newBuilder();
		builder.addAllInfo(GroundManager.instance().getGroundProto(pid));
		this.write(Package.create(cmd, builder.build()));
	}

	public void createSociety(short cmd, Message message)
	{
		Gs.CreateSociety gsSociety = (Gs.CreateSociety) message;
		String name = gsSociety.getName();
		String introduction = gsSociety.getIntroduction();
		if (Strings.isNullOrEmpty(name) || Strings.isNullOrEmpty(introduction)
				|| player.getSocietyId() != null)
		{
			return;
		}
		Society society = SocietyManager.createSociety(player.id(), name, introduction);
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
				 * 踢出公会
				 */
				UUID playerId = Util.toUuid(params.getPlayerId().toByteArray());
				MailBox.instance().sendMail(Mail.MailType.SOCIETY_KICK_OUT.getMailType(), playerId,null, new UUID[]{societyId}, null);
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
		City.instance().forEachGrid(new GridIndex(gridIndex.getX(), gridIndex.getY()).toSyncRange(),
				grid ->
				{
					Gs.ContractGridDetail.GridInfo.Builder infoBuilder = builder.addGridInfoBuilder();
					infoBuilder.getIdxBuilder().setX(grid.getX()).setY(grid.getY());
					grid.forAllBuilding(building ->
					{
						if (building instanceof IBuildingContract
								&& !building.outOfBusiness()
								&& ((IBuildingContract) building).getBuildingContract().isOpen()
								&& !((IBuildingContract) building).getBuildingContract().isSign())
						{
							Gs.ContractGridDetail.BuildingInfo.Builder b = infoBuilder.addInfoBuilder();
							b.setOwnerId(Util.toByteString(building.ownerId()))
									.setBuildingName(building.getName())
									.setPos(building.coordinate().toProto())
									.setHours(((IBuildingContract) building).getBuildingContract().getDurationHour())
									.setPrice(((IBuildingContract) building).getBuildingContract().getPrice())
									.setMId(building.metaId())
									.setLift(building.getLift());
						}
					});
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

	public void queryBuildingTech(short cmd, Message message)
	{
		Gs.ByteNum param = (Gs.ByteNum) message;
		UUID bid = Util.toUuid(param.getId().toByteArray());
		int techId = param.getNum();
		Building building = City.instance().getBuilding(bid);
		if (building != null)
		{
			Gs.BuildingTech.Builder builder = Gs.BuildingTech.newBuilder()
					.setBid(param.getId())
					.setTechId(techId)
					.setPId(Util.toByteString(building.ownerId()))
					.setBname(building.getName())
					.setMId(building.metaId());

			if (building.ownerId().equals(player.id()))
			{
				Gs.BuildingTech.Infos.Builder builder1 = builder.addInfosBuilder().setPId(Util.toByteString(player.id()));
				EvaManager.getInstance().getEva(player.id(), techId).forEach(eva ->
				{
					builder1.addTechInfo(eva.toTechInfo());

				});
			}
			builder.addAllInfos(LeagueManager.getInstance().getBuildingLeagueTech(bid, techId));
			this.write(Package.create(cmd,builder.build()));
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
					BuildingInfo buildingInfo=b.toProto();
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
		e.setB(-1);
		EvaManager.getInstance().updateEva(e);

		Player player=GameDb.getPlayer(Util.toUuid(eva.getPid().toByteArray()));
		player.decEva(eva.getDecEva());
		GameDb.saveOrUpdate(player);

		this.write(Package.create(cmd, eva.toBuilder().setCexp(cexp).setLv(level).setDecEva(eva.getDecEva()).build()));
	}

	//TODO:Eva改版(保存eva修改信息的位置在查询修改前的数据之后保存)=====================================================
	public void updateMyEvas(short cmd, Message message)
	{
		Gs.Evas evas = (Gs.Evas)message;//传过来的Evas
		Gs.EvaResultInfos.Builder results = Gs.EvaResultInfos.newBuilder();//要返回的值
		Gs.EvaResultInfo.Builder result =null;
		Eva oldEva=null;//修改前的Eva信息
		for (Gs.Eva eva : evas.getEvaList()) {
			result=Gs.EvaResultInfo.newBuilder();
			//修改后eva信息
			Eva newEva = EvaManager.getInstance().updateMyEva(eva);
			//修改前的eva
			oldEva= new Eva();
			oldEva.setLv(eva.getLv());
			oldEva.setAt(eva.getAt());
			oldEva.setBt(eva.getBt().getNumber());
			oldEva.setB(eva.getB());
			Player player=GameDb.getPlayer(Util.toUuid(eva.getPid().toByteArray()));
			player.decEva(eva.getDecEva());
			GameDb.saveOrUpdate(player);
			//基础信息(加点前、加点后)
			Gs.EvasInfo.Builder evaInfo = Gs.EvasInfo.newBuilder().setOldEva(eva).setNewEva(newEva.toProto());
			result.setEvasInfo(evaInfo);
			//升级对比信息
			if(MetaGood.isItem(eva.getAt())&&eva.getBt().equals(Gs.Eva.Btype.Quality)){//1.加工厂品质提升（计算竞争力）（*）
				//筛选玩家所有该建筑
				List<Building> buildings = City.instance().getPlayerBListByBtype(player.id(), MetaBuilding.PRODUCE);
				Map<UUID, Double> oldCompetitiveMap = CompeteAndExpectUtil.getProductCompetitiveMap(buildings, oldEva);//1.加点前的竞争力
				EvaManager.getInstance().updateEva(newEva);
				Map<UUID, Double> newCompetitiveMap = CompeteAndExpectUtil.getProductCompetitiveMap(buildings, newEva);//2.修改前后的竞争力
				List<Gs.Promote> promotes = ProtoUtil.getPromoteList(buildings, oldCompetitiveMap, newCompetitiveMap,MetaBuilding.PRODUCE,eva.getAt());
				result.addAllPromotes(promotes);
			}else if(eva.getBt().equals(Gs.Eva.Btype.PromotionAbility)){//2.推广公司推广能力（*）
				List<Building> buildings = City.instance().getPlayerBListByBtype(player.id(), MetaBuilding.PUBLIC);
				Map<UUID, Double> oldCompetitiveMap = CompeteAndExpectUtil.getPublicCompetitiveMap(buildings, oldEva);//1.修改前的竞争力
				EvaManager.getInstance().updateEva(newEva);
				Map<UUID, Double> newCompetitiveMap = CompeteAndExpectUtil.getPublicCompetitiveMap(buildings,newEva);//2.修改后的竞争力
				List<Gs.Promote> promotes = ProtoUtil.getPromoteList(buildings,oldCompetitiveMap,newCompetitiveMap,MetaBuilding.PUBLIC,null);
				result.addAllPromotes(promotes);
			}else if(eva.getBt().equals(Gs.Eva.Btype.InventionUpgrade)||eva.getBt().equals(Gs.Eva.Btype.EvaUpgrade)){//3.研究所的研究成功率提升（*）
				//同理，先获取未加点前的研究所竞争力，再获取加点后的
				List<Building> buildings = City.instance().getPlayerBListByBtype(player.id(), MetaBuilding.LAB);
				Map<UUID, Double> oldCompetitiveMap = CompeteAndExpectUtil.getLabCompetitiveMap(buildings, oldEva);//1.修改前的竞争力
				EvaManager.getInstance().updateEva(newEva);
				Map<UUID, Double> newCompetitiveMap = CompeteAndExpectUtil.getLabCompetitiveMap(buildings, newEva);//2.修改后的竞争力
				ProtoUtil.getPromoteList(buildings,oldCompetitiveMap,newCompetitiveMap,MetaBuilding.LAB,null);
				List<Gs.Promote> promotes = ProtoUtil.getPromoteList(buildings, oldCompetitiveMap, newCompetitiveMap,MetaBuilding.LAB,null);
				result.addAllPromotes(promotes);
			}else if(eva.getAt()==MetaBuilding.APARTMENT&&eva.getBt().equals(Gs.Eva.Btype.Quality)){//4.住宅的品质提升，计算预期入住人数（*，目前只差一个繁荣度）
				List<Building> buildings = City.instance().getPlayerBListByBtype(player.id(), MetaBuilding.APARTMENT);
				//npc花费比例
				double spendMoneyRatio = MetaData.getBuildingSpendMoneyRatio(eva.getAt());
				Map<UUID, List<Integer>> oldExpectSpend = CompeteAndExpectUtil.getApartmentExpectSpend(buildings, oldEva, spendMoneyRatio);//1.获取修改前的预期花费
				EvaManager.getInstance().updateEva(newEva);
				Map<UUID, List<Integer>> newExpectSpend = CompeteAndExpectUtil.getApartmentExpectSpend(buildings, newEva, spendMoneyRatio);//2.修改后的预期花费
				//封装数据
				List<Gs.ApartmentData> apartmentData = ProtoUtil.getApartmentResultList(buildings, oldExpectSpend, newExpectSpend, MetaBuilding.APARTMENT);
				result.addAllApartmentData(apartmentData);
			}else if(eva.getAt()==MetaBuilding.RETAIL&&eva.getBt().equals(Gs.Eva.Btype.Quality)){//5.零售店品质提升率=提升的等级/全城该项eva最高等级
				EvaManager.getInstance().updateEva(newEva);
				//提升比例:提升的等级/全城该项eva最高等级  如果平级，提升为0
				int maxLv = GlobalUtil.getEvaMaxAndMinValue(eva.getAt(), eva.getBt().getNumber()).get("max").getLv();
				int lv = newEva.getLv();
				result.setRetailSpendRatio(maxLv == lv ? 0 : lv / maxLv);
			}
			else {
				EvaManager.getInstance().updateEva(newEva);
			}
			results.addResultInfo(result);
		}
		//更新评分数据
		BrandManager.instance().getAllBuildingBrandOrQuality();
		this.write(Package.create(cmd, results.build()));
	}

	public void queryMyBrands(short cmd, Message message){
		Gs.QueryMyBrands msg = (Gs.QueryMyBrands)message;
		UUID pid = Util.toUuid(msg.getPId().toByteArray());
		Gs.MyBrands.Builder list = Gs.MyBrands.newBuilder();
		//需要根据原料厂、加工厂、零售店、住宅、推广公司、研究所等查询
		List<Gs.MyBrands.Brand> materialBrand = BrandManager.instance().getBrandByType(MetaBuilding.MATERIAL, pid);//原料
		List<Gs.MyBrands.Brand> goodBrand = BrandManager.instance().getBrandByType(MetaBuilding.PRODUCE, pid);//加工厂
		List<Gs.MyBrands.Brand> retailShopBrand = BrandManager.instance().getBrandByType(MetaBuilding.RETAIL, pid);//零售店
		List<Gs.MyBrands.Brand> apartmentBrand = BrandManager.instance().getBrandByType(MetaBuilding.APARTMENT, pid);//住宅
		List<Gs.MyBrands.Brand> labBrand = BrandManager.instance().getBrandByType(MetaBuilding.LAB, pid);//研究所
		List<Gs.MyBrands.Brand> promotionBrand = BrandManager.instance().getBrandByType(MetaBuilding.PUBLIC, pid);//推广
		list.addAllMaterialBrand(materialBrand)
				.addAllGoodBrand(goodBrand)
				.addAllRetailShopBrand(retailShopBrand)
				.addAllApartmentBrand(apartmentBrand)
				.addAllPromotionBrand(promotionBrand)
				.addAllLabBrand(labBrand);
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
			Gs.BuildingInfo buildInfo = build.toProto();
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

	//修改品牌名字
	public void modyfyMyBrandName(short cmd,Message message){
		Gs.ModyfyMyBrandName msg = (Gs.ModyfyMyBrandName)message;
		UUID pId = Util.toUuid(msg.getPId().toByteArray());
		if(!this.player.id().equals(pId)){
			GlobalConfig.cityError("[modyfyMyBrandName] Brand-name only can be modified by it's owner!");
			return;
		}
		int techId=msg.getTypeId();
		Long result = BrandManager.instance().changeBrandName(pId, techId, msg.getNewBrandName());
		//-1 名称重复、1修改成功。其他，返回上次修改时间（只有可能是不能修改的时间）
		if(result==-1){
			this.write(Package.fail(cmd,Common.Fail.Reason.roleNameDuplicated));
		}else if(result==1){
			this.write(Package.create(cmd, msg));
		}else{//时间过短，修改失败，返回上次修改时间。
			Gs.ModyfyMyBrandName.Builder builder = msg.toBuilder().setLastChangeTime(result);
			this.write(Package.create(cmd, builder.build()));
		}
	}

	//修改公司名字
	public void modifyCompanyName(short cmd,Message message){
		Gs.ModifyCompanyName msg = (Gs.ModifyCompanyName) message;
		String newName = msg.getNewName();
		UUID pid = Util.toUuid(msg.getPid().toByteArray());
		//查询玩家信息
		if(!player.id().equals(pid)){
            GlobalConfig.cityError("[modyfyCompanyName] CompanyName only can be modified by it's owner!");
		}
		//判断名称是否重复
		else if(player.getCompanyName().equals(newName)||GameDb.companyNameIsInUsed(newName)){//已经被使用的名称(或者和以前名称相同)
			this.write(Package.fail(cmd,Common.Fail.Reason.roleNameDuplicated));
		}
		else if(!player.canBeModify()){ //时间未到（返回冻结状态错误码）
			this.write(Package.fail(cmd,Common.Fail.Reason.accountInFreeze));
		}else{
			player.setCompanyName(newName);
			player.setLast_modify_time(new Date().getTime());
			GameDb.saveOrUpdate(player);
			Gs.RoleInfo roleInfo = playerToRoleInfo(player);
			this.write(Package.create(cmd,roleInfo));
		}
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
		updateBuildingVisitor(b);
		this.write(Package.create(cmd, b.detailProto()));
	}

	//2.获取玩家的建筑信息（建筑信息）
	public void getPlayerBuildingDetail(short cmd){
		Gs.BuildingSet.Builder builder = Gs.BuildingSet.newBuilder();
		City.instance().forEachBuilding(player.id(), (Building b)->{
			b.appendDetailProto(builder);
		});
		//根据玩家id获取租的仓库
		List<WareHouseRenter> renters = WareHouseManager.instance().getWareHouseByRenterId(player.id());
		renters.forEach(r->{
			r.appendDetailProto(builder);
		});
		this.write(Package.create(cmd, builder.build()));
	}
	//3.设置仓库出租信息
	public void setWareHouseRent(short cmd, Message message){
		Gs.SetWareHouseRent info = (Gs.SetWareHouseRent) message;
		if(WareHouseManager.instance().settingWareHouseRentInfo(player.id(),info)){
			this.write(Package.create(cmd, info));
		}else
			this.write(Package.fail(cmd));
	}

	//关闭出租
	public void closeWareHouseRent(short cmd, Message message){
		Gs.SetWareHouseRent info = (Gs.SetWareHouseRent) message;
		if(WareHouseManager.instance().closeWareHouseRentInfo(player.id(),info)){
			this.write(Package.create(cmd, info));
		}else
			this.write(Package.fail(cmd));
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
			//同步缓存数据
			if(storage instanceof  WareHouseRenter){
				WareHouseRenter renter = (WareHouseRenter) storage;
				WareHouseManager.updateWareHouseMap(renter);
			}
 			else if(storage instanceof  WareHouse){
				WareHouse wareHouse= (WareHouse) storage;
				WareHouseManager.updateWareHouseMap(wareHouse);
			}
			this.write(Package.create(cmd,c));
		}else
			this.write(Package.fail(cmd));
	}
	//5.租用集散中心仓库
	public void rentWareHouse(short cmd, Message message){
		Gs.rentWareHouse rentInfo = (Gs.rentWareHouse) message;
		Gs.rentWareHouse rentWareHouse = WareHouseManager.instance().rentWareHouse(player, rentInfo);
		if(rentWareHouse!=null) {
			Gs.detailWareHouseRenter.Builder builder = Gs.detailWareHouseRenter.newBuilder();
			//返回租的所有仓库
			UUID bid = Util.toUuid(rentWareHouse.getBid().toByteArray());
			List<WareHouseRenter> renters = WareHouseManager.instance().getWareHouseByRenterIdFromWareHouse(bid, player.id());
			renters.forEach(r->{
				builder.addRenters(r.toProto());
			});
			builder.setBuildingId(rentInfo.getBid());
			this.write(Package.create(cmd, builder.build()));
		}
		else
			this.write(Package.fail(cmd));
	}


	//7.购买上架商品（包括购买在租户仓库的物品）
	public void buyInShelfGood(short cmd, Message message) throws Exception {
		Gs.BuyInShelfGood inShelf = (Gs.BuyInShelfGood) message;
		if(inShelf.getGood().getPrice()<0)
			return;
		UUID bid = Util.toUuid(inShelf.getGood().getBuildingId().toByteArray());
		UUID wid = Util.toUuid(inShelf.getWareHouseId().toByteArray());
		//2.判断商品所属id建筑是不是租的仓库（根据orderid判断）
		WareHouseRenter sellRenter=null;
		WareHouseRenter buyRenter=null;
		Building sellBuilding = City.instance().getBuilding(bid);//销售方
		IShelf sellShelf = (IShelf) sellBuilding;
		IStorage buyStore = IStorage.get(wid, player);//买方
		UUID sellOwnerId=sellBuilding.ownerId();
		//3.卖方是否是租的仓库
		if(inShelf.getGood().hasOrderid()){
			//表明是租的仓库
			sellRenter = WareHouseUtil.getWareRenter(bid, inShelf.getGood().getOrderid());
			if(sellRenter==null)
				return;
			sellShelf = sellRenter;
			sellOwnerId = sellRenter.getRenterId();
		}
		//买方是否也是租的仓库
		if(inShelf.hasOrderid()){
			buyRenter= WareHouseUtil.getWareRenter(wid, inShelf.getOrderid());
			if(buyRenter==null){
				return;
			}
			buyStore = buyRenter;
		}
		Item itemBuy = new Item(inShelf.getGood().getItem());
		Shelf.Content i = sellShelf.getContent(itemBuy.key);
		//4.如果和上架的价格不对应或者上架数量小于要购买的数量，失败
		if(i == null || i.price != inShelf.getGood().getPrice() || i.n < itemBuy.n) {
			this.write(Package.fail(cmd));
			return;
		}
		//5.计算价格（运费+商品所需价值）
		long cost = itemBuy.n*inShelf.getGood().getPrice();//计算商品总价值
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
		player.decMoney(cost+freight);//扣除商品+运费
		//8.2向出售方发送收入通知提示
		Gs.IncomeNotify notify = Gs.IncomeNotify.newBuilder()
				.setBuyer(Gs.IncomeNotify.Buyer.PLAYER)
				.setBuyerId(Util.toByteString(player.id()))
				.setFaceId(player.getFaceId())
				.setCost(cost)
				.setType(Gs.IncomeNotify.Type.INSHELF)
				.setBid(sellBuilding.metaBuilding.id)
				.setItemId(itemBuy.key.meta.id)
				.setCount(itemBuy.n)
				.build();
		GameServer.sendIncomeNotity(seller.id(),notify);
		//8.3 发送消息通知
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
		//9.日志记录
		int itemId = itemBuy.key.meta.id;
		int type = MetaItem.type(itemBuy.key.meta.id);//获取商品类型
		LogDb.playerIncome(seller.id(), cost);
		LogDb.playerPay(player.id(), cost);
		LogDb.playerPay(player.id(), freight);
		//9.1记录运输日志(区分建筑还是租户仓库)
		if(sellRenter==null&&buyRenter==null) {
		//记录商品品质及知名度
        double brand = BrandManager.instance().getGood(player.id(), itemId);
        double quality = itemBuy.key.qty;LogDb.payTransfer(player.id(), freight, bid, wid, itemBuy.key.producerId, itemBuy.n);}else{
			Serializable srcId=bid;
			Serializable dstId=wid;
			if(sellRenter!=null)
				srcId = inShelf.getGood().hasOrderid();
			if(buyRenter!=null)
				dstId = inShelf.getOrderid();
			LogDb.payRenterTransfer(player.id(),freight,srcId,dstId,itemBuy.key.producerId, itemBuy.n);
		}
		//9.2记录货架收入与建筑收入信息(区分建筑还是租户仓库)
		//8.6记录交易日志
		LogDb.payTransfer(player.id(), freight, bid, wid, itemBuy.key.producerId, itemBuy.n);
		if(!inShelf.getGood().hasOrderid()) { //商品不在租的仓库
			LogDb.buyInShelf(player.id(), seller.id(), itemBuy.n, inShelf.getGood().getPrice(),
					itemBuy.key.producerId, sellBuilding.id(), type, itemId);
			LogDb.buildingIncome(bid, player.id(), cost, type, itemId);
		}
		else{//租户货架上购买的（统计日志）
			LogDb.buyRenterInShelf(player.id(), seller.id(), itemBuy.n, inShelf.getGood().getPrice(),
					itemBuy.key.producerId,sellRenter.getOrderId(), type, itemId);
			//租户货架收入记录
			LogDb.renterShelfIncome(inShelf.getGood().getOrderid(),player.id(), cost, type, itemId);
		}
		//8.7.销售方减少上架数量
		sellShelf.delshelf(itemBuy.key, itemBuy.n, false);
		IStorage sellStorage = (IStorage) sellShelf;
		sellStorage.consumeLock(itemBuy.key, itemBuy.n);
		//更每每日的收入
		if(sellRenter!=null){
			sellRenter.updateTodayIncome(cost);//更新今日收入
		}else{
			sellBuilding.updateTodayIncome(cost);
		}
		buyStore.consumeReserve(itemBuy.key, itemBuy.n, inShelf.getGood().getPrice());
		GameDb.saveOrUpdate(Arrays.asList(player,seller,sellStorage,buyStore));
		//同步缓存数据
		if(sellStorage instanceof WareHouseRenter){
			WareHouseManager.updateWareHouseMap((WareHouseRenter) sellStorage);
		}else if(sellStorage instanceof  WareHouse){
			WareHouseManager.updateWareHouseMap((WareHouse) sellStorage);
		}
		if(buyStore instanceof WareHouseRenter)
			WareHouseManager.updateWareHouseMap((WareHouseRenter) buyStore);
		else if(buyStore instanceof  WareHouse)
			WareHouseManager.updateWareHouseMap((WareHouse) buyStore);

		this.write(Package.create(cmd,inShelf));
	}
	//8.上架
	public void putAway(short cmd, Message message) throws Exception {
		Gs.PutAway c = (Gs.PutAway) message;
		Item item = new Item(c.getItem());
		UUID bid = Util.toUuid(c.getBuildingId().toByteArray());
		Building building = City.instance().getBuilding(bid);
		IShelf s = null;
		//如果有订单号，表示在租用的仓库中上架
		if(c.hasOrderId()){
			//从租的仓库中上架
			WareHouseRenter renter = WareHouseUtil.getWareRenter(bid, c.getOrderId());
			s=renter;
		}else {
			if (building == null || !(building instanceof IShelf) || !building.canUseBy(player.id()) || building.outOfBusiness())
				return;
			if (building instanceof RetailShop && item.key.meta instanceof MetaMaterial)
				return;
			s = (IShelf) building;
		}

		if(s.addshelf(item,c.getPrice(),c.getAutoRepOn())){
			GameDb.saveOrUpdate(s);
			//同步缓存数据
			if(s instanceof WareHouse)
				WareHouseManager.updateWareHouseMap((WareHouse)s);
			else if(s instanceof WareHouseRenter)
				WareHouseManager.updateWareHouseMap((WareHouseRenter)s);
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
			//同步缓存数据
			WareHouse wareHouse = wareRenter.getWareHouse();
			WareHouseManager.wareHouseMap.put(wareHouse.id(),wareHouse);
			this.write(Package.create(cmd,r));
		}else
			this.write(Package.fail(cmd));
	}

	//10.下架（包含其他建筑和租用仓库）
	public void soldOutShelf(short cmd, Message message) throws Exception {
		Gs.SoldOutShelf s = (Gs.SoldOutShelf) message;
		Item item = new Item(s.getItem());
		Building building = City.instance().getBuilding(Util.toUuid(s.getBuildingId().toByteArray()));
		IShelf sf = null;
		//情况1，是租用的仓库下架
		if(s.hasOrderId()){
			//从租的仓库中下架
			WareHouseRenter renter = WareHouseUtil.getWareRenter(Util.toUuid(s.getBuildingId().toByteArray()),s.getOrderId());
			if(renter==null)
				return;
			sf = renter;
		}else {//普通建筑下架
			if(building == null || !(building instanceof IShelf) || !building.canUseBy(player.id()) || building.outOfBusiness())
				return;
			if(building instanceof RetailShop && item.key.meta instanceof MetaMaterial)
				return;
			sf = (IShelf) building;
		}
		if (sf.delshelf(item.key, item.n, true)) {
			//同步缓存数据
			if(sf instanceof WareHouse)
				WareHouseManager.updateWareHouseMap((WareHouse)sf);
			else if(sf instanceof  WareHouseRenter)
				WareHouseManager.updateWareHouseMap((WareHouseRenter)sf);
			GameDb.saveOrUpdate(sf);
			this.write(Package.create(cmd, s));
		} else
			this.write(Package.fail(cmd));
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
			//同步数据
			WareHouseManager.updateWareHouseMap(renter);
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

	//13.detailWareHouse 租户详情，根据集散中心获取当前的租户详情
	public void detailWareHouseRenter(short cmd,Message message){
		Gs.Id c = (Gs.Id) message;
		UUID bid = Util.toUuid(c.getId().toByteArray());
		List<WareHouseRenter> renters = WareHouseManager.instance().getWareHouseByRenterIdFromWareHouse(bid, player.id());
		if(renters.size()<=0){
			this.write(Package.fail(cmd));
			return;
		}
		Gs.detailWareHouseRenter.Builder builder = Gs.detailWareHouseRenter.newBuilder();
		renters.forEach(p->{
			if(p.getRenterId().equals(player.id()))
				builder.addRenters(p.toProto());
		});
		builder.setBuildingId(c.getId());
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

	//15.获取集散中心数据详情,客户端传递一个中心坐标
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
	//16.运输
	public void transportGood(short cmd,Message message) throws Exception {
		Gs.TransportGood t = (Gs.TransportGood) message;
		UUID srcId = Util.toUuid(t.getSrc().toByteArray());
		UUID dstId = Util.toUuid(t.getDst().toByteArray());
		IStorage src = IStorage.get(srcId, player);
		IStorage dst = IStorage.get(dstId, player);
		Item item = new Item(t.getItem());
		if (t.hasSrcOrderId())
			src = WareHouseUtil.getWareRenter(srcId, t.getSrcOrderId());
		if(t.hasDstOrderId())
			dst=WareHouseUtil.getWareRenter(dstId, t.getDstOrderId());
		if(src == null || dst == null)
			return;
		//运费=距离*运费比例*数量
		int charge  = (int) Math.ceil(IStorage.distance(src, dst)) * item.n * MetaData.getSysPara().transferChargeRatio;
		if(player.money() < charge)
			return;
		if(!src.lock(item.key, item.n)) {
			this.write(Package.fail(cmd));
			return;
		}
		if(!dst.reserve(item.key.meta, item.n)) {
			src.unLock(item.key, item.n);
			this.write(Package.fail(cmd));
			return;
		}
		player.decMoney(charge);
		MoneyPool.instance().add(charge);
		//日志记录
		LogDb.playerPay(player.id(), charge);
		if(!t.hasSrcOrderId()&&!t.hasDstOrderId()) {
			LogDb.payTransfer(player.id(), charge, srcId, dstId, item.key.producerId, item.n);
		}else{
			Serializable srcId1=srcId;
			Serializable dstId1=dstId;
			if(t.hasSrcOrderId())
				srcId1 = t.getSrcOrderId();
			if(t.hasDstOrderId())
				dstId1 = t.getDstOrderId();
			LogDb.payRenterTransfer(player.id(), charge, srcId1, dstId1, item.key.producerId, item.n);
		}
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
		//同步数据
		if(srcShelf instanceof WareHouseRenter)
			WareHouseManager.updateWareHouseMap((WareHouseRenter) srcShelf);
		else if(srcShelf instanceof WareHouse)
			WareHouseManager.updateWareHouseMap((WareHouse) srcShelf);

		if(dstShelf instanceof WareHouseRenter)
			WareHouseManager.updateWareHouseMap((WareHouseRenter) dstShelf);
		else if(dstShelf instanceof WareHouse)
			WareHouseManager.updateWareHouseMap((WareHouse) dstShelf);
		GameDb.saveOrUpdate(Arrays.asList(src, dst, player));
		this.write(Package.create(cmd,t));
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
	//住宅推荐价格 √
	public void queryApartmentRecommendPrice(short cmd, Message message) {
		Gs.QueryBuildingInfo msg = (Gs.QueryBuildingInfo) message;
		UUID buildingId = Util.toUuid(msg.getBuildingId().toByteArray());
		Building building = City.instance().getBuilding(buildingId);
		if (building == null || building.type() != MetaBuilding.APARTMENT) {
			return;
		}
		UUID playerId = Util.toUuid(msg.getPlayerId().toByteArray()); //暂时不用
		Gs.ApartmentRecommendPrice.Builder builder = Gs.ApartmentRecommendPrice.newBuilder();

		this.write(Package.create(cmd,builder.build()));

	}

	//原料推荐价格 √
	public void queryMaterialRecommendPrice(short cmd, Message message) {
		Gs.MaterialMsg msg = (Gs.MaterialMsg) message;
		int itemId = msg.getMaterialId();// 原料id
		UUID buildingId = Util.toUuid(msg.getInfo().getBuildingId().toByteArray()); //建筑id
		UUID playerId = Util.toUuid(msg.getInfo().getPlayerId().toByteArray()); //玩家id
		Building building = City.instance().getBuilding(buildingId);
		if (building == null || building.type() != MetaBuilding.MATERIAL) {
			return;
		}
		int sumPrice = 0;
		int count = 0;
		//1.城市原料均销售定价 货架上的
		Collection<Building> allBuilding = City.instance().getAllBuilding();
		for (Building b : allBuilding) {
			if (b.type() != MetaBuilding.MATERIAL || !b.outOfBusiness() || !(b instanceof IShelf)) {
				return;
			}
			MaterialFactory mf = (MaterialFactory) b;
			Map<Item, Integer> saleInfo = mf.shelf.getSaleDetail(itemId);
			if (saleInfo != null) {
				sumPrice += new ArrayList<>(saleInfo.values()).get(0);
				count++;
			}

		}
		int avgPrice = sumPrice / count;
		//2.定价   当前原料厂某原料定价
		MaterialFactory materialFactory = (MaterialFactory) building;
		if (materialFactory.ownerId() != playerId || !materialFactory.outOfBusiness()) {
			return;
		}
		Map<Item, Integer> saleDetail = materialFactory.shelf.getSaleDetail(itemId);
		int price = new ArrayList<>(saleDetail.values()).get(0);
		//3.某原料生产速度
		MetaMaterial material = MetaData.getMaterial(itemId);
		double n = material.n;

		//4. 原料厂建筑工资
		double industrySalary = City.instance().getIndustrySalary(building.type());
		Gs.MaterialRecommendPrice.Builder builder = Gs.MaterialRecommendPrice.newBuilder();
		builder.setAvgPrice(avgPrice).setPrice(price).setNumOneSec(n).setSalary(industrySalary);
		this.write(Package.create(cmd, builder.build()));
	}

	//推广推荐价格 √
	public void queryPromotionRecommendPrice(short cmd, Message message) {
		Gs.PromotionMsg msg = (Gs.PromotionMsg) message;
		int typeId = msg.getTypeId(); // 推广类型
		Gs.QueryBuildingInfo info = msg.getInfo();
		UUID buildingId = Util.toUuid(info.getBuildingId().toByteArray()); //建筑id
		UUID playerId = Util.toUuid(info.getPlayerId().toByteArray()); //玩家id
		int pricePerMs = 0;
		int abilitySum = 0;
		int count = 0;
		//所有的推广公司
		Collection<Building> allBuilding = City.instance().getAllBuilding();
		for (Building fcySeller : allBuilding) {
			if (fcySeller == null || fcySeller.type() != MetaBuilding.PUBLIC) {
				return;
			}
			PublicFacility publicFacility = (PublicFacility) fcySeller;
			pricePerMs += publicFacility.getCurPromPricePerMs();
			abilitySum += publicFacility.getAllPromoTypeAbility(typeId);
			count++;
		}
		// 1.推广均价 毫秒总价/全城推广公司数量
		int avgPrice = pricePerMs / count;
		// 2. 均推广能力值
		int avgAbility = abilitySum / count;
		Building building = City.instance().getBuilding(buildingId);
		if (building == null || building.type() != MetaBuilding.PUBLIC || !building.outOfBusiness()) {
			return;
		}
		PublicFacility owner = (PublicFacility) building;
		// 3.员工总数
		int workerNum = owner.getWorkerNum();
		//4. 推广公司建筑工资
		double industrySalary = City.instance().getIndustrySalary(building.type());
		Gs.PromotionRecommendPrice.Builder builder = Gs.PromotionRecommendPrice.newBuilder();
		builder.setAvgPrice(avgPrice).setAvgAbility(avgAbility).setWorkerNum(workerNum).setSalary(industrySalary);
		this.write(Package.create(cmd, builder.build()));
	}
	// 研究所推荐定价
	public void queryLaboratoryRecommendPrice(short cmd, Message message) {
		Gs.LaboratoryMsg msg = (Gs.LaboratoryMsg) message;
		int typeId = msg.getTypeId(); // 研究商品 或者 发明点数
		Gs.QueryBuildingInfo info = msg.getInfo();
		UUID buildingId = Util.toUuid(info.getBuildingId().toByteArray()); //建筑id
		UUID playerId = Util.toUuid(info.getPlayerId().toByteArray()); //玩家id
		Building building = City.instance().getBuilding(buildingId);
		if (building == null || building.type() != MetaBuilding.LAB || !building.outOfBusiness()) {
			return;
		}
		Laboratory lab = (Laboratory) building;
        //1.推荐定价   研究推荐定价 > 发明推荐定价 ? 研究推荐定价 : 发明推荐定价
        int guidePriceA = GlobalUtil.getLabRecommendPrice(typeId, Gs.Eva.Btype.InventionUpgrade.getNumber(), lab.getGoodProb());
        int guidePriceB = GlobalUtil.getLabRecommendPrice(typeId, Gs.Eva.Btype.EvaUpgrade.getNumber(), lab.getEvaProb());
        //2.定价 当前研究所定价
		int price = lab.getPricePreTime();
		//3.工人总数
		int workerNum = lab.getWorkerNum();
		//4.建筑工资(行业工资)
		double industrySalary = City.instance().getIndustrySalary(building.type());
		Gs.LaboratoryRecommendPrice.Builder builder = Gs.LaboratoryRecommendPrice.newBuilder();
        builder.setGuidePrice(guidePriceA > guidePriceB ? guidePriceA : guidePriceB).setPrice(price).setWorkerNum(workerNum).setSalary(industrySalary);
		this.write(Package.create(cmd, builder.build()));

	}

	//加工厂商品推荐价格
	public void queryProduceDepRecommendPrice(short cmd, Message message) {
		Gs.ProduceDepMsg msg = (Gs.ProduceDepMsg) message;
		int itemId = msg.getItemId();
		Gs.QueryBuildingInfo info = msg.getInfo();
		UUID buildingId = Util.toUuid(info.getBuildingId().toByteArray()); //建筑id
		UUID playerId = Util.toUuid(info.getPlayerId().toByteArray()); //玩家id
		Building building = City.instance().getBuilding(buildingId);
		if (building == null || building.type() != MetaBuilding.PRODUCE || !building.outOfBusiness()) {
			return;
		}
		//推荐定价 guidePrice   全城商品销售均价 * (玩家知名度权重 + 玩家品质权重) / (全城知名度权重 + 全城品质权重)
		//加工厂成本 cost   配方原料成本(单个建筑购买) + 1 / 商品生产速度(秒产个) * 建筑工资(行业工资
		//竞争力 comp   推荐定价 / 玩家定价 * 100 (向上取整)

		Gs.ProduceDepRecommendPrice.Builder builder = Gs.ProduceDepRecommendPrice.newBuilder();
		this.write(Package.create(cmd, builder.build()));
	}

	//零售店推荐价格
	public void queryRetailShopRecommendPrice(short cmd, Message message) {
		Gs.RetailShopMsg msg = (Gs.RetailShopMsg) message;
		int itemId = msg.getItemId();  // 商品id
		UUID buildingId = Util.toUuid(msg.getInfo().getBuildingId().toByteArray());
		UUID playerId = Util.toUuid(msg.getInfo().getPlayerId().toByteArray()); //暂时不用
		Gs.RetailShopRecommendPrice.Builder builder = Gs.RetailShopRecommendPrice.newBuilder();
		Building building = City.instance().getBuilding(buildingId);

		this.write(Package.create(cmd,builder.build()));
	}



	//查询城市主页
	public void queryCityIndex(short cmd){
		Gs.QueryCityIndex.Builder builder = Gs.QueryCityIndex.newBuilder();
		Map<Integer, Integer> npcMap = NpcManager.instance().countNpcByType();
		//1.城市信息(名称)
		MetaCity city = MetaData.getCity();
		builder.setCityName(city.name);
		//2.人口信息
		Gs.QueryCityIndex.HumanInfo.Builder humanInfo = Gs.QueryCityIndex.HumanInfo.newBuilder();
		Map<String, Integer> genderSex = CityUtil.genderSex(GameDb.getAllPlayer());
		long socialNum =NpcManager.instance().getUnEmployeeNpcCount();//失业人员（社会福利人员）
		long npcNum = NpcManager.instance().getNpcCount()+socialNum;//所有npc数量
		humanInfo.setBoy(genderSex.get("boy"));
		humanInfo.setGirl(genderSex.get("girl"));
		humanInfo.setCitizens(npcNum);
		builder.setSexNum(humanInfo);
		//3.设置城市摘要信息
		Gs.QueryCityIndex.CitySummary.Builder citySummary = Gs.QueryCityIndex.CitySummary.newBuilder();
		//土地拍卖信息
		int groundSum = 0;
		for (Map.Entry<Integer, MetaGroundAuction> mg : MetaData.getGroundAuction().entrySet()) {
			MetaGroundAuction value = mg.getValue();
			groundSum+=value.area.size();
		}
		int auctionNum = GameDb.countGroundInfo();
		citySummary.setTotalNum(groundSum).setAuctionNum(auctionNum);
		//4.设置城市运费
		citySummary.setTransferCharge(MetaData.getSysPara().transferChargeRatio);
		//5.设置平均工资
		citySummary.setAvgSalary((long) City.instance().getAvgIndustrySalary());//平均工资
		citySummary.setUnEmployedNum(socialNum);//失业人员
		citySummary.setEmployeeNum(npcNum - socialNum);//在职人员
		citySummary.setUnEmployedPercent((int)Math.ceil((double)socialNum/npcNum*100));//失业率(数量/总数*100)
		//平均资产（区分福利npc）
		Gs.QueryCityIndex.CitySummary.AvgProperty.Builder avgProperty = Gs.QueryCityIndex.CitySummary.AvgProperty.newBuilder();
		Map<Integer, Long> moneyMap = CityUtil.cityAvgProperty();//城市平均资产
		avgProperty.setSocialMoney(moneyMap.get(1));
		avgProperty.setEmployeeMoney(moneyMap.get(0));
		citySummary.setAvgProperty(avgProperty);
		builder.setSummary(citySummary);
		//工资涨幅（指的是npc购买不起商品，然后商品和npc金钱的差价累加，/行业人数 就是下次增长的幅度）
		double v = CityUtil.increaseRatio();
		builder.setSalaryIncre(v);
		//8.市民保障福利（不工作npc的待遇）
		int socialMoney = CityUtil.socialMoney();
		builder.setSocialWelfare(socialMoney);
		//9.税收
		long tax = CityUtil.getTax();
		builder.setTax(tax);
		//10.城市资金（奖金池）
		builder.setMoneyPool(MoneyPool.instance().money());
		this.write(Package.create(cmd,builder.build()));
	}

	//修改建筑名称
	public void updateBuildingName(short cmd, Message message)
	{
		Gs.UpdateBuildingName msg = (Gs.UpdateBuildingName) message;
		UUID bid = Util.toUuid(msg.getBuildingId().toByteArray());
		Building building = City.instance().getBuilding(bid);
		//设置建筑的修改时间（7天改一次）
		if(building.canBeModify()) {
			building.setName(msg.getName());
			building.setLast_modify_time(new Date().getTime());
			GameDb.saveOrUpdate(building);
			this.write(Package.create(cmd, building.toProto()));
		}else{
			this.write(Package.fail(cmd,Common.Fail.Reason.accountInFreeze));
		}
    }
    //查询原料厂信息
    public void queryMaterialInfo(short cmd, Message message)
    {
    	Gs.QueryBuildingInfo msg = (Gs.QueryBuildingInfo) message;
    	UUID buildingId = Util.toUuid(msg.getBuildingId().toByteArray());
    	UUID playerId = Util.toUuid(msg.getPlayerId().toByteArray());
    	Building building = City.instance().getBuilding(buildingId);

    	Gs.MaterialInfo.Builder builder=Gs.MaterialInfo.newBuilder();
		builder.setSalary(building.salaryRatio);
		builder.setStaffNum(building.getWorkerNum());
		//建筑基本信息
		Gs.BuildingGeneral.Builder buildingInfo = buildingToBuildingGeneral(building);
		builder.setBuildingInfo(buildingInfo);
    	MetaData.getBuildingTech(MetaBuilding.MATERIAL).forEach(itemId->{
    		Gs.MaterialInfo.Material.Builder b=builder.addMaterialBuilder();
    		MetaMaterial material=MetaData.getMaterial(itemId);
        	Eva e=EvaManager.getInstance().getEva(playerId, itemId, Gs.Eva.Btype.ProduceSpeed.getNumber());
        	b.setItemId(itemId);
        	b.setIsUsed(material.useDirectly);
        	b.setNumOneSec(material.n);
        	b.setEva(e!=null?e.toProto():null);
    	});
    	this.write(Package.create(cmd, builder.build()));
    }
    //查询加工厂信息
    public void queryProduceDepInfo(short cmd,Message message){
    	Gs.QueryBuildingInfo msg = (Gs.QueryBuildingInfo) message;
    	UUID buildingId = Util.toUuid(msg.getBuildingId().toByteArray());
    	UUID playerId = Util.toUuid(msg.getPlayerId().toByteArray());
    	Building building = City.instance().getBuilding(buildingId);

		Gs.ProduceDepInfo.Builder builder = Gs.ProduceDepInfo.newBuilder();
    	builder.setSalary(building.salaryRatio);
    	builder.setStaffNum(building.getWorkerNum());
		//建筑基本信息
		Gs.BuildingGeneral.Builder buildingInfo = buildingToBuildingGeneral(building);
		builder.setBuildingInfo(buildingInfo);builder.setBuildingInfo(buildingInfo);
    	double totalBrand=0;
    	double totalQuality=0;
    	Set<Integer> set=MetaData.getBuildingTech(MetaBuilding.PRODUCE);
    	for (Integer itemId : set) {
    		Eva SpeedEva=EvaManager.getInstance().getEva(playerId, itemId,Gs.Eva.Btype.ProduceSpeed_VALUE);
    	   	Eva brandEva=EvaManager.getInstance().getEva(playerId, itemId,Gs.Eva.Btype.Brand_VALUE);
        	Eva qualityEva=EvaManager.getInstance().getEva(playerId, itemId,Gs.Eva.Btype.Quality_VALUE);
    		Gs.ProduceDepInfo.Goods.Builder b=builder.addGdsBuilder();
    		MetaGood goods=MetaData.getGood(itemId);
        	b.setItemId(itemId);
        	b.setIsUsed(goods.useDirectly);
        	b.setNumOneSec(goods.n);
        	b.setBrand(goods.brand);
        	b.setQuality(goods.quality);
          	b.setAddNumOneSec(EvaManager.getInstance().computePercent(SpeedEva));
        	b.setAddBrand(EvaManager.getInstance().computePercent(brandEva));
        	b.setAddQuality(EvaManager.getInstance().computePercent(qualityEva));
        	totalBrand+=b.getBrand()*(1+b.getAddBrand());
        	totalQuality+=b.getQuality()*(1+b.getAddQuality());
		}
    	builder.setTotalBrand(totalBrand);
    	builder.setTotalQuality(totalQuality);
    	this.write(Package.create(cmd, builder.build()));
    }
	//查询零售店或住宅信息
    public void queryRetailShopOrApartmentInfo(short cmd,Message message){
    	Gs.QueryBuildingInfo msg = (Gs.QueryBuildingInfo) message;
    	UUID buildingId = Util.toUuid(msg.getBuildingId().toByteArray());
    	UUID playerId = Util.toUuid(msg.getPlayerId().toByteArray());
    	Building building = City.instance().getBuilding(buildingId);
		if (building == null) {
			return;
		}
    	Gs.RetailShopOrApartmentInfo.Builder builder=Gs.RetailShopOrApartmentInfo.newBuilder();
    	builder.setSalary(building.salaryRatio);
    	builder.setStaffNum(building.getWorkerNum());
		//建筑基本信息
		Gs.BuildingGeneral.Builder buildingInfo = buildingToBuildingGeneral(building);
		builder.setBuildingInfo(buildingInfo);

	  	Map<Integer,Double> brandMap=new HashMap<Integer,Double>();
    	Map<Integer,Double> qtyMap=new HashMap<Integer,Double>();
	   	//单个建筑的值
    	BrandManager.instance().getBuildingBrandOrQuality(building, brandMap, qtyMap);
       	double basicBrand=BrandManager.instance().getValFromMap(brandMap, Gs.ScoreType.BasicBrand_VALUE);
       	double addBrand=BrandManager.instance().getValFromMap(brandMap, Gs.ScoreType.AddBrand_VALUE);
    	double basicQuality=BrandManager.instance().getValFromMap(qtyMap, Gs.ScoreType.BasicQuality_VALUE);
    	double addQuality=BrandManager.instance().getValFromMap(qtyMap, Gs.ScoreType.AddQuality_VALUE);
    	brandMap.clear();
    	qtyMap.clear();
    	//所有建筑的值
      	Map<Integer,Map<Integer,Double>> map=BrandManager.instance().getTotalBrandQualityMap();
    	brandMap=map.get(Gs.Eva.Btype.Brand_VALUE);
    	qtyMap=map.get(Gs.Eva.Btype.Quality_VALUE);
    	double totalBrand=BrandManager.instance().getValFromMap(brandMap,building.type());
    	double totalQuality=BrandManager.instance().getValFromMap(qtyMap,building.type());

    	builder.addScore(Gs.RetailShopOrApartmentInfo.Score.newBuilder().setType(Gs.ScoreType.BasicBrand).setVal(basicBrand).build());
    	builder.addScore(Gs.RetailShopOrApartmentInfo.Score.newBuilder().setType(Gs.ScoreType.AddBrand).setVal(addBrand).build());
    	builder.addScore(Gs.RetailShopOrApartmentInfo.Score.newBuilder().setType(Gs.ScoreType.TotalBrand).setVal(totalBrand).build());
    	builder.addScore(Gs.RetailShopOrApartmentInfo.Score.newBuilder().setType(Gs.ScoreType.BasicQuality).setVal(basicQuality).build());
    	builder.addScore(Gs.RetailShopOrApartmentInfo.Score.newBuilder().setType(Gs.ScoreType.AddQuality).setVal(addQuality).build());
    	builder.addScore(Gs.RetailShopOrApartmentInfo.Score.newBuilder().setType(Gs.ScoreType.TotalQuality).setVal(totalQuality).build());
    	this.write(Package.create(cmd, builder.build()));
    }
	//推广公司信息(修改版)
	public void queryPromotionCompanyInfo(short cmd,Message message){
		Gs.QueryBuildingInfo msg = (Gs.QueryBuildingInfo) message;
		UUID buildingId = Util.toUuid(msg.getBuildingId().toByteArray());
		UUID playerId = Util.toUuid(msg.getPlayerId().toByteArray());
		Building building = City.instance().getBuilding(buildingId);

		//检查是否是推广公司
		Building sellerBuilding = City.instance().getBuilding(buildingId);
		if(sellerBuilding == null || sellerBuilding.outOfBusiness() || sellerBuilding.type() != MetaBuilding.PUBLIC){
			if(GlobalConfig.DEBUGLOG){
				GlobalConfig.cityError("GameSession.queryPromotionCompanyInfo: building type of seller is not PublicFacility!");
			}
			return;
		}
		PublicFacility fcySeller = (PublicFacility) building ;
		Gs.PromotionCompanyInfo.Builder builder=Gs.PromotionCompanyInfo.newBuilder();
		builder.setSalary(building.salaryRatio);
		builder.setStaffNum(building.getWorkerNum());
		builder.setBaseAbility(fcySeller.getBaseAbility());
		//建筑基本信息
		Gs.BuildingGeneral.Builder buildingInfo = buildingToBuildingGeneral(building);
		builder.setBuildingInfo(buildingInfo);
		Set<Integer> buildingTech = MetaData.getBuildingTech(MetaBuilding.PUBLIC);
		buildingTech.forEach(type->{
			Gs.PromotionCompanyInfo.PromoAbility.Builder b=builder.addAbilitysBuilder();
			Integer value = (int)fcySeller.getAllPromoTypeAbility(type);//推广能力加成需要由eva来获取
			Eva promotionEva = EvaManager.getInstance().getEva(playerId, type, Gs.Eva.Btype.PromotionAbility_VALUE);
			b.setAddAbility(EvaManager.getInstance().computePercent(promotionEva))//基础推广能力加成
					.setTypeId(type)
					.setAbility(value);//推广能力值（单项推广能力，也就是的总能力）
		});
		this.write(Package.create(cmd, builder.build()));
	}

	//查询仓库信息
    public void queryWarehouseInfo(short cmd,Message message){
    	Gs.QueryBuildingInfo msg = (Gs.QueryBuildingInfo) message;
    	UUID buildingId = Util.toUuid(msg.getBuildingId().toByteArray());
    	UUID playerId = Util.toUuid(msg.getPlayerId().toByteArray());
    	Building building = City.instance().getBuilding(buildingId);
    	WareHouse wareHouse = (WareHouse) building ;

    	Gs.WarehouseInfo.Builder builder=Gs.WarehouseInfo.newBuilder();
      	builder.setSalary(wareHouse.salaryRatio);
    	builder.setStaffNum(wareHouse.getWorkerNum());
    	int basicCapacity=wareHouse.metaWarehouse.storeCapacity;
    	builder.setBasicCapacity(basicCapacity);
    	Eva eva=EvaManager.getInstance().getEva(playerId, MetaBuilding.WAREHOUSE, Gs.Eva.Btype.WarehouseUpgrade_VALUE);
    	builder.setCurCapacity((int) (basicCapacity*(1+EvaManager.getInstance().computePercent(eva))));
		//建筑基本信息
		Gs.BuildingGeneral.Builder buildingInfo = buildingToBuildingGeneral(building);
		builder.setBuildingInfo(buildingInfo);
    	this.write(Package.create(cmd, builder.build()));
    }
	//查询研究所信息
    public void queryLaboratoryInfo(short cmd,Message message){
		Gs.QueryBuildingInfo msg = (Gs.QueryBuildingInfo) message;
		UUID buildingId = Util.toUuid(msg.getBuildingId().toByteArray());
		UUID playerId = Util.toUuid(msg.getPlayerId().toByteArray());
		Building building = City.instance().getBuilding(buildingId);
		Laboratory lab = (Laboratory) building ;

		Gs.LaboratoryInfo.Builder builder=Gs.LaboratoryInfo.newBuilder();
		builder.setSalary(lab.salaryRatio);
		builder.setStaffNum(lab.getWorkerNum());
		builder.setEvaProb(lab.getEvaProb());//已经乘以员工人数和薪资
		builder.setGoodProb(lab.getGoodProb());
		//建筑基本信息
		Gs.BuildingGeneral.Builder buildingInfo = buildingToBuildingGeneral(building);
		builder.setBuildingInfo(buildingInfo);
		Set<Integer> buildingTech = MetaData.getBuildingTech(MetaBuilding.LAB);
		//现在可以得到研究所的所有at和研究所的所有bt，
		buildingTech.forEach(item->{
			Gs.LaboratoryInfo.LabAbility.Builder b=builder.addAbilitysBuilder();
			//因为研究所一个atype只对应一个eva，所以获取第一个。
			Eva eva = EvaManager.getInstance().getEva(playerId, item).get(0);
			b.setTypeId(eva.getBt());
			b.setAbility(EvaManager.getInstance().computePercent(eva));
		});
		this.write(Package.create(cmd, builder.build()));
	}
    /*查询品牌信息*/
	public void queryBrand(short cmd, Message message) {
		Gs.queryBrand brand = (Gs.queryBrand) message;
		UUID pid = Util.toUuid(brand.getPId().toByteArray());
		int typeId = brand.getTypeId();
		BrandManager.BrandInfo info = BrandManager.instance().getBrand(pid, typeId);
		Gs.BrandInfo.Builder band = Gs.BrandInfo.newBuilder();
		band.setItemId(typeId).setPId(brand.getPId());
		if (info.hasBrandName()) {
			band.setBrandName(info.getBrandName());
		} else
			// 现在暂时使用公司名称
			band.setBrandName(GameDb.getPlayer(pid).getCompanyName());
		this.write(Package.create(cmd, band.build()));
	}

	public void ct_createUser(short cmd,Message message){
		ccapi.dddbind.Dddbind.ct_createUser msg = (ccapi.dddbind.Dddbind.ct_createUser) message;
		UUID playerId = Util.toUuid(msg.getPlayerId().toByteArray());
		ccapi.cc.CcOuterClass.CreateUserReq req = msg.getCreateUserReq();
		try {
			chainClient.instance().CreateUser(req);
		}  catch (Exception e) {
			return ;
		}
		this.write(Package.create(cmd, msg));
		int t = 0 ;
	}

	public void ct_GenerateOrderReq(short cmd,Message message){
		ccapi.dddbind.Dddbind.ct_GenerateOrderReq msg = (ccapi.dddbind.Dddbind.ct_GenerateOrderReq) message;
		UUID playerId = Util.toUuid(msg.getPlayerId().toByteArray());
		this.write(Package.create(cmd, msg.toBuilder().setPurchaseId(UUID.randomUUID().toString()).build()));
		int t = 0 ;
	}
	public void ct_RechargeRequestReq(short cmd,Message message){
		ccapi.dddbind.Dddbind.ct_RechargeRequestReq msg = (ccapi.dddbind.Dddbind.ct_RechargeRequestReq ) message;
		UUID playerId = Util.toUuid(msg.getPlayerId().toByteArray());
		ccapi.cc.CcOuterClass.RechargeRequestReq req = msg.getRechargeRequestReq();
		String privateKeyStr = "1368816272920190601123456";
		String key = "123456";
		String pubStr = Bouncycastle_Secp256k1.GetPublicKeyFromPrivateKey(privateKeyStr);
		if(pubStr.equals(req.getPubKey())){
			int a = 0 ;
		}
		try {
			String data = "Hello motal";
			String signature =  Bouncycastle_Secp256k1.sig_s(data.getBytes(),privateKeyStr.getBytes(),key.getBytes());
			chainClient.instance().RechargeRequestReq(req.toBuilder().setSignature(ByteString.copyFrom(signature.getBytes())).build());
		}  catch (Exception e) {
			return ;
		}
		//this.write(Package.create(cmd, msg));
		int t = 0 ;
	}

    //获取建筑的通用信息（抽取，yty）
    public Gs.BuildingGeneral.Builder buildingToBuildingGeneral(Building building){
		if(building!=null){
			Gs.BuildingGeneral.Builder buildingInfo = Gs.BuildingGeneral.newBuilder();
			BuildingInfo info = building.toProto();
			buildingInfo.setBid(info.getId());
			buildingInfo.setCreateTime(info.getConstructCompleteTs());
			buildingInfo.setMid(info.getMId());
			buildingInfo.setName(info.getName());
			buildingInfo.setState(info.getState());
			return buildingInfo;
		}else {
			return null;
		}
	}

}
