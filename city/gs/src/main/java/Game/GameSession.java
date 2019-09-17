package Game;

import Game.CityInfo.CityLevel;
import Game.CityInfo.IndustryMgr;
import Game.Contract.BuildingContract;
import Game.Contract.Contract;
import Game.Contract.ContractManager;
import Game.Contract.IBuildingContract;
import Game.Exceptions.GroundAlreadySoldException;
import Game.FriendManager.*;
import Game.Gambling.Flight;
import Game.Gambling.FlightManager;
import Game.Gambling.ThirdPartyDataSource;
import Game.League.LeagueManager;
import Game.Meta.*;
import Game.OffLineInfo.OffLineInformation;
import Game.Util.*;
import Game.ddd.*;
import Shared.*;
import Shared.Package;
import ccapi.CcOuterClass;
import ccapi.Dddbind;
import ccapi.GlobalDef;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import com.yunpian.sdk.model.Result;
import com.yunpian.sdk.model.SmsSingleSend;
import common.Common;
import gs.Gs;
import gs.Gs.BuildingInfo;
import gscode.GsCode;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.util.concurrent.ScheduledFuture;
import org.apache.log4j.Logger;
import org.bson.Document;
import org.ethereum.crypto.ECKey;
import org.spongycastle.util.encoders.Hex;

import java.lang.reflect.Method;
import java.math.BigInteger;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

class SmVerify {
    UUID playerId;
    String authCode;

    public CcOuterClass.DisChargeReq getDisChargeReq() {
        return disChargeReq;
    }

    ccapi.CcOuterClass.DisChargeReq disChargeReq;
    public Long getTs() {
        return ts;
    }

    Long    ts;
    public SmVerify(UUID pid, String code, Long ints, ccapi.CcOuterClass.DisChargeReq req){
        playerId = pid;
        authCode = code;
        ts = ints;
        disChargeReq = req;
    }

