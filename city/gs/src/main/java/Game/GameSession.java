package Game;

import Game.Contract.BuildingContract;
import Game.Contract.Contract;
import Game.Contract.ContractManager;
import Game.Contract.IBuildingContract;
import Game.Eva.Eva;
import Game.Eva.EvaManager;
import Game.Exceptions.GroundAlreadySoldException;
import Game.FriendManager.*;
import Game.Gambling.Flight;
import Game.Gambling.FlightManager;
import Game.Gambling.ThirdPartyDataSource;
import Game.IndustryInfo.IndustryMgr;
import Game.IndustryInfo.TopInfo;
import Game.League.LeagueInfo;
import Game.League.LeagueManager;
import Game.Meta.*;
import Game.RecommendPrice.GuidePriceMgr;
import Game.Util.*;
import Game.OffLineInfo.OffLineInformation;
import Game.Promote.PromotePoint;
import Game.Promote.PromotePointManager;
import Game.Promote.PromotionCompany;
import Game.Technology.SciencePoint;
import Game.Technology.SciencePointManager;
import Game.Technology.Technology;
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
import org.spongycastle.util.Pack;
import org.spongycastle.util.encoders.Hex;

import java.io.Serializable;
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
        if(cheat != null)
            _runCheat(cheat);
        this.write(Package.create(cmd, message));
    }
    private static class Cheat {
        enum Type {
            addmoney,
            additem,
            addground,
            invent,
            fill_warehouse
        }
        Type cmd;
        String[] paras;
    }
    private void _runCheat(Cheat cheat) {
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
                if(MetaGood.isItem(m.id)) {
                    if (!BrandManager.instance().brandIsExist(building.ownerId(), m.id)) {
                        //Player owner = GameDb.getPlayer(building.ownerId());
                        BrandManager.instance().addBrand(building.ownerId(), m.id);
                    }
                }
                GameDb.saveOrUpdate(building);
                break;
            }
            case addmoney: {
                int n = Integer.valueOf(cheat.paras[0]);
                if(n <= 0)
                    return;
                player.addMoney(n);
                LogDb.playerIncome(player.id(), n,0);
                GameDb.saveOrUpdate(player);
                break;
            }
            case additem: {
                int id = Integer.parseInt(cheat.paras[0]);
                int n = Integer.parseInt(cheat.paras[1]);
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
        p.addMoney(999999999999999l);
        LogDb.playerIncome(p.id(), 999999999999999l,0);
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
            /*添加推广点数并初始化*/
            List<PromotePoint> promotePoints=new ArrayList<>();
            MetaData.getPromotionItem().values().forEach(pro->{
                promotePoints.add(new PromotePoint(p.id(), pro.id,100));
            });
            PromotePointManager.getInstance().addPromotePointList(promotePoints);
            /*添加科技点数分类并初始化*/
            List<SciencePoint> sciencePoints = new ArrayList<>();
            MetaData.getScienceItem().values().forEach(science->{
                sciencePoints.add(new SciencePoint(p.id(), science.id, 100));
            });
            SciencePointManager.getInstance().addSciencePointList(sciencePoints);
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
        } else if (b instanceof Technology||b instanceof PromotionCompany) {
            ScienceBuildingBase science = (ScienceBuildingBase) b;
            science.cleanData();//清除建筑数据
        } else if (b instanceof PublicFacility) {
            if(b.type()==MetaBuilding.RETAIL){
                RetailShop r = (RetailShop) b;
                r.cleanData();
            }/*else {
                PublicFacility facility = (PublicFacility) b;
                facility.clear();//清除推广队列
            }*/
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
                            bb.addSaleBuilder().setItem(k.toProto()).setPrice(v).setGuidePrice(GuidePriceMgr.instance().getMaterialOrGoodsPrice(k));
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
                    //bb.setQueuedTimes(s.getQueuedTimes());
                    bb.setQueuedTimes(s.getLastQueuedCompleteTime());  //改为最后一个队列完成的时间
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
                        long value =s.getLocalPromoAbility(tp);
                        bb.addCurAbilitys((int) value);
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
        double minersRatio = MetaData.getSysPara().minersCostRatio/10000;
        long minerCost = (long) Math.floor(cost * minersRatio);
        long income =cost - minerCost;//收入（扣除矿工费后）
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
                .setCost(cost)
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
                    .setCost(cost)
                    .setTs(System.currentTimeMillis())
                    .build()));
            LogDb.cityBroadcast(seller.id(),player.id(),cost,0,1);
        }
        player.decMoney(freight);
        LogDb.playerPay(player.id(), freight,buyBuilding.type());

        GameServer.sendToAll(Package.create(GsCode.OpCode.makeMoneyInform_VALUE,Gs.MakeMoney.newBuilder()
                .setBuildingId(Util.toByteString(bid))
                .setMoney(cost)
                .setPos(sellBuilding.toProto().getPos())
                .setItemId(itemBuy.key.meta.id)
                .build()
        ));

        int itemId = itemBuy.key.meta.id;
        int type = MetaItem.type(itemBuy.key.meta.id);
        LogDb.payTransfer(player.id(), freight, bid, wid,itemId,itemBuy.key.producerId, itemBuy.n);
        //获取品牌名
        BrandManager.BrandName brandName = BrandManager.instance().getBrand(seller.id(), itemId).brandName;
        String goodName=brandName==null?seller.getCompanyName():brandName.getBrandName();
        LogDb.payTransfer(player.id(), freight, bid, wid,itemId,itemBuy.key.producerId, itemBuy.n);
        // 记录商品评分
        double brandScore = GlobalUtil.getBrandScore(itemBuy.key.getTotalBrand(), itemId);
        double goodQtyScore = GlobalUtil.getGoodQtyScore(itemBuy.key.getTotalQty(), itemId, MetaData.getGoodQuality(itemId));
        double score = (type == MetaItem.GOOD ? (brandScore + goodQtyScore) / 2 : -1);
        LogDb.buyInShelf(player.id(), seller.id(), itemBuy.n, c.getPrice(),
                    itemBuy.key.producerId, sellBuilding.id(), wid, type, itemId, goodName, score, sellBuilding.type());
        LogDb.buildingIncome(bid,player.id(),cost,type,itemId);//商品支出记录不包含运费
        LogDb.buildingPay(bid,player.id(),freight);//建筑运费支出
        /*离线收益，只在玩家离线期间统计*/
        if(!GameServer.isOnline(seller.id())) {
            LogDb.sellerBuildingIncome(sellBuilding.id(), sellBuilding.type(), seller.id(), itemBuy.n, c.getPrice(), itemId);//记录建筑收益详细信息
        }
        //矿工费用日志记录(需调整)
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
    public void detailLaboratory(short cmd, Message message) {
        Gs.Id c = (Gs.Id) message;
        UUID id = Util.toUuid(c.getId().toByteArray());
        Building b = City.instance().getBuilding(id);
        if(b == null || b.type() != MetaBuilding.LAB)
            return;
        registBuildingDetail(b);
        updateBuildingVisitor(b);
        Gs.Laboratory laboratory = (Gs.Laboratory) b.detailProto();
        Gs.Laboratory.Builder builder = laboratory.toBuilder();
        //设置已完成的线只显示当前玩家本人研究的生产线
        Laboratory lab = (Laboratory) b;
        builder.addAllCompleted(lab.getOwnerLine(player.id()));
        this.write(Package.create(cmd, builder.build()));
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
            //Integer value = (int)fcySeller.getAllPromoTypeAbility(tp);
            Integer value = (int) fcySeller.getLocalPromoAbility(tp);
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
        Player seller = GameDb.getPlayer(sellerBuilding.ownerId());
        seller.delpayedPromotion(promoId);

        //获取该广告公司最后一个广告
        UUID lastPromotion = fcySeller.getLastPromotion();

        if(lastPromotion == null){
            fcySeller.setNewPromoStartTs(-1);
        }else{
            PromoOrder lastOrder = PromotionMgr.instance().getPromotion(lastPromotion);
            if(lastOrder != null){
                fcySeller.setNewPromoStartTs(lastOrder.promStartTs+lastOrder.promDuration);
            }else{
                if(GlobalConfig.DEBUGLOG){
                    GlobalConfig.cityError("GameSession.AdRemovePromoOrder(): PromoOrder not exist which id equal to "+ lastPromotion);
                }
            }
        }
        GameDb.saveOrUpdate(fcySeller);

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
        int fee = selfPromo? 0 : (fcySeller.getCurPromPricePerHour()) * ((int)gs_AdAddNewPromoOrder.getPromDuration()/3600000);
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
        int buildType=0;
        if(newOrder.buildingType>0){
            buildType=newOrder.buildingType%100;//零售店、住宅
        }else{
            buildType=12;//加工厂
        }
        LogDb.playerPay(buyer.id(), fee+minerCost, buildType);
        LogDb.playerIncome(seller.id(), fee-minerCost, sellerBuilding.type());

        GameServer.sendToAll(Package.create(GsCode.OpCode.makeMoneyInform_VALUE,Gs.MakeMoney.newBuilder()
                .setBuildingId(Util.toByteString(b.id()))
                .setMoney(fee-minerCost)
                .setPos(b.toProto().getPos())
                .setItemId(gs_AdAddNewPromoOrder.hasBuildingType() ? gs_AdAddNewPromoOrder.getBuildingType() : gs_AdAddNewPromoOrder.getProductionType())
                .build()
        ));

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
            //增加玩家建筑收入记录
            if(!GameServer.isOnline(seller.id())) {
                LogDb.sellerBuildingIncome(sellerBuildingId, fcySeller.type(), seller.id(), (int) (gs_AdAddNewPromoOrder.getPromDuration() / 3600000), fcySeller.getCurPromPricePerHour(), 0);//暂时推广内容没有记录，以后可以加上
            }
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
        LogDb.playerPay(player.id(), slot.rentPreDay,0);
        LogDb.playerIncome(owner.id(), slot.rentPreDay,building.type());
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
        if (!building.canUseBy(this.player.id()) && !lab.isExclusiveForOwner()) {//如果不是建筑主人，同时要求开放研究所
            if (!c.hasTimes())
                return;
            if (c.getTimes() > lab.getRemainingTime())
                return;
            lab.useTime(c.getTimes());
            cost = c.getTimes() * lab.getPricePreTime();
            //TODO:矿工费用
            double minersRatio = MetaData.getSysPara().minersCostRatio/10000;
            long minerCost = (long) Math.floor(cost * minersRatio);
            if (!player.decMoney(cost + minerCost))
                return;
            seller.addMoney(cost - minerCost);
            LogDb.playerPay(this.player.id(), cost + minerCost,0);
            LogDb.playerIncome(seller.id(), cost - minerCost, building.type());

            GameServer.sendToAll(Package.create(GsCode.OpCode.makeMoneyInform_VALUE,Gs.MakeMoney.newBuilder()
                    .setBuildingId(Util.toByteString(bid))
                    .setMoney(cost - minerCost)
                    .setPos(building.toProto().getPos())
                    .setItemId(c.hasGoodCategory() ? c.getGoodCategory() : 0)
                    .build()
            ));

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

            int itemId = c.hasGoodCategory() ? c.getGoodCategory() : 0;//用于统计研究的是什么
            if(!GameServer.isOnline(seller.id())) {
                LogDb.sellerBuildingIncome(lab.id(), lab.type(), lab.ownerId(), c.getTimes(), lab.getPricePreTime(), itemId);
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
        LogDb.laboratoryRecord(lab.ownerId(), player.id(), lab.id(), lab.getPricePreTime(), cost, c.hasGoodCategory() ? c.getGoodCategory() : 0, c.hasGoodCategory() ? true : false);
        Laboratory.Line line = lab.addLine(c.hasGoodCategory() ? c.getGoodCategory() : 0, c.getTimes(), this.player.id(), cost);
        //如果不是建筑主人且数量租完了，建筑不开放
        if(!building.canUseBy(this.player.id()) && lab.getRemainingTime()==0){
            lab.setExclusive(true);
        }
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
            Gs.LabCancelLine.Builder builder = c.toBuilder().addAllInProcessLine(lab.getAllLineProto(player.id()));
            this.write(Package.create(cmd, builder.build()));
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
        if(building == null || building.outOfBusiness() || !(building instanceof Laboratory))
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
                builder.addAllLabResult(r.labResult);//开启的5个结果集
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

    public void queryMyEva(short cmd, Message message)
    {
        UUID pid = Util.toUuid(((Gs.Id) message).getId().toByteArray());
        Gs.Evas.Builder list = Gs.Evas.newBuilder();
        EvaManager.getInstance().getEvaList(pid).forEach(eva->{
            list.addEva(eva.toProto());
        });
        List<Gs.BuildingPoint> buildingPoints = EvaTypeUtil.classifyBuildingTypePoint(pid);
        list.addAllBuildingPoint(buildingPoints);
        this.write(Package.create(cmd, list.build()));
    }

/*    public void updateMyEvas(short cmd, Message message)  //TODO:删除
    {
        Gs.Evas evas = (Gs.Evas)message;//传过来的Evas
        Gs.EvaResultInfos.Builder results = Gs.EvaResultInfos.newBuilder();//要返回的值
        Gs.EvaResultInfo.Builder result =null;
        Eva oldEva=null;//修改前的Eva信息
        boolean retailOrApartmentQtyIsChange = false;//（标志）确定是否更新了零售店或者住宅的品质，以便于更新全城最大最小的建筑品质值
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
            //判断最大最小建筑品质是否要更新标志
            if((eva.getAt()==MetaBuilding.APARTMENT||eva.getAt()==MetaBuilding.RETAIL)&&eva.getBt().equals(Gs.Eva.Btype.Quality)){
                retailOrApartmentQtyIsChange = true;
            }
            EvaManager.getInstance().updateEva(newEva);//同步保存eva
            results.addResultInfo(result);
        }
        if(retailOrApartmentQtyIsChange) {
            //更新建筑最大最小品质
            BuildingUtil.instance().updateMaxOrMinTotalQty();//更新全城建筑的最高最低品质
        }
        //BrandManager.instance().getAllBuildingBrandOrQuality();
        this.write(Package.create(cmd, results.build()));
    }*/
    /*新版Eva修改*/
    public void updateMyEvas(short cmd, Message message){
        Gs.UpdateMyEvas evaSummary = (Gs.UpdateMyEvas)message;
        /*首先获取到所有的Eva信息并且设置好加点点数*/
        Gs.Evas evas = EvaManager.getInstance().getAllUpdateEvas(evaSummary);
        List<Gs.Eva> updateEvas = new ArrayList<>();//加点成功的eva
        List<Eva> evaData = new ArrayList<>();   /*批量同步到数据库*/
        UUID playerId=null;
        boolean retailOrApartmentQtyIsChange = false;//是否更新最大最小建筑品质标志
        //批量修改Evas加点科技
        List<PromotePoint> playerPromotePoints = new ArrayList<>();
        List<SciencePoint> playerSciencePoint = new ArrayList<>();
        /*1.首先一次性判断，所有Eva是否能够加点成功*/
       if(!EvaTypeUtil.hasEnoughPoint(evas)){
           this.write(Package.fail(cmd, Common.Fail.Reason.evaPointNotEnough));
           return;
       }else {
           for (Gs.Eva eva : evas.getEvaList()) {
               if (playerId == null) {
                   playerId = Util.toUuid(eva.getPid().toByteArray());
               }
               /*1.确定是哪种科技加点  ，判断Bt类型*/
               if (EvaTypeUtil.judgeScienceType(eva) == EvaTypeUtil.PROMOTE_TYPE) {    //推广类型
                   /*确定具体加点建筑类型*/
                   int pointType = EvaTypeUtil.getEvaPointType(EvaTypeUtil.PROMOTE_TYPE, eva.getAt());
                   PromotePoint promotePoint = PromotePointManager.getInstance().getPromotePoint(playerId, pointType);
                   Eva newEva = EvaManager.getInstance().updateMyEva(eva); /*进行加点*/
                   playerPromotePoints.add(PromotePointManager.getInstance().updatePlayerPromotePoint(playerId, pointType, -eva.getDecEva())); /*扣减点数*/
                   EvaManager.getInstance().updateEvaSalary(eva.getDecEva());
                   updateEvas.add(newEva.toProto());
                   evaData.add(newEva);
               } else {                                                              //科技点数类型加成
                   int pointType = EvaTypeUtil.getEvaPointType(EvaTypeUtil.SCIENCE_TYPE, eva.getAt()); /*确定具体加点建筑类型*/
                   SciencePoint sciencePoint = SciencePointManager.getInstance().getSciencePoint(playerId, pointType);
                   if ((eva.getAt() == MetaBuilding.APARTMENT || eva.getAt() == MetaBuilding.RETAIL) && eva.getBt().equals(Gs.Eva.Btype.Quality))
                       retailOrApartmentQtyIsChange = true;
                   Eva newEva = EvaManager.getInstance().updateMyEva(eva);/*进行加点*/
                   playerSciencePoint.add(SciencePointManager.getInstance().updateSciencePoint(playerId, pointType, -eva.getDecEva())); /*扣减点数*/
                   EvaManager.getInstance().updateEvaSalary(eva.getDecEva());
                   updateEvas.add(newEva.toProto());
                   evaData.add(newEva);
               }
           }
           if (retailOrApartmentQtyIsChange) {
               BuildingUtil.instance().updateMaxOrMinTotalQty();//更新全城建筑的最高最低品质
           }
           playerSciencePoint.forEach(p -> SciencePointManager.getInstance().updateSciencePoint(p));
           playerPromotePoints.forEach(p -> PromotePointManager.getInstance().updatePromotionPoint(p));
           evaData.forEach(eva->EvaManager.getInstance().updateEva(eva));//同步更新Evamanager 并保存到数据库
           Gs.Evas.Builder builder = Gs.Evas.newBuilder().addAllEva(updateEvas);
           /*重新查询玩家的所有点数信息*/
           List<Gs.BuildingPoint> buildingPoints = EvaTypeUtil.classifyBuildingTypePoint(playerId);
           builder.addAllBuildingPoint(buildingPoints);
           this.write(Package.create(cmd, builder.build()));
       }
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
        List<Gs.MyBrands.Brand> labBrand = BrandManager.instance().getBrandByType(MetaBuilding.TECHNOLOGY, pid);//研究所
        List<Gs.MyBrands.Brand> promotionBrand = BrandManager.instance().getBrandByType(MetaBuilding.PROMOTE, pid);//推广
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
		//-1 名称重复、1修改成功。其他，返回上次修改时间
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
            this.write(Package.fail(cmd,Common.Fail.Reason.numberNotEnough));
            return;
        }
        //5.计算价格（运费+商品所需价值）
        long cost = itemBuy.n*inShelf.getGood().getPrice();//计算商品总价值
        //商品的运费
        int freight = (int) (MetaData.getSysPara().transferChargeRatio * Math.ceil(IStorage.distance(buyStore, (IStorage) sellBuilding)))*itemBuy.n;
        //6.如果玩家钱少于要支付的，交易失败
        if(player.money() < cost + freight) {
            this.write(Package.fail(cmd, Common.Fail.Reason.moneyNotEnough));
            return;
        }
        //7.仓库存放不下，失败
        if(!buyStore.reserve(itemBuy.key.meta, itemBuy.n)) {
            this.write(Package.fail(cmd,Common.Fail.Reason.spaceNotEnough));
            return;
        }
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
        LogDb.playerIncome(seller.id(), cost,sellBuilding.type());
        Building buyBuilding = City.instance().getBuilding(wid);
        LogDb.playerPay(player.id(), cost,buyBuilding.type());
        LogDb.playerPay(player.id(), freight,buyBuilding.type());
        //9.1记录运输日志(区分建筑还是租户仓库)
        if(sellRenter==null&&buyRenter==null) {
            //记录商品品质及知名度
            double brand = BrandManager.instance().getGood(player.id(), itemId);
            double quality = itemBuy.key.qty;
            LogDb.payTransfer(player.id(), freight, bid, wid,itemId,itemBuy.key.producerId, itemBuy.n);
        }
        else{
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
        LogDb.payTransfer(player.id(), freight, bid, wid,itemBuy.key.meta.id, itemBuy.key.producerId, itemBuy.n);
        if(!inShelf.getGood().hasOrderid()) { //商品不在租的仓库
            //获取品牌名
            BrandManager.BrandName brandName = BrandManager.instance().getBrand(seller.id(), itemId).brandName;
            String goodName=brandName==null?seller.getCompanyName():brandName.getBrandName();
            LogDb.buyInShelf(player.id(), seller.id(), itemBuy.n, inShelf.getGood().getPrice(),
                    itemBuy.key.producerId, sellBuilding.id(), wid, type, itemId, goodName, 0, sellBuilding.type());
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
        Building buyBuilding = City.instance().getBuilding(dstId);
        LogDb.playerPay(player.id(), charge,buyBuilding.type());
        if(!t.hasSrcOrderId()&&!t.hasDstOrderId()) {
            LogDb.payTransfer(player.id(), charge, srcId, dstId,item.key.meta.id,item.key.producerId, item.n);
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
    //住宅推荐价格 √
    public void queryApartmentRecommendPrice(short cmd, Message message) {
        Gs.QueryBuildingInfo msg = (Gs.QueryBuildingInfo) message;
        UUID buildingId = Util.toUuid(msg.getBuildingId().toByteArray());
        UUID playerId = Util.toUuid(msg.getPlayerId().toByteArray()); //暂时不用
        Building building = City.instance().getBuilding(buildingId);
        if (building == null || building.type() != MetaBuilding.APARTMENT) {
            return;
        }
        Apartment apartment = (Apartment) building;
        // 玩家住宅评分
        double brandScore = GlobalUtil.getBrandScore(apartment.getTotalBrand(), apartment.type());
        double retailScore = GlobalUtil.getBuildingQtyScore(apartment.getTotalQty(), apartment.type());
        double curRetailScore = (brandScore + retailScore) / 2;
        // 玩家住宅繁荣度
        double prosperityScore = ProsperityManager.instance().getBuildingProsperityScore(building);
        double guidePrice = GuidePriceMgr.instance().getApartmentGuidePrice(curRetailScore,prosperityScore);
        //NPC预期消费 = 行业工资(可能会调整为城市工资) * 住宅预期消费比例
        double moneyRatio = MetaData.getBuildingSpendMoneyRatio(building.type());
        double salary = City.instance().getIndustrySalary(building.type());
        Gs.ApartmentRecommendPrice.Builder builder = Gs.ApartmentRecommendPrice.newBuilder();
        builder.setNpc(moneyRatio * salary).setGuidePrice(guidePrice);
        this.write(Package.create(cmd,builder.setBuildingId(msg.getBuildingId()).build()));

    }

    //原料推荐价格 √
    public void queryMaterialRecommendPrice(short cmd, Message message) {
        Gs.QueryBuildingInfo msg = (Gs.QueryBuildingInfo) message;
        UUID buildingId = Util.toUuid(msg.getBuildingId().toByteArray()); // 建筑id
        UUID playerId = Util.toUuid(msg.getPlayerId().toByteArray()); //暂时不用
        Building building = City.instance().getBuilding(buildingId);
        if (building == null || building.type() != MetaBuilding.MATERIAL) {
            return;
        }
        this.write(Package.create(cmd, GuidePriceMgr.instance().getMaterialPrice(buildingId)));
    }


    //加工厂商品推荐价格 √
    public void queryProduceDepRecommendPrice(short cmd, Message message) {
        Gs.QueryBuildingInfo msg = (Gs.QueryBuildingInfo) message;
        UUID buildingId = Util.toUuid(msg.getBuildingId().toByteArray());
        UUID playerId = Util.toUuid(msg.getPlayerId().toByteArray()); //暂时不用
        Building building = City.instance().getBuilding(buildingId);
        if (building == null || building.type() != MetaBuilding.PRODUCE || building.outOfBusiness()) {
            return;
        }
        ProduceDepartment department = (ProduceDepartment) building;
        List<Item> itemList = department.store.getAllItem();
        Map<Integer, Double> map = new HashMap<>();
        if (!itemList.isEmpty() && itemList.size() > 0) {
            itemList.stream().forEach(item ->
            {
                double score = GlobalUtil.getBrandScore(item.getKey().getTotalBrand(), item.key.meta.id) + MetaData.getGoodQuality(item.key.meta.id);
                map.put(item.key.meta.id, score / 2);
            });

        } else {
            List<Item> items = department.getShelf().getAllSaleDetail();
            items.stream().forEach(item ->
            {
                double score = GlobalUtil.getBrandScore(item.getKey().getTotalBrand(), item.key.meta.id) + MetaData.getGoodQuality(item.key.meta.id);
                map.put(item.key.meta.id, score / 2);
            });

        }
        this.write(Package.create(cmd, GuidePriceMgr.instance().getProducePrice(map, buildingId)));
    }


    //零售店推荐价格 √
    public void queryRetailShopRecommendPrice(short cmd, Message message) {
        Gs.QueryBuildingInfo msg = (Gs.QueryBuildingInfo) message;
        UUID buildingId = Util.toUuid(msg.getBuildingId().toByteArray());
        UUID playerId = Util.toUuid(msg.getPlayerId().toByteArray()); //暂时不用
        Building building = City.instance().getBuilding(buildingId);
        if (building == null || building.type() != MetaBuilding.RETAIL || building.outOfBusiness()) {
            return;
        }
        RetailShop retailShop = (RetailShop) building;
        List<Item> items = retailShop.getStore().getAllItem();
        Map<Integer, Double> map = new HashMap<>();
        if (!items.isEmpty() && items.size() > 0) {
            items.stream().forEach(item ->
            {
                double score = GlobalUtil.getBrandScore(item.getKey().getTotalBrand(), item.key.meta.id) + MetaData.getGoodQuality(item.key.meta.id);
                map.put(item.key.meta.id, score / 2);
            });

        } else {
            List<Item> itemList = retailShop.getShelf().getAllSaleDetail();
            items.stream().forEach(item ->
            {
                double score = GlobalUtil.getBrandScore(item.getKey().getTotalBrand(), item.key.meta.id) + MetaData.getGoodQuality(item.key.meta.id);
                map.put(item.key.meta.id, score / 2);
            });

        }

        this.write(Package.create(cmd,GuidePriceMgr.instance().getRetailPrice(map, buildingId)));
    }

    // 研究所推荐定价
    public void queryLaboratoryRecommendPrice(short cmd, Message message) {
        Gs.QueryBuildingInfo msg = (Gs.QueryBuildingInfo) message;
        UUID buildingId = Util.toUuid(msg.getBuildingId().toByteArray());
        UUID playerId = Util.toUuid(msg.getPlayerId().toByteArray()); //暂时不用
        Building building = City.instance().getBuilding(buildingId);
        if (building == null || building.type() != MetaBuilding.TECHNOLOGY || building.outOfBusiness()) {
            return;
        }
        this.write(Package.create(cmd,GuidePriceMgr.instance().getLabOrProPrice(buildingId,true)));
    }

    //推广推荐价格 √
    public void queryPromotionRecommendPrice(short cmd, Message message) {
        Gs.QueryBuildingInfo msg = (Gs.QueryBuildingInfo) message;
        UUID buildingId = Util.toUuid(msg.getBuildingId().toByteArray());
        UUID playerId = Util.toUuid(msg.getPlayerId().toByteArray()); //暂时不用
        Building building = City.instance().getBuilding(buildingId);
        if (building == null || building.type() != MetaBuilding.PROMOTE || building.outOfBusiness()) {
            return;
        }
        this.write(Package.create(cmd,GuidePriceMgr.instance().getLabOrProPrice(buildingId,false)));
    }

    // 土地交易推荐价格
    public void queryGroundRecommendPrice(short cmd) {
        this.write(Package.create(cmd, Gs.Num.newBuilder().setNum((int) GuidePriceMgr.instance().getGroundPrice()).build()));
    }



    //原料竞争力
    public void materialGuidePrice(short cmd, Message message) {
        Gs.GoodSummary msg = (Gs.GoodSummary) message;
        Map<Integer, Double> materialMap = BuildingUtil.getMaterial();
        Gs.GoodSummary.Builder builder = Gs.GoodSummary.newBuilder();
        materialMap.forEach((k,v)->{
            Gs.GoodSummary.GoodMap.Builder goodMap = Gs.GoodSummary.GoodMap.newBuilder();
            builder.addGoodMap(goodMap.addItemId(k).addGudePrice(v).build());
        });
        this.write(Package.create(cmd, builder.setBuildingId(msg.getBuildingId()) .build()));
    }

    //住宅竞争力
    public void apartmentGuidePrice(short cmd, Message message) {
        Gs.AartmentMsg msg = (Gs.AartmentMsg) message;
        UUID buildingId = Util.toUuid(msg.getBuildingId().toByteArray());
        UUID playerId = Util.toUuid(msg.getPlayerId().toByteArray());
        Building building = City.instance().getBuilding(buildingId);
        Apartment apartment = (Apartment) building;
        //当前建筑评分
        double brandScore = GlobalUtil.getBrandScore(apartment.getTotalBrand(), apartment.type());
        double apartmentScore = GlobalUtil.getBuildingQtyScore(apartment.getTotalQty(), apartment.type());
        double score = (brandScore + apartmentScore) / 2;
        List<Double> info = BuildingUtil.getApartment();
        Gs.AartmentMsg.ApartmentPrice.Builder apartmentPrice = Gs.AartmentMsg.ApartmentPrice.newBuilder();
        apartmentPrice.setAvgPrice(info.get(0)).setAvgScore(info.get(1)).setScore(score);
        this.write(Package.create(cmd, Gs.AartmentMsg.newBuilder().addApartmentPrice(apartmentPrice.build()).setBuildingId(msg.getBuildingId()).build()));
    }

    //加工厂竞争力
    public void produceGuidePrice(short cmd, Message message) {
        Gs.GoodSummary msg = (Gs.GoodSummary) message;
        UUID buildingId = Util.toUuid(msg.getBuildingId().toByteArray());
        UUID playerId = Util.toUuid(msg.getPlayerId().toByteArray());

		Building building = City.instance().getBuilding(buildingId);
		if (building == null || building.type() != MetaBuilding.PRODUCE) {
			return;
		}
		ProduceDepartment department = (ProduceDepartment) building;
		Set<Integer> ids = MetaData.getAllGoodId();
		Map<Integer, List<Double>> produce = BuildingUtil.getProduce();
		Gs.GoodSummary.Builder builder = Gs.GoodSummary.newBuilder();
		for (Object id : ids) {
			Gs.GoodSummary.GoodMap.Builder goodMap = Gs.GoodSummary.GoodMap.newBuilder();
			int itemId = 0;
			double goodQtyScore = 0;
			double brandScore = 0;
			if (id instanceof Integer && produce != null && produce.size() > 0) {
				itemId = (Integer) id;
				List<Double> list = produce.get(itemId);
				double priceAvg = list.get(0);
				double scoreAvg = list.get(1);
				List<Item> itemList = department.store.getItem(itemId);
				if (!itemList.isEmpty()) {
					for (Item item : itemList) {
						brandScore = GlobalUtil.getBrandScore(item.getKey().getTotalQty(), itemId);
						goodQtyScore = GlobalUtil.getGoodQtyScore(item.getKey().getTotalQty(), itemId, MetaData.getGoodQuality(itemId));
					}
				} else {
					List<Item> saleList = new ArrayList<>(department.getSaleDetail(itemId).keySet());
					for (Item item : saleList) {
						brandScore = GlobalUtil.getBrandScore(item.getKey().getTotalQty(), itemId);
						goodQtyScore = GlobalUtil.getGoodQtyScore(item.getKey().getTotalQty(), itemId, MetaData.getGoodQuality(itemId));
					}
				}
				goodMap.addItemId(itemId).addAllGudePrice(Arrays.asList(priceAvg, scoreAvg, (brandScore + goodQtyScore) / 2));
			}
			builder.addGoodMap(goodMap.build());
		}
		this.write(Package.create(cmd, builder.setBuildingId(msg.getBuildingId()).build()));
	}

	//零售店竞争力
	public void retailGuidePrice(short cmd, Message message) {
		Gs.GoodSummary msg = (Gs.GoodSummary) message;
		UUID buildingId = Util.toUuid(msg.getBuildingId().toByteArray());
		UUID playerId = Util.toUuid(msg.getPlayerId().toByteArray());

		Building building = City.instance().getBuilding(buildingId);
		if (building == null || building.type() != MetaBuilding.RETAIL) {
			return;
		}
		RetailShop retailShop = (RetailShop) building;
		Set<Integer> ids = MetaData.getAllGoodId();
		Map<Integer, List<Double>> retail = BuildingUtil.getRetailGood();
		Gs.GoodSummary.Builder builder = Gs.GoodSummary.newBuilder();
		int itemId = 0;
		for (Integer id : ids) {
			//当前商品评分(取自仓库)
			double curBrandScore = 0;
			double curQtyScore = 0;
			Gs.GoodSummary.GoodMap.Builder goodMap = Gs.GoodSummary.GoodMap.newBuilder();
			if (id instanceof Integer && retail != null && retail.size() > 0) {
				itemId = id;
				List<Double> list = retail.get(itemId);
				double avgPrice = list.get(0);
				double avgGoodScore = list.get(1);
				List<Item> itemList = retailShop.getStore().getItem(itemId);
				if (!itemList.isEmpty()) {
					for (Item item : itemList) {
						curBrandScore = GlobalUtil.getBrandScore(item.getKey().getTotalQty(), itemId);
						curQtyScore = GlobalUtil.getGoodQtyScore(item.getKey().getTotalQty(), itemId, MetaData.getGoodQuality(itemId));
					}
				} else {
					List<Item> saleList = new ArrayList<>(retailShop.getSaleDetail(itemId).keySet());
					for (Item item : saleList) {
						curBrandScore = GlobalUtil.getBrandScore(item.getKey().getTotalQty(), itemId);
						curQtyScore = GlobalUtil.getGoodQtyScore(item.getKey().getTotalQty(), itemId, MetaData.getGoodQuality(itemId));
					}
				}
				//当前建筑评分
				double brandScore = GlobalUtil.getBrandScore(retailShop.getTotalBrand(), retailShop.type());
				double retailScore = GlobalUtil.getBuildingQtyScore(retailShop.getTotalQty(), retailShop.type());
				double curRetailScore = (brandScore + retailScore) / 2;
				goodMap.addItemId(itemId).addAllGudePrice(Arrays.asList(avgPrice, avgGoodScore, BuildingUtil.getRetail(), (curBrandScore + curQtyScore) / 2, curRetailScore));
			}
			builder.addGoodMap(goodMap.build());
		}
		this.write(Package.create(cmd, builder.setBuildingId(msg.getBuildingId()).build()));
	}
	//推广竞争力
	public void promotionGuidePrice(short cmd, Message message) {
		Gs.PromotionMsg msg = (Gs.PromotionMsg) message;
		UUID buildingId = Util.toUuid(msg.getBuildingId().toByteArray());
		UUID playerId = Util.toUuid(msg.getPlayerId().toByteArray());
		//四种推广类型
		Set<Integer> proIds = MetaData.getAllBuildingTech(MetaBuilding.PUBLIC);
		Building building = City.instance().getBuilding(buildingId);
		if (building == null || building.type() != MetaBuilding.PUBLIC) {
			return;
		}
		PublicFacility facility = (PublicFacility) building;
		int sumAbilitys = 0;
		for (Integer typeId : proIds) {
			sumAbilitys += ((int) facility.getLocalPromoAbility(typeId));
		}
		double price = BuildingUtil.getPromotion();
		//全城均推广能力
		double promotionInfo = GlobalUtil.getPromotionInfo();
		Gs.PromotionMsg.PromotionPrice.Builder promotionPrice = Gs.PromotionMsg.PromotionPrice.newBuilder();
		promotionPrice.setCurAbilitys((sumAbilitys / 4)).setGuidePrice(price).setAvgAbility(promotionInfo);
		this.write(Package.create(cmd, Gs.PromotionMsg.newBuilder().addProPrice(promotionPrice.build()).setBuildingId(msg.getBuildingId()).build()));
	}
	//研究竞争力
	public void laboratoryGuidePrice(short cmd, Message message) {
		Gs.LaboratoryMsg msg = (Gs.LaboratoryMsg) message;
		UUID buildingId = Util.toUuid(msg.getBuildingId().toByteArray());
		UUID playerId = Util.toUuid(msg.getPlayerId().toByteArray());
		Building building = City.instance().getBuilding(buildingId);
		if (building == null || building.type() != MetaBuilding.LAB) {
			return;
		}
		Laboratory laboratory = (Laboratory) building;
		double price = BuildingUtil.getLaboratory();
		//总概率
		Map<Integer, Double> prob = laboratory.getTotalSuccessProb();
		double evaProb = prob.get(Gs.Eva.Btype.EvaUpgrade_VALUE);
		double goodProb = prob.get(Gs.Eva.Btype.InventionUpgrade_VALUE);
		//全城均研发能力
		double labProb = GlobalUtil.getLaboratoryInfo();
		Gs.LaboratoryMsg.LaboratoryPrice.Builder labPrice = Gs.LaboratoryMsg.LaboratoryPrice.newBuilder();
		labPrice.setAvgProb(labProb).setCurProb((evaProb + goodProb) / 2).setGuidePrice(price);
		this.write(Package.create(cmd, Gs.LaboratoryMsg.newBuilder().addLabPrice(labPrice.build()).setBuildingId(msg.getBuildingId()).build()));
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
        double totalBrand=BrandManager.instance().getValFromMap(brandMap,building.type());
        double basicQuality=BrandManager.instance().getValFromMap(qtyMap, Gs.ScoreType.BasicQuality_VALUE);
        double addQuality=BrandManager.instance().getValFromMap(qtyMap, Gs.ScoreType.AddQuality_VALUE);

        //知名度评分
        double brandScore=GlobalUtil.getBrandScore(totalBrand,building.type());
        //品质评分
        double localQty = basicQuality * (1 + addQuality);
        double qtyScore=GlobalUtil.getBuildingQtyScore(localQty,building.type());
        builder.addScore(Gs.RetailShopOrApartmentInfo.Score.newBuilder().setType(Gs.ScoreType.BasicBrand).setVal(totalBrand).build());
        builder.addScore(Gs.RetailShopOrApartmentInfo.Score.newBuilder().setType(Gs.ScoreType.AddBrand).setVal(addBrand).build());
        builder.addScore(Gs.RetailShopOrApartmentInfo.Score.newBuilder().setType(Gs.ScoreType.TotalBrand).setVal(brandScore).build());
        builder.addScore(Gs.RetailShopOrApartmentInfo.Score.newBuilder().setType(Gs.ScoreType.BasicQuality).setVal(basicQuality).build());
        builder.addScore(Gs.RetailShopOrApartmentInfo.Score.newBuilder().setType(Gs.ScoreType.AddQuality).setVal(addQuality).build());
        builder.addScore(Gs.RetailShopOrApartmentInfo.Score.newBuilder().setType(Gs.ScoreType.TotalQuality).setVal(qtyScore).build());
        this.write(Package.create(cmd, builder.build()));
    }
    //推广公司信息(修改版)
    public void queryPromotionCompanyInfo(short cmd,Message message){  //TODO
        Gs.QueryBuildingInfo msg = (Gs.QueryBuildingInfo) message;
        UUID bid = Util.toUuid(msg.getBuildingId().toByteArray());
        Building building = City.instance().getBuilding(bid);
        if(!(building instanceof PromotionCompany)){
            return;
        }
        this.write(Package.create(cmd, building.toProto()));
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
        UUID bid = Util.toUuid(msg.getBuildingId().toByteArray());
        Building building = City.instance().getBuilding(bid);
        if(!(building instanceof Technology)){
            return;
        }
        this.write(Package.create(cmd, building.toProto()));
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
            //查询eva信息
            Eva eva = EvaManager.getInstance().getEva(playerId, materialId, Gs.Eva.Btype.ProduceSpeed_VALUE);
            //生产速度queryBuildingMaterialInfo等于 员工人数*基础值*（1+eva加成）
            double numOneSec = workerNum * item.n * (1 + EvaManager.getInstance().computePercent(eva));
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
            //查询eva信息
            Eva speedEva = EvaManager.getInstance().getEva(playerId, goodId, Gs.Eva.Btype.ProduceSpeed_VALUE);
            Eva qtyEva = EvaManager.getInstance().getEva(playerId, goodId, Gs.Eva.Btype.Quality_VALUE);
            //1.生产速度等于 员工人数*基础值*（1+eva加成）
            double numOneSec = workerNum * good.n * (1 + EvaManager.getInstance().computePercent(speedEva));
            //2.知名度评分
            int brand=good.brand;//基础值
            brand += BrandManager.instance().getBrand(playerId, goodId).getV();//当前品牌值
            double brandScore = GlobalUtil.getBrandScore(brand, goodId);
            //3.品质评分
            double quality = good.quality;
            quality =quality * (1+EvaManager.getInstance().computePercent(qtyEva));
            double qtyScore=GlobalUtil.getGoodQtyScore(quality, goodId,good.quality);
            //4.品牌名(如果没有则取公司名)
            String brandName=player.getCompanyName();
            BrandManager.BrandName brandNameInfo = BrandManager.instance().getBrand(playerId, goodId).brandName;
            if(brandNameInfo!=null)
                brandName = brandNameInfo.getBrandName();
            Gs.BuildingGoodInfo.ItemInfo.Builder itemInfo = Gs.BuildingGoodInfo.ItemInfo.newBuilder();
            itemInfo.setKey(goodId).setNumOneSec(numOneSec).setBrandScore(brandScore).setQtyScore(qtyScore).setBrandName(brandName);
            goodInfo.addItems(itemInfo);
        }
        this.write(Package.create(cmd,goodInfo.build()));
    }

    //查询推广公司的商品推广列表的详细信息
    public void queryPromotionItemInfo(short cmd,Message message){
        Gs.QueryPromotionItemInfo info = (Gs.QueryPromotionItemInfo) message;
        UUID playerId = Util.toUuid(info.getPlayerId().toByteArray());
        List<Integer> typeIdsList = info.getTypeIdsList();
        Gs.PromotionItemInfo.Builder itemInfo = Gs.PromotionItemInfo.newBuilder();
        itemInfo.setBuildingId(info.getBuildingId());
        for (Integer goodType : typeIdsList) {
            MetaGood good = MetaData.getGood(goodType);
            if(null==good)
                return;
            //1 当前商品知名度信息
            int brand = good.brand;
            BrandManager.BrandInfo brandInfo = BrandManager.instance().getBrand(playerId,goodType);
            brand += brandInfo.getV();
            //2 当前知名度评分
            double brandScore=GlobalUtil.getBrandScore(brand,goodType);
            Gs.PromotionItemInfo.ItemInfo.Builder item = Gs.PromotionItemInfo.ItemInfo.newBuilder();
            item.setItemId(goodType).setBrand(brand).setBrandScore(brandScore);
            itemInfo.addItems(item);
        }
        this.write(Package.create(cmd,itemInfo.build()));
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
        } else if (null!=building&&building instanceof ScienceBuildingBase) {
            ScienceBuildingBase buildingBase = (ScienceBuildingBase) building;
            List<ScienceLineBase> line = buildingBase.line;
            if (buildingBase.getState() == Gs.BuildingState.SHUTDOWN_VALUE) {  // 停业
                builder.setStatue(Gs.BuildingProduceStatue.Statue.StopBusiness);
            } else {
                if (line == null && line.size() == 0) {
                    builder.setStatue(Gs.BuildingProduceStatue.Statue.LineUnUsed);
                } else {
                    ScienceLineBase l = line.get(0);
                    builder.setItemId(l.item.id);
                    builder.setStatue(Gs.BuildingProduceStatue.Statue.InProduction);
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
            builder.addInfoBuilder()
                    .setIdx(Gs.GridIndex.newBuilder().setX(grid.getX()).setY(grid.getY()))
                    .setType(type)
                    .setNum(n.intValue());
        });
        this.write(Package.create(cmd,builder.build()));
    }

    /*查询小地图全城类别建筑类别详细信息*/
    public void queryTypeBuildingDetail(short cmd, Message message) {
        Gs.QueryTypeBuildingDetail query = (Gs.QueryTypeBuildingDetail) message;
        int type = query.getType();
        GridIndex centerIdx = new GridIndex(query.getCenterIdx().getX(), query.getCenterIdx().getY());
        Gs.TypeBuildingDetail.Builder builder = Gs.TypeBuildingDetail.newBuilder();
        Gs.TypeBuildingDetail.GridInfo.Builder gridInfo = Gs.TypeBuildingDetail.GridInfo.newBuilder();
        gridInfo.getIdxBuilder().setX(centerIdx.x).setY(centerIdx.y);
        City.instance().forEachBuilding(centerIdx, (b) -> {
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
                        // 玩家住宅评分
                        double brandScore = GlobalUtil.getBrandScore(apartment.getTotalBrand(), apartment.type());
                        double retailScore = GlobalUtil.getBuildingQtyScore(apartment.getTotalQty(), apartment.type());
                        double curRetailScore = (brandScore + retailScore) / 2;
                        // 玩家住宅繁荣度
                        double prosperityScore = ProsperityManager.instance().getBuildingProsperityScore(b);
                        double guidePrice = GuidePriceMgr.instance().getApartmentGuidePrice(curRetailScore, prosperityScore);
                        Gs.TypeBuildingDetail.GridInfo.BuildingSummary.ApartmentSummary.Builder apartSummary = Gs.TypeBuildingDetail.GridInfo.BuildingSummary.ApartmentSummary.newBuilder();
                        apartSummary.setCapacity(apartment.getCapacity())
                                .setRent(apartment.cost())
                                //计算总评分及推荐定价
                                .setGuidePrice((int) guidePrice)
                                .setRenter(apartment.getRenterNum());
                        summary.setApartmentSummary(apartSummary);
                    }else if(b instanceof ScienceBuildingBase){/*研究所和推广公司*/
                        ScienceBuildingBase science = (ScienceBuildingBase) b;
                        summary.setShelfCount(science.getShelf().getAllNum());
                    }
                    typeBuilding.setBuildingInfo(summary);
                }
                gridInfo.addTypeInfo(typeBuilding);
            }
        });
        Gs.TypeBuildingDetail.Builder typeBuildings = builder.setInfo(gridInfo).setType(type);
        this.write(Package.create(cmd, typeBuildings.build()));
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
        }
        Gs.PlayerIncomePay.Builder build=Gs.PlayerIncomePay.newBuilder();
        build.setPlayerId(((Gs.PlayerIncomePay) message).getPlayerId()).setBType(((Gs.PlayerIncomePay) message).getBType()).setIsIncome(isIncome);

        long yestodayStartTime=DateUtil.todayStartTime();
        long todayStartTime=System.currentTimeMillis();
        List<Document> list=null;
        if(isIncome){//收入
            if(buildType==Gs.PlayerIncomePay.BuildType.MATERIAL.getNumber()||buildType==Gs.PlayerIncomePay.BuildType.PRODUCE.getNumber()){
                list = LogDb.daySummaryShelfIncome(yestodayStartTime, todayStartTime, LogDb.getBuyInShelf(),type,playerId);
                list.forEach(document -> {
                    Building sellBuilding = City.instance().getBuilding(document.get("b",UUID.class));
                    Gs.PlayerIncomePay.IncomePay.Builder incomePay=Gs.PlayerIncomePay.IncomePay.newBuilder();
                    incomePay.setItemId(document.getInteger("tpi"))
                            .setNum((int)(document.getLong("a")/document.getLong("p")))
                            .setAmount(document.getLong("a"))
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
                            .setAmount(document.getLong("a"))
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
        }else{//支出
            if(buildType==Gs.PlayerIncomePay.BuildType.MATERIAL.getNumber()||buildType==Gs.PlayerIncomePay.BuildType.PRODUCE.getNumber()){
                list = LogDb.daySummaryShelfPay(yestodayStartTime, todayStartTime, LogDb.getBuyInShelf(),type,playerId);
                list.forEach(document -> {
                    Building buyBuilding = City.instance().getBuilding(document.get("w",UUID.class));
                    Gs.PlayerIncomePay.IncomePay.Builder incomePay=Gs.PlayerIncomePay.IncomePay.newBuilder();
                    if(buildType==MetaItem.type(buyBuilding.metaId())){
                    incomePay.setItemId(document.getInteger("tpi"))
                            .setNum((int)(document.getLong("a")/document.getLong("p")))
                            .setAmount(document.getLong("a"))
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
            }else if(buildType==Gs.PlayerIncomePay.BuildType.APARTMENT.getNumber()){
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

    //开启宝箱
    public void openScienceBox(short cmd,Message message){
        Gs.OpenScience box = (Gs.OpenScience) message;
        UUID bid = Util.toUuid(box.getBuildingId().toByteArray());
        Building building = City.instance().getBuilding(bid);
        if(!(building instanceof Technology)||box.getNum()<0){
            System.err.println("建筑类型错误或数量错误！");
            return;
        }
        MetaScienceItem item = MetaData.getScienceItem(box.getItemId());
        ItemKey key = new ItemKey(item, building.ownerId());
        Technology tec = (Technology) building;
        if (!tec.hasEnoughBox(key, box.getNum())) {
            System.err.println("宝箱数量不足！");
            return;
        }
        //开始开启宝箱
        int result = tec.useScienceBox(key, box.getNum());
        /*更新自动补货*/
        tec.updateAutoReplenish(key);
        Gs.ScienceBoxACK.Builder builder = Gs.ScienceBoxACK.newBuilder();
        builder.setKey(key.toProto())
                .setBuildingId(box.getBuildingId())
                .setOpenNum(box.getNum())
                .setResultPoint(result);
        GameDb.saveOrUpdate(tec);
        this.write(Package.create(cmd, builder.build()));
    }
    //使用研究所科技点数
    public void useSciencePoint(short cmd,Message message){
        Gs.OpenScience science = (Gs.OpenScience) message;
        UUID bid = Util.toUuid(science.getBuildingId().toByteArray());
        Building building = City.instance().getBuilding(bid);
        if(!(building instanceof  Technology)||!building.canUseBy(player.id()))
            return;
        Technology tec = (Technology) building;
        MetaScienceItem item = MetaData.getScienceItem(science.getItemId());
        ItemKey key = new ItemKey(item, building.ownerId());
        if(!tec.hasEnoughPintInStore(key, science.getNum())){
           this.write(Package.fail(cmd, Common.Fail.Reason.numberNotEnough));
           return;
        }
        //使用科技点数
        if(tec.getStore().consumeInHand(key,science.getNum())){
           //增加玩家对应的科技点数
            SciencePoint sciencePoint = SciencePointManager.getInstance().updateSciencePoint(player.id(), item.id,science.getNum());
            SciencePointManager.getInstance().updateSciencePoint(sciencePoint);//更新缓存并同步数据库
            GameDb.saveOrUpdate(tec);
            long pointNum = SciencePointManager.getInstance().getSciencePoint(player.id(), item.id).point;
            Gs.OpenScience.Builder builder = science.toBuilder().setPointNum((int) pointNum);
            this.write(Package.create(cmd,builder.build()));
        }else {
            this.write(Package.fail(cmd, Common.Fail.Reason.numberNotEnough));
            return;
        }
    }


    //=============新版推广公司==================
    //推广公司建筑详情
    public void detailPromotionCompany(short cmd,Message message){
        Gs.Id id = (Gs.Id) message;
        UUID bId = Util.toUuid(id.getId().toByteArray());
        Building building = City.instance().getBuilding(bId);
        if(building==null||!(building instanceof PromotionCompany))
            return;
        registBuildingDetail(building);
        updateBuildingVisitor(building);
        this.write(Package.create(cmd, building.detailProto()));
    }
    //推广公司仓库使用推广点数
    public void usePromotionPoint(short cmd,Message message){
        Gs.OpenScience science = (Gs.OpenScience) message;
        UUID bid = Util.toUuid(science.getBuildingId().toByteArray());
        Building building = City.instance().getBuilding(bid);
        if(!(building instanceof  PromotionCompany)||!building.canUseBy(player.id()))
            return;
        PromotionCompany promotion = (PromotionCompany) building;
        MetaPromotionItem item = MetaData.getPromotionItem(science.getItemId());
        ItemKey key = new ItemKey(item, building.ownerId());
        if(!promotion.hasEnoughPintInStore(key, science.getNum())){
            this.write(Package.fail(cmd, Common.Fail.Reason.numberNotEnough));
            return;
        }
        //使用科技点数
        if(promotion.getStore().consumeInHand(key,science.getNum())){
            //增加玩家对应的科技点数
            PromotePoint promotePoint = PromotePointManager.getInstance().updatePlayerPromotePoint(player.id(), item.id, science.getNum());
            PromotePointManager.getInstance().updatePromotionPoint(promotePoint);//更新缓存并同步数据库
            GameDb.saveOrUpdate(promotion);
            long pointNum = PromotePointManager.getInstance().getPromotePoint(player.id(), item.id).getPromotePoint();//加点后的数量
            Gs.OpenScience.Builder builder = science.toBuilder().setPointNum((int) pointNum);
            this.write(Package.create(cmd,builder.build()));
        }else {
            this.write(Package.fail(cmd, Common.Fail.Reason.numberNotEnough));
        }
    }

    //=========推广公司研究所公用协议==========
    //添加建筑生产线（推广公司、研究所）
    public void addScienceLine(short cmd,Message message){
        Gs.AddLine newLine = (Gs.AddLine) message;
        if(newLine.getTargetNum() <= 0)
            return;
        UUID id = Util.toUuid(newLine.getId().toByteArray());
        Building b = City.instance().getBuilding(id);
        if(b == null||b.outOfBusiness() || !(b instanceof ScienceBuildingBase) || !b.ownerId().equals(player.id()))
            return;
        MetaItem m = MetaData.getItem(newLine.getItemId());
        if(m == null)
            return;
        ScienceBuildingBase science = (ScienceBuildingBase) b;
        ScienceLineBase line = science.addLine(m, science.getWorkerNum(), newLine.getTargetNum());
        if(line!=null)
            GameDb.saveOrUpdate(science);
    }
    //删除生产线（推广公司、研究所）
    public void delScienceLine(short cmd,Message message){
        Gs.DelLine c = (Gs.DelLine) message;
        UUID id = Util.toUuid(c.getBuildingId().toByteArray());
        Building b = City.instance().getBuilding(id);
        if (b == null || b.outOfBusiness() ||!(b instanceof ScienceBuildingBase)|| !b.ownerId().equals(player.id()))
            return;
        UUID lineId = Util.toUuid(c.getLineId().toByteArray());
        ScienceBuildingBase science = (ScienceBuildingBase) b;
        int delIndex = science.getIndex(lineId);
        ScienceLineBase delLine = science.__delLine(lineId);
        if(delLine!=null) {
            GameDb.saveOrUpdate(science);
            GameDb.delete(delLine);
            if(science.line.size() >=(delIndex+1)){
                /*获取下一条生产线*/
                ScienceLineBase nextLine = science.line.get(delIndex);
                UUID nextLineId=null;
                if(nextLine!=null){
                    nextLineId = nextLine.id;
                }else{
                    nextLineId=science.line.get(0).getId();
                }
                this.write(Package.create(cmd, c.toBuilder().setNextlineId(Util.toByteString(nextLineId)).build()));
            }else{
                this.write(Package.create(cmd, c));
            }
        }
    }
    //调整eva类加点建筑生产线顺序（推广公司、研究所）
    public void setScienceLineOrder(short cmd,Message message){
        Gs.SetLineOrder c = (Gs.SetLineOrder) message;
        UUID id = Util.toUuid(c.getBuildingId().toByteArray());
        Building b = City.instance().getBuilding(id);
        if (b == null || b.outOfBusiness() || !(b instanceof ScienceBuildingBase)|| !b.ownerId().equals(player.id()))
            return;
        UUID lineId = Util.toUuid(c.getLineId().toByteArray());
        int pos = c.getLineOrder() - 1;
        ScienceBuildingBase science = (ScienceBuildingBase) b;
        if(pos >=0 && pos < science.line.size()){
            for (int i = science.line.size() -1; i >= 0 ; i--) {
                ScienceLineBase l = science.line.get(i);
                if(l.getId().equals(lineId)){
                    science.line.add(pos,science.line.remove(i));
                    break;
                }
            }
            this.write(Package.create(cmd, c));
        }else{
            this.write(Package.fail(cmd));
        }
    }
    //研究所或推广公司上架（推广公司、研究所）
    public void scienceShelfAdd(short cmd,Message message) throws Exception {
        Gs.ShelfAdd c = (Gs.ShelfAdd)message;
        Item item = null;
        item = new Item(c.getItem());
        UUID id = Util.toUuid(c.getBuildingId().toByteArray());
        Building building = City.instance().getBuilding(id);
        if(building == null|| !(building instanceof ScienceBuildingBase)||!building.canUseBy(player.id()))
            return;
        ScienceBuildingBase science = (ScienceBuildingBase) building;
        if(science.addshelf(item, c.getPrice(),c.getAutoRepOn())){
            GameDb.saveOrUpdate(science);
            Gs.Item.Builder itemBuilder = c.getItem().toBuilder().setN(science.getShelf().getSaleNum(item.key.meta.id));
            ScienceShelf.Content content = science.getContent(item.key);
            Gs.ShelfAdd.Builder builder = c.toBuilder().setItem(item.toProto())
                    .setCurCount(science.getShelf().getAllNum())                /*设置货架上的总数量*/
                    .setStoreNum(science.getStore().getItemCount(item.getKey()))/*设置仓库中的当前商品的可用数量*/
                    .setItem(itemBuilder).setAutoRepOn(content.autoReplenish);
           /* this.write(Package.create(cmd, builder.build()));*/
            science.sendToAllWatchers(Package.create(cmd, builder.build()));
        }else{
            this.write(Package.fail(cmd, Common.Fail.Reason.numberNotEnough));
            System.err.println("数量不足");
        }
    }
    //下架eva类加点建筑货架上的东西（推广公司、研究所）
    public void scienceShelfDel(short cmd,Message message) throws Exception {
        Gs.ShelfDel c = (Gs.ShelfDel)message;
        Item item = new Item(c.getItem());
        UUID bid = Util.toUuid(c.getBuildingId().toByteArray());
        Building building = City.instance().getBuilding(bid);
        if(building == null || !(building instanceof ScienceBuildingBase)|| !building.canUseBy(player.id()) || building.outOfBusiness())
            return;
        ScienceBuildingBase science = (ScienceBuildingBase) building;
        ScienceShelf.Content content = science.getContent(item.key);
        if(content!=null){
            content.autoReplenish = false;//关闭自动补货
            if(science.delshelf(item.key, content.n, true)) {
                GameDb.saveOrUpdate(science);
                Gs.ShelfDel.Builder builder = c.toBuilder().setCurCount(science.getShelf().getAllNum());
                /*this.write(Package.create(cmd, builder.build()));*/
                science.sendToAllWatchers(Package.create(cmd, builder.build()));
            }
        }else{
            this.write(Package.fail(cmd,Common.Fail.Reason.numberNotEnough));
        }
    }
    //修改eva类加点建筑货架上科技点信息（推广公司、研究所）
    public void scienceShelfSet(short cmd,Message message) throws Exception {
        Gs.ShelfSet c = (Gs.ShelfSet)message;
        if(c.getPrice() <= 0)
            return;
        Item item = new Item(c.getItem());
        UUID bid = Util.toUuid(c.getBuildingId().toByteArray());
        Building building = City.instance().getBuilding(bid);
        if(building == null || !(building instanceof ScienceBuildingBase) || !building.canUseBy(player.id()) || building.outOfBusiness())
            return;
        ScienceBuildingBase science = (ScienceBuildingBase) building;
        if(science.shelfSet(item, c.getPrice(),c.getAutoRepOn())){
            GameDb.saveOrUpdate(science);
            ScienceShelf.Content content = science.getContent(item.key);
            Gs.Item.Builder itemBuilder = c.getItem().toBuilder().setN(science.getShelf().getSaleNum(item.key.meta.id));
            Gs.ShelfSet.Builder builder = c.toBuilder();
            builder.setStoreNum(science.getStore().getItemCount(item.getKey()))
                    .setCurCount(science.getShelf().getAllNum()).setItem(itemBuilder).setAutoRepOn(content.autoReplenish);
           /*this.write(Package.create(cmd, builder.build()));*/
            science.sendToAllWatchers(Package.create(cmd, builder.build()));
        } else {
            this.write(Package.fail(cmd, Common.Fail.Reason.numberNotEnough));
        }
    }
    //购买科技资料（推广公司、研究所）
    public void buySciencePoint(short cmd,Message message) throws Exception {
        Gs.BuySciencePoint c = (Gs.BuySciencePoint)message;
        if(c.getPrice() <= 0)
            return;
        UUID bid = Util.toUuid(c.getBuildingId().toByteArray());
        UUID buyerId = Util.toUuid(c.getBuyerId().toByteArray());//由于购买者就是当前玩家，所以不需要传递玩家id
        Building sellBuilding = City.instance().getBuilding(bid);
        if(sellBuilding == null || !(sellBuilding instanceof ScienceBuildingBase)||sellBuilding.canUseBy(buyerId)|| sellBuilding.outOfBusiness())
            return;
        Item item = new Item(c.getItem());
        //1.检测购买的数量是否充足（不充足返回错误码）
        //2.购买科技点数（建筑增加今日营收，以及日志记录）
        //3.购买者增加对应的eva点数（需等eva改版完成）
        //4.（玩家消费记录）、（建筑）以及（建筑主人）收入
        ScienceBuildingBase science = (ScienceBuildingBase) sellBuilding;
        if(science.checkShelfSlots(item.key,item.n)){
            ScienceShelf.Content content = science.getContent(item.key);
            //计算费用
            int cost = item.n * content.price;
            //消费货架数量
            science.delshelf(item.key, item.n, false);
            sellBuilding.updateTodayIncome(cost);
            //玩家支出和收入记录
            Player seller = GameDb.getPlayer(science.ownerId());
            player.decMoney(cost);//消费者支出
            seller.addMoney(cost);//卖家收入
            int itemId = item.key.meta.id;
            //增加玩家的科技点数
            if(sellBuilding.type()==MetaBuilding.TECHNOLOGY) {
                SciencePoint sciencePoint = SciencePointManager.getInstance().updateSciencePoint(player.id(), itemId, item.n);
                SciencePointManager.getInstance().updateSciencePoint(sciencePoint);
                c=c.toBuilder().setTypePointAllNum(sciencePoint.point).build();
            }else{
                PromotePoint promotePoint = PromotePointManager.getInstance().updatePlayerPromotePoint(player.id(), itemId, item.n);
                PromotePointManager.getInstance().updatePromotionPoint(promotePoint);
                c=c.toBuilder().setTypePointAllNum(promotePoint.promotePoint).build();
            }
            int type = MetaItem.scienceItemId(itemId);//获取商品类型
            //日志记录
            LogDb.playerPay(player.id(),cost,sellBuilding.type());
            LogDb.playerIncome(seller.id(),cost,sellBuilding.type());
            LogDb.buyInShelf(player.id(), seller.id(), item.n, content.getPrice(),
                    item.key.producerId, sellBuilding.id(),player.id(),type,itemId,seller.getCompanyName(),0,seller.getName(),seller.getCompanyName(),sellBuilding.type());
            LogDb.buildingIncome(bid,player.id(),cost,0,itemId);
            if(!GameServer.isOnline(seller.id())) {
                LogDb.sellerBuildingIncome(sellBuilding.id(), sellBuilding.type(), seller.id(), item.n, c.getPrice(), itemId);//离线通知统计
            }
            GameDb.saveOrUpdate(Arrays.asList(player,seller,sellBuilding));
            //推送商品变化通知
            sellBuilding.sendToAllWatchers(Package.create(cmd, c));
           /* this.write(Package.create(cmd, c));*/
        }else{
            System.err.println("货架数量不足");
            this.write(Package.fail(cmd, Common.Fail.Reason.numberNotEnough));
        }
    }
    //获取科技加点建筑所货架（推广公司、研究所）
    public void getScienceShelfData(short cmd,Message message){
        Gs.Id id = (Gs.Id) message;
        UUID bid = Util.toUuid(id.getId().toByteArray());
        Building building = City.instance().getBuilding(bid);
        if(building==null||!(building instanceof ScienceBuildingBase))
            return;
        ScienceBuildingBase scienceBuildingBase = (ScienceBuildingBase) building;
        Gs.ScienceShelfData.Builder shelfData = Gs.ScienceShelfData.newBuilder();
        Gs.ScienceShelf scienceShelf = scienceBuildingBase.getShelf().toProto(scienceBuildingBase);
        shelfData.setShelf(scienceShelf).setBuildingId(id.getId());
        this.write(Package.create(cmd,shelfData.build()));
    }
    //获取科技加点建筑仓库数据（推广公司、研究所）
    public void getScienceStorageData(short cmd,Message message){
        Gs.Id id = (Gs.Id) message;
        UUID bid = Util.toUuid(id.getId().toByteArray());
        Building building = City.instance().getBuilding(bid);
        if(building==null||!(building instanceof ScienceBuildingBase))
            return;
        ScienceBuildingBase scienceBuildingBase = (ScienceBuildingBase) building;
        Gs.ScienceStorageData.Builder storeData = Gs.ScienceStorageData.newBuilder();
        List<Gs.ScienceStoreItem> storeItems = scienceBuildingBase.getStore().toProto();
        Gs.ScienceStorageData.Builder builder = storeData.setBuildingId(id.getId()).addAllStore(storeItems);
        this.write(Package.create(cmd,builder.build()));
    }
    //获取科技加点建筑生产线信息（推广公司、研究所）
    public void getScienceLineData(short cmd,Message message){
        Gs.Id id = (Gs.Id) message;
        UUID bid = Util.toUuid(id.getId().toByteArray());
        Building building = City.instance().getBuilding(bid);
        if(building==null||!(building instanceof ScienceBuildingBase))
            return;
        ScienceBuildingBase scienceBuildingBase = (ScienceBuildingBase) building;
        Gs.ScienceLineData.Builder builder = Gs.ScienceLineData.newBuilder();
        scienceBuildingBase.line.forEach(l->{
            builder.addLine(l.toProto(building.ownerId()));
        });
        if(scienceBuildingBase.type()==MetaBuilding.TECHNOLOGY){/*如果是研究所，生产线还包含了宝箱信息*/
            Technology tec = (Technology) scienceBuildingBase;
            builder.addAllBox(tec.getBoxStore().toProto()).setBuildingId(id.getId());
        }
        builder.setBuildingId(id.getId());
        this.write(Package.create(cmd,builder.build()));
    }
    //获取科技加点类建筑生产列表的生产速度（推广公司、研究所）
    public void getScienceItemSpeed(short cmd,Message message){
        Gs.Id id = (Gs.Id) message;
        UUID bid = Util.toUuid(id.getId().toByteArray());
        Building building = City.instance().getBuilding(bid);
        if(building==null||!(building instanceof ScienceBuildingBase))
            return;
        ScienceBuildingBase scienceBuildingBase = (ScienceBuildingBase) building;
        Gs.ScienceItemSpeed.Builder builder = Gs.ScienceItemSpeed.newBuilder();
        builder.setBuildingId(id.getId());
        /*基础数据*/
        /*暂时不能够查询生产速度加成（需等Eva改版完成）*/
        if(scienceBuildingBase.type()==MetaBuilding.TECHNOLOGY) {
            for (MetaScienceItem item : MetaData.getScienceItem().values()) {
                Eva eva = EvaManager.getInstance().getEva(player.id(), item.id, Gs.Eva.Btype.ProduceSpeed_VALUE);//此处Eva可能会不对
                builder.addItemSpeedBuilder().setType(item.id)
                        .setSpeed(item.n*scienceBuildingBase.getWorkerNum()* (1 + EvaManager.getInstance().computePercent(eva)));//TODO 需加上eva加成
            }
        }else if(scienceBuildingBase.type()==MetaBuilding.PROMOTE){
            for (MetaPromotionItem item : MetaData.getPromotionItem().values()) {
                Eva eva = EvaManager.getInstance().getEva(player.id(), item.id, Gs.Eva.Btype.ProduceSpeed_VALUE);//此处Eva可能会不对
                builder.addItemSpeedBuilder().setType(item.id)
                        .setSpeed(item.n *scienceBuildingBase.getWorkerNum()*(1 + EvaManager.getInstance().computePercent(eva)));//TODO 需加上eva加成
            }
        }
        this.write(Package.create(cmd,builder.build()));
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

    public void queryTechnologySummary(short cmd, Message message) {
        Gs.Num n = (Gs.Num) message;
        MetaScienceItem item = MetaData.getScienceItem(n.getNum());
        if (item == null){
            GlobalConfig.cityError("queryTechnologySummary: MetaScienceItem is not exist!");
            return;
        }
        Gs.TechOrPromSummary.Builder builder = Gs.TechOrPromSummary.newBuilder();
        City.instance().forAllGrid((grid -> {
            AtomicInteger num = new AtomicInteger(0);
            grid.forAllBuilding(b->{
                if (!(b.outOfBusiness()) && b instanceof ScienceBuildingBase) {
                    Technology tech = (Technology) b;
                    if (tech.shelf.getSaleNum(item.id) > 0) {
                        num.addAndGet(1);
                    }
                }
            });
            builder.addInfoBuilder()
                    .setIdx(Gs.GridIndex.newBuilder().setX(grid.getX()).setY(grid.getY()))
                    .setCount(num.intValue());
        }));
        this.write(Package.create(cmd, builder.setTypeId(item.id).build()));
    }

    public void queryPromotionSummary(short cmd, Message message) {
        Gs.Num n = (Gs.Num) message;
        MetaPromotionItem item = MetaData.getPromotionItem(n.getNum());
        if (item == null){
            GlobalConfig.cityError("queryPromotionSummary: MetaPromotionItem is not exist!");
            return;
        }
        Gs.TechOrPromSummary.Builder builder = Gs.TechOrPromSummary.newBuilder();
        City.instance().forAllGrid((grid -> {
            AtomicInteger num = new AtomicInteger(0);
            grid.forAllBuilding(b->{
                if (!(b.outOfBusiness()) && b instanceof ScienceBuildingBase) {
                    PromotionCompany tech = (PromotionCompany) b;
                    if (tech.shelf.getSaleNum(item.id) > 0) {
                        num.addAndGet(1);
                    }
                }
            });
            builder.addInfoBuilder()
                    .setIdx(Gs.GridIndex.newBuilder().setX(grid.getX()).setY(grid.getY()))
                    .setCount(num.intValue());
        }));
        this.write(Package.create(cmd, builder.setTypeId(item.id).build()));
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
            Gs.SupplyAndDemand.Info.Builder info = Gs.SupplyAndDemand.Info.newBuilder();
            info.setTime(d.getLong("time"));
            info.setDemand(d.getLong("demand"));
            info.setSupply(d.getLong("supply"));
            builder.addInfo(info);
        });
        this.write(Package.create(cmd, builder.build()));

    }

    // 行业排行
    public void queryIndustryTopInfo(short cmd, Message message) {
        Gs.QueryIndustry m = (Gs.QueryIndustry) message;
        int type = m.getType();  // 行业类型id
        UUID id = Util.toUuid(m.getPid().toByteArray()); // 玩家id
        long industryStaffNum = IndustryMgr.instance().getIndustryStaffNum(type); // 行业总员工
        long industrySumIncome = IndustryMgr.instance().getIndustrySumIncome(type); // 行业总营收
        Gs.IndustryTopInfo.Builder builder = Gs.IndustryTopInfo.newBuilder();
        AtomicInteger owner = new AtomicInteger(0);
        if (type == Gs.SupplyAndDemand.IndustryType.GROUND_VALUE) {
            List<TopInfo> infos = IndustryMgr.instance().queryTop();
            infos.stream().filter(o -> o != null).forEach(d -> {
                owner.incrementAndGet();
                Gs.IndustryTopInfo.TopInfo.Builder info = Gs.IndustryTopInfo.TopInfo.newBuilder();
                info.setPid(Util.toByteString(d.pid)).setName(d.name).setIncome(d.yesterdayIncome).setCount(d.count);
                if (d.pid.equals(id)) {
                    builder.setOwner(owner.intValue());
                }
                builder.addTopInfo(info);
            });
            TopInfo top = IndustryMgr.instance().queryMyself(id, type);
            builder.addTopInfo(Gs.IndustryTopInfo.TopInfo.newBuilder().setPid(Util.toByteString(top.pid)).setName(top.name).setIncome(top.yesterdayIncome).setCount(top.count));

        } else {
            builder.setStaffNum(industryStaffNum).setTotal(industrySumIncome).setOwner(0);
            List<TopInfo> infos = IndustryMgr.instance().queryTop(type);
            infos.stream().filter(o -> o != null).forEach(d -> {
                owner.incrementAndGet();
                Gs.IndustryTopInfo.TopInfo.Builder info = Gs.IndustryTopInfo.TopInfo.newBuilder();
                info.setPid(Util.toByteString(d.pid)).setName(d.name).setIncome(d.yesterdayIncome).setScience(d.science).setPromotion(d.promotion);
                if (d.pid.equals(id)) {
                    builder.setOwner(owner.intValue());
                }
                builder.addTopInfo(info);
            });
            TopInfo myself = IndustryMgr.instance().queryMyself(id, type);
            builder.addTopInfo(Gs.IndustryTopInfo.TopInfo.newBuilder().setPid(Util.toByteString(myself.pid)).setIncome(myself.yesterdayIncome).setName(myself.name).setScience(myself.science).setPromotion(myself.promotion).setMyself(true).build());
        }
        this.write(Package.create(cmd, builder.build()));
    }

}
