package Game;

import Game.Exceptions.GroundAlreadySoldException;
import Shared.*;
import Shared.Package;
import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import common.Common;
import gs.Gs;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.util.concurrent.ScheduledFuture;
import org.apache.log4j.Logger;
import org.bson.types.ObjectId;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ExecutionException;

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
		GameDb.evict(player);
		//City.instance().execute(()->GameDb.evict(player));

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
							e.printStackTrace();
						else
							this.close();
					}
				}
				else {
					try {
						m.invoke(this, cmd, message);
					} catch (IllegalArgumentException e) {
						if(GlobalConfig.debug())
							e.printStackTrace();
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
	public void cheat(short cmd, Message message) {
		Gs.Str c = (Gs.Str)message;
		System.out.println(c.getStr());
		Cheat cheat = _parseCheatString(c.getStr());
		if(cheat != null)
			_runCheat(cheat);
	}
	private static class Cheat {
		enum Type {
			addmoney,
			additem,
			addground
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
				if(player.getBag().reserve(mi, n))
					player.getBag().consumeReserve(mi, n);
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
                    GroundManager.instance().addGround(id(), cp.toCoordinates());
				} catch (GroundAlreadySoldException e) {
					e.printStackTrace();
				}
                GameDb.saveOrUpdate(GroundManager.instance());
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

		player.setCity(City.instance()); // toProto will use Player.city
		loginState = LoginState.ROLE_LOGIN;
		logger.debug("account: " + this.accountName + " login");

		this.write(Package.create(cmd, player.toProto()));

		City.instance().add(player); // will send UnitCreate
		player.online();
	}

	public void createRole(short cmd, Message message) {
		Gs.Str c = (Gs.Str)message;
		Player p = new Player(c.getStr(), this.accountName);
		p.addMoney(999999999);
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

//	public void addBuilding(short cmd, Message message) {
//		Gs.AddBuilding c = (Gs.AddBuilding) message;
//		int id = c.getId();
//		if(MetaBuilding.type(id) != MetaBuilding.VIRTUAL)
//			return;
//		VirtualBuilding building = (VirtualBuilding) Building.create(id, new Coordinate(c.getPos()), player.id());
//		boolean ok = City.instance().addVirtualBuilding(building);
//		if(!ok)
//			this.write(Package.fail(cmd));
//		else
//			this.write(Package.create(cmd));
//	}
//	public void construct(short cmd, Message message) {
//		Gs.Bytes c = (Gs.Bytes)message;
//		UUID id = Util.toUuid(c.toByteArray());
//		Building b = City.instance().getBuilding(id);
//		if(b == null || b.type() != MetaBuilding.VIRTUAL || !b.ownerId().equals(player.id()))
//			return;
//		VirtualBuilding a = (VirtualBuilding)b;
//		if(a.construct())
//			a.broadcastChange();
//	}
//	public void startBusiness(short cmd, Message message) {
//		Gs.Bytes c = (Gs.Bytes)message;
//		UUID id = Util.toUuid(c.toByteArray());
//		Building b = City.instance().getBuilding(id);
//		if(b == null || b.type() != MetaBuilding.VIRTUAL || !b.ownerId().equals(player.id()))
//			return;
//		VirtualBuilding a = (VirtualBuilding)b;
//		if(a.startBusiness()) {
//			City.instance().delBuilding(a);
//			Building building = Building.create(a.linkBuildingId(), a.coordinate(), player.id());
//			building.state = Gs.BuildingState.WAITING_OPEN_VALUE;
//			City.instance().addBuilding(building);
//		}
//	}
//	public void addBuilding(short cmd, Message message) {
//		Gs.AddBuilding c = (Gs.AddBuilding) message;
//		int id = c.getId();
//		if(MetaBuilding.type(id) != MetaBuilding.VIRTUAL)
//			return;
//		VirtualBuilding building = (VirtualBuilding) Building.create(id, new Coordinate(c.getPos()), player.id());
//		boolean ok = City.instance().addVirtualBuilding(building);
//		if(!ok)
//			this.write(Package.fail(cmd));
//		else
//			this.write(Package.create(cmd));
//	}
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
		infos.forEach((i)->builder.addInfo(Gs.RoleInfo.newBuilder().setId(Util.toByteString(i.id)).setName(i.name)));
		this.write(Package.create(cmd, builder.build()));
	}
	public void addBuilding(short cmd, Message message) {
		Gs.AddBuilding c = (Gs.AddBuilding) message;
		int mid = c.getId();
		if(MetaBuilding.type(mid) == MetaBuilding.TRIVIAL)
			return;
		MetaBuilding m = MetaData.getBuilding(mid);
		Coordinate ul = new Coordinate(c.getPos());
		if(!GroundManager.instance().canBuild(player.id(), m.area(ul)))
			return;
		Building building = Building.create(mid, ul, player.id());
		boolean ok = City.instance().addBuilding(building);
		if(!ok)
			this.write(Package.fail(cmd));
		else
			this.write(Package.create(cmd));
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
	public void shelfAdd(short cmd, Message message) {
		Gs.ShelfAdd c = (Gs.ShelfAdd)message;
		if(c.getNum() <= 0 || c.getPrice() <= 0)
			return;
		MetaItem mi = MetaData.getItem(c.getItemId());
		if(mi == null)
			return;
		UUID id = Util.toUuid(c.getBuildingId().toByteArray());
		Building building = City.instance().getBuilding(id);
		if(building == null || !(building instanceof IShelf) || !building.canUseBy(player.id()))
			return;
		IShelf s = (IShelf)building;
		UUID cid = s.addshelf(mi, c.getNum(), c.getPrice());
		if(cid != null)
			this.write(Package.create(cmd, Gs.Shelf.Content.newBuilder()
					.setId(Util.toByteString(cid))
					.setItemId(mi.id)
					.setNum(c.getNum())
					.setPrice(c.getPrice()).build()
			));
		else
			this.write(Package.fail(cmd));
	}
	public void shelfDel(short cmd, Message message) {
		Gs.ShelfDel c = (Gs.ShelfDel)message;
		UUID bid = Util.toUuid(c.getBuildingId().toByteArray());
		UUID cid = Util.toUuid(c.getContentId().toByteArray());
		Building building = City.instance().getBuilding(bid);
		if(building == null || !(building instanceof IShelf) || !building.canUseBy(player.id()))
			return;
		IShelf s = (IShelf)building;
		if(s.delshelf(cid))
			this.write(Package.create(cmd, c));
		else
			this.write(Package.fail(cmd));
	}
	public void shelfSet(short cmd, Message message) {
		Gs.ShelfSet c = (Gs.ShelfSet)message;
		if(c.getNum() < 0 || c.getPrice() <= 0)
			return;
		UUID bid = Util.toUuid(c.getBuildingId().toByteArray());
		UUID cid = Util.toUuid(c.getContentId().toByteArray());
		Building building = City.instance().getBuilding(bid);
		if(building == null || !(building instanceof IShelf) || !building.canUseBy(player.id()))
			return;
		IShelf shelf = (IShelf)building;
		Shelf.ItemInfo i = shelf.getContent(cid);
		if(i == null) {
			this.write(Package.fail(cmd));
			return;
		}
		if(i.n != c.getNum()) {
			if(shelf.setNum(cid, c.getNum())) {
				i.price = c.getPrice();
				this.write(Package.create(cmd, c));
			}
			else
				this.write(Package.fail(cmd));
		}
		else if(i.price != c.getPrice()) {
			i.price = c.getPrice();
			this.write(Package.create(cmd, c));
		}
		else
			this.write(Package.create(cmd, c));
	}

	public void buyInShelf(short cmd, Message message) {
		Gs.BuyInShelf c = (Gs.BuyInShelf)message;
		if(c.getNum() <= 0 || c.getPrice() <= 0)
			return;
		UUID bid = Util.toUuid(c.getBuildingId().toByteArray());
		UUID wid = Util.toUuid(c.getWareHouseId().toByteArray());
		UUID cid = Util.toUuid(c.getContentId().toByteArray());
		Building sellBuilding = City.instance().getBuilding(bid);
		if(sellBuilding == null || !(sellBuilding instanceof IShelf) || sellBuilding.canUseBy(player.id()))
			return;
        IStorage buyStore = IStorage.get(wid, player);
		if(buyStore == null)
		    return;
		IShelf sellShelf = (IShelf)sellBuilding;
		Shelf.ItemInfo i = sellShelf.getContent(cid);
		if(i == null || i.price != c.getPrice() || i.n < c.getNum()) {
			this.write(Package.fail(cmd));
			return;
		}
		long cost = c.getNum()*c.getPrice();
		if(player.money() < cost)
			return;

		// begin do modify
		if(!buyStore.reserve(i.item, c.getNum()))
			return;
		Player seller = GameDb.queryPlayer(sellBuilding.ownerId());
		seller.addMoney(cost);
		player.decMoney(cost);
		buyStore.consumeReserve(i.item, c.getNum());
		sellShelf.setNum(cid, i.n - c.getNum());

		GameDb.saveOrUpdate(Arrays.asList(player, seller, buyStore, sellBuilding));
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
		if (s == null || !s.lock(mi, c.getNum()))
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
	public void detailApartment(short cmd, Message message) {
		Gs.Id c = (Gs.Id) message;
		UUID id = Util.toUuid(c.getId().toByteArray());
		Building b = City.instance().getBuilding(id);
		if(b == null || b.type() != MetaBuilding.APARTMENT)
			return;
		b.watchDetailInfo(this);
		this.write(Package.create(cmd, b.detailProto()));
	}
	public void detailMaterialFactory(short cmd, Message message) {
		Gs.Id c = (Gs.Id) message;
		UUID id = Util.toUuid(c.getId().toByteArray());
		Building b = City.instance().getBuilding(id);
		if(b == null || b.type() != MetaBuilding.MATERIAL)
			return;
        b.watchDetailInfo(this);
		this.write(Package.create(cmd, b.detailProto()));
	}
    public void detailProduceDepartment(short cmd, Message message) {
        Gs.Id c = (Gs.Id) message;
        UUID id = Util.toUuid(c.getId().toByteArray());
        Building b = City.instance().getBuilding(id);
        if(b == null || b.type() != MetaBuilding.PRODUCE)
            return;
        b.watchDetailInfo(this);
        this.write(Package.create(cmd, b.detailProto()));
    }
    public void detailLaboratory(short cmd, Message message) {
        Gs.Id c = (Gs.Id) message;
        UUID id = Util.toUuid(c.getId().toByteArray());
        Building b = City.instance().getBuilding(id);
        if(b == null || b.type() != MetaBuilding.LAB)
            return;
        b.watchDetailInfo(this);
        this.write(Package.create(cmd, b.detailProto()));
    }
    public void detailRetailShop(short cmd, Message message) {
        Gs.Id c = (Gs.Id) message;
        UUID id = Util.toUuid(c.getId().toByteArray());
        Building b = City.instance().getBuilding(id);
        if(b == null || b.type() != MetaBuilding.RETAIL)
            return;
        b.watchDetailInfo(this);
        this.write(Package.create(cmd, b.detailProto()));
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
		if(b == null || b.type() != MetaBuilding.APARTMENT || !b.ownerId().equals(player.id()))
			return;
		Apartment a = (Apartment)b;
		a.setRent(c.getNum());
		this.write(Package.create(cmd, c));
	}
	public void addLine(short cmd, Message message) {
		Gs.AddLine c = (Gs.AddLine) message;
		UUID id = Util.toUuid(c.getId().toByteArray());
		Building b = City.instance().getBuilding(id);
		if(b == null || (b.type() != MetaBuilding.PRODUCE && b.type() != MetaBuilding.MATERIAL) || !b.ownerId().equals(player.id()))
			return;
		MetaItem m = MetaData.getItem(c.getItemId());
		if(m == null || (!m.useDirectly && !player.canProduce(m.id)))
			return;
		FactoryBase f = (FactoryBase) b;
		if (f.freeWorkerNum() < c.getWorkerNum() || f.lineFull())
			return;
		LineBase line = f.addLine(m);
		this.write(Package.create(cmd, line.toProto()));
	}

	public void changeLine(short cmd, Message message) {
		Gs.ChangeLine c = (Gs.ChangeLine) message;
		UUID id = Util.toUuid(c.getBuildingId().toByteArray());
		Building b = City.instance().getBuilding(id);
		if (b == null || (b.type() != MetaBuilding.PRODUCE && b.type() != MetaBuilding.MATERIAL) || !b.ownerId().equals(player.id()))
			return;
		ObjectId lineId = new ObjectId(c.getLineId().toByteArray());
        FactoryBase f = (FactoryBase) b;
        OptionalInt tn = c.hasTargetNum()?OptionalInt.of(c.getTargetNum()):OptionalInt.empty();
        OptionalInt wn = c.hasWorkerNum()?OptionalInt.of(c.getWorkerNum()):OptionalInt.empty();
        boolean ok = f.changeLine(lineId, tn, wn);
        if(ok)
            this.write(Package.create(cmd, c));
        else
            this.write(Package.fail(cmd));
	}
	public void adAddSlot(short cmd, Message message) {
		Gs.AddSlot c = (Gs.AddSlot)message;
		if(c.getMaxDayToRent() < c.getMinDayToRent() || c.getRentPreDay() <= 0 || c.getDeposit() <= 0)
			return;
		UUID bid = Util.toUuid(c.getBuildingId().toByteArray());
		Building building = City.instance().getBuilding(bid);
		if(building == null || !(building instanceof PublicFacility) || !building.canUseBy(player.id()))
			return;
		PublicFacility pf = (PublicFacility)building;
		PublicFacility.Slot slot = pf.addSlot(c.getMaxDayToRent(), c.getMinDayToRent(), c.getRentPreDay(), c.getDeposit());
		this.write(Package.create(cmd, slot.toProto()));
	}
	public void adDelSlot(short cmd, Message message) {
		Gs.AdDelSlot c = (Gs.AdDelSlot)message;
		UUID bid = Util.toUuid(c.getBuildingId().toByteArray());
		Building building = City.instance().getBuilding(bid);
		if(building == null || !(building instanceof PublicFacility) || !building.canUseBy(player.id()))
			return;
		UUID slotId = Util.toUuid(c.getSlotId().toByteArray());
		PublicFacility pf = (PublicFacility)building;
		if(pf.delSlot(slotId))
			this.write(Package.create(cmd, c));
		else
			this.write(Package.fail(cmd));
	}
	public void adPutAdToSlot(short cmd, Message message) {
		Gs.AddAd c = (Gs.AddAd)message;
		UUID bid = Util.toUuid(c.getBuildingId().toByteArray());
		Building building = City.instance().getBuilding(bid);
		if(building == null || !(building instanceof PublicFacility))
			return;

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
		if(c.getType() == Gs.PublicFacility.Ad.Type.GOOD) {
			MetaItem m = MetaData.getItem(c.getMetaId());
			if(m == null)
				return;
			pf.addAd(sr, m);
		}
		else if(c.getType() == Gs.PublicFacility.Ad.Type.BUILDING) {
			MetaBuilding m = MetaData.getBuilding(c.getMetaId());
			if(m == null)
				return;
			pf.addAd(sr, m);
		}
		else
			return;
		GameDb.saveOrUpdate(pf);
		this.write(Package.create(cmd));
	}

	public void adDelAdFromSlot(short cmd, Message message) {
		Gs.AdDelAdFromSlot c = (Gs.AdDelAdFromSlot)message;
		UUID bid = Util.toUuid(c.getBuildingId().toByteArray());
		Building building = City.instance().getBuilding(bid);
		if(building == null || !(building instanceof PublicFacility))
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
		if(building == null || !(building instanceof PublicFacility) || building.canUseBy(player.id()))
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
		player.decMoney(slot.rentPreDay);
		player.lockMoney(slot.id, slot.deposit);
		pf.buySlot(slotId, c.getDay(), player.id());
		GameDb.saveOrUpdate(Arrays.asList(pf, player));
		this.write(Package.create(cmd));
	}
	public void detailPublicFacility(short cmd, Message message) {
		Gs.Id c = (Gs.Id) message;
		UUID id = Util.toUuid(c.getId().toByteArray());
		Building b = City.instance().getBuilding(id);
		if(b == null || b.type() != MetaBuilding.PUBLIC)
			return;
		b.watchDetailInfo(this);
		this.write(Package.create(cmd, b.detailProto()));
	}
	public void delItem(short cmd, Message message) {
		Gs.DelItem c = (Gs.DelItem)message;
		UUID id = Util.toUuid(c.getBuildingId().toByteArray());
		IStorage storage = IStorage.get(id, player);
		if(storage == null)
			return;
		MetaItem mi = MetaData.getItem(c.getItemId());
		if(mi == null)
			return;
		if(storage.delItem(mi))
		{
			GameDb.saveOrUpdate(storage);
			this.write(Package.create(cmd, c));
		}
		else
			this.write(Package.fail(cmd));
	}
	public void transferItem(short cmd, Message message) {
		Gs.TransferItem c = (Gs.TransferItem)message;
		UUID srcId = Util.toUuid(c.getSrc().toByteArray());
		UUID dstId = Util.toUuid(c.getDst().toByteArray());
		IStorage src = IStorage.get(srcId, player);
		IStorage dst = IStorage.get(dstId, player);
		if(src == null || dst == null)
			return;
		MetaItem mi = MetaData.getItem(c.getItemId());
		if(mi == null)
			return;
		if(!src.lock(mi, c.getN())) {
			this.write(Package.fail(cmd));
			return;
		}
		if(!dst.reserve(mi, c.getN())) {
			src.unLock(mi, c.getN());
			this.write(Package.fail(cmd));
			return;
		}
		src.consumeLock(mi, c.getN());
		dst.consumeReserve(mi, c.getN());
		GameDb.saveOrUpdate(Arrays.asList(src, dst));
		this.write(Package.create(cmd, c));
	}
	public void rentOutGround(short cmd, Message message) {
		Gs.GroundRent c = (Gs.GroundRent)message;
		RentPara rentPara = new RentPara(c.getRentPreDay(), c.getPaymentCycleDays(), c.getDeposit(), c.getRentDays());
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
		Gs.GroundRent c = (Gs.GroundRent)message;
		RentPara rentPara = new RentPara(c.getRentPreDay(), c.getPaymentCycleDays(), c.getDeposit(), c.getRentDays());
		if(!rentPara.valid())
			return;
		List<Coordinate> coordinates = new ArrayList<>(c.getCoordCount());
		for(Gs.MiniIndex i : c.getCoordList()) {
			coordinates.add(new Coordinate(i));
		}
		int cost = c.getRentPreDay() * c.getPaymentCycleDays() + c.getDeposit();
		if(player.money() < cost)
			return;
		UUID transactionId = GroundManager.instance().rentGround(player, coordinates, rentPara);
		if(transactionId != null) {
			player.decMoney(c.getRentPreDay() * c.getPaymentCycleDays());
			player.lockMoney(transactionId, c.getDeposit());
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
}
