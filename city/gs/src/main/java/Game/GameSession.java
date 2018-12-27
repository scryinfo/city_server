package Game;

import Game.Exceptions.GroundAlreadySoldException;
import Game.FriendManager.*;
import Shared.*;
import Shared.Package;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import common.Common;
import gs.Gs;
import gscode.GsCode;
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
	public void logout(boolean isEvict){
		if(!this.roleLogin()){
			return;
		}
		if (isEvict)
		{
			GroundAuction.instance().unregist(this.channelId);
			player.offline();
			GameDb.evict(player);
			//City.instance().execute(()->GameDb.evict(player));
			GameServer.allGameSessions.remove(id());
			//GameDb.saveOrUpdate(player); // unnecessary in this game, and can not do this, due to current thread is not city thread
			//offline action of validate
			Validator.getInstance().unRegist(accountName, token);
			logger.debug("account: " + player.getAccount() + " logout");

			//Notify friends
			FriendManager.getInstance().broadcastStatue(player.id(),false);
		}
		//re-login
		else
		{
			GroundAuction.instance().regist(this.channelId);
			GameServer.allGameSessions.get(id()).disconnect();
			logger.debug("account: " + player.getAccount() + " logout by re-login");
		}
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
							logger.fatal(Throwables.getStackTraceAsString(e));
						else
							this.close();
					}
				}
				else {
					try {
						m.invoke(this, cmd, message);
					} catch (IllegalArgumentException e) {
						if(GlobalConfig.debug())
							logger.fatal(Throwables.getStackTraceAsString(e));
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
                    GroundManager.instance().addGround(id(), cp.toCoordinates());
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
		loginState = LoginState.ROLE_LOGIN;
		if (GameServer.allGameSessions.containsKey(roleId))
		{
			logout(false);
		}
		GameServer.allGameSessions.put(player.id(), this);

		player.setCity(City.instance()); // toProto will use Player.city
		logger.debug("account: " + this.accountName + " login");

		this.write(Package.create(cmd, player.toProto()));

		City.instance().add(player); // will send UnitCreate
		player.online();
		sendSocialInfo();
	}

	public void createRole(short cmd, Message message) {
		Gs.CreateRole c = (Gs.CreateRole)message;
		Player p = new Player(c.getName(), this.accountName, c.getMale(), c.getCompanyName());
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
		}
	}

	public void move(short cmd, Message message) {
		Gs.GridIndex c = (Gs.GridIndex)message;
		logger.debug("move " + c.getX() + " " + c.getY());
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
			this.write(Package.create(cmd, c));
	}
	public void registGroundBidInform(short cmd) {
		GroundAuction.instance().regist(this.channelId);
	}
	public void unregistGroundBidInform(short cmd) {
		GroundAuction.instance().unregist(this.channelId);
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
			this.write(Package.create(cmd));
	}
	public void shutdownBusiness(short cmd, Message message) {
		Gs.Id c = (Gs.Id)message;
		UUID id = Util.toUuid(c.getId().toByteArray());
		Building b = City.instance().getBuilding(id);
		if(b == null || !b.ownerId().equals(player.id()))
			return;
		b.shutdownBusiness(player);
		this.write(Package.create(cmd));
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
		Coordinate ul = new Coordinate(c.getPos());
		if(!GroundManager.instance().canBuild(player.id(), m.area(ul)))
			return;
		Building building = Building.create(mid, ul, player.id());
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
		Gs.Shelf.Content proto = s.addshelf(item, c.getPrice());
		if(proto != null) {
			GameDb.saveOrUpdate(s);
			this.write(Package.create(cmd, proto));
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
		if(s.delshelf(item.key, item.n, true))
			this.write(Package.create(cmd, c));
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
		IShelf shelf = (IShelf)building;
		if(shelf.setPrice(item.key, c.getPrice()))
			this.write(Package.create(cmd, c));
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
		if(player.money() < cost)
			return;

		// begin do modify
		if(!buyStore.reserve(itemBuy.key.meta, itemBuy.n))
			return;
		Player seller = GameDb.queryPlayer(sellBuilding.ownerId());
		seller.addMoney(cost);
		player.decMoney(cost);
		LogDb.incomeInShelf(seller.id(),seller.id(),seller.money(),itemBuy.n,c.getPrice(),itemBuy.key.producerId);
		LogDb.buyInShelf(player.id(),seller.id(),player.money(),itemBuy.n,c.getPrice(),itemBuy.key.producerId);
		sellShelf.delshelf(itemBuy.key, itemBuy.n, false);
		((IStorage)sellBuilding).consumeLock(itemBuy.key, itemBuy.n);

		buyStore.consumeReserve(itemBuy.key, itemBuy.n, c.getPrice());

		GameDb.saveOrUpdate(Arrays.asList(player, seller, buyStore, sellBuilding));
		this.write(Package.create(cmd, c));
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
	public void detailApartment(short cmd, Message message) {
		Gs.Id c = (Gs.Id) message;
		UUID id = Util.toUuid(c.getId().toByteArray());
		Building b = City.instance().getBuilding(id);
		if(b == null || b.type() != MetaBuilding.APARTMENT)
			return;
		if(b.canUseBy(player.id()))
			b.watchDetailInfo(this);
		this.write(Package.create(cmd, b.detailProto()));
	}
	public void detailMaterialFactory(short cmd, Message message) {
		Gs.Id c = (Gs.Id) message;
		UUID id = Util.toUuid(c.getId().toByteArray());
		Building b = City.instance().getBuilding(id);
		if(b == null || b.type() != MetaBuilding.MATERIAL)
			return;
		if(b.canUseBy(player.id()))
			b.watchDetailInfo(this);
		this.write(Package.create(cmd, b.detailProto()));
	}
    public void detailProduceDepartment(short cmd, Message message) {
        Gs.Id c = (Gs.Id) message;
        UUID id = Util.toUuid(c.getId().toByteArray());
        Building b = City.instance().getBuilding(id);
        if(b == null || b.type() != MetaBuilding.PRODUCE)
            return;
		if(b.canUseBy(player.id()))
			b.watchDetailInfo(this);
        this.write(Package.create(cmd, b.detailProto()));
    }
	public void detailPublicFacility(short cmd, Message message) {
		Gs.Id c = (Gs.Id) message;
		UUID id = Util.toUuid(c.getId().toByteArray());
		Building b = City.instance().getBuilding(id);
		if(b == null || b.type() != MetaBuilding.PUBLIC)
			return;
		if(b.canUseBy(player.id()))
			b.watchDetailInfo(this);
		this.write(Package.create(cmd, b.detailProto()));
	}
    public void detailLaboratory(short cmd, Message message) {
        Gs.Id c = (Gs.Id) message;
        UUID id = Util.toUuid(c.getId().toByteArray());
        Building b = City.instance().getBuilding(id);
        if(b == null || b.type() != MetaBuilding.LAB)
            return;
		if(b.canUseBy(player.id()))
			b.watchDetailInfo(this);
        this.write(Package.create(cmd, b.detailProto()));
    }
    public void detailRetailShop(short cmd, Message message) {
        Gs.Id c = (Gs.Id) message;
        UUID id = Util.toUuid(c.getId().toByteArray());
        Building b = City.instance().getBuilding(id);
        if(b == null || b.type() != MetaBuilding.RETAIL)
            return;
		if(b.canUseBy(player.id()))
			b.watchDetailInfo(this);
        this.write(Package.create(cmd, b.detailProto()));
    }

    public void setSalaryRatio(short cmd, Message message) {
		Gs.ByteNum c = (Gs.ByteNum) message;
		UUID id = Util.toUuid(c.getId().toByteArray());
		Building b = City.instance().getBuilding(id);
		if(b == null || c.getNum() < 40 || c.getNum() > 100)
			return;
		b.setSalaryRatio(c.getNum());
		this.write(Package.create(cmd, c));
	}
	public void setRent(short cmd, Message message) {
		Gs.ByteNum c = (Gs.ByteNum) message;
		UUID id = Util.toUuid(c.getId().toByteArray());
		Building b = City.instance().getBuilding(id);
		if(b == null || b.outOfBusiness() || b.type() != MetaBuilding.APARTMENT || !b.ownerId().equals(player.id()))
			return;
		Apartment a = (Apartment)b;
		a.setRent(c.getNum());
		this.write(Package.create(cmd, c));
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
		LineBase line = f.addLine(m, c.getWorkerNum(), c.getTargetNum(), player.getGoodLevel(m.id));
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
			this.write(Package.create(cmd, c));
		}
	}
	public void ftyChangeLine(short cmd, Message message) {
		Gs.ChangeLine c = (Gs.ChangeLine) message;
		UUID id = Util.toUuid(c.getBuildingId().toByteArray());
		Building b = City.instance().getBuilding(id);
		if (b == null || b.outOfBusiness() || (b.type() != MetaBuilding.PRODUCE && b.type() != MetaBuilding.MATERIAL) || !b.ownerId().equals(player.id()))
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
			if(!MetaBuilding.canAd(MetaBuilding.type(c.getMetaId())))
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
			MetaBuilding m = MetaData.getBuilding(c.getMetaId());
			if(m == null)
				return;
			ad = pf.addAd(sr, m);
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
		Player owner = GameDb.queryPlayer(building.ownerId());
		owner.addMoney(slot.rentPreDay);
		player.decMoney(slot.rentPreDay);
		LogDb.incomeAdSlot(owner.id(), owner.money(), bid, slotId, slot.rentPreDay);
		LogDb.buyAdSlot(player.id(), player.money(), bid, slotId, slot.rentPreDay);
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
		LogDb.payTransfer(player.id(), player.money(), charge, srcId, dstId, item.key.producerId, item.n);
		Storage.AvgPrice avg = src.consumeLock(item.key, item.n);
		dst.consumeReserve(item.key, item.n, (int) avg.avg);
		GameDb.saveOrUpdate(Arrays.asList(src, dst, player));
		this.write(Package.create(cmd, c));
	}
	public void rentOutGround(short cmd, Message message) {
		Gs.GroundRent c = (Gs.GroundRent)message;
		RentPara rentPara = new RentPara(c.getRentPreDay(), c.getDeposit(), c.getRentDaysMin(), c.getRentDaysMax());
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
		RentPara rentPara = new RentPara(c.getInfo().getRentPreDay(), c.getDays(), c.getInfo().getDeposit(), c.getInfo().getRentDaysMin(), c.getInfo().getRentDaysMax(), c.getDays());
		if(!rentPara.valid())
			return;
		List<Coordinate> coordinates = new ArrayList<>(c.getInfo().getCoordCount());
		for(Gs.MiniIndex i : c.getInfo().getCoordList()) {
			coordinates.add(new Coordinate(i));
		}
		if(player.money() < rentPara.requiredCost() * coordinates.size())
			return;
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
	public void labLineSetWorkerNum(short cmd, Message message) {
		Gs.LabSetLineWorkerNum c = (Gs.LabSetLineWorkerNum)message;
		UUID bid = Util.toUuid(c.getBuildingId().toByteArray());
		Building building = City.instance().getBuilding(bid);
		if(building == null || building.outOfBusiness() || !(building instanceof Laboratory) || !building.canUseBy(player.id()))
			return;
		UUID lineId = Util.toUuid(c.getLineId().toByteArray());
		Laboratory lab = (Laboratory)building;
		boolean ok = lab.setLineWorkerNum(lineId, c.getN());
		if(ok) {
			GameDb.saveOrUpdate(lab);
			this.write(Package.create(cmd, c));
		}
	}
	public void labLineAdd(short cmd, Message message) {
		Gs.LabAddLine c = (Gs.LabAddLine)message;
		if(c.getType() != Formula.Type.INVENT.ordinal() && c.getType() != Formula.Type.RESEARCH.ordinal())
			return;
		UUID bid = Util.toUuid(c.getBuildingId().toByteArray());
		Building building = City.instance().getBuilding(bid);
		if(building == null || building.outOfBusiness() || !(building instanceof Laboratory) || !building.canUseBy(player.id()))
			return;
		Laboratory lab = (Laboratory)building;
		Formula.Type type = Formula.Type.values()[c.getType()];
		Formula formula = null;
		if(type == Formula.Type.RESEARCH) {
			if(!player.hasItem(c.getItemId()))
				return;
			formula = MetaData.getFormula(new Formula.Key(type, c.getItemId(), player.getGoodLevel(c.getItemId())+1));
		}
		else if(type == Formula.Type.INVENT) {
			formula = MetaData.getFormula(new Formula.Key(type, c.getItemId(), 0));
		}
		if(formula == null)
			return;
		Laboratory.Line line = lab.addLine(formula, c.getWorkerNum());
		if(line != null) {
			GameDb.saveOrUpdate(lab);
		}
	}
	public void labLineDel(short cmd, Message message) {
		Gs.LabDelLine c = (Gs.LabDelLine)message;
		UUID bid = Util.toUuid(c.getBuildingId().toByteArray());
		Building building = City.instance().getBuilding(bid);
		if(building == null || building.outOfBusiness() || !(building instanceof Laboratory) || !building.canUseBy(player.id()))
			return;
		UUID lineId = Util.toUuid(c.getLineId().toByteArray());
		Laboratory lab = (Laboratory)building;
		if(lab.delLine(lineId))
			GameDb.saveOrUpdate(lab);
	}
	public void labLaunchLine(short cmd, Message message) {
		Gs.LabLaunchLine c = (Gs.LabLaunchLine) message;
		UUID bid = Util.toUuid(c.getBuildingId().toByteArray());
		Building building = City.instance().getBuilding(bid);
		if(building == null || !(building instanceof Laboratory) || !building.canUseBy(player.id()))
			return;
		UUID lineId = Util.toUuid(c.getLineId().toByteArray());
		Laboratory lab = (Laboratory)building;
		Laboratory.Line line = lab.getLine(lineId);
		if(line == null || building.outOfBusiness() || line.isComplete() || line.isRunning() || line.leftPhase() < c.getPhase() || c.getPhase() <= 0)
			return;
		Formula.Consume[] consumes = line.getConsumes();
		if(consumes == null)
			return;
		boolean enoughMaterial = true;
		for (Formula.Consume consume : consumes) {
			if(consume.m == null)
				continue;
			if(lab.getNumber(consume.m) < consume.n*c.getPhase())
				enoughMaterial = false;
		}
		if(!enoughMaterial)
			return;
		for (Formula.Consume consume : consumes) {
			if(consume.m == null)
				continue;
			lab.offset(consume.m, -consume.n*c.getPhase());
		}
		line.launch(c.getPhase());
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
		Laboratory.Line line = lab.getLine(lineId);
		if(line == null || line.isComplete())
			return;
		Laboratory.Line.UpdateResult r = line.roll();
		if(r != null) {
			if(r.phaseChange) {
				lab.broadcastLine(line);
			}
			else {
				if(r.type == Formula.Type.INVENT) {
					player.addItem(line.formula.key.targetId, 0);
					TechTradeCenter.instance().techCompleteAction(line.formula.key.targetId, 0);
					lab.delLine(line.id);
					GameDb.saveOrUpdate(Arrays.asList(lab, player, TechTradeCenter.instance()));
				}
				if(r.type == Formula.Type.RESEARCH) {
					OptionalInt lv = player.addItemLv(line.formula.key.targetId, r.v);
					assert lv.isPresent();
					TechTradeCenter.instance().techCompleteAction(line.formula.key.targetId, lv.getAsInt());
					//lab.delLine(line.id);
					GameDb.saveOrUpdate(Arrays.asList(lab, player, TechTradeCenter.instance()));
				}
			}
			this.write(Package.create(cmd, c));
		}
		else
			this.write(Package.fail(cmd));
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
		LogDb.buyTech(player.id(), sell.ownerId, player.money(), sell.price, sell.metaId);
		Player seller = GameDb.queryPlayer(sell.ownerId);
		seller.addMoney(sell.price);
		LogDb.incomeTech(seller.id(), player.id(), seller.money(), sell.price, sell.metaId);
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
			try
			{
				for (Player.Info i :
						GameDb.getPlayerInfo(ImmutableList.of(from_id)))
				{
					name = i.getName();
				}
			}
			catch (ExecutionException e)
			{
				logger.fatal("get player name failed : id=" + from_id);
				e.printStackTrace();
			}
			builder.setId(Util.toByteString(from_id))
					.setName(name)
					.setDesc(fr.getDescp());
			this.write(Package.create(GsCode.OpCode.addFriendReq_VALUE, builder.build()));
			fr.setCount(fr.getCount() + 1);
			toBeUpdate.add(fr);
		}
		GameDb.saveOrUpdateAndDelete(toBeUpdate,toBeDel);

		//push offline message
		List<OfflineMessage> lists = GameDb.getOfflineMsg(player.id());
		lists.forEach(message -> {
			ManagerCommunication.getInstance().sendMsgToPersion(this, message);
		});
		GameDb.delete(lists);

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
				.build();
	}

	private Gs.RoleInfo playerToRoleInfo(Player player)
	{
		return Gs.RoleInfo.newBuilder()
				.setId(Util.toByteString(player.id()))
				.setName(player.getName())
				.build();
	}

	public void addFriend(short cmd, Message message)
	{
		Gs.ByteStr addMsg = (Gs.ByteStr) message;
		UUID targetId = Util.toUuid(addMsg.getId().toByteArray());
		if (!FriendManager.playerFriends.getUnchecked(id()).contains(targetId))
		{
			if (GameDb.queryPlayer(targetId).getBlacklist().contains(player.id()))
			{
				//邮件通知黑名单拒绝添加
				String[] targetPlayer = {targetId.toString()};
				MailBox.instance().sendMail(Mail.MailType.ADD_FRIEND_FAIL.getMailType(),player.id(),targetPlayer);
			}
			else
			{
				GameSession gs = GameServer.allGameSessions.get(targetId);
				if (gs != null)
				{
					Gs.RequestFriend.Builder builder = Gs.RequestFriend.newBuilder();
					builder.setId(Util.toByteString(player.id()))
							.setName(player.getName())
							.setDesc(addMsg.getDesc());
					gs.write(Package.create(GsCode.OpCode.addFriendReq_VALUE, builder.build()));

				}
				List<FriendRequest> list = GameDb.getFriendRequest(player.id(), targetId);
				if (list.isEmpty())
				{
					FriendRequest friendRequest = new FriendRequest(player.id(), targetId, addMsg.getDesc());
					GameDb.saveOrUpdate(friendRequest);
				}
			}
		}
	}

	public void addFriendResult(short cmd, Message message)
	{
		Gs.ByteBool result = (Gs.ByteBool) message;
		UUID sourceId = Util.toUuid(result.getId().toByteArray());
		if (result.getB() &&
				!FriendManager.playerFriends.getUnchecked(player.id()).contains(sourceId))
		{
			FriendManager.getInstance().saveFriendship(sourceId, player.id());
			Player temp = GameDb.queryPlayer(sourceId);
			if (temp.getBlacklist().contains(player.id()))
			{
				temp.getBlacklist().remove(player.id());
				GameDb.saveOrUpdate(temp);
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
			}
		}
		else
		{
			//refuse add
			//邮件通知系统对方拒绝添加
			String[] oppositePlayer = {player.id().toString()};
			MailBox.instance().sendMail(Mail.MailType.ADD_FRIEND_REFUSED.getMailType(),sourceId,oppositePlayer);
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

	//===========================================================

	//llb========================================================

	public void getAllMails(short cmd) {
		Collection<Mail> mails = MailBox.instance().getAllMails(player.id());
		Gs.Mails.Builder builder = Gs.Mails.newBuilder();
		mails.forEach(mail -> builder.addMail(mail.toProto()));
		this.write(Package.create(cmd, builder.build()));
	}
	//===========================================================
}