    public boolean TimeoutChecks(){
        return System.currentTimeMillis() - ts <= 60000;
    }
}

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
    //支付短信验证码缓存
    private Map<UUID, SmVerify> paySmCache = new HashMap<>();
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
        if (cheat != null && _runCheat(cheat))
        {
            this.write(Package.create(cmd, message));
        }
    }
    private static class Cheat {
        enum Type {
            addmoney,
            additem,
            addground,
            invent,
            fill_warehouse,
            payMoney
        }
        Type cmd;
        String[] paras;
    }
    private boolean _runCheat(Cheat cheat) {
        switch (cheat.cmd) {
            case fill_warehouse:
            {
                int itemId = Integer.parseInt(cheat.paras[0]);
                int count = Integer.parseInt(cheat.paras[1]);
                String buildingId = cheat.paras[2];
                MetaItem m = MetaData.getItem(itemId);
                int lv = 0;
                if(m instanceof MetaGood)
                    lv = player.getGoodLevel(m.id);

                Building building = City.instance().getBuilding(UUID.fromString(buildingId));
                IStorage iStorage = (IStorage) building;
                ItemKey itemKey = new ItemKey(m, building.ownerId(), lv, building.ownerId());
                if (!MetaGood.isItem(m.id)) {
                    itemKey = new ItemKey(m, building.ownerId());
                }
                iStorage.offset(itemKey, count);
                GameDb.saveOrUpdate(building);
                break;
            }
            case addmoney: {
                int n = Integer.valueOf(cheat.paras[0]);
                if(n <= 0)
                    return false;
                player.addMoney(n);
                LogDb.playerIncome(player.id(), n,0);
                GameDb.saveOrUpdate(player);
                break;
            }
            case payMoney:
                {
                    if (player.getAccount().equals(cheat.paras[0]))
                    {
                        String account = cheat.paras[1];
                        long amount = Long.valueOf(cheat.paras[2]);
                        Player p = GameDb.getAccount(account);
                        if (p != null)
                        {
                            p = GameDb.getPlayer(p.id());
                            long before = p.money();
                            p.addMoney(amount);
                            GameDb.saveOrUpdate(p);
                            this.write(Package.create(GsCode.OpCode.cheat_VALUE,
                                    Gs.PayStatus.newBuilder().setStatus(0)
                                            .setAccount(account)
                                            .setName(p.getName())
                                            .setBefore(before)
                                            .setAfter(p.money())
                                            .setWho(player.getAccount())
                                            .build()));
                        }
                        else
                        {
                            this.write(Package.create(GsCode.OpCode.cheat_VALUE,
                                    Gs.PayStatus.newBuilder().setStatus(2).build()));
                        }
                    }
                    else
                    {
                        this.write(Package.create(GsCode.OpCode.cheat_VALUE,
                                Gs.PayStatus.newBuilder().setStatus(1).build()));
                    }
                    return false;
                }
            case additem: {
                int id = Integer.parseInt(cheat.paras[0]);
                int n = Integer.parseInt(cheat.paras[1]);
                MetaItem mi = MetaData.getItem(id);
                if (mi == null)
                    return false;
                if (n <= 0)
                    return false;
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
                int x1 = Integer.parseInt(cheat.paras[0]);
                int y1 = Integer.parseInt(cheat.paras[1]);
                int x2 = Integer.parseInt(cheat.paras[2]);
                int y2 = Integer.parseInt(cheat.paras[3]);
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
                int mId = Integer.parseInt(cheat.paras[0]);
                int lv = Integer.parseInt(cheat.paras[1]);
                MetaItem mi = MetaData.getItem(mId);
                if(mi == null)
                    return false;
                if(mi instanceof MetaMaterial && lv != 0)
                    return false;
                if(lv < 0)
                    return false;
                player.addItem(mId, lv);
                TechTradeCenter.instance().techCompleteAction(mId, lv);
                GameDb.saveOrUpdate(Arrays.asList(player, TechTradeCenter.instance()));
                break;
            }
        }
        return true;
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

        res.paras = new String[sa.length-1];
        for(int i = 1; i < sa.length; ++i) {
            res.paras[i-1] = sa[i];
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
    private Player kickOff() {
        assert player != null;

        // logout can not rely on channelInactive, it will called by netty asynchronously, so that might called after the disconnect future complete
        this.logout(true);
        this.disconnect();
        return player;
    }
    public void logout(boolean kickOff){
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
            /*统计玩家的在线时间*/
            long loginTimes = player.getOfflineTs() - player.getOnlineTs();
            LogDb.playerLoginTime(player.id(),loginTimes,DateUtil.getTimeDayStartTime(player.getOnlineTs()));
        }
        GameServer.allGameSessions.remove(id());
        if (player.getSocietyId() != null)
        {
            //必须在allGameSessions.remove(id())之后调用
            SocietyManager.broadOffline(player);
        }
        Validator.getInstance().unRegist(accountName, token);
        this.valid = false;
    }
    public void roleLogin(short cmd, Message message) {
        // in city thread
        Gs.Id c = (Gs.Id)message;
        UUID roleId = Util.toUuid(c.getId().toByteArray());
        GameSession otherOne = GameServer.allGameSessions.get(roleId);
        if(otherOne != null) {
            this.player = otherOne.kickOff();
        }
        else {
            this.player = GameDb.getPlayer(roleId);
            if (player == null) {
                this.write(Package.fail(cmd));
                return;
            }
        }
        this.player.setSession(this);
        loginState = LoginState.ROLE_LOGIN;
        GameServer.allGameSessions.put(this.player.id(), this);

        this.player.setCity(City.instance()); // toProto will use Player.java
        logger.debug("account: " + this.accountName + " login");
        //添加矿工费用（系统参数）
        Gs.Role.Builder role = this.player.toProto().toBuilder();
        role.setMinersCostRatio(MetaData.getSysPara().minersCostRatio);
        this.write(Package.create(cmd, role.build()));
        City.instance().add(this.player); // will send UnitCreate
        this.player.online();
        if (this.player.getSocietyId() != null)
        {
            SocietyManager.broadOnline(this.player);
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
        p.addMoney(0);
        LogDb.playerIncome(p.id(), 0,0);
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
        Gs.BidGround c = (Gs.BidGround)message;
        Optional<Common.Fail.Reason> err = GroundAuction.instance().bid(c.getId(), player, c.getNum());
        if(err.isPresent()) {
            GroundAuction.Entry entry = GroundAuction.instance().getAuctions(c.getId());
            //如果是当前竞拍者并且价格相同则不发送高价竞拍者的信息
            if(entry.price()!=c.getNum()&&!(entry.biderId().equals(player))){
                Gs.BidChange.Builder builder = Gs.BidChange.newBuilder().setBiderId(Util.toByteString(entry.biderId()))
                        .setNowPrice(entry.price())
                        .setTargetId(c.getId())
                        .setTs(entry.ts());
                Package pack = Package.create(GsCode.OpCode.bidChangeInform_VALUE, builder.build());
                GameServer.sendToAll(pack);
            }
            this.write(Package.fail(cmd, err.get()));
        }
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
            LogDb.playerBuildingBusiness(player.id(),1,b.getWorkerNum(),b.type());
            this.write(Package.create(cmd,c));
            //GameDb.saveOrUpdate(b);
            GameDb.saveOrUpdate(Arrays.asList(b,player));
            if(b.type()==MetaBuilding.APARTMENT){/*更新购买住宅缓存*/
               City.instance().buildApartmentMoveKnownValue(b);
            }
        }
    }

    public void shutdownBusiness(short cmd, Message message) {
        Gs.Id c = (Gs.Id) message;
        UUID id = Util.toUuid(c.getId().toByteArray());
        Building b = City.instance().getBuilding(id);
        if (b == null || !b.ownerId().equals(player.id()))
            return;
        b.shutdownBusiness();
        b.addUnEmployeeNpc();//变成失业人员
        if (b instanceof Apartment) { //住宅停业，清空入住人数
            Apartment apartment = (Apartment) b;
            apartment.deleteRenter();
        }  else if (b instanceof PublicFacility) {
            if(b.type()==MetaBuilding.RETAIL){
                RetailShop r = (RetailShop) b;
                r.cleanData();
                /*更新npc住宅可选建筑的缓存*/
                City.instance().removeKnownApartmentMap(b);
            }
        } else if (b instanceof FactoryBase) {//有仓库和货架，以及生产线，清除
            FactoryBase f = (FactoryBase) b;
            f.cleanData();
        }
        GameDb.saveOrUpdate(b);
        this.write(Package.create(cmd, c));
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

    public void setRoleFaceId(short cmd, Message message) {
        Gs.Str c = (Gs.Str) message;
        if(c.getStr().length() > Player.MAX_FACE_ID_LEN)
            return;
        Gs.Bool.Builder result = Gs.Bool.newBuilder();
        if(this.player.decScoreValue(Player.COST_FACE_SCORE_VALUE)){
            this.player.setFaceId(c.getStr());
            GameDb.saveOrUpdate(player);
            result.setB(true);
        }else{
            result.setB(false);
        }
        this.write(Package.create(cmd,result.build()));
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
                    IShelf s = (IShelf) building;
                    if(s.getSaleCount(c.getItemId())>0){
                        Gs.MarketDetail.GridInfo.Building.Builder bb = gb.addBBuilder();
                        bb.setId(Util.toByteString(building.id()));
                        bb.setPos(building.coordinate().toProto());
                        s.getSaleDetail(c.getItemId()).forEach((k, v) -> {// 新添加的小地图竞争力
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
        Coordinate coordinate = new Coordinate(c.getPos());
        GroundInfo groundInfo = GroundManager.instance().getGroundInfo(coordinate);
        if(groundInfo.getStatus()!=GroundInfo.GroundStatus.STATELESS){
            System.err.println("你的土地正在出售！不能建造");
            return;
        }
        if(m == null)
            return;
        Coordinate ul = new Coordinate(c.getPos());
        if(!GroundManager.instance().canBuild(player.id(), m.area(ul))) {
            System.err.println("建造失败");
            return;
        }
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
            Gs.ShelfAdd.Builder builder = c.toBuilder().setItem(item.toProto());
            /*更新零售店Npc购物信息*/
            if(building.type()==MetaBuilding.RETAIL) {
                City.instance().buildRetailMoveKnownValue(building);
            }
            this.write(Package.create(cmd, builder.build()));
        }
        else {
            this.write(Package.fail(cmd, Common.Fail.Reason.numberNotEnough));
            System.err.println("数量或者货架空间不足");
        }
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
        //这里是全部下架，所以统一把自动补货关掉
        Shelf.Content delContent = s.getContent(item.key);
        delContent.autoReplenish = false;
        if(s.delshelf(item.key, delContent.n, true)) {
            GameDb.saveOrUpdate(s);
            //如果货架上还有该商品则推送，否则不推送
            Shelf.Content content = s.getContent(item.key);
            if(content!=null){
                UUID producerId=null;
                if(MetaGood.isItem(item.key.meta.id)){
                    producerId = item.key.producerId;
                }
                building.sendToWatchers(building.id(),item.key.meta.id, content.n,content.price,content.autoReplenish,producerId);
            }
            this.write(Package.create(cmd, c));
            /*更新零售店的商品信息 移除掉下架的商品*/
            if(building.type()==MetaBuilding.RETAIL) {
                City.instance().removeRetailGoodInfo(building, item.key);
            }
        }
        else{
            //this.write(Package.fail(cmd));
            this.write(Package.fail(cmd,Common.Fail.Reason.numberNotEnough));
            //this.write(Package.create(cmd, c.toBuilder().setCurCount(s.getContent(item.key).getCount()).build()));
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
            /*更新零售点商品信息*/
            if(building.type()==MetaBuilding.RETAIL) {
                City.instance().buildRetailMoveKnownValue(building);
            }
        } else {
            this.write(Package.fail(cmd, Common.Fail.Reason.numberNotEnough));
            //this.write(Package.fail(cmd));
        }
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
            //返回数量不足错误码
            this.write(Package.fail(cmd,Common.Fail.Reason.numberNotEnough));
            return;
        }
        long cost = itemBuy.n*c.getPrice();
        int freight = (int) (MetaData.getSysPara().transferChargeRatio * IStorage.distance(buyStore, (IStorage) sellBuilding));

        //TODO:矿工费用（商品基本费用*矿工费用比例）(向下取整),
        double minersRatio = MetaData.getSysPara().minersCostRatio;
        long minerCost = (long) Math.floor(cost * minersRatio);
        long income =cost-minerCost;//收入（扣除矿工费后）
        long pay=cost+minerCost;
        if(player.money() < cost + freight+minerCost) {
            this.write(Package.fail(cmd, Common.Fail.Reason.moneyNotEnough));
            return;
        }
        // begin do modify
        if(!buyStore.reserve(itemBuy.key.meta, itemBuy.n)) {
            this.write(Package.fail(cmd, Common.Fail.Reason.spaceNotEnough));
            return;
        }
        Player seller = GameDb.getPlayer(sellBuilding.ownerId());
        seller.addMoney(income);
        Gs.IncomeNotify notify = Gs.IncomeNotify.newBuilder()
                .setBuyer(Gs.IncomeNotify.Buyer.PLAYER)
                .setBuyerId(Util.toByteString(player.id()))
                .setFaceId(player.getFaceId())
                .setCost(income)
                .setType(Gs.IncomeNotify.Type.INSHELF)
                .setBid(sellBuilding.metaBuilding.id)
                .setItemId(itemBuy.key.meta.id)
                .setCount(itemBuy.n)
                .build();
        GameServer.sendIncomeNotity(seller.id(),notify);
        player.decMoney(pay);
        Building buyBuilding = City.instance().getBuilding(wid);
        LogDb.playerPay(player.id(),pay,buyBuilding.type());
        LogDb.playerIncome(seller.id(),income,sellBuilding.type());
        if(cost>=10000000){//重大交易,交易额达到1000,广播信息给客户端,包括玩家ID，交易金额，时间
            GameServer.sendToAll(Package.create(GsCode.OpCode.cityBroadcast_VALUE,Gs.CityBroadcast.newBuilder()
                    .setType(1)
                    .setSellerId(Util.toByteString(seller.id()))
                    .setBuyerId(Util.toByteString(player.id()))
                    .setCost(income)
                    .setTs(System.currentTimeMillis())
                    .build()));
            LogDb.cityBroadcast(seller.id(),player.id(),pay,0,1);
        }
        player.decMoney(freight);
        LogDb.playerPay(player.id(), freight,buyBuilding.type());

        GameServer.sendToAll(Package.create(GsCode.OpCode.makeMoneyInform_VALUE,Gs.MakeMoney.newBuilder()
                .setBuildingId(Util.toByteString(bid))
                .setMoney(income)
                .setPos(sellBuilding.toProto().getPos())
                .setItemId(itemBuy.key.meta.id)
                .build()
        ));

        int itemId = itemBuy.key.meta.id;
        int type = MetaItem.type(itemBuy.key.meta.id);
        LogDb.payTransfer(player.id(), freight, bid, wid,itemId,itemBuy.key.producerId, itemBuy.n);
        //LogDb.payTransfer(player.id(), freight, bid, wid,itemId,itemBuy.key.producerId, itemBuy.n);
        //TODO  待修改*
        /*LogDb.buyInShelf(player.id(), seller.id(), itemBuy.n, c.getPrice(),
                    itemBuy.key.producerId, sellBuilding.id(), wid, type, itemId, goodName, score, sellBuilding.type(),minerCost);*/
        LogDb.buildingIncome(bid,player.id(),income,type,itemId);//商品支出记录不包含运费
        LogDb.buildingPay(bid,player.id(),freight);//建筑运费支出
        /*离线收益，只在玩家离线期间统计*/
        if(!GameServer.isOnline(seller.id())) {
            LogDb.sellerBuildingIncome(sellBuilding.id(), sellBuilding.type(), seller.id(), itemBuy.n,i.getPrice(), itemId);//记录建筑收益详细信息
        }
        //矿工费用日志记录
        LogDb .minersCost(player.id(),minerCost,minersRatio);
        LogDb.minersCost(seller.id(),minerCost,minersRatio);
        sellShelf.delshelf(itemBuy.key, itemBuy.n, false);
        //((IStorage)sellBuilding).consumeLock(itemBuy.key, itemBuy.n); 在删除商品的时候已经消费过了，这里会造成二次消费
        sellBuilding.updateTodayIncome(income);

        buyStore.consumeReserve(itemBuy.key, itemBuy.n, c.getPrice());
        GameDb.saveOrUpdate(Arrays.asList(player, seller, buyStore, sellBuilding));
        //如果货架上已经没有该商品了，不推送，有则推送
        i = sellShelf.getContent(itemBuy.key);
        if(i!=null){
            //如果是商品则传递produceId
            UUID produceId=null;
            if(MetaGood.isItem(itemBuy.key.meta.id)){
                produceId = itemBuy.key.producerId;
            }
            sellBuilding.sendToWatchers(sellBuilding.id(),itemId,i.n,i.price,i.autoReplenish,produceId);
        }
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
        if(c.hasName() && (c.getName().length() == 0 || c.getName().length() >= 30*3))
            return;
        if(c.hasDes() && (c.getDes().length() == 0 || c.getDes().length() >= 30*3))
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
        /*if(buildingDetail.size() < MAX_DETAIL_BUILDING && building.canUseBy(player.id())) {*/
        if(buildingDetail.size() < MAX_DETAIL_BUILDING) {
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
            GameDb.saveOrUpdate(storage);
            this.write(Package.create(cmd, c));
        }
        else
            this.write(Package.fail(cmd,Common.Fail.Reason.numberNotEnough));
    }
    public void transferItem(short cmd, Message message) throws Exception {
        Gs.TransferItem c = (Gs.TransferItem)message;
        UUID srcId = Util.toUuid(c.getSrc().toByteArray());
        UUID dstId = Util.toUuid(c.getDst().toByteArray());
        IStorage src = IStorage.get(srcId, player);
        IStorage dst = IStorage.get(dstId, player);
        if(srcId.equals(dstId)){
            System.err.println("错误，运入和运出地址相同=========");
        }
        if(src == null || dst == null) {
            System.err.println("运输失败：运输地址不对");
            return;
        }
        int charge = (int) (MetaData.getSysPara().transferChargeRatio * IStorage.distance(src, dst));
        if(player.money() < charge) {
            System.err.println("运输失败：钱不够");
            this.write(Package.fail(cmd, Common.Fail.Reason.moneyNotEnough));
            return;
        }
        Item item = new Item(c.getItem());
        //如果运出的一方没有足够的存量进行锁定，那么操作失败
        if(!src.lock(item.key, item.n)) {
            System.err.println("运输失败：数量不够");
            this.write(Package.fail(cmd,Common.Fail.Reason.numberNotEnough));
            return;
        }
        //如果运入的一方没有足够的预留空间，那么操作失败
        if(!dst.reserve(item.key.meta, item.n)) {
            src.unLock(item.key, item.n);
            System.err.println("运输失败：空间不足");
            this.write(Package.fail(cmd,Common.Fail.Reason.spaceNotEnough));
            return;
        }

        player.decMoney(charge);
        Building buyBuilding = City.instance().getBuilding(dstId);
        LogDb.playerPay(player.id(), charge,buyBuilding.type());
        MoneyPool.instance().add(charge);
        LogDb.payTransfer(player.id(), charge, srcId, dstId,item.key.meta.id,item.key.producerId, item.n);
        LogDb.buildingPay(srcId,player.id(),charge);
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
        ThirdPartyDataSource.instance().postFlightSearchRequest(c.getDate(), c.getId(), (Flight flight)->{
            City.instance().execute(()->{
                if(flight == null) {
                    this.write(Package.fail(cmd));
                    return;
                }

                Common.Fail.Reason reason = flight.canBet();
                if(reason != null) {
                    this.write(Package.fail(cmd, reason));
                }
                else {
                    if(FlightManager.instance().betFlight(player.id(), flight, c.getDelay(), c.getScore())) {
                        player.offsetScore(-c.getScore());
                        GameDb.saveOrUpdate(Arrays.asList(player, FlightManager.instance()));
                        this.write(Package.create(cmd, c));
                    }
                    else
                        this.write(Package.fail(cmd));
                }
            });
        });
    }

    public void getFlightBetHistory(short cmd) {
        Gs.FlightBetHistory.Builder builder = Gs.FlightBetHistory.newBuilder();
        for(LogDb.FlightBetRecord r : LogDb.getFlightBetRecord(player.id())) {
            builder.addInfoBuilder().setAmount(r.amount).setDelay(r.delay).setWin(r.win).setData(r.data);
        }
        this.write(Package.create(cmd, builder.build()));
    }

    public void getAllFlight(short cmd) {
        this.write(Package.create(cmd, FlightManager.instance().toProto(player.id())));
    }

    public void searchFlight(short cmd, Message message) {
        Gs.SearchFlight c = (Gs.SearchFlight)message;
        if(c.getArrCode().isEmpty() || c.getDepCode().isEmpty() || c.getDate().isEmpty())
            return;
        final UUID playerId = player.id();
        ThirdPartyDataSource.instance().postFlightSearchRequest(c.getDate(), c.getArrCode(), c.getDepCode(), (List<Flight> flights)->{
            City.instance().execute(()->{
                Gs.FlightSearchResult.Builder builder = Gs.FlightSearchResult.newBuilder();
                flights.forEach(f->builder.addData(f.toProto()));
                this.write(Package.create(cmd, builder.build()));
                //GameServer.sendTo(Arrays.asList(playerId), Package.create(cmd, builder.build()));
            });
        });
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
        LogDb.playerPay(player.id(), sell.price,0);
        LogDb.playerIncome(seller.id(), sell.price,0);
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
            LogDb.playerPay(player.id(), cost,0);
            LogDb.playerIncome(talent.id(), cost, b.type());
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

    public void queryWeatherInfo(short cmd)
    {
        this.write(Package.create(cmd,ThirdPartyDataSource.instance().getWeather().toProto()));
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
        NpcManager.instance().countRealNpcByType().forEach((k,v)->{
            list.addCountNpcMap(Gs.CountNpcMap.newBuilder().setKey(k).setValue(v).build());
        });
        list.setWorkNpcNum(NpcManager.instance().getNpcCount())
                .setUnEmployeeNpcNum(NpcManager.instance().getUnEmployeeNpcCount())
                .setRealWorkNpc(NpcManager.instance().getRealNpcNumByType(1))
                .setRealUnEmployeeNpcNum(NpcManager.instance().getRealNpcNumByType(2));
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
           /* case MetaBuilding.LAB:
                buildingdata = MetaData.getLaboratory(tp);
                break;*/
            case MetaBuilding.TECHNOLOGY:
                buildingdata = MetaData.getTechnology(tp);
                break;
            case MetaBuilding.PROMOTE:
                buildingdata = MetaData.getPromotionCompany(tp);
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
            //修改玩家未修改名称的建筑
            List<Building> buildings = new ArrayList<>();
            City.instance().forEachBuilding(player.id(),b->{
                if(b.getLast_modify_time()==0){
                    b.setName(newName);
                    buildings.add(b);
                }
            });
            GameDb.saveOrUpdate(buildings);
            GameDb.saveOrUpdate(player);
            Gs.RoleInfo roleInfo = playerToRoleInfo(player);
            this.write(Package.create(cmd,roleInfo));
        }
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

    //查询建筑名称
    public void queryBuildingName(short cmd, Message message) {
        Gs.Id id = (Gs.Id) message;
        UUID buildingId = Util.toUuid(id.getId().toByteArray());
        Building building = City.instance().getBuilding(buildingId);
        if (building == null || building.outOfBusiness()) {
            return;
        }
        this.write(Package.create(cmd, Gs.Str.newBuilder().setStr(building.getName()).build()));
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
            this.write(Package.fail(cmd,Common.Fail.Reason.timeNotSatisfy));
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
        /*MetaData.getBuildingTech(MetaBuilding.MATERIAL).forEach(itemId->{
            Gs.MaterialInfo.Material.Builder b=builder.addMaterialBuilder();
            MetaMaterial material=MetaData.getMaterial(itemId);
            Eva e=EvaManager.getInstance().getEva(playerId, itemId, Gs.Eva.Btype.ProduceSpeed.getNumber());
            b.setItemId(itemId);
            b.setIsUsed(material.useDirectly);
            b.setNumOneSec(material.n);
            b.setEva(e!=null?e.toProto():null);
        });*/
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
        Set<Integer> set=MetaData.getBuildingTech(MetaBuilding.PRODUCE);
        for (Integer itemId : set) {

            Gs.ProduceDepInfo.Goods.Builder b=builder.addGdsBuilder();
            MetaGood goods=MetaData.getGood(itemId);
            b.setItemId(itemId);
            b.setIsUsed(goods.useDirectly);
            b.setNumOneSec(goods.n);
            /*b.setAddNumOneSec(EvaManager.getInstance().computePercent(SpeedEva));
            b.setAddBrand(EvaManager.getInstance().computePercent(brandEva));
            b.setAddQuality(EvaManager.getInstance().computePercent(qualityEva));*/
        }
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
        this.write(Package.create(cmd, builder.build()));
    }

    public void ct_createUser(short cmd,Message message){
        ccapi.Dddbind.ct_createUser msg = (ccapi.Dddbind.ct_createUser) message;
        UUID playerId = Util.toUuid(msg.getPlayerId().toByteArray());
        ccapi.CcOuterClass.CreateUserReq req = msg.getCreateUserReq();
        try {
            ccapi.GlobalDef.ResHeader resp = chainClient.instance().CreateUser(req);
            if(resp.getErrCode() == GlobalDef.ErrCode.ERR_SUCCESS)
                this.write(Package.create(cmd, msg));
            else
                this.write(Package.fail(cmd));
        }  catch (Exception e) {
            return ;
        }
        int t = 0 ;
    }

    public void ct_GenerateOrderReq(short cmd,Message message){
        ccapi.Dddbind.ct_GenerateOrderReq msg = (ccapi.Dddbind.ct_GenerateOrderReq) message;
        UUID playerId = Util.toUuid(msg.getPlayerId().toByteArray());
        this.write(Package.create(cmd, msg.toBuilder().setPurchaseId(UUID.randomUUID().toString().replace("-","")).build()));
        int t = 0 ;
    }

    public void ct_GetTradingRecords(short cmd,Message message){
        ccapi.Dddbind.ct_GetTradingRecords msg = (ccapi.Dddbind.ct_GetTradingRecords) message;
        UUID playerId = Util.toUuid(msg.getPlayerId().toByteArray());
        long range_StartTime = msg.getRangeStartTime();
        long range_EndTime = msg.getRangeEndTime();
        List<ddd_purchase> list = GameDb.GetTradingRecords(playerId, range_StartTime, range_EndTime);
        ccapi.Dddbind.ct_GetTradingRecords.Builder retMsg = msg.toBuilder();
        list.forEach(ddd_purchase -> retMsg.addRecords(ddd_purchase.toProto()));
        this.write(Package.create(cmd, retMsg.build()));
    }

    public void ct_DisPaySmVefifyReq(short cmd,Message message){
        ccapi.Dddbind.ct_DisPaySmVefifyReq msg = (ccapi.Dddbind.ct_DisPaySmVefifyReq) message;
        UUID playerId = Util.toUuid(msg.getPlayerId().toByteArray());
        SmVerify sv = paySmCache.get(playerId);

        if(paySmCache.get(playerId).authCode.equals(msg.getAuthCode())){
            if(sv.TimeoutChecks()){
                //验证成功
                paySmCache.remove(playerId);
                //服务器签名验证测试
                ccapi.CcOuterClass.DisChargeReq req = sv.getDisChargeReq();
                //计算哈希
                byte[] pubKey = Hex.decode(req.getPubKey()) ;
                byte[] pubK = req.getPubKey().getBytes() ;

                ActiveSing activeSing = new ActiveSing(
                        req.getPurchaseId()
                        , req.getEthAddr()
                        , req.getTs()
                        , req.getAmount()
                        , pubKey
                );
                try{
                    byte[] hActiveSing = activeSing.ToHash();
                    //验证： 构造新的pubkey和签名
                    byte[] sigbts = Hex.decode(req.getSignature().toStringUtf8());
                    ECKey.ECDSASignature newsig = new ECKey.ECDSASignature(
                            new BigInteger(1,Arrays.copyOfRange(sigbts, 0, 32)),
                            new BigInteger(1,Arrays.copyOfRange(sigbts, 32, 64))
                    );
                    ECKey newpubkey = ECKey.fromPublicOnly(pubKey);
                    boolean pass =  newpubkey.verify(hActiveSing ,newsig); //验证通过

                    int t = 0 ;
                }catch (Exception e){
                    int t = 0;
                }

                //double dddAmount = GameDb.calDDDFromEEE(Double.parseDouble(req.getAmount()));
                double dddAmount = Double.parseDouble(req.getAmount());
                //添加交易
                ddd_purchase pur = new ddd_purchase(Util.toUuid(req.getPurchaseId().getBytes()),playerId, -dddAmount ,"",req.getEthAddr());
                if(dddPurchaseMgr.instance().addPurchase(pur)){
                    try{
                        //转发给ccapi服务器
                        ccapi.CcOuterClass.DisChargeRes response = chainRpcMgr.instance().DisChargeReq(req);

                        //因为提币操作是在ddd服务器操作，而且时间比较长，需提醒玩家提币请求开始处理了
                        ccapi.CcOuterClass.DisChargeStartRes.Builder msgStart = CcOuterClass.DisChargeStartRes.newBuilder();
                        msgStart.setResHeader(GlobalDef.ResHeader.newBuilder().setReqId(response.getResHeader().getReqId()).setVersion(response.getResHeader().getVersion()).build());
                        ddd_purchase dp = dddPurchaseMgr.instance().getPurchase(Util.toUuid(response.getPurchaseId().getBytes()));
                        Player player = GameDb.getPlayer(dp.player_id);
                        GameDb.saveOrUpdate(pur);
                        if(!player.equals(null)){
                            this.write(Package.create(cmd,msg.toBuilder().setErrorCode(0).build()));
                        }else{
                            this.write(Package.create(cmd,msg.toBuilder().setErrorCode(2).build()));
                        }
                    }catch (Exception e){
                        this.write(Package.create(cmd,msg.toBuilder().setErrorCode(2).build()));
                    }
                }else{

                    this.write(Package.create(cmd,msg.toBuilder().setErrorCode(2).build()));
                }
            }else{
                //提示超时
                this.write(Package.create(cmd,msg.toBuilder().setErrorCode(1).build()));
            }
        }else{
            //验证失败
            this.write(Package.create(cmd,msg.toBuilder().setErrorCode(2).build()));
        }
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
            buildingInfo.setOpeningTs(info.getOpeningTs());
            return buildingInfo;
        }else {
            return null;
        }
    }


    public void ct_RechargeRequestReq(short cmd,Message message){
        ccapi.Dddbind.ct_RechargeRequestReq msg = (ccapi.Dddbind.ct_RechargeRequestReq ) message;
        UUID playerId = Util.toUuid(msg.getPlayerId().toByteArray());

        CcOuterClass.RechargeRequestReq req = msg.getRechargeRequestReq();

        //服务器签名验证测试
        //计算哈希
        byte[] pubKey = Hex.decode(req.getPubKey()) ;
        byte[] pubK = req.getPubKey().getBytes() ;

        SignCharge pSignCharge = new SignCharge(
                req.getPurchaseId()
                , req.getAmount()
                , req.getTs()
                //, pubKey
        );
        try{
            byte[] hSignCharge = pSignCharge.ToHash();
            //验证： 构造新的pubkey和签名
            byte[] sigbts = Hex.decode(req.getSignature().toStringUtf8());
            //byte[] sigbts1 = Hex.decode(req.getSignature().toString(16));
            ECKey.ECDSASignature newsig = new ECKey.ECDSASignature(
                    new BigInteger(1,Arrays.copyOfRange(sigbts, 0, 32)),
                    new BigInteger(1,Arrays.copyOfRange(sigbts, 32, 64))
            );
            ECKey newpubkey = ECKey.fromPublicOnly(pubKey);
            boolean pass =  newpubkey.verify(hSignCharge ,newsig); //验证通过
            int t = 0 ;
        }catch (Exception e){

        }

        //添加交易
        double dddAmount = Double.parseDouble(req.getAmount());
        ddd_purchase pur = new ddd_purchase(Util.toUuid(req.getPurchaseId().getBytes()) , playerId, dddAmount ,"","");
        if(dddPurchaseMgr.instance().addPurchase(pur)){
            //转发给ccapi服务器
            try{
                ccapi.CcOuterClass.RechargeRequestRes resp = chainRpcMgr.instance().RechargeRequestReq(req);
                pur.ddd_to = resp.getEthAddr();
                if(resp.getResHeader().getErrCode() == GlobalDef.ErrCode.ERR_SUCCESS)
                    this.write(Package.create(cmd, ccapi.Dddbind.ct_RechargeRequestRes.newBuilder()
                            .setRechargeRequestRes(resp)
                            .setPlayerId(msg.getPlayerId())
                            .build()));
                else{
                    this.write(Package.fail(cmd));
                    logger.debug("ct_RechargeRequestReq: " + resp.getResHeader().getErrMsg());
                }
            }catch (Exception e){
                this.write(Package.fail(cmd));
            }
        }else{
            this.write(Package.fail(cmd));
        }
    }

    //ct_DisChargeReq
    public void ct_DisChargeReq(short cmd,Message message){
        Dddbind.ct_DisChargeReq msg = (Dddbind.ct_DisChargeReq) message;
        UUID playerId = Util.toUuid(msg.getPlayerId().toByteArray());
        ccapi.CcOuterClass.DisChargeReq req = msg.getDisChargeReq();

        String authCode = YunSmsManager.numberAuthCode();
        paySmCache.put(playerId,new SmVerify(playerId,authCode,System.currentTimeMillis(),req));
        String phoneNumber = GameDb.getPlayer(playerId).getAccount();
        Result<SmsSingleSend> result = YunSmsManager.getInstance().sendAuthCode(phoneNumber, authCode);
        int a = 0;
    }

    //查询原料厂所有的原料列表信息
    public void queryBuildingMaterialInfo(short cmd,Message message){
        Gs.Id id = (Gs.Id) message;
        UUID bid = Util.toUuid(id.getId().toByteArray());
        Building building = City.instance().getBuilding(bid);
        if(building==null||building.type()!=MetaBuilding.MATERIAL)
            return;
        UUID playerId = building.ownerId();
        int workerNum = building.getWorkerNum();
        Gs.BuildingMaterialInfo.Builder materialInfo = Gs.BuildingMaterialInfo.newBuilder();
        materialInfo.setBuildingId(id.getId());
        for (Integer materialId : MetaData.getBuildingTech(building.type())) {
            Gs.BuildingMaterialInfo.ItemInfo.Builder itemInfo = Gs.BuildingMaterialInfo.ItemInfo.newBuilder();
            MetaMaterial item = MetaData.getMaterial(materialId);
            //生产速度queryBuildingMaterialInfo等于 员工人数*基础值*（1+eva加成）
            double numOneSec = workerNum * item.n;
            itemInfo.setKey(materialId).setNumOneSec(numOneSec);
            materialInfo.addItems(itemInfo);
        }
        this.write(Package.create(cmd,materialInfo.build()));
    }

    //查询加工厂所有的商品列表详细信息
    public void queryBuildingGoodInfo(short cmd,Message message){
        Gs.Id id = (Gs.Id) message;
        UUID bid = Util.toUuid(id.getId().toByteArray());
        Building building = City.instance().getBuilding(bid);
        UUID playerId = building.ownerId();
        int type = building.type();
        int workerNum = building.getWorkerNum();
        if(type!=MetaBuilding.PRODUCE)
            return;
        Gs.BuildingGoodInfo.Builder goodInfo = Gs.BuildingGoodInfo.newBuilder();
        goodInfo.setBuildingId(id.getId());
        Player player = GameDb.getPlayer(playerId);
        for (Integer goodId : MetaData.getBuildingTech(building.type())) {
            MetaGood good = MetaData.getGood(goodId);
            //1.生产速度等于 员工人数*基础值*（1+eva加成）
            double numOneSec = workerNum * good.n;
            //4.品牌名(如果没有则取公司名)
            String brandName=player.getCompanyName();
            Gs.BuildingGoodInfo.ItemInfo.Builder itemInfo = Gs.BuildingGoodInfo.ItemInfo.newBuilder();
            itemInfo.setKey(goodId).setNumOneSec(numOneSec).setBrandName(brandName);
            goodInfo.addItems(itemInfo);
        }
        this.write(Package.create(cmd,goodInfo.build()));
    }

    //查询货架数据
    public void getShelfData (short cmd,Message message){
        Gs.Id id = (Gs.Id) message;
        UUID bid = Util.toUuid(id.getId().toByteArray());
        Building building = City.instance().getBuilding(bid);
        if(building==null||!(building instanceof IShelf))
            return;
        int type = building.type();
        Gs.ShelfData.Builder builder = Gs.ShelfData.newBuilder();
        Gs.Shelf shelf=null;
        switch (type){
            case MetaBuilding.MATERIAL:
                MaterialFactory materialFactory = (MaterialFactory) building;
                shelf = materialFactory.shelf.toProto();
                break;
            case MetaBuilding.PRODUCE:
                ProduceDepartment produceDepartment = (ProduceDepartment) building;
                shelf = produceDepartment.shelf.toProto();
                break;
            case MetaBuilding.RETAIL:
                RetailShop retailShop = (RetailShop) building;
                shelf = retailShop.getShelf().toProto();
                break;
        }
        builder.setShelf(shelf).setBuildingId(id.getId());
        this.write(Package.create(cmd,builder.build()));
    }

    //查询仓库数据
    public void getStorageData (short cmd,Message message){
        Gs.Id id = (Gs.Id) message;
        UUID bid = Util.toUuid(id.getId().toByteArray());
        Building building = City.instance().getBuilding(bid);
        if(building==null||!(building instanceof IStorage))
            return;
        int type = building.type();
        Gs.StorageData.Builder builder = Gs.StorageData.newBuilder();
        Gs.Store store=null;
        switch (type){
            case MetaBuilding.MATERIAL:
                MaterialFactory materialFactory = (MaterialFactory) building;
                store = materialFactory.store.toProto();
                break;
            case MetaBuilding.PRODUCE:
                ProduceDepartment produceDepartment = (ProduceDepartment) building;
                store = produceDepartment.store.toProto();
                break;
            case MetaBuilding.RETAIL:
                RetailShop retailShop = (RetailShop) building;
                store = retailShop.getStore().toProto();
                break;
        }
        builder.setStore(store).setBuildingId(id.getId());
        this.write(Package.create(cmd,builder.build()));
    }

    public void getLineData(short cmd,Message message){
        Gs.Id id = (Gs.Id) message;
        UUID bid = Util.toUuid(id.getId().toByteArray());
        Building building = City.instance().getBuilding(bid);
        if(building==null||!(building instanceof FactoryBase))
            return;
        int type = building.type();
        Gs.LineData.Builder lineBuilder = Gs.LineData.newBuilder();
        switch (type){
            case MetaBuilding.MATERIAL:
                MaterialFactory materialFactory = (MaterialFactory) building;
                materialFactory.lines.forEach(l->lineBuilder.addLine(l.toProto()));
                break;
            case MetaBuilding.PRODUCE:
                ProduceDepartment produceDepartment = (ProduceDepartment) building;
                produceDepartment.lines.forEach(l->{
                    ItemKey itemKey = new ItemKey(l.item, building.ownerId(), l.itemLevel, building.ownerId());
                    Gs.ItemKey key = itemKey.toProto();
                    Gs.Line.Builder builder = l.toProto().toBuilder().setBrandScore(key.getBrandScore()).setQtyScore(key.getQualityScore()).setBrandName(key.getBrandName());
                    lineBuilder.addLine(builder.build());
                });
                break;
        }
        FactoryBase factoryBase = (FactoryBase) building;
        lineBuilder.setBuildingId(id.getId()).setWarehouseCapacity(factoryBase.store.usedSize());
        this.write(Package.create(cmd,lineBuilder.build()));
    }

    /*查询建筑生产线状态*/
    public void queryBuildingProduceStatue(short cmd,Message message){
        Gs.Id id = (Gs.Id) message;
        UUID buildingId = Util.toUuid(id.getId().toByteArray());
        Building building = City.instance().getBuilding(buildingId);
        Gs.BuildingProduceStatue.Builder builder = Gs.BuildingProduceStatue.newBuilder();
        if(null!=building&&building instanceof FactoryBase){
            FactoryBase factory = (FactoryBase) building;
            List<LineBase> lines = factory.lines;
            if(building.getState()==Gs.BuildingState.SHUTDOWN_VALUE){  //停业状态
                builder.setStatue(Gs.BuildingProduceStatue.Statue.StopBusiness);
            }else{
                //1.没有生产线，生产线空闲
                if(lines.size()==0){
                    builder.setStatue(Gs.BuildingProduceStatue.Statue.LineUnUsed);
                }else {//有生产线
                    //2.空间是否充足，获取第一条生产线状态,并设置生产商品id
                    LineBase lineBase = lines.get(0);
                    builder.setItemId(lineBase.item.id);
                    if(lineBase.pause) {//如果生产线状态是暂停
                        if (!factory.hasEnoughMaterial(lineBase, factory.ownerId())) {	//3.原材料不足
                            builder.setStatue(Gs.BuildingProduceStatue.Statue.MaterialNotEnough);
                        }else if(factory.store.availableSize()<=0){	//4.空间不足
                            builder.setStatue(Gs.BuildingProduceStatue.Statue.StoreCapacityFull);
                        }
                    }else{//5.生产中
                        builder.setStatue(Gs.BuildingProduceStatue.Statue.InProduction);
                    }
                }
            }
        } else {
            System.err.println("建筑为空或不属于工厂建筑");
            return;
        }

        this.write(Package.create(cmd,builder.build()));
    }

    public void queryRetailShopGoods(short cmd,Message message){
        Gs.Id id = (Gs.Id) message;
        UUID buildingId = Util.toUuid(id.getId().toByteArray());
        Building b = City.instance().getBuilding(buildingId);
        if(null==b||!(b instanceof PublicFacility)||!(b.type()==MetaBuilding.RETAIL)){
            System.err.println("建筑为空或不属于零售店");
            return;
        }
        RetailShop r = (RetailShop) b;
        Collection<ItemKey> collection=r.getShelf().getGoodsItemKey();

        Gs.RetailShopGoods.Builder list = Gs.RetailShopGoods.newBuilder();
        collection.forEach(itemKey->{
            Gs.RetailShopGoods.ShopGoods.Builder builder= Gs.RetailShopGoods.ShopGoods.newBuilder();
            builder.setItemId(itemKey.meta.id);
            builder.setBrandName(itemKey.toProto().getBrandName());

            int itemId=itemKey.meta.id;
            UUID produceId=itemKey.producerId;

            List<Document> todayDocumentList = LogDb.getDayGoodsSoldDetail(DateUtil.getTodayStart(),System.currentTimeMillis(), LogDb.getNpcBuyInShelf());
            List<Document> yesterdayDocumentList = LogDb.getDayGoodsSoldDetail(DateUtil.getTodayStart()-TimeUnit.HOURS.toMillis(24),DateUtil.getTodayStart(), LogDb.getNpcBuyInShelf());
            todayDocumentList.forEach(document ->{
                UUID p=document.get("p",UUID.class);
                int i=document.getInteger("id");
                if((itemId==i)&&(produceId==p)){
                    builder.setTodaySoldAmount(document.getLong("total"));
                }
            });
            yesterdayDocumentList.forEach(document ->{
                UUID p=document.get("p",UUID.class);
                int i=document.getInteger("id");
                if((itemId==i)&&(produceId==p)){
                    builder.setYestdaySoldAmount(document.getLong("total"));
                }
            });
            list.addGoods(builder.build());
        });
        this.write(Package.create(cmd,list.build()));
    }
    /*查询离线通知*/
    public void queryOffLineInformation(short cmd){
        Gs.UnLineInformation playerUnLineInformation = OffLineInformation.instance().getPlayerUnLineInformation(player.id());
        this.write(Package.create(cmd,playerUnLineInformation));
    }

    //查询建筑繁荣度
    public void queryBuildingProsperity(short cmd,Message message){
        Gs.Id id = (Gs.Id) message;
        UUID buildingId = Util.toUuid(id.getId().toByteArray());
        Building building = City.instance().getBuilding(buildingId);
        if(building==null){
            return;
        }
        double prosperityValue = ProsperityManager.instance().getBuildingProsperity(building);
        Gs.BuildingProsperity.Builder builder = Gs.BuildingProsperity.newBuilder();
        builder.setBuildingId(Util.toByteString(buildingId)).setProsperityValue(prosperityValue);
        this.write(Package.create(cmd,builder.build()));
    }


    /*查询小地图建筑类别摘要信息*/
    public void queryTypeBuildingSummary(short cmd,Message message){
        Gs.Num num = (Gs.Num) message;
        int type = num.getNum();
        Gs.BuildingGridSummary.Builder builder = Gs.BuildingGridSummary.newBuilder();
        City.instance().forAllGrid((grid)->{
            AtomicInteger n = new AtomicInteger(0);
            grid.forAllBuilding(building -> {
                if(building.type()==type) {
                        n.addAndGet(1);
                }
            });
            Gs.BuildingGridSummary.Info.Builder info = Gs.BuildingGridSummary.Info.newBuilder();
            info.setIdx(Gs.GridIndex.newBuilder().setX(grid.getX()).setY(grid.getY()))
                    .setType(type)
                    .setNum(n.intValue());
            builder.addInfo(info);
        });
        this.write(Package.create(cmd,builder.build()));
    }

    /*查询小地图全城类别建筑类别详细信息*/
    public void queryTypeBuildingDetail(short cmd, Message message) {
        Gs.QueryTypeBuildingDetail query = (Gs.QueryTypeBuildingDetail) message;
        int type = query.getType();
        GridIndex centerIdx = new GridIndex(query.getCenterIdx().getX(), query.getCenterIdx().getY());
        Gs.TypeBuildingDetail.Builder builder = Gs.TypeBuildingDetail.newBuilder();
        City.instance().forEachGrid(centerIdx.toSyncRange(), (grid)->{
            Gs.TypeBuildingDetail.GridInfo.Builder gridInfo = Gs.TypeBuildingDetail.GridInfo.newBuilder();
            gridInfo.getIdxBuilder().setX(grid.getX()).setY(grid.getY());
            grid.forAllBuilding(b-> {
                if (b.type() == type) {
                    Gs.TypeBuildingDetail.GridInfo.TypeBuildingInfo.Builder typeBuilding = Gs.TypeBuildingDetail.GridInfo.TypeBuildingInfo.newBuilder();
                    if (b.state == Gs.BuildingState.SHUTDOWN_VALUE) {//未开业,不添加其他建筑数据
                        typeBuilding.setIsopen(false);
                    } else {
                        typeBuilding.setIsopen(true);
                        Gs.TypeBuildingDetail.GridInfo.BuildingSummary.Builder summary = Gs.TypeBuildingDetail.GridInfo.BuildingSummary.newBuilder();
                        //通用信息设置
                        summary.setOwnerId(Util.toByteString(b.ownerId()))
                                .setPos(b.coordinate().toProto()).setName(b.getName())
                                .setMetaId(b.metaId());
                        if (b instanceof IShelf) {       //货架建筑的出售信息
                            IShelf shelf = (IShelf) b;
                            summary.setShelfCount(shelf.getTotalSaleCount());
                        } else if (b instanceof Apartment) {//住宅类型信息
                            Apartment apartment = (Apartment) b;
                            // 玩家住宅繁荣度
                            double prosperityScore = ProsperityManager.instance().getBuildingProsperityScore(b);
                            Gs.TypeBuildingDetail.GridInfo.BuildingSummary.ApartmentSummary.Builder apartSummary = Gs.TypeBuildingDetail.GridInfo.BuildingSummary.ApartmentSummary.newBuilder();
                            apartSummary.setCapacity(apartment.getCapacity())
                                    .setRent(apartment.cost())
                                    .setRenter(apartment.getRenterNum());
                            summary.setApartmentSummary(apartSummary);
                        }
                    }
                    gridInfo.addTypeInfo(typeBuilding);
                }
            });
            builder.addInfo(gridInfo);
        });
        builder.setType(type);
        this.write(Package.create(cmd, builder.build()));
    }

    public void queryPlayerIncomePay(short cmd, Message message){
        UUID playerId = Util.toUuid(((Gs.PlayerIncomePay) message).getPlayerId().toByteArray());
        int buildType = ((Gs.PlayerIncomePay) message).getBType().getNumber();
        boolean isIncome = ((Gs.PlayerIncomePay) message).getIsIncome();
        int type=0;
        if(buildType==Gs.PlayerIncomePay.BuildType.MATERIAL.getNumber()){
            type=21;//原料厂-原料
        }else if(buildType==Gs.PlayerIncomePay.BuildType.PRODUCE.getNumber()){
            type=22;//加工厂-商品
        }else if(buildType==Gs.PlayerIncomePay.BuildType.TECHNOLOGY.getNumber()){
            type = 15; //研究所 -研究点数
        }else if(buildType==Gs.PlayerIncomePay.BuildType.PROMOTE.getNumber()){
            type = 16; //推广公司-研究点数
        }
        Gs.PlayerIncomePay.Builder build=Gs.PlayerIncomePay.newBuilder();
        build.setPlayerId(((Gs.PlayerIncomePay) message).getPlayerId()).setBType(((Gs.PlayerIncomePay) message).getBType()).setIsIncome(isIncome);

        long yestodayStartTime=DateUtil.todayStartTime();
        long todayStartTime=System.currentTimeMillis();
        List<Document> list=null;
        if(isIncome){//收入
            if(buildType==Gs.PlayerIncomePay.BuildType.MATERIAL.getNumber()||buildType==Gs.PlayerIncomePay.BuildType.PRODUCE.getNumber()
                    ||buildType==Gs.PlayerIncomePay.BuildType.TECHNOLOGY.getNumber()||buildType==Gs.PlayerIncomePay.BuildType.PROMOTE.getNumber()){
                list = LogDb.daySummaryShelfIncome(yestodayStartTime, todayStartTime, LogDb.getBuyInShelf(),type,playerId);
                list.forEach(document -> {
                    Building sellBuilding = City.instance().getBuilding(document.get("b",UUID.class));
                    Gs.PlayerIncomePay.IncomePay.Builder incomePay=Gs.PlayerIncomePay.IncomePay.newBuilder();
                    incomePay.setItemId(document.getInteger("tpi"))
                            .setNum((int)(document.getLong("a")/document.getLong("p")))
                            .setAmount(document.getLong("a")-document.getLong("miner"))/*需要减去旷工费才算是真正的收入*/
                            .setTime(document.getLong("t"))
                            .setName(sellBuilding.getName())
                            .setMetaId(sellBuilding.metaId());
                    build.addIncomePay(incomePay.build());
                });
            }else if(buildType==Gs.PlayerIncomePay.BuildType.RETAILSHOP.getNumber()){
                list = LogDb.daySummaryRetailShopIncome(yestodayStartTime, todayStartTime, LogDb.getNpcBuyInShelf(),buildType,playerId);
                list.forEach(document -> {
                    Building sellBuilding = City.instance().getBuilding(document.get("b",UUID.class));
                    Gs.PlayerIncomePay.IncomePay.Builder incomePay=Gs.PlayerIncomePay.IncomePay.newBuilder();
                    incomePay.setItemId(document.getInteger("tpi"))
                            .setNum((int)(document.getLong("a")/document.getLong("p")))
                            .setAmount(document.getLong("a")-document.getLong("miner"))
                            .setTime(document.getLong("t"))
                            .setName(sellBuilding.getName())
                            .setMetaId(sellBuilding.metaId());
                    build.addIncomePay(incomePay.build());
                });
            }else if(buildType==Gs.PlayerIncomePay.BuildType.APARTMENT.getNumber()){
                list = LogDb.daySummaryApartmentIncome(yestodayStartTime, todayStartTime, LogDb.getNpcRentApartment(),buildType,playerId);
                list.forEach(document -> {
                    Building sellBuilding = City.instance().getBuilding(document.get("b",UUID.class));
                    Gs.PlayerIncomePay.IncomePay.Builder incomePay=Gs.PlayerIncomePay.IncomePay.newBuilder();
                    incomePay.setItemId(2000)
                            .setNum((int)(document.getLong("a")/document.getLong("p")))
                            .setAmount(document.getLong("a"))
                            .setTime(document.getLong("t"))
                            .setName(sellBuilding.getName())
                            .setMetaId(document.getInteger("mid"));
                    build.addIncomePay(incomePay.build());
                });
            }else if(buildType==Gs.PlayerIncomePay.BuildType.GROUND.getNumber()){
                list = LogDb.daySummaryGroundIncome(yestodayStartTime, todayStartTime, LogDb.getBuyGround(),buildType,playerId);
                list.forEach(document -> {
                    Gs.PlayerIncomePay.IncomePay.Builder incomePay=Gs.PlayerIncomePay.IncomePay.newBuilder();
                    incomePay.setItemId(3000)
                            .setNum((int)(document.getLong("a")/document.getLong("s")))
                            .setAmount(document.getLong("a"))
                            .setTime(document.getLong("t"))
                            .setName(LogDb.getPositionStr(document.get("p",List.class)));
                    build.addIncomePay(incomePay.build());
                });
            }
        }else{//支出（所有货架支出要加上旷工费(需要加2次)）
            if(buildType==Gs.PlayerIncomePay.BuildType.MATERIAL.getNumber()||buildType==Gs.PlayerIncomePay.BuildType.PRODUCE.getNumber()){
                list = LogDb.daySummaryShelfPay(yestodayStartTime, todayStartTime, LogDb.getBuyInShelf(),type,playerId);
                list.forEach(document -> {
                    Building buyBuilding = City.instance().getBuilding(document.get("w",UUID.class));
                    Gs.PlayerIncomePay.IncomePay.Builder incomePay=Gs.PlayerIncomePay.IncomePay.newBuilder();
                    if(buildType==MetaItem.type(buyBuilding.metaId())){
                    incomePay.setItemId(document.getInteger("tpi"))
                            .setNum((int)(document.getLong("a")/document.getLong("p")))
                            .setAmount(document.getLong("a")+document.getLong("miner")) //加上旷工费
                            .setTime(document.getLong("t"))
                            .setName(buyBuilding.getName())
                            .setMetaId(buyBuilding.metaId());
                    build.addIncomePay(incomePay.build());
                    }
                });
                list = LogDb.daySummaryTransferPay(yestodayStartTime, todayStartTime, LogDb.getPayTransfer(),type,playerId);
                list.forEach(document -> {
                    Building buyBuilding = City.instance().getBuilding(document.get("d",UUID.class));
                    Gs.PlayerIncomePay.IncomePay.Builder incomePay=Gs.PlayerIncomePay.IncomePay.newBuilder();
                    if(buildType==MetaItem.type(buyBuilding.metaId())){
                        incomePay.setItemId(document.getInteger("tpi"))
                                .setNum(document.getInteger("c"))
                                .setAmount(0)
                                .setFreight(document.getLong("a"))
                                .setTime(document.getLong("t"))
                                .setName(buyBuilding.getName())
                                .setMetaId(buyBuilding.metaId());
                        build.addIncomePay(incomePay.build());
                    }
                });
            }else if(buildType==Gs.PlayerIncomePay.BuildType.RETAILSHOP.getNumber()){
                list = LogDb.daySummaryTransferPay(yestodayStartTime, todayStartTime, LogDb.getPayTransfer(),type,playerId);
                list.forEach(document -> {
                    Building buyBuilding = City.instance().getBuilding(document.get("d",UUID.class));
                    Gs.PlayerIncomePay.IncomePay.Builder incomePay=Gs.PlayerIncomePay.IncomePay.newBuilder();
                    if(buildType==MetaItem.type(buyBuilding.metaId())){
                        incomePay.setItemId(document.getInteger("tpi"))
                                .setNum(document.getInteger("c"))
                                .setAmount(0)
                                .setFreight(document.getLong("a"))
                                .setTime(document.getLong("t"))
                                .setName(buyBuilding.getName())
                                .setMetaId(buyBuilding.metaId());
                        build.addIncomePay(incomePay.build());
                    }
                });
            }else if(buildType==Gs.PlayerIncomePay.BuildType.TECHNOLOGY.getNumber()||buildType==Gs.PlayerIncomePay.BuildType.PROMOTE.getNumber()){
                list = LogDb.daySummaryShelfPay(yestodayStartTime, todayStartTime, LogDb.getBuyInShelf(),type,playerId);
                list.forEach(document -> {
                    Gs.PlayerIncomePay.IncomePay.Builder incomePay=Gs.PlayerIncomePay.IncomePay.newBuilder();
                        incomePay.setItemId(5000)
                                .setNum((int)(document.getLong("a")/document.getLong("p")))
                                .setAmount(document.getLong("a")+document.getLong("miner"))
                                .setTime(document.getLong("t"))
                                .setName("Eva点数")
                                .setMetaId(document.getInteger("tpi"));
                        build.addIncomePay(incomePay.build());
                });
            }
            else if(buildType==Gs.PlayerIncomePay.BuildType.APARTMENT.getNumber()){
            }else if(buildType==Gs.PlayerIncomePay.BuildType.GROUND.getNumber()){
                list = LogDb.daySummaryGroundPay(yestodayStartTime, todayStartTime, LogDb.getBuyGround(),buildType,playerId);
                list.forEach(document -> {
                    Gs.PlayerIncomePay.IncomePay.Builder incomePay=Gs.PlayerIncomePay.IncomePay.newBuilder();
                    incomePay.setItemId(3000)
                            .setNum((int)(document.getLong("a")/document.getLong("s")))
                            .setAmount(document.getLong("a"))
                            .setTime(document.getLong("t"))
                            .setName(LogDb.getPositionStr(document.get("p",List.class)));
                    build.addIncomePay(incomePay.build());
                });
                list = LogDb.daySummaryGroundPay(yestodayStartTime, todayStartTime, LogDb.getLandAuction(),buildType,playerId);
                list.forEach(document -> {
                    Gs.PlayerIncomePay.IncomePay.Builder incomePay=Gs.PlayerIncomePay.IncomePay.newBuilder();
                    incomePay.setItemId(4000)
                            .setNum((int)(document.getLong("a")/document.getLong("s")))
                            .setAmount(document.getLong("a"))
                            .setTime(document.getLong("t"))
                            .setName(LogDb.getPositionStr(document.get("p",List.class)));
                    build.addIncomePay(incomePay.build());
                });
            }
            //员工工资（几种建筑通用）  支出
            if(buildType!=Gs.PlayerIncomePay.BuildType.GROUND.getNumber()){
                list = LogDb.daySummaryStaffSalaryPay(yestodayStartTime, todayStartTime, LogDb.getPaySalary(),buildType,playerId);
                list.forEach(document ->{
                    Building buyBuilding = City.instance().getBuilding(document.get("b",UUID.class));
                    Gs.PlayerIncomePay.IncomePay.Builder incomePay=Gs.PlayerIncomePay.IncomePay.newBuilder();
                    incomePay.setItemId(1000)
                            .setNum((int)(document.getLong("a")/document.getLong("s")))
                            .setAmount(document.getLong("a"))
                            .setTime(document.getLong("t"))
                            .setName(buyBuilding.getName())
                            .setMetaId(buyBuilding.metaId());
                    build.addIncomePay(incomePay.build());
                });
            }
        }
        this.write(Package.create(cmd,build.build()));
    }
    public void queryPlayerIncomeRanking(short cmd,Message message){
        Gs.PlayerIncomeRanking msg = (Gs.PlayerIncomeRanking) message;
        int buildType=msg.getType().getNumber();
        Gs.PlayerIncomeRanking.Builder b=Gs.PlayerIncomeRanking.newBuilder();
        b.setType(msg.getType());
        //历史总营收
        List<Document> histList=LogDb.dayPlayerIncome(DateUtil.todayStartTime(),buildType,LogDb.getDayPlayerIncome());
        //今日收入
        Map<UUID,Long> todayMap=LogDb.todayPlayerIncome(DateUtil.todayStartTime(),System.currentTimeMillis(),LogDb.getPlayerIncome(),buildType);
        histList.forEach(document ->{
            Player player=GameDb.getPlayer(document.get("id",UUID.class));
            List<Building> buildings=City.instance().getPlayerBListByBtype(player.id(),buildType);
            int sum = buildings.stream().mapToInt(Building::getWorkerNum).sum();
            Gs.PlayerIncomeRanking.IncomeRanking.Builder incomeRank=Gs.PlayerIncomeRanking.IncomeRanking.newBuilder();
            incomeRank.setPlayerName(player.getName())
                    .setCompanyName(player.getCompanyName())
                    .setStaffNum(sum)
                    .setHisTotal(document.getLong("total"))
                    .setTodayTotal(todayMap.get(player.id()));
            b.addIncomeRanking(incomeRank.build());
        });
        this.write(Package.create(cmd,b.build()));
    }

    //=================新版研究所===================
    //新版研究所 建筑详情
    public void detailTechnology(short cmd, Message message){
        Gs.Id c = (Gs.Id) message;
        UUID id = Util.toUuid(c.getId().toByteArray());
        Building b = City.instance().getBuilding(id);
        if(b == null || b.type() != MetaBuilding.TECHNOLOGY)
            return;
        registBuildingDetail(b);
        updateBuildingVisitor(b);
        this.write(Package.create(cmd, b.detailProto()));
    }


    // 获取1*1地图
    public void queryMapProsperity(short cmd) {
        Gs.MapProsperity.Builder builder = Gs.MapProsperity.newBuilder();
        List<Gs.MapProsperity.ProspInfo> list = new ArrayList<>();
        ProsperityManager.instance().allGround.stream().filter(o -> o != null).forEach(g -> {
            //繁荣度
            int prosperity = ProsperityManager.instance().getGroundProsperity(g);
            list.add(Gs.MapProsperity.ProspInfo.newBuilder().setProsperity(prosperity).setIndex(g.toProto()).build());
        });
        this.write(Package.create(cmd, builder.addAllInfo(list).build()));
    }

    public void queryMapBuidlingSummary(short cmd, Message message) {
        Gs.MiniIndex index = (Gs.MiniIndex) message;
        Gs.MapBuildingSummary.Builder builder = Gs.MapBuildingSummary.newBuilder();
        Coordinate coordinate = new Coordinate(index.getX(), index.getY());
        GroundInfo info = GroundManager.instance().getGroundInfo(coordinate);
        if (info == null) {
            if (GlobalConfig.DEBUGLOG) {
                GlobalConfig.cityError("queryMapBuidlingSummary: groundInfo is entity!");
            }
            return;
        }
        // 如果是出售中的土地
        if (info.inSelling()) {
            int prosperity = ProsperityManager.instance().getGroundProsperity(coordinate);
            Player player = GameDb.getPlayer(info.ownerId);
            builder.setIdx(Gs.MiniIndexCollection.newBuilder().addCoord(coordinate.toProto())).setStatus(Gs.MapBuildingSummary.Status.Selling).setProsperity(prosperity).setRoleName(player.getName()).setCompanyName(player.getCompanyName());
        } else if (info.inStateless()) {
            // 如果是闲置状态
            int prosperity = ProsperityManager.instance().getGroundProsperity(coordinate);
            Player player = GameDb.getPlayer(info.ownerId);
            builder.setIdx(Gs.MiniIndexCollection.newBuilder().addCoord(coordinate.toProto())).setStatus(Gs.MapBuildingSummary.Status.Idle).setProsperity(prosperity).setRoleName(player.getName()).setCompanyName(player.getCompanyName());
        } else if (info.inRenting()) {
            // 如果是出租
            int prosperity = ProsperityManager.instance().getGroundProsperity(coordinate);
            Player player = GameDb.getPlayer(info.ownerId);
            builder.setIdx(Gs.MiniIndexCollection.newBuilder().addCoord(coordinate.toProto())).setStatus(Gs.MapBuildingSummary.Status.Renting).setProsperity(prosperity).setRoleName(player.getName()).setCompanyName(player.getCompanyName());
        } else {
            // 如果已经修建
            City.instance().forEachBuilding(coordinate.toGridIndex(), b -> {
                if (b.outOfBusiness() || b == null) {
                    GlobalConfig.cityError("queryMapBuidlingSummary: building is not business or not exist!");
                    return;
                }
                int prosperity = ProsperityManager.instance().getBuildingProsperity(b);
                List<Gs.MiniIndex> list = new ArrayList<>();
                b.area().toCoordinates().stream().filter(o -> o != null).forEach(c -> list.add(c.toProto()));
                Player owner = GameDb.getPlayer(info.ownerId);
                builder.setIdx(Gs.MiniIndexCollection.newBuilder().addAllCoord(list)).setStatus(Gs.MapBuildingSummary.Status.Used).setProsperity(prosperity).setRoleName(owner.getName()).setCompanyName(owner.getCompanyName()).setTypeId(b.metaId());
            });
        }
        this.write(Package.create(cmd, builder.build()));
    }


    /*查询土地繁荣度*/
    public void queryGroundProsperity(short cmd, Message message){
        Gs.MiniIndex index= (Gs.MiniIndex) message;
        Coordinate coordinate = new Coordinate(index);
        int groundProsperity = ProsperityManager.instance().getGroundProsperity(coordinate);
        Gs.Num.Builder builder = Gs.Num.newBuilder().setNum(groundProsperity);
        this.write(Package.create(cmd, builder.build()));
    }

    public void queryAuctionProsperity(short cmd, Message message){
        Gs.Num auctionId= (Gs.Num) message;
        MetaGroundAuction groundAuction = MetaData.getGroundAuction(auctionId.getNum());
        if(groundAuction==null){
            System.err.println("拍卖地块id不存在");
            return;
        }
        List<Coordinate> area = groundAuction.area;
        int sum=0;
        for (Coordinate coordinate : area) {
            sum+=ProsperityManager.instance().getGroundProsperity(coordinate);
        }
        Gs.AuctionProsperity.Builder builder = Gs.AuctionProsperity.newBuilder();
        builder.setGroundId(auctionId.getNum()).setProsperity(sum);
        this.write(Package.create(cmd, builder.build()));
    }

    //行业供需
    public void querySupplyAndDemand(short cmd,Message message) {
        Gs.SupplyAndDemand msg = (Gs.SupplyAndDemand) message;
        int type = msg.getType().getNumber();
        List<Document> list = LogDb.querySupplyAndDemand(type);
        Gs.SupplyAndDemand.Builder builder = Gs.SupplyAndDemand.newBuilder();
        builder.setType(msg.getType());
        long demand = IndustryMgr.instance().getTodayDemand(type); // 行业今日成交数量
        int supply = IndustryMgr.instance().getTodaySupply(type);  // 行业剩余数量
        // 供： 交易总量+剩余数量
        builder.setTodayS(demand+supply);
        builder.setTodayD(demand);
        list.stream().forEach(d->{
            Gs.SupplyAndDemand.Info.Builder info = builder.addInfoBuilder();
            info.setTime(d.getLong("time"));
            info.setDemand(d.getLong("demand"));
            info.setSupply(d.getLong("supply"));
        });
        this.write(Package.create(cmd, builder.build()));

    }
    // 商品供需
    public void queryItemSupplyAndDemand(short cmd, Message message) {
        Gs.queryItemSupplyAndDemand q = (Gs.queryItemSupplyAndDemand) message;
        int industryId = q.getIndustryId();
        int itemId = q.getItemId();
        Gs.SupplyAndDemand.Builder builder = Gs.SupplyAndDemand.newBuilder();
        builder.setType(Gs.SupplyAndDemand.IndustryType.valueOf(industryId));
        long demand = IndustryMgr.instance().getTodayDemand(industryId,itemId); // 行业今日成交数量
        int supply = IndustryMgr.instance().getTodaySupply(industryId,itemId);  // 行业剩余数量
        // 供： 交易总量+剩余数量
        builder.setTodayS(demand+supply);
        builder.setTodayD(demand);
        List<Document> list = LogDb.querySupplyAndDemand(industryId, itemId);
        list.stream().filter(o->o!=null).forEach(d->{
            Gs.SupplyAndDemand.Info.Builder info = builder.addInfoBuilder();
            info.setTime(d.getLong("time"));
            info.setDemand(d.getLong("demand"));
            info.setSupply(d.getLong("supply"));
        });
        this.write(Package.create(cmd, builder.build()));
    }

    public void queryCityLevel(short cmd) {
        this.write(Package.create(cmd,CityLevel.instance().toProto()));
    }
}
