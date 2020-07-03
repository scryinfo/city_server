package Game;

import Game.CityInfo.CityLevel;
import Game.CityInfo.EvaGradeMgr;
import Game.CityInfo.IndustryMgr;
import Game.CityInfo.TopInfo;
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
import Game.League.LeagueInfo;
import Game.League.LeagueManager;
import Game.Meta.*;
import Game.OffLineInfo.OffLineInformation;
import Game.Promote.PromotePoint;
import Game.Promote.PromotePointManager;
import Game.Promote.PromotionCompany;
import Game.RecommendPrice.GuidePriceMgr;
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
    //Payment SMS verification code cache
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
            /*Statistics player online time*/
            long loginTimes = player.getOfflineTs() - player.getOnlineTs();
            LogDb.playerLoginTime(player.id(),loginTimes,DateUtil.getTimeDayStartTime(player.getOnlineTs()));
        }
        GameServer.allGameSessions.remove(id());
        if (player.getSocietyId() != null)
        {
            //Must be called after allGameSessions.remove(id())
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
        //Add miner fees (system parameters)
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
        //If the company name exists, return
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
            //Copy eva metadata information to the database
            List<Eva> evaList=new ArrayList<Eva>();
            MetaData.getAllEva().forEach(m->{
                evaList.add(new Eva(p.id(),m.at,m.bt,m.lv,m.cexp,m.b));
            });
            EvaManager.getInstance().addEvaList(evaList);
            /*Add promotion points and initialize*/
            List<PromotePoint> promotePoints=new ArrayList<>();
            MetaData.getPromotionItem().values().forEach(pro->{
                promotePoints.add(new PromotePoint(p.id(), pro.id,100));
            });
            PromotePointManager.getInstance().addPromotePointList(promotePoints);
            /*Add technology points and initialize*/
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
            //If it is the current bidder and the price is the same, no high-price bidder information will be sent
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
        b.createNpc();//Generate npc
        if(b.startBusiness(player)){
            LogDb.playerBuildingBusiness(player.id(),1,b.getWorkerNum(),b.type());
            this.write(Package.create(cmd,c));
            //GameDb.saveOrUpdate(b);
            GameDb.saveOrUpdate(Arrays.asList(b,player));
            if(b.type()==MetaBuilding.APARTMENT){/*Update to buy residential cache*/
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
        b.addUnEmployeeNpc();//Become unemployed
        if (b instanceof Apartment) { //Residential closed, emptying occupants
            Apartment apartment = (Apartment) b;
            apartment.deleteRenter();
        } else if (b instanceof Technology||b instanceof PromotionCompany) {
            ScienceBuildingBase science = (ScienceBuildingBase) b;
            science.cleanData();//Clear building data
        } else if (b instanceof PublicFacility) {
            if(b.type()==MetaBuilding.RETAIL){
                RetailShop r = (RetailShop) b;
                r.cleanData();
                /*Update cache of optional building in npc residence*/
                City.instance().removeKnownApartmentMap(b);
            }
        } else if (b instanceof FactoryBase) {//There are warehouses and shelves, as well as production lines, cleared
            FactoryBase f = (FactoryBase) b;
            f.cleanData();
        }
        GameDb.saveOrUpdate(b);
        this.write(Package.create(cmd, c));
    }

    public void queryMarketSummary(short cmd, Message message) {
        Gs.Num c = (Gs.Num)message;
        MetaItem mi = MetaData.getItem(c.getNum()); //Get product type
        if(mi == null)
            return;
        Gs.MarketSummary.Builder builder = Gs.MarketSummary.newBuilder();
        City.instance().forAllGrid((grid)->{
            AtomicInteger n = new AtomicInteger(0);
            grid.forAllBuilding(building -> {
                if(building instanceof IShelf && !building.outOfBusiness()&&building instanceof FactoryBase) {
                    //If it is a distribution center and there are tenants, it is necessary to obtain listing information from the tenants
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
                        s.getSaleDetail(c.getItemId()).forEach((k, v) -> {// Newly added small map competitiveness
                            bb.addSaleBuilder().setItem(k.toProto()).setPrice(v).setGuidePrice(GuidePriceMgr.instance().getMaterialOrGoodsPrice(k));
                        });
                        bb.setOwnerId(Util.toByteString(building.ownerId()));
                        bb.setName(building.getName());
                        bb.setMetaId(building.metaId());//Building type id
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
                    bb.setQueuedTimes(s.getLastQueuedCompleteTime());  //Change to the time the last queue was completed
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
            /*Update retail store Npc shopping information*/
            if(building.type()==MetaBuilding.RETAIL) {
                City.instance().buildRetailMoveKnownValue(building);
            }
            this.write(Package.create(cmd, builder.build()));
        }
        else {
            this.write(Package.fail(cmd, Common.Fail.Reason.numberNotEnough));
            System.err.println("Insufficient quantity or shelf space");
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
            //Handling automatic replenishment
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
        //Here are all off the shelf, so turn off automatic replenishment
        Shelf.Content delContent = s.getContent(item.key);
        delContent.autoReplenish = false;
        if(s.delshelf(item.key, delContent.n, true)) {
            GameDb.saveOrUpdate(s);
            //If there is still the product on the shelf, it will be pushed, otherwise it will not be pushed
            Shelf.Content content = s.getContent(item.key);
            if(content!=null){
                UUID producerId=null;
                if(MetaGood.isItem(item.key.meta.id)){
                    producerId = item.key.producerId;
                }
                building.sendToWatchers(building.id(),item.key.meta.id, content.n,content.price,content.autoReplenish,producerId);
            }
            this.write(Package.create(cmd, c));
            /*Update the product information of the retail store*/
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
            /*Update retail product information*/
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
            //Error code returned
            this.write(Package.fail(cmd,Common.Fail.Reason.numberNotEnough));
            return;
        }
        long cost = itemBuy.n*c.getPrice();
        int freight = (int) (MetaData.getSysPara().transferChargeRatio * IStorage.distance(buyStore, (IStorage) sellBuilding));

        //TODO:Miner's cost (commodity basic cost * miner's cost ratio) (rounded down),
        double minersRatio = MetaData.getSysPara().minersCostRatio;
        long minerCost = (long) Math.floor(cost * minersRatio);
        long income =cost-minerCost;//Revenue (after deducting miner's fee)
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
        if(cost>=10000000){//Major transaction, transaction amount reaches 1000, broadcast information to the client, including player ID, transaction amount, time
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
        //Get brand name
        BrandManager.BrandName brandName = BrandManager.instance().getBrand(seller.id(), itemId).brandName;
        String goodName=brandName==null?seller.getCompanyName():brandName.getBrandName();
        //LogDb.payTransfer(player.id(), freight, bid, wid,itemId,itemBuy.key.producerId, itemBuy.n);
        // Record product ratings
        double score = 0;
        if (MetaGood.isItem(itemId)) {
            double brandScore = GlobalUtil.getBrandScore(itemBuy.key.getTotalBrand(), itemId);
            double goodQtyScore = GlobalUtil.getGoodQtyScore(itemBuy.key.getTotalQty(), itemId, MetaData.getGoodQuality(itemId));
            score = (type == MetaItem.GOOD ? (brandScore + goodQtyScore) / 2 : -1);
        }
        LogDb.buyInShelf(player.id(), seller.id(), itemBuy.n, c.getPrice(),
                    itemBuy.key.producerId, sellBuilding.id(), wid, type, itemId, goodName, score, sellBuilding.type(),minerCost);
        LogDb.buildingIncome(bid,player.id(),income,type,itemId);//Commodity expenditure records do not include freight
        LogDb.buildingPay(bid,player.id(),freight);//Construction freight expenses
        /*Offline revenue, only counted when the player is offline*/
        if(!GameServer.isOnline(seller.id())) {
            LogDb.sellerBuildingIncome(sellBuilding.id(), sellBuilding.type(), seller.id(), itemBuy.n,i.getPrice(), itemId);//Record details of construction revenue
        }
        //Miner fee logging
        LogDb .minersCost(player.id(),minerCost,minersRatio);
        LogDb.minersCost(seller.id(),minerCost,minersRatio);
        sellShelf.delshelf(itemBuy.key, itemBuy.n, false);
        //((IStorage)sellBuilding).consumeLock(itemBuy.key, itemBuy.n); It has been consumed when the product is deleted, which will cause secondary consumption
        sellBuilding.updateTodayIncome(income);

        buyStore.consumeReserve(itemBuy.key, itemBuy.n, c.getPrice());
        GameDb.saveOrUpdate(Arrays.asList(player, seller, buyStore, sellBuilding));
        //If the product is no longer on the shelf, don’t push it, push it
        i = sellShelf.getContent(itemBuy.key);
        if(i!=null){
            //If it is a product, pass the produceId
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
        //Set the completed line to display only the production line researched by the current player himself
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
        //Check if it is a promotion company
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
        //Back to client
        this.write(Package.create(cmd, newPromotions.build()));
    }
    //adQueryPromotion
    public void AdQueryPromotion(short cmd, Message message) {
		/*
				Query ad list, divided into two cases
				1. Advertiser (seller) query
				2. Advertiser (buyer) query
				Way: Query through promotionId
			*/
        Gs.AdQueryPromotion AdQueryPromotion = (Gs.AdQueryPromotion) message;
        boolean isSeller = AdQueryPromotion.getIsSeller();
        UUID buyerId = Util.toUuid(AdQueryPromotion.getPlayerId().toByteArray());
        Player player =  GameDb.getPlayer(buyerId);
        //Get paidPromotion
        if(player == null){
            if(GlobalConfig.DEBUGLOG){
                GlobalConfig.cityError("PromotionMgr.AdQueryPromotion: isSeller is false but player not exist!");
            }
            return;
        }

        List<UUID> promoIDs = new ArrayList<>();
        if( isSeller || AdQueryPromotion.hasSellerBuildingId()){
            //Get soldedmotion
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
        //Back to client
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
        //Update Advertisement List in Advertiser Player Information
        Building sellerBuilding = City.instance().getBuilding(promoOrder.sellerBuildingId);
        PublicFacility fcySeller = (PublicFacility) sellerBuilding ;
        List<PromoOdTs> tslist = fcySeller.delSelledPromotion(promoId,true);
        Gs.AdRemovePromoOrder.Builder newMsg = gs_AdRemovePromoOrder.toBuilder();
        tslist.forEach(ts->newMsg.addPromoTsChanged(ts.toProto()));
        Player seller = GameDb.getPlayer(sellerBuilding.ownerId());
        seller.delpayedPromotion(promoId);

        //Get the last ad of the advertising company
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

        //Send client notification
        this.write(Package.create(cmd, newMsg.build()));
    }

    //adjustPromoSellingSetting
    public void AdjustPromoSellingSetting(short cmd, Message message) {
        Gs.AdjustPromoSellingSetting adjustPromo = (Gs.AdjustPromoSellingSetting) message;
        UUID sellerBuildingId = Util.toUuid(adjustPromo.getSellerBuildingId().toByteArray());
        //Check if it is a promotion company
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

        //Send client notification
        this.write(Package.create(cmd, adjustPromo));
    }

    //adAddNewPromoOrder
    public void AdAddNewPromoOrder(short cmd, Message message) {
        Gs.AdAddNewPromoOrder gs_AdAddNewPromoOrder = (Gs.AdAddNewPromoOrder) message;
        //UUID id = Util.toUuid(gs_AdAddNewPromoOrder.getSellerBuildingId().toByteArray());
        UUID sellerBuildingId = Util.toUuid(gs_AdAddNewPromoOrder.getSellerBuildingId().toByteArray());
        UUID buyerPlayerId = Util.toUuid(gs_AdAddNewPromoOrder.getBuyerPlayerId().toByteArray());
        //Check if it is a promotion company
        Building b = City.instance().getBuilding(sellerBuildingId);
        if(b == null || b.outOfBusiness()){
            if(GlobalConfig.DEBUGLOG){
                GlobalConfig.cityError("GameSession.AdAddNewPromoOrder(): can't find the building instance which id equals to "+sellerBuildingId);
            }
            return;
        }
        //Check promotion target type
        //1. Building type, including retail store (RETAIL), residential (APARTMENT)
        //2. Commodity type: clothing, food
        //How to deal with the priority when the client and the product type are filled in and the building type is filled in?
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
        //The order records the price (do you need to record it?)
        newOrder.setTransactionPrice(fcySeller.getCurPromPricePerHour());

        //Is the length of purchase legal?
        //Advertisers do not need to consider available time for self-add promotion
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

        //Advertisers do not need to consider available time for self-add promotion
        int fee = selfPromo? 0 : (fcySeller.getCurPromPricePerHour()) * ((int)gs_AdAddNewPromoOrder.getPromDuration()/3600000);
        //TODO: miner's fee (round down)
        double minersRatio = MetaData.getSysPara().minersCostRatio;
        long minerCost = (long) Math.floor(fee * minersRatio);
        if(buyer.money() < fee+minerCost){
            if(GlobalConfig.DEBUGLOG){
                GlobalConfig.cityError("GameSession.AdAddNewPromoOrder(): PromDuration required by client greater than sellerBuilding's remained.");
            }
            this.write(Package.fail(cmd, Common.Fail.Reason.moneyNotEnough));
            return;
        }

        //Add order
			/*
				Order ID: promotionId
				* Calculation of advertising promotion ability and brand value increase
					*During the current advertising execution period, it is calculated every hour, and the advertiser’s brand value is accumulated based on the current advertising company’s promotion capabilities.
						For example: the brand value of the advertiser is 10000, and the current promotion capacity of the advertising company is 1000, then it is 11000 after calculation;
						The next hour is 12000, so accumulated
					*The advertising promotion power is calculated as a whole-hour global update. Even if it is an ad at 1:59, an ad calculation will be performed at 2 o'clock.
					* The increment of the brand value for each update is:
						* Current advertising company promotion capabilities * Update duration/1 hour
						*Update time: 5
							* If the ad performs an hourly update for the first time, the "update time" is the time from the ad's start time to the hour
							* Not the first hourly update, the update duration is 1 hour
				* Can promotionId use an index as an ID (does it need to be globally unique)?
					* Where does this data depend?
						*Advertisers will record this ID, and Npc will visit the advertiser’s brand value when making consumer decisions
							*Because the brand value has been updated, consumption decisions only need to directly use the advertiser’s brand value, and do not need to be calculated separately.
							* Need to examine the current consumption decision of npc to see how the original brand value is calculated
						* Where is this update better?
							* Placed in the promotion company building
							* In the global ad manager
					*Once promoOrderQueue because the advertiser canceled the advertising order placed for itself, then the entire
				* Where should I put promotionId?
					* Put in the example of an advertising company?
						* Consumer decision-making will first identify the advertising company
							*Update the brand value on the whole point, and read the consumption decision directly
					* So it’s okay to put promotionId in the building itself
						* But if you want to consider the convenience of statistics behind, or screw it out and put it in a manager, then you must use UUID
							*
			*/
			/*
				UUID 生成：
					this.id = UUID.randomUUID();
				UUID Conversion to byte[]
					startBusiness Util.toUuid(gs_AdAddNewPromoOrder.getId().toByteArray());
					In other words, the current transmission is stored using a byte array, and the UUID used by the service is converted from the byte array.
					The method used for conversion is UUID toUuid(byte[] bytes)
			*/

        //Get the last ad of the advertising company
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

        //Temporarily handle mismatches, which would not happen under normal circumstances
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

        //To calculate promStartTs, first take out the promotionId list of all advertisements in the advertising company, and calculate the starting point of the new advertisement
        newOrder.promStartTs = lastOrder.promStartTs + lastOrder.promDuration;
        newOrder.promProgress = 0;
        //The time unit sent by the client is milliseconds
        newOrder.promDuration = gs_AdAddNewPromoOrder.getPromDuration();
        fcySeller.setNewPromoStartTs(newOrder.promStartTs+gs_AdAddNewPromoOrder.getPromDuration());
        fcySeller.setPromRemainTime(fcySeller.getPromRemainTime() - (selfPromo ? 0: gs_AdAddNewPromoOrder.getPromDuration()));

        if(hasBuildingType){
				/*
				It is better for PromotionMgr to uniformly maintain the addition, deletion and modification of all advertisements. Centralized update and convenient statistics. Let the maintenance in the building,
				You have to add an update to each building separately.
				Maintenance strategy
				Added: PromotionMgr maintenance, the building only updates the corresponding promotionId list
				Delete: PromotionMgr maintenance, the building only updates the corresponding promotionId list
					*Currently only advertisers can delete their own PromoOrder, which is very infrequent.
				Change: PromotionMgr maintenance, the result value is updated to the brand value of the building
				Check: PromotionMgr Maintenance
				*/
            int buildingType = gs_AdAddNewPromoOrder.getBuildingType(); // Four building id
            newOrder.buildingType = buildingType;
            LogDb.promotionRecord(seller.id(), buyer.id(), sellerBuildingId, selfPromo ? 0 : fcySeller.getCurPromPricePerMs(), fee, buildingType, buildingType / 100, true);
        }else{
            int productionType = gs_AdAddNewPromoOrder.getProductionType(); //Seven-digit product id
            newOrder.productionType = productionType;
            LogDb.promotionRecord(seller.id(), buyer.id(), sellerBuildingId, selfPromo ? 0 : fcySeller.getCurPromPricePerMs(), fee, productionType,MetaGood.category(productionType),false);
        }
        PromotionMgr.instance().AdAddNewPromoOrder(newOrder);
        GameDb.saveOrUpdate(PromotionMgr.instance());
        //Bill, please
        buyer.decMoney(fee+minerCost);
        seller.addMoney(fee-minerCost);
        int buildType=0;
        if(newOrder.buildingType>0){
            buildType=newOrder.buildingType%100;//Retail stores, residential
        }else{
            buildType=12;//Processing plant
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

        //Miner's expense records
        LogDb.minersCost(buyer.id(),minerCost,MetaData.getSysPara().minersCostRatio);
        LogDb.minersCost(seller.id(),minerCost,MetaData.getSysPara().minersCostRatio);
        //Update the ad cache in buyer player information
        buyer.addPayedPromotion(newOrder.promotionId);
        GameDb.saveOrUpdate(buyer);
        //Update advertiser ad list
        fcySeller.addSelledPromotion(newOrder.promotionId);
        sellerBuilding.updateTodayIncome(fee);
        LogDb.buildingIncome(sellerBuildingId, buyer.id(), fee, 0, 0);//Excluding miners' fees
        GameDb.saveOrUpdate(Arrays.asList(fcySeller,sellerBuilding));
        //If it is promoted in its own company without notice
        if (!selfPromo) {
            //Increase player construction revenue record
            if(!GameServer.isOnline(seller.id())) {
                LogDb.sellerBuildingIncome(sellerBuildingId, fcySeller.type(), seller.id(), (int) (gs_AdAddNewPromoOrder.getPromDuration() / 3600000), fcySeller.getCurPromPricePerHour(), 0);//There is no record of temporarily promoted content, you can add it later
            }
            //Promotion company appointment notice
            long newPromoStartTs = newOrder.promStartTs; //Estimated start time
            long promDuration = newOrder.promDuration; //Ad duration
            UUID[] buildingId = {sellerBuilding.id()};
            StringBuilder sb = new StringBuilder().append(fee - minerCost + ",").append(promDuration + ",").append(newPromoStartTs);
            MailBox.instance().sendMail(Mail.MailType.PUBLICFACILITY_APPOINTMENT.getMailType(), sellerBuilding.ownerId(), null, buildingId, null, sb.toString());
        }
        //Send client notification
        this.write(Package.create(cmd, gs_AdAddNewPromoOrder.toBuilder().setRemainTime(fcySeller.getPromRemainTime()).build()));
        //Is it possible to add an enumeration value noFail to Fail to directly return the received packet to the client, which is a waste of server bandwidth
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
        //Start time, in hours
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
                //human traffic
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
            //Synchronous Data
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
        //If the shipping party does not have enough stock to lock, the operation fails
        if(!src.lock(item.key, item.n)) {
            System.err.println("运输失败：数量不够");
            this.write(Package.fail(cmd,Common.Fail.Reason.numberNotEnough));
            return;
        }
        //If the transported party does not have enough reserved space, the operation fails
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
        {//Handling automatic replenishment
            Shelf.Content srcContent = srcShelf.getContent(item.key);
            Shelf.Content dstContent = dstShelf.getContent(item.key);
            if(srcContent != null && srcContent.autoReplenish){
                //Update automatic restocking shelves
                IShelf.updateAutoReplenish(srcShelf,item.key);
            }
            if(dstContent != null && dstContent.autoReplenish){
                //Update automatic restocking shelves
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
        if (!building.canUseBy(this.player.id()) && !lab.isExclusiveForOwner()) {//If it is not the owner of the building, also request to open the research institute
            if (!c.hasTimes())
                return;
            if (c.getTimes() > lab.getRemainingTime())
                return;
            lab.useTime(c.getTimes());
            cost = c.getTimes() * lab.getPricePreTime();
            //TODO:Miner's fee
            double minersRatio = MetaData.getSysPara().minersCostRatio;
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

            //Miner's expense records
            LogDb.minersCost(this.player.id(), minerCost, MetaData.getSysPara().minersCostRatio);
            LogDb.minersCost(seller.id(), minerCost, MetaData.getSysPara().minersCostRatio);
            lab.updateTodayIncome(cost - minerCost);
            if (c.hasGoodCategory()) {
                lab.updateTotalGoodIncome(cost - minerCost, c.getTimes());
            } else {
                lab.updateTotalEvaIncome(cost - minerCost, c.getTimes());
            }
            LogDb.buildingIncome(lab.id(), this.player.id(), cost, 0, 0);//Does not include miner fees

            int itemId = c.hasGoodCategory() ? c.getGoodCategory() : 0;//What is used for statistical research
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
        //If it is not the owner of the building and the quantity is rented out, the building is not open
        if(!building.canUseBy(this.player.id()) && lab.getRemainingTime()==0){
            lab.setExclusive(true);
        }
        if (null != line) {
            GameDb.saveOrUpdate(Arrays.asList(lab, player, seller)); // let hibernate generate the fucking line.id first
            // Institute appointment notice (if you do not issue a notice in your own company)
            boolean flag = this.player.id().equals(building.ownerId()) ;
            if (!flag) {
                long beginProcessTs = line.beginProcessTs;//Estimated start time
                int times = c.getTimes();//Research duration
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
                builder.addAllLabResult(r.labResult);//5 result sets opened
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
                //Email notification blacklist refused to add
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
            //Email notification to add friends successfully
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
                 * Kick out guild
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
            //I signed the contract
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
     *(Citizen demand) the number of each type of npc
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

    //Check the industry average salary
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

    /*New version of Eva classification query*/
    public void queryMyEva(short cmd, Message message)
    {
        Gs.QueryMyEva myEva = (Gs.QueryMyEva) message;
        UUID playerId = Util.toUuid(myEva.getPlayerId().toByteArray());
        int buildingType = myEva.getBuildingType();
        Gs.BuildingEva buildingEva = EvaManager.getInstance().queryTypeBuildingEvaInfo(playerId, buildingType);
        this.write(Package.create(cmd,buildingEva));
    }

    /*New version of Eva modified*/
    public void updateMyEvas(short cmd, Message message){
        long totalPoint=0L;//This time the total plus point
        Gs.UpdateMyEvas evaSummary = (Gs.UpdateMyEvas)message;
        /*First get all the Eva information and set up points*/
        Gs.Evas evas = EvaManager.getInstance().getAllUpdateEvas(evaSummary);
        List<Gs.Eva> updateEvas = new ArrayList<>();//Add some success eva
        List<Eva> evaData = new ArrayList<>();   /*Batch sync to database*/
        UUID playerId=null;
        boolean retailOrApartmentQtyIsChange = false;//Whether to update the maximum and minimum building quality marks
        //Batch modify Evas plus some technology
        List<PromotePoint> playerPromotePoints = new ArrayList<>();
        List<SciencePoint> playerSciencePoint = new ArrayList<>();
        /*1.First of all, judge whether all Eva can be added successfully.*/
       if(!EvaTypeUtil.hasEnoughPoint(evas)){
           this.write(Package.fail(cmd, Common.Fail.Reason.evaPointNotEnough));
           return;
       }else {
           for (Gs.Eva eva : evas.getEvaList()) {
               totalPoint += eva.getDecEva();
               if (playerId == null) {
                   playerId = Util.toUuid(eva.getPid().toByteArray());
               }
               /*1.Determine what kind of technology is added, determine the type of Bt*/
               if (EvaTypeUtil.judgeScienceType(eva) == EvaTypeUtil.PROMOTE_TYPE) {    //Promotion type
                   /*Determine the specific building type*/
                   int pointType = EvaTypeUtil.getEvaPointType(EvaTypeUtil.PROMOTE_TYPE, eva.getAt());
                   PromotePoint promotePoint = PromotePointManager.getInstance().getPromotePoint(playerId, pointType);
                   Eva newEva = EvaManager.getInstance().updateMyEva(eva); /*Add points*/
                   playerPromotePoints.add(PromotePointManager.getInstance().updatePlayerPromotePoint(playerId, pointType, -eva.getDecEva())); /*Deduction points*/
                   EvaManager.getInstance().updateEvaSalary(eva.getDecEva());
                   updateEvas.add(newEva.toSimpleEvaProto());
                   evaData.add(newEva);
               } else {                                                              //Technology points type bonus
                   int pointType = EvaTypeUtil.getEvaPointType(EvaTypeUtil.SCIENCE_TYPE, eva.getAt()); /*Determine the specific building type*/
                   SciencePoint sciencePoint = SciencePointManager.getInstance().getSciencePoint(playerId, pointType);
                   if ((eva.getAt() == MetaBuilding.APARTMENT || eva.getAt() == MetaBuilding.RETAIL) && eva.getBt().equals(Gs.Eva.Btype.Quality))
                       retailOrApartmentQtyIsChange = true;
                   Eva newEva = EvaManager.getInstance().updateMyEva(eva);/*Add points*/
                   playerSciencePoint.add(SciencePointManager.getInstance().updateSciencePoint(playerId, pointType, -eva.getDecEva())); /*Deduction points*/
                   EvaManager.getInstance().updateEvaSalary(eva.getDecEva());
                   updateEvas.add(newEva.toSimpleEvaProto());
                   evaData.add(newEva);
               }
           }
           if (retailOrApartmentQtyIsChange) {
               BuildingUtil.instance().updateMaxOrMinTotalQty();//Update the highest and lowest quality of buildings in the city
           }
           playerSciencePoint.forEach(p -> SciencePointManager.getInstance().updateSciencePoint(p));
           playerPromotePoints.forEach(p -> PromotePointManager.getInstance().updatePromotionPoint(p));
           evaData.forEach(eva->EvaManager.getInstance().updateEva(eva));//Update Evamanager synchronously and save to the database
           Gs.BuildingEvas buildingEvas = EvaManager.getInstance().classifyEvaType(updateEvas, playerId);//Eva after classification modification
           this.write(Package.create(cmd, buildingEvas));
           /*Update level experience across the city*/
           CityLevel.instance().updateCityLevel(totalPoint);
       }
    }

    public void queryMyBrands(short cmd, Message message){
        Gs.QueryMyBrands msg = (Gs.QueryMyBrands)message;
        UUID pid = Util.toUuid(msg.getPId().toByteArray());
        Gs.MyBrands.Builder list = Gs.MyBrands.newBuilder();
        //Need to query based on raw material factory, processing plant, retail store, residential, promotion company, research institute, etc.
        List<Gs.MyBrands.Brand> materialBrand = BrandManager.instance().getBrandByType(MetaBuilding.MATERIAL, pid);//raw material
        List<Gs.MyBrands.Brand> goodBrand = BrandManager.instance().getBrandByType(MetaBuilding.PRODUCE, pid);//Processing plant
        List<Gs.MyBrands.Brand> retailShopBrand = BrandManager.instance().getBrandByType(MetaBuilding.RETAIL, pid);//Retail store
        List<Gs.MyBrands.Brand> apartmentBrand = BrandManager.instance().getBrandByType(MetaBuilding.APARTMENT, pid);//Residential
        List<Gs.MyBrands.Brand> labBrand = BrandManager.instance().getBrandByType(MetaBuilding.TECHNOLOGY, pid);//graduate School
        List<Gs.MyBrands.Brand> promotionBrand = BrandManager.instance().getBrandByType(MetaBuilding.PROMOTE, pid);//Promote
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

        Set<LeagueInfo.UID> set=LeagueManager.getInstance().getBuildingLeagueTech(bId); //Joining technology
        for (LeagueInfo.UID info : set) {
            int techId=info.getTechId();
            if(itemId==techId){
                ls.add(info.getPlayerId());//A certain technology may join multiple, but only one of them can be used
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

    //Edit brand name
    public void modyfyMyBrandName(short cmd,Message message){
        Gs.ModyfyMyBrandName msg = (Gs.ModyfyMyBrandName)message;
        UUID pId = Util.toUuid(msg.getPId().toByteArray());
        if(!this.player.id().equals(pId)){
            GlobalConfig.cityError("[modyfyMyBrandName] Brand-name only can be modified by it's owner!");
            return;
        }
        int techId=msg.getTypeId();
        Long result = BrandManager.instance().changeBrandName(pId, techId, msg.getNewBrandName());
		//-1 Duplicate name, 1 modified successfully. Other, return to the last modified time
        if(result==-1){
            this.write(Package.fail(cmd,Common.Fail.Reason.roleNameDuplicated));
        }else if(result==1){
            this.write(Package.create(cmd, msg));
        }else{//-1 Duplicate name, 1 modified successfully. Other, return to the last modified time
            Gs.ModyfyMyBrandName.Builder builder = msg.toBuilder().setLastChangeTime(result);
            this.write(Package.create(cmd, builder.build()));
        }
    }

    //Modify company name
    public void modifyCompanyName(short cmd,Message message){
        Gs.ModifyCompanyName msg = (Gs.ModifyCompanyName) message;
        String newName = msg.getNewName();
        UUID pid = Util.toUuid(msg.getPid().toByteArray());
        //Query player information
        if(!player.id().equals(pid)){
            GlobalConfig.cityError("[modyfyCompanyName] CompanyName only can be modified by it's owner!");
        }
        //Determine if the name is duplicate
        else if(player.getCompanyName().equals(newName)||GameDb.companyNameIsInUsed(newName)){//The name already used (or the same as the previous name)
            this.write(Package.fail(cmd,Common.Fail.Reason.roleNameDuplicated));
        }
        else if(!player.canBeModify()){ //The time is not up (return to the error code of the frozen state)
            this.write(Package.fail(cmd,Common.Fail.Reason.accountInFreeze));
        }else{
            player.setCompanyName(newName);
            player.setLast_modify_time(new Date().getTime());
            //Modify the player's unmodified building
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

    //1.Distribution center detailed data acquisition
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

    //2.Get player's building information (building information)
    public void getPlayerBuildingDetail(short cmd){
        Gs.BuildingSet.Builder builder = Gs.BuildingSet.newBuilder();
        City.instance().forEachBuilding(player.id(), (Building b)->{
            b.appendDetailProto(builder);
        });
        //Get rented warehouse based on player id
        List<WareHouseRenter> renters = WareHouseManager.instance().getWareHouseByRenterId(player.id());
        renters.forEach(r->{
            r.appendDetailProto(builder);
        });
        this.write(Package.create(cmd, builder.build()));
    }
    //3.Set warehouse rental information
    public void setWareHouseRent(short cmd, Message message){
        Gs.SetWareHouseRent info = (Gs.SetWareHouseRent) message;
        if(WareHouseManager.instance().settingWareHouseRentInfo(player.id(),info)){
            this.write(Package.create(cmd, info));
        }else
            this.write(Package.fail(cmd));
    }

    //Close rental
    public void closeWareHouseRent(short cmd, Message message){
        Gs.SetWareHouseRent info = (Gs.SetWareHouseRent) message;
        if(WareHouseManager.instance().closeWareHouseRentInfo(player.id(),info)){
            this.write(Package.create(cmd, info));
        }else
            this.write(Package.fail(cmd));
    }

    //4.Delete the specified number of products
    public void delItems(short cmd, Message message) throws Exception {
        Gs.ItemsInfo c = (Gs.ItemsInfo) message;
        Item item = new Item(c.getItem());
        UUID bid = Util.toUuid(c.getBuildingId().toByteArray());
        IStorage storage = IStorage.get(bid, player);//Get building warehouse information
        if(c.hasOrderId()){
            storage = WareHouseUtil.getWareRenter(bid, c.getOrderId());
        }
        if(storage == null)
            return;
        if(storage.delItem(item)){
            GameDb.saveOrUpdate(storage);//Modify the database
            //Synchronize cache data
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
    //5.Renting warehouse of distribution center
    public void rentWareHouse(short cmd, Message message){
        Gs.rentWareHouse rentInfo = (Gs.rentWareHouse) message;
        Gs.rentWareHouse rentWareHouse = WareHouseManager.instance().rentWareHouse(player, rentInfo);
        if(rentWareHouse!=null) {
            Gs.detailWareHouseRenter.Builder builder = Gs.detailWareHouseRenter.newBuilder();
            //Return all rented warehouses
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


    //7.Purchase of listed goods (including purchase of items in tenant warehouse)
    public void buyInShelfGood(short cmd, Message message) throws Exception {
        Gs.BuyInShelfGood inShelf = (Gs.BuyInShelfGood) message;
        if(inShelf.getGood().getPrice()<0)
            return;
        UUID bid = Util.toUuid(inShelf.getGood().getBuildingId().toByteArray());
        UUID wid = Util.toUuid(inShelf.getWareHouseId().toByteArray());
        //2.Determine if the id building to which the product belongs is a rented warehouse (based on orderid)
        WareHouseRenter sellRenter=null;
        WareHouseRenter buyRenter=null;
        Building sellBuilding = City.instance().getBuilding(bid);//Seller
        IShelf sellShelf = (IShelf) sellBuilding;
        IStorage buyStore = IStorage.get(wid, player);//buyer
        UUID sellOwnerId=sellBuilding.ownerId();
        //3.Whether the seller is a rented warehouse
        if(inShelf.getGood().hasOrderid()){
            //Indicate that it is a rented warehouse
            sellRenter = WareHouseUtil.getWareRenter(bid, inShelf.getGood().getOrderid());
            if(sellRenter==null)
                return;
            sellShelf = sellRenter;
            sellOwnerId = sellRenter.getRenterId();
        }
        //Whether the buyer is also a rented warehouse
        if(inShelf.hasOrderid()){
            buyRenter= WareHouseUtil.getWareRenter(wid, inShelf.getOrderid());
            if(buyRenter==null){
                return;
            }
            buyStore = buyRenter;
        }
        Item itemBuy = new Item(inShelf.getGood().getItem());
        Shelf.Content i = sellShelf.getContent(itemBuy.key);
        //4.If it does not correspond to the listed price or the number of shelves is less than the quantity to be purchased, it fails
        if(i == null || i.price != inShelf.getGood().getPrice() || i.n < itemBuy.n) {
            this.write(Package.fail(cmd,Common.Fail.Reason.numberNotEnough));
            return;
        }
        //5.Calculate the price (shipping + required value of goods)
        long cost = itemBuy.n*inShelf.getGood().getPrice();//Calculate the total value of goods
        //Shipping cost
        int freight = (int) (MetaData.getSysPara().transferChargeRatio * Math.ceil(IStorage.distance(buyStore, (IStorage) sellBuilding)))*itemBuy.n;
        //6.If the player's money is less than what is to be paid, the transaction fails
        if(player.money() < cost + freight) {
            this.write(Package.fail(cmd, Common.Fail.Reason.moneyNotEnough));
            return;
        }
        //7.The warehouse could not be stored and failed
        if(!buyStore.reserve(itemBuy.key.meta, itemBuy.n)) {
            this.write(Package.fail(cmd,Common.Fail.Reason.spaceNotEnough));
            return;
        }
        //========================
        //8.Start modifying data
        //8.1Get information about the owner of the product
        Player seller = GameDb.getPlayer(sellOwnerId);
        seller.addMoney(cost);//transaction
        player.decMoney(cost+freight);//Deduct goods + shipping
        //8.2Send income notification tips to sellers
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
        //8.3 Send message notification
        if(cost>=10000000){//Major transaction, transaction amount reaches 1000, broadcast information to the client, including player ID, transaction amount, time
            GameServer.sendToAll(Package.create(GsCode.OpCode.cityBroadcast_VALUE,Gs.CityBroadcast.newBuilder()
                    .setType(1)
                    .setSellerId(Util.toByteString(seller.id()))
                    .setBuyerId(Util.toByteString(player.id()))
                    .setCost(cost)
                    .setTs(System.currentTimeMillis())
                    .build()));
            LogDb.cityBroadcast(seller.id(),player.id(),cost,0,1);
        }
        //9.Logging
        int itemId = itemBuy.key.meta.id;
        int type = MetaItem.type(itemBuy.key.meta.id);//Get product type
        LogDb.playerIncome(seller.id(), cost,sellBuilding.type());
        Building buyBuilding = City.instance().getBuilding(wid);
        LogDb.playerPay(player.id(), cost,buyBuilding.type());
        LogDb.playerPay(player.id(), freight,buyBuilding.type());
        //9.1Record transportation logs (differentiated between building and tenant warehouse)
        if(sellRenter==null&&buyRenter==null) {
            //Record product quality and popularity
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
        //9.2Record shelf revenue and construction revenue information (distinguish between building and tenant warehouse)
        //8.6Record transaction log
        LogDb.payTransfer(player.id(), freight, bid, wid,itemBuy.key.meta.id, itemBuy.key.producerId, itemBuy.n);
        if(!inShelf.getGood().hasOrderid()) { //The goods are not in the rented warehouse
            //Get brand name
            BrandManager.BrandName brandName = BrandManager.instance().getBrand(seller.id(), itemId).brandName;
            String goodName=brandName==null?seller.getCompanyName():brandName.getBrandName();
            LogDb.buyInShelf(player.id(), seller.id(), itemBuy.n, inShelf.getGood().getPrice(),
                    itemBuy.key.producerId, sellBuilding.id(), wid, type, itemId, goodName, 0, sellBuilding.type(),0);
            LogDb.buildingIncome(bid, player.id(), cost, type, itemId);
        }
        else{//Purchased on tenant shelf (statistical log)
            LogDb.buyRenterInShelf(player.id(), seller.id(), itemBuy.n, inShelf.getGood().getPrice(),
                    itemBuy.key.producerId,sellRenter.getOrderId(), type, itemId);
            //Tenant shelf revenue record
            LogDb.renterShelfIncome(inShelf.getGood().getOrderid(),player.id(), cost, type, itemId);
        }
        //8.7.The seller reduces the number of shelves
        sellShelf.delshelf(itemBuy.key, itemBuy.n, false);
        IStorage sellStorage = (IStorage) sellShelf;
        sellStorage.consumeLock(itemBuy.key, itemBuy.n);
        //Update daily income
        if(sellRenter!=null){
            sellRenter.updateTodayIncome(cost);//Update today's income
        }else{
            sellBuilding.updateTodayIncome(cost);
        }
        buyStore.consumeReserve(itemBuy.key, itemBuy.n, inShelf.getGood().getPrice());
        GameDb.saveOrUpdate(Arrays.asList(player,seller,sellStorage,buyStore));
        //Synchronize cache data
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
    //8.Put on shelf
    public void putAway(short cmd, Message message) throws Exception {
        Gs.PutAway c = (Gs.PutAway) message;
        Item item = new Item(c.getItem());
        UUID bid = Util.toUuid(c.getBuildingId().toByteArray());
        Building building = City.instance().getBuilding(bid);
        IShelf s = null;
        //If there is an order number, it means that it is listed in the rented warehouse
        if(c.hasOrderId()){
            //Shelf from rented warehouse
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
            //Synchronize cache data
            if(s instanceof WareHouse)
                WareHouseManager.updateWareHouseMap((WareHouse)s);
            else if(s instanceof WareHouseRenter)
                WareHouseManager.updateWareHouseMap((WareHouseRenter)s);
            this.write(Package.create(cmd,c));
        }else{
            this.write(Package.fail(cmd));
        }
    }

    //9.Modify the goods listed in the rented warehouse
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
            //Synchronize cache data
            WareHouse wareHouse = wareRenter.getWareHouse();
            WareHouseManager.wareHouseMap.put(wareHouse.id(),wareHouse);
            this.write(Package.create(cmd,r));
        }else
            this.write(Package.fail(cmd));
    }

    //10.Off shelf (including other buildings and rented warehouses)
    public void soldOutShelf(short cmd, Message message) throws Exception {
        Gs.SoldOutShelf s = (Gs.SoldOutShelf) message;
        Item item = new Item(s.getItem());
        Building building = City.instance().getBuilding(Util.toUuid(s.getBuildingId().toByteArray()));
        IShelf sf = null;
        //Case 1, the warehouse is rented off the shelf
        if(s.hasOrderId()){
            //Remove from the rented warehouse
            WareHouseRenter renter = WareHouseUtil.getWareRenter(Util.toUuid(s.getBuildingId().toByteArray()),s.getOrderId());
            if(renter==null)
                return;
            sf = renter;
        }else {//General building off the shelf
            if(building == null || !(building instanceof IShelf) || !building.canUseBy(player.id()) || building.outOfBusiness())
                return;
            if(building instanceof RetailShop && item.key.meta instanceof MetaMaterial)
                return;
            sf = (IShelf) building;
        }
        if (sf.delshelf(item.key, item.n, true)) {
            //Synchronize cache data
            if(sf instanceof WareHouse)
                WareHouseManager.updateWareHouseMap((WareHouse)sf);
            else if(sf instanceof  WareHouseRenter)
                WareHouseManager.updateWareHouseMap((WareHouseRenter)sf);
            GameDb.saveOrUpdate(sf);
            this.write(Package.create(cmd, s));
        } else
            this.write(Package.fail(cmd));
    }

    //11.Set up tenant warehouse automatic replenishment
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
            //Handling automatic replenishment
            if(content != null && content.autoReplenish){
                IShelf.updateAutoReplenish(shelf,itemKey);
            }
            //Synchronous Data
            WareHouseManager.updateWareHouseMap(renter);
            GameDb.saveOrUpdate(shelf);
            this.write(Package.create(cmd, c));
        }
        else
            this.write(Package.fail(cmd));
    }

    //12.Get the income information of the distribution center today
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

    //13.detailWareHouse Tenant details, get the current tenant details according to the distribution center
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

    //14.Get a summary of the data in the distribution center
    public void  queryWareHouseSummary(short cmd){
        Gs.WareHouseSummary.Builder builder = Gs.WareHouseSummary.newBuilder();
        //Convenient for all coordinate systems
        City.instance().forAllGrid(g -> {
            Gs.WareHouseSummary.Info.Builder info=builder.addInfoBuilder();
            GridIndex gi = new GridIndex(g.getX(), g.getY());
            info.setIdx(gi.toProto());
            AtomicInteger n = new AtomicInteger();
            //Convenience grid
            g.forAllBuilding(building->{
                if(building instanceof WareHouse&&!building.outOfBusiness())
                    n.incrementAndGet();
            });
            info.setCount(n.intValue());
        });
        this.write(Package.create(cmd,builder.build()));
    }

    //15.To obtain the details of the distribution center data, the client passes a center coordinate
    public void queryWareHouseDetail(short cmd,Message message){
        Gs.QueryWareHouseDetail c = (Gs.QueryWareHouseDetail) message;
        GridIndex center = new GridIndex(c.getCenterIdx().getX(),c.getCenterIdx().getY());
        Gs.WareHouseDetail.Builder builder = Gs.WareHouseDetail.newBuilder();
        //Traverse the city grid around the center coordinates
        City.instance().forEachGrid(center.toSyncRange(), (grid)->{
            Gs.WareHouseDetail.GridInfo.Builder info = builder.addInfoBuilder();
            info.getIdxBuilder().setX(grid.getX()).setY(grid.getY());//Parameter 1
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
    //16.transport
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
        //Freight = distance * freight ratio * quantity
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
        //Logging
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
        {//Handling automatic replenishment
            Shelf.Content srcContent = srcShelf.getContent(item.key);
            Shelf.Content dstContent = dstShelf.getContent(item.key);
            if(srcContent != null && srcContent.autoReplenish){
                //Update automatic restocking shelves
                IShelf.updateAutoReplenish(srcShelf,item.key);
            }
            if(dstContent != null && dstContent.autoReplenish){
                //Update automatic restocking shelves
                IShelf.updateAutoReplenish(dstShelf,item.key);
            }
        }
        //Synchronous Data
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


    //Not querying guild information based on id in guild
    public void getOneSocietyInfo(short cmd, Message message)
    {
        UUID societyId = Util.toUuid(((Gs.Id) message).getId().toByteArray());
        Society society = SocietyManager.getSociety(societyId);
        if (society != null)
        {
            this.write(Package.create(cmd, SocietyManager.toSocietyDetailProto(society)));

        }
    }

    //Query the number of registered players
    public void getPlayerAmount(short cmd) {
        long playerAmount = GameDb.getPlayerAmount();
        this.write(Package.create(cmd, Gs.PlayerAmount.newBuilder().setPlayerAmount(playerAmount).build()));
    }

    //Query building name
    public void queryBuildingName(short cmd, Message message) {
        Gs.Id id = (Gs.Id) message;
        UUID buildingId = Util.toUuid(id.getId().toByteArray());
        Building building = City.instance().getBuilding(buildingId);
        if (building == null || building.outOfBusiness()) {
            return;
        }
        this.write(Package.create(cmd, Gs.Str.newBuilder().setStr(building.getName()).build()));
    }
    //Residential recommended price √
    public void queryApartmentRecommendPrice(short cmd, Message message) {
        Gs.QueryBuildingInfo msg = (Gs.QueryBuildingInfo) message;
        UUID buildingId = Util.toUuid(msg.getBuildingId().toByteArray());
        UUID playerId = Util.toUuid(msg.getPlayerId().toByteArray()); //Being not
        Building building = City.instance().getBuilding(buildingId);
        if (building == null || building.type() != MetaBuilding.APARTMENT) {
            return;
        }
        Apartment apartment = (Apartment) building;
        // Player Residence Rating
        double brandScore = GlobalUtil.getBrandScore(apartment.getTotalBrand(), apartment.type());
        double retailScore = GlobalUtil.getBuildingQtyScore(apartment.getTotalQty(), apartment.type());
        double curRetailScore = (brandScore + retailScore) / 2;
        // Player housing boom
        double prosperityScore = ProsperityManager.instance().getBuildingProsperityScore(building);
        double guidePrice = GuidePriceMgr.instance().getApartmentGuidePrice(curRetailScore,prosperityScore);
        //NPC Expected Consumption = Industry Wage (may be adjusted to City Wage) * Proportion of Residential Expenditure
        double moneyRatio = MetaData.getBuildingSpendMoneyRatio(building.type());
        double salary = City.instance().getIndustrySalary(building.type());
        Gs.ApartmentRecommendPrice.Builder builder = Gs.ApartmentRecommendPrice.newBuilder();
        builder.setNpc(moneyRatio * salary).setGuidePrice(guidePrice);
        this.write(Package.create(cmd,builder.setBuildingId(msg.getBuildingId()).build()));

    }

    //Recommended price of raw materials √
    public void queryMaterialRecommendPrice(short cmd, Message message) {
        Gs.QueryBuildingInfo msg = (Gs.QueryBuildingInfo) message;
        UUID buildingId = Util.toUuid(msg.getBuildingId().toByteArray()); // Building id
        UUID playerId = Util.toUuid(msg.getPlayerId().toByteArray()); //Being not
        Building building = City.instance().getBuilding(buildingId);
        if (building == null || building.type() != MetaBuilding.MATERIAL) {
            return;
        }
        this.write(Package.create(cmd, GuidePriceMgr.instance().getMaterialPrice(buildingId)));
    }


    //Recommended price of processing plant goods √
    public void queryProduceDepRecommendPrice(short cmd, Message message) {
        Gs.QueryBuildingInfo msg = (Gs.QueryBuildingInfo) message;
        UUID buildingId = Util.toUuid(msg.getBuildingId().toByteArray());
        UUID playerId = Util.toUuid(msg.getPlayerId().toByteArray()); //Being not
        Building building = City.instance().getBuilding(buildingId);
        if (building == null || building.type() != MetaBuilding.PRODUCE || building.outOfBusiness()) {
            return;
        }
        ProduceDepartment department = (ProduceDepartment) building;
        List<Item> itemList = department.store.getAllItem();
        Map<Integer, Double> map = new HashMap<>();
        if (!itemList.isEmpty() && itemList!=null) {
            itemList.stream().forEach(item ->
            {
                double score = GlobalUtil.getBrandScore(item.getKey().getTotalQty(), item.key.meta.id)+GlobalUtil.getGoodQtyScore(item.getKey().getTotalQty(), item.key.meta.id, MetaData.getGoodQuality(item.key.meta.id));
                map.put(item.key.meta.id, score / 2);
            });

        } else {
            List<Item> items = department.getShelf().getAllSaleDetail();
            items.stream().forEach(item ->
            {
                double score = GlobalUtil.getBrandScore(item.getKey().getTotalQty(), item.key.meta.id)+GlobalUtil.getGoodQtyScore(item.getKey().getTotalQty(), item.key.meta.id, MetaData.getGoodQuality(item.key.meta.id));
                map.put(item.key.meta.id, score / 2);
            });

        }
        this.write(Package.create(cmd, GuidePriceMgr.instance().getProducePrice(map, buildingId)));
    }


    //Recommended retail store prices √
    public void queryRetailShopRecommendPrice(short cmd, Message message) {
        Gs.QueryBuildingInfo msg = (Gs.QueryBuildingInfo) message;
        UUID buildingId = Util.toUuid(msg.getBuildingId().toByteArray());
        UUID playerId = Util.toUuid(msg.getPlayerId().toByteArray()); //Being not
        Building building = City.instance().getBuilding(buildingId);
        if (building == null || building.type() != MetaBuilding.RETAIL || building.outOfBusiness()) {
            return;
        }
        RetailShop retailShop = (RetailShop) building;
        List<Item> items = retailShop.getStore().getAllItem();
        Map<Integer, Double> map = new HashMap<>();
        if (!items.isEmpty() && items!=null) {
            items.stream().forEach(item ->
            {
                double score = GlobalUtil.getBrandScore(item.getKey().getTotalQty(), item.key.meta.id)+GlobalUtil.getGoodQtyScore(item.getKey().getTotalQty(), item.key.meta.id, MetaData.getGoodQuality(item.key.meta.id));
                map.put(item.key.meta.id, score / 2);
            });

        } else {
            List<Item> itemList = retailShop.getShelf().getAllSaleDetail();
            items.stream().forEach(item ->
            {
                double score = GlobalUtil.getBrandScore(item.getKey().getTotalQty(), item.key.meta.id)+GlobalUtil.getGoodQtyScore(item.getKey().getTotalQty(), item.key.meta.id, MetaData.getGoodQuality(item.key.meta.id));
                map.put(item.key.meta.id, score / 2);
            });

        }

        this.write(Package.create(cmd,GuidePriceMgr.instance().getRetailPrice(map, buildingId)));
    }

    //Institute recommended pricing
    public void queryLaboratoryRecommendPrice(short cmd, Message message) {
        Gs.QueryBuildingInfo msg = (Gs.QueryBuildingInfo) message;
        UUID buildingId = Util.toUuid(msg.getBuildingId().toByteArray());
        UUID playerId = Util.toUuid(msg.getPlayerId().toByteArray()); //Being not
        Building building = City.instance().getBuilding(buildingId);
        if (building == null || building.type() != MetaBuilding.TECHNOLOGY || building.outOfBusiness()) {
            return;
        }
        this.write(Package.create(cmd,GuidePriceMgr.instance().getLabOrProPrice(buildingId,true)));
    }

    //Promotion and recommendation price √
    public void queryPromotionRecommendPrice(short cmd, Message message) {
        Gs.QueryBuildingInfo msg = (Gs.QueryBuildingInfo) message;
        UUID buildingId = Util.toUuid(msg.getBuildingId().toByteArray());
        UUID playerId = Util.toUuid(msg.getPlayerId().toByteArray()); //Being not
        Building building = City.instance().getBuilding(buildingId);
        if (building == null || building.type() != MetaBuilding.PROMOTE || building.outOfBusiness()) {
            return;
        }
        this.write(Package.create(cmd,GuidePriceMgr.instance().getLabOrProPrice(buildingId,false)));
    }

    // Recommended land transaction price
    public void queryGroundRecommendPrice(short cmd) {
        this.write(Package.create(cmd, Gs.Num.newBuilder().setNum((int) GuidePriceMgr.instance().getGroundPrice()).build()));
    }



    //Raw material competitiveness
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

    //Residential competitiveness
    public void apartmentGuidePrice(short cmd, Message message) {
        Gs.AartmentMsg msg = (Gs.AartmentMsg) message;
        UUID buildingId = Util.toUuid(msg.getBuildingId().toByteArray());
        UUID playerId = Util.toUuid(msg.getPlayerId().toByteArray());
        Building building = City.instance().getBuilding(buildingId);
        Apartment apartment = (Apartment) building;
        //Current building score
        double brandScore = GlobalUtil.getBrandScore(apartment.getTotalBrand(), apartment.type());
        double apartmentScore = GlobalUtil.getBuildingQtyScore(apartment.getTotalQty(), apartment.type());
        double score = (brandScore + apartmentScore) / 2;
        List<Double> info = BuildingUtil.getApartment();
        Gs.AartmentMsg.ApartmentPrice.Builder apartmentPrice = Gs.AartmentMsg.ApartmentPrice.newBuilder();
        apartmentPrice.setAvgPrice(info.get(0)).setAvgScore(info.get(1)).setScore(score);
        this.write(Package.create(cmd, Gs.AartmentMsg.newBuilder().addApartmentPrice(apartmentPrice.build()).setBuildingId(msg.getBuildingId()).build()));
    }

    //Processing plant competitiveness
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

	//Retail store competitiveness
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
			//Current product rating (taken from the warehouse)
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
				//Current building score
				double brandScore = GlobalUtil.getBrandScore(retailShop.getTotalBrand(), retailShop.type());
				double retailScore = GlobalUtil.getBuildingQtyScore(retailShop.getTotalQty(), retailShop.type());
				double curRetailScore = (brandScore + retailScore) / 2;
				goodMap.addItemId(itemId).addAllGudePrice(Arrays.asList(avgPrice, avgGoodScore, BuildingUtil.getRetail(), (curBrandScore + curQtyScore) / 2, curRetailScore));
			}
			builder.addGoodMap(goodMap.build());
		}
		this.write(Package.create(cmd, builder.setBuildingId(msg.getBuildingId()).build()));
	}
	//Promote competitiveness
	public void promotionGuidePrice(short cmd, Message message) {
		Gs.PromotionMsg msg = (Gs.PromotionMsg) message;
		UUID buildingId = Util.toUuid(msg.getBuildingId().toByteArray());
		UUID playerId = Util.toUuid(msg.getPlayerId().toByteArray());
		//Four types of promotion
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
		//Promotion capacity across the city
		double promotionInfo = GlobalUtil.getPromotionInfo();
		Gs.PromotionMsg.PromotionPrice.Builder promotionPrice = Gs.PromotionMsg.PromotionPrice.newBuilder();
		promotionPrice.setCurAbilitys((sumAbilitys / 4)).setGuidePrice(price).setAvgAbility(promotionInfo);
		this.write(Package.create(cmd, Gs.PromotionMsg.newBuilder().addProPrice(promotionPrice.build()).setBuildingId(msg.getBuildingId()).build()));
	}
	//Research competitiveness
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
		//Total probability
		Map<Integer, Double> prob = laboratory.getTotalSuccessProb();
		double evaProb = prob.get(Gs.Eva.Btype.EvaUpgrade_VALUE);
		double goodProb = prob.get(Gs.Eva.Btype.InventionUpgrade_VALUE);
		//R&D capabilities across the city
		double labProb = GlobalUtil.getLaboratoryInfo();
		Gs.LaboratoryMsg.LaboratoryPrice.Builder labPrice = Gs.LaboratoryMsg.LaboratoryPrice.newBuilder();
		labPrice.setAvgProb(labProb).setCurProb((evaProb + goodProb) / 2).setGuidePrice(price);
		this.write(Package.create(cmd, Gs.LaboratoryMsg.newBuilder().addLabPrice(labPrice.build()).setBuildingId(msg.getBuildingId()).build()));
	}

    //Query city homepage
    public void queryCityIndex(short cmd){
        Gs.QueryCityIndex.Builder builder = Gs.QueryCityIndex.newBuilder();
        Map<Integer, Integer> npcMap = NpcManager.instance().countNpcByType();
        //1. City Information (Name)
        MetaCity city = MetaData.getCity();
        builder.setCityName(city.name);
        //2. Demographic information
        Gs.QueryCityIndex.HumanInfo.Builder humanInfo = Gs.QueryCityIndex.HumanInfo.newBuilder();
        Map<String, Integer> genderSex = CityUtil.genderSex(GameDb.getAllPlayer());
        long socialNum =NpcManager.instance().getUnEmployeeNpcCount();//Unemployed (social welfare personnel)
        long npcNum = NpcManager.instance().getNpcCount()+socialNum;//All npc quantity
        humanInfo.setBoy(genderSex.get("boy"));
        humanInfo.setGirl(genderSex.get("girl"));
        humanInfo.setCitizens(npcNum);
        builder.setSexNum(humanInfo);
        //3. Set city summary information
        Gs.QueryCityIndex.CitySummary.Builder citySummary = Gs.QueryCityIndex.CitySummary.newBuilder();
        //Land auction information
        int groundSum = 0;
        for (Map.Entry<Integer, MetaGroundAuction> mg : MetaData.getGroundAuction().entrySet()) {
            MetaGroundAuction value = mg.getValue();
            groundSum+=value.area.size();
        }
        int auctionNum = GameDb.countGroundInfo();
        citySummary.setTotalNum(groundSum).setAuctionNum(auctionNum);
        //4.Set city freight
        citySummary.setTransferCharge(MetaData.getSysPara().transferChargeRatio);
        //5.Set average salary
        citySummary.setAvgSalary((long) City.instance().getAvgIndustrySalary());//average salary
        citySummary.setUnEmployedNum(socialNum);//Unemployed
        citySummary.setEmployeeNum(npcNum - socialNum);//Serving officers
        citySummary.setUnEmployedPercent((int)Math.ceil((double)socialNum/npcNum*100));//Unemployment rate (number/total*100)
        //Average assets (different welfare npc)
        Gs.QueryCityIndex.CitySummary.AvgProperty.Builder avgProperty = Gs.QueryCityIndex.CitySummary.AvgProperty.newBuilder();
        Map<Integer, Long> moneyMap = CityUtil.cityAvgProperty();//City average assets
        avgProperty.setSocialMoney(moneyMap.get(1));
        avgProperty.setEmployeeMoney(moneyMap.get(0));
        citySummary.setAvgProperty(avgProperty);
        builder.setSummary(citySummary);
        //Wage increase (refers to the fact that npc cannot afford to buy goods, and then the difference between the goods and npc money accumulates, / the number of industries is the next increase)
        double v = CityUtil.increaseRatio();
        builder.setSalaryIncre(v);
        //8.Citizen security benefits (treatment of not working npc)
        int socialMoney = CityUtil.socialMoney();
        builder.setSocialWelfare(socialMoney);
        //9.tax
        long tax = CityUtil.getTax();
        builder.setTax(tax);
        //10.City funds (bonus pool)
        builder.setMoneyPool(MoneyPool.instance().money());
        this.write(Package.create(cmd,builder.build()));
    }

    //Modify building name
    public void updateBuildingName(short cmd, Message message)
    {
        Gs.UpdateBuildingName msg = (Gs.UpdateBuildingName) message;
        UUID bid = Util.toUuid(msg.getBuildingId().toByteArray());
        Building building = City.instance().getBuilding(bid);
        //Set the modification time of the building (change it every 7 days)
        if(building.canBeModify()) {
            building.setName(msg.getName());
            building.setLast_modify_time(new Date().getTime());
            GameDb.saveOrUpdate(building);
            this.write(Package.create(cmd, building.toProto()));
        }else{
            this.write(Package.fail(cmd,Common.Fail.Reason.timeNotSatisfy));
        }
    }
    //Query information of raw material factory
    public void queryMaterialInfo(short cmd, Message message)
    {
        Gs.QueryBuildingInfo msg = (Gs.QueryBuildingInfo) message;
        UUID buildingId = Util.toUuid(msg.getBuildingId().toByteArray());
        UUID playerId = Util.toUuid(msg.getPlayerId().toByteArray());
        Building building = City.instance().getBuilding(buildingId);

        Gs.MaterialInfo.Builder builder=Gs.MaterialInfo.newBuilder();
        builder.setSalary(building.salaryRatio);
        builder.setStaffNum(building.getWorkerNum());
        //Basic building information
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
    //Query processing plant information
    public void queryProduceDepInfo(short cmd,Message message){
        Gs.QueryBuildingInfo msg = (Gs.QueryBuildingInfo) message;
        UUID buildingId = Util.toUuid(msg.getBuildingId().toByteArray());
        UUID playerId = Util.toUuid(msg.getPlayerId().toByteArray());
        Building building = City.instance().getBuilding(buildingId);

        Gs.ProduceDepInfo.Builder builder = Gs.ProduceDepInfo.newBuilder();
        builder.setSalary(building.salaryRatio);
        builder.setStaffNum(building.getWorkerNum());
        //Basic building information
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
    //Query retail store or residential information
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
        //Basic building information
        Gs.BuildingGeneral.Builder buildingInfo = buildingToBuildingGeneral(building);
        builder.setBuildingInfo(buildingInfo);

        Map<Integer,Double> brandMap=new HashMap<Integer,Double>();
        Map<Integer,Double> qtyMap=new HashMap<Integer,Double>();
        //Value of a single building
        BrandManager.instance().getBuildingBrandOrQuality(building, brandMap, qtyMap);
        double basicBrand=BrandManager.instance().getValFromMap(brandMap, Gs.ScoreType.BasicBrand_VALUE);
        double addBrand=BrandManager.instance().getValFromMap(brandMap, Gs.ScoreType.AddBrand_VALUE);
        double totalBrand=BrandManager.instance().getValFromMap(brandMap,building.type());
        double basicQuality=BrandManager.instance().getValFromMap(qtyMap, Gs.ScoreType.BasicQuality_VALUE);
        double addQuality=BrandManager.instance().getValFromMap(qtyMap, Gs.ScoreType.AddQuality_VALUE);

        //Popularity score
        double brandScore=GlobalUtil.getBrandScore(totalBrand,building.type());
        //Quality score
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
    //Promotion of company information (revised version)
    public void queryPromotionCompanyInfo(short cmd,Message message){  //TODO
        Gs.QueryBuildingInfo msg = (Gs.QueryBuildingInfo) message;
        UUID bid = Util.toUuid(msg.getBuildingId().toByteArray());
        Building building = City.instance().getBuilding(bid);
        if(!(building instanceof PromotionCompany)){
            return;
        }
        this.write(Package.create(cmd, building.toProto()));
    }

    //Query warehouse information
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
        //Basic building information
        Gs.BuildingGeneral.Builder buildingInfo = buildingToBuildingGeneral(building);
        builder.setBuildingInfo(buildingInfo);
        this.write(Package.create(cmd, builder.build()));
    }
    //Search Institute Information
    public void queryLaboratoryInfo(short cmd,Message message){
        Gs.QueryBuildingInfo msg = (Gs.QueryBuildingInfo) message;
        UUID bid = Util.toUuid(msg.getBuildingId().toByteArray());
        Building building = City.instance().getBuilding(bid);
        if(!(building instanceof Technology)){
            return;
        }
        this.write(Package.create(cmd, building.toProto()));
    }
    /*Query brand information*/
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
            // Use company name for now
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
                //Successful verification
                paySmCache.remove(playerId);
                //Server signature verification test
                ccapi.CcOuterClass.DisChargeReq req = sv.getDisChargeReq();
                //Calculate the hash
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
                    //Verification: construct new pubkey and signature
                    byte[] sigbts = Hex.decode(req.getSignature().toStringUtf8());
                    ECKey.ECDSASignature newsig = new ECKey.ECDSASignature(
                            new BigInteger(1,Arrays.copyOfRange(sigbts, 0, 32)),
                            new BigInteger(1,Arrays.copyOfRange(sigbts, 32, 64))
                    );
                    ECKey newpubkey = ECKey.fromPublicOnly(pubKey);
                    boolean pass =  newpubkey.verify(hActiveSing ,newsig); //Verified

                    int t = 0 ;
                }catch (Exception e){
                    int t = 0;
                }

                //double dddAmount = GameDb.calDDDFromEEE(Double.parseDouble(req.getAmount()));
                double dddAmount = Double.parseDouble(req.getAmount());
                //Add transaction
                ddd_purchase pur = new ddd_purchase(Util.toUuid(req.getPurchaseId().getBytes()),playerId, -dddAmount ,"",req.getEthAddr());
                if(dddPurchaseMgr.instance().addPurchase(pur)){
                    try{
                        //Forward to ccapi server
                        ccapi.CcOuterClass.DisChargeRes response = chainRpcMgr.instance().DisChargeReq(req);

                        //Because the withdrawal operation is performed on the ddd server and the time is relatively long, the player needs to be reminded that the withdrawal request has been processed
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
                //Prompt timeout
                this.write(Package.create(cmd,msg.toBuilder().setErrorCode(1).build()));
            }
        }else{
            //verification failed
            this.write(Package.create(cmd,msg.toBuilder().setErrorCode(2).build()));
        }
        int t = 0 ;
    }

    //Get general information about the building (extract, yty)
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

        //Server signature verification test
        //Calculate the hash
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
            //Verification: construct new pubkey and signature
            byte[] sigbts = Hex.decode(req.getSignature().toStringUtf8());
            //byte[] sigbts1 = Hex.decode(req.getSignature().toString(16));
            ECKey.ECDSASignature newsig = new ECKey.ECDSASignature(
                    new BigInteger(1,Arrays.copyOfRange(sigbts, 0, 32)),
                    new BigInteger(1,Arrays.copyOfRange(sigbts, 32, 64))
            );
            ECKey newpubkey = ECKey.fromPublicOnly(pubKey);
            boolean pass =  newpubkey.verify(hSignCharge ,newsig); //Verified
            int t = 0 ;
        }catch (Exception e){

        }

        //Add transaction
        double dddAmount = Double.parseDouble(req.getAmount());
        ddd_purchase pur = new ddd_purchase(Util.toUuid(req.getPurchaseId().getBytes()) , playerId, dddAmount ,"","");
        if(dddPurchaseMgr.instance().addPurchase(pur)){
            //Forward to ccapi server
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

    //Query all the raw material list information of the raw material factory
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
            //Query eva information
            Eva eva = EvaManager.getInstance().getEva(playerId, materialId, Gs.Eva.Btype.ProduceSpeed_VALUE);
            //The production speed queryBuildingMaterialInfo is equal to the number of employees * basic value * (1+eva bonus)
            double numOneSec = workerNum * item.n * (1 + EvaManager.getInstance().computePercent(eva));
            itemInfo.setKey(materialId).setNumOneSec(numOneSec);
            materialInfo.addItems(itemInfo);
        }
        this.write(Package.create(cmd,materialInfo.build()));
    }

    //Query detailed information of all the product list of the processing plant
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
            //Query eva information
            Eva speedEva = EvaManager.getInstance().getEva(playerId, goodId, Gs.Eva.Btype.ProduceSpeed_VALUE);
            Eva qtyEva = EvaManager.getInstance().getEva(playerId, goodId, Gs.Eva.Btype.Quality_VALUE);
            //1.Production speed is equal to the number of employees * basic value * (1+eva bonus)
            double numOneSec = workerNum * good.n * (1 + EvaManager.getInstance().computePercent(speedEva));
            //2.Popularity score
            int brand=good.brand;//Base value
            brand += BrandManager.instance().getBrand(playerId, goodId).getV();//Current brand value
            double brandScore = GlobalUtil.getBrandScore(brand, goodId);
            //3.Quality score
            double quality = good.quality;
            quality =quality * (1+EvaManager.getInstance().computePercent(qtyEva));
            double qtyScore=GlobalUtil.getGoodQtyScore(quality, goodId,good.quality);
            //4.Brand name (if not, take the company name)
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

    //Query the detailed information of the promotion company's product promotion list
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
            //1 Current product awareness information
            int brand = good.brand;
            BrandManager.BrandInfo brandInfo = BrandManager.instance().getBrand(playerId,goodType);
            brand += brandInfo.getV();
            //2 Current popularity rating
            double brandScore=GlobalUtil.getBrandScore(brand,goodType);
            Gs.PromotionItemInfo.ItemInfo.Builder item = Gs.PromotionItemInfo.ItemInfo.newBuilder();
            item.setItemId(goodType).setBrand(brand).setBrandScore(brandScore);
            itemInfo.addItems(item);
        }
        this.write(Package.create(cmd,itemInfo.build()));
    }

    //Query shelf data
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

    //Query warehouse data
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

    /*Query the status of construction production line*/
    public void queryBuildingProduceStatue(short cmd,Message message){
        Gs.Id id = (Gs.Id) message;
        UUID buildingId = Util.toUuid(id.getId().toByteArray());
        Building building = City.instance().getBuilding(buildingId);
        Gs.BuildingProduceStatue.Builder builder = Gs.BuildingProduceStatue.newBuilder();
        if(null!=building&&building instanceof FactoryBase){
            FactoryBase factory = (FactoryBase) building;
            List<LineBase> lines = factory.lines;
            if(building.getState()==Gs.BuildingState.SHUTDOWN_VALUE){  //Closed state
                builder.setStatue(Gs.BuildingProduceStatue.Statue.StopBusiness);
            }else{
                //1.No production line, the production line is idle
                if(lines.size()==0){
                    builder.setStatue(Gs.BuildingProduceStatue.Statue.LineUnUsed);
                }else {//Has production line
                    //2.Whether the space is sufficient, get the status of the first production line, and set the product id
                    LineBase lineBase = lines.get(0);
                    builder.setItemId(lineBase.item.id);
                    if(lineBase.pause) {//If the production line status is suspended
                        if (!factory.hasEnoughMaterial(lineBase, factory.ownerId())) {	//3.Insufficient raw materials
                            builder.setStatue(Gs.BuildingProduceStatue.Statue.MaterialNotEnough);
                        }else if(factory.store.availableSize()<=0){	//4.not enough space
                            builder.setStatue(Gs.BuildingProduceStatue.Statue.StoreCapacityFull);
                        }
                    }else{//5.in production
                        builder.setStatue(Gs.BuildingProduceStatue.Statue.InProduction);
                    }
                }
            }
        } else if (null!=building&&building instanceof ScienceBuildingBase) {
            ScienceBuildingBase buildingBase = (ScienceBuildingBase) building;
            List<ScienceLineBase> line = buildingBase.line;
            if (buildingBase.getState() == Gs.BuildingState.SHUTDOWN_VALUE) {  // Closed
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
    /*Query offline notification*/
    public void queryOffLineInformation(short cmd){
        Gs.UnLineInformation playerUnLineInformation = OffLineInformation.instance().getPlayerUnLineInformation(player.id());
        this.write(Package.create(cmd,playerUnLineInformation));
    }

    //Check building prosperity
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


    /*Query the summary information of small map building categories*/
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

    /*Query the detailed information of the city's architectural categories on the small map*/
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
                    if (b.state == Gs.BuildingState.SHUTDOWN_VALUE) {//Not open, no other building data added
                        typeBuilding.setIsopen(false);
                    } else {
                        typeBuilding.setIsopen(true);
                        Gs.TypeBuildingDetail.GridInfo.BuildingSummary.Builder summary = Gs.TypeBuildingDetail.GridInfo.BuildingSummary.newBuilder();
                        //General information settings
                        summary.setOwnerId(Util.toByteString(b.ownerId()))
                                .setPos(b.coordinate().toProto()).setName(b.getName())
                                .setMetaId(b.metaId());
                        if (b instanceof IShelf) {       //Sale information for shelf construction
                            IShelf shelf = (IShelf) b;
                            summary.setShelfCount(shelf.getTotalSaleCount());
                        } else if (b instanceof Apartment) {//Housing type information
                            Apartment apartment = (Apartment) b;
                            // Player Residence Rating
                            double brandScore = GlobalUtil.getBrandScore(apartment.getTotalBrand(), apartment.type());
                            double retailScore = GlobalUtil.getBuildingQtyScore(apartment.getTotalQty(), apartment.type());
                            double curRetailScore = (brandScore + retailScore) / 2;
                            // Player housing boom
                            double prosperityScore = ProsperityManager.instance().getBuildingProsperityScore(b);
                            double guidePrice = GuidePriceMgr.instance().getApartmentGuidePrice(curRetailScore, prosperityScore);
                            Gs.TypeBuildingDetail.GridInfo.BuildingSummary.ApartmentSummary.Builder apartSummary = Gs.TypeBuildingDetail.GridInfo.BuildingSummary.ApartmentSummary.newBuilder();
                            apartSummary.setCapacity(apartment.getCapacity())
                                    .setRent(apartment.cost())
                                    //Calculate total score and recommended pricing
                                    .setGuidePrice((int) guidePrice)
                                    .setRenter(apartment.getRenterNum());
                            summary.setApartmentSummary(apartSummary);
                        } else if (b instanceof ScienceBuildingBase) {/*Research Institute and Promotion Company*/
                            ScienceBuildingBase science = (ScienceBuildingBase) b;
                            summary.setShelfCount(science.getShelf().getAllNum());
                        }
                        typeBuilding.setBuildingInfo(summary);
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
            type=21;//Raw material factory-raw materials
        }else if(buildType==Gs.PlayerIncomePay.BuildType.PRODUCE.getNumber()){
            type=22;//Processing Factory-Goods
        }else if(buildType==Gs.PlayerIncomePay.BuildType.TECHNOLOGY.getNumber()){
            type = 15; //Research Institute-Research Points
        }else if(buildType==Gs.PlayerIncomePay.BuildType.PROMOTE.getNumber()){
            type = 16; //Promotion Company-Research Points
        }
        Gs.PlayerIncomePay.Builder build=Gs.PlayerIncomePay.newBuilder();
        build.setPlayerId(((Gs.PlayerIncomePay) message).getPlayerId()).setBType(((Gs.PlayerIncomePay) message).getBType()).setIsIncome(isIncome);

        long yestodayStartTime=DateUtil.todayStartTime();
        long todayStartTime=System.currentTimeMillis();
        List<Document> list=null;
        if(isIncome){//income
            if(buildType==Gs.PlayerIncomePay.BuildType.MATERIAL.getNumber()||buildType==Gs.PlayerIncomePay.BuildType.PRODUCE.getNumber()
                    ||buildType==Gs.PlayerIncomePay.BuildType.TECHNOLOGY.getNumber()||buildType==Gs.PlayerIncomePay.BuildType.PROMOTE.getNumber()){
                list = LogDb.daySummaryShelfIncome(yestodayStartTime, todayStartTime, LogDb.getBuyInShelf(),type,playerId);
                list.forEach(document -> {
                    Building sellBuilding = City.instance().getBuilding(document.get("b",UUID.class));
                    Gs.PlayerIncomePay.IncomePay.Builder incomePay=Gs.PlayerIncomePay.IncomePay.newBuilder();
                    incomePay.setItemId(document.getInteger("tpi"))
                            .setNum((int)(document.getLong("a")/document.getLong("p")))
                            .setAmount(document.getLong("a")-document.getLong("miner"))/*You need to subtract the absenteeism fee to be regarded as real income*/
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
        }else{//Expenditure (all shelf expenses must add absenteeism fee (need to add 2 times)
            if(buildType==Gs.PlayerIncomePay.BuildType.MATERIAL.getNumber()||buildType==Gs.PlayerIncomePay.BuildType.PRODUCE.getNumber()){
                list = LogDb.daySummaryShelfPay(yestodayStartTime, todayStartTime, LogDb.getBuyInShelf(),type,playerId);
                list.forEach(document -> {
                    Building buyBuilding = City.instance().getBuilding(document.get("w",UUID.class));
                    Gs.PlayerIncomePay.IncomePay.Builder incomePay=Gs.PlayerIncomePay.IncomePay.newBuilder();
                    if(buildType==MetaItem.type(buyBuilding.metaId())){
                    incomePay.setItemId(document.getInteger("tpi"))
                            .setNum((int)(document.getLong("a")/document.getLong("p")))
                            .setAmount(document.getLong("a")+document.getLong("miner")) //Plus absenteeism fee
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
            //Employee salary (common to several buildings)
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
        //Historical total revenue
        List<Document> histList=LogDb.dayPlayerIncome(DateUtil.todayStartTime(),buildType,LogDb.getDayPlayerIncome());
        //Today's income
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

    //=================New Research Institute===================
    //New Institute Building Details
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

    //Open treasure chest
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
        //Open open treasure chest
        int result = tec.useScienceBox(key, box.getNum());
        /*Update automatic replenishment*/
        tec.updateAutoReplenish(key);
        Gs.ScienceBoxACK.Builder builder = Gs.ScienceBoxACK.newBuilder();
        builder.setKey(key.toProto())
                .setBuildingId(box.getBuildingId())
                .setOpenNum(box.getNum())
                .setResultPoint(result);
        GameDb.saveOrUpdate(tec);
        this.write(Package.create(cmd, builder.build()));
    }
    //Use Institute Technology Points
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
        //Use technology points
        if(tec.getStore().consumeInHand(key,science.getNum())){
           //Increase the player's corresponding technology points
            SciencePoint sciencePoint = SciencePointManager.getInstance().updateSciencePoint(player.id(), item.id,science.getNum());
            SciencePointManager.getInstance().updateSciencePoint(sciencePoint);//Update the cache and synchronize the database
            GameDb.saveOrUpdate(tec);
            long pointNum = SciencePointManager.getInstance().getSciencePoint(player.id(), item.id).point;
            Gs.OpenScience.Builder builder = science.toBuilder().setPointNum((int) pointNum);
            this.write(Package.create(cmd,builder.build()));
        }else {
            this.write(Package.fail(cmd, Common.Fail.Reason.numberNotEnough));
            return;
        }
    }


    //=============New promotion company==================
    //Promote company building details
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
    //Promote the use of promotional points in the company's warehouse
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
        //Use technology points
        if(promotion.getStore().consumeInHand(key,science.getNum())){
            //Increase the player's corresponding technology points
            PromotePoint promotePoint = PromotePointManager.getInstance().updatePlayerPromotePoint(player.id(), item.id, science.getNum());
            PromotePointManager.getInstance().updatePromotionPoint(promotePoint);//Update the cache and synchronize the database
            GameDb.saveOrUpdate(promotion);
            long pointNum = PromotePointManager.getInstance().getPromotePoint(player.id(), item.id).getPromotePoint();//Dotted quantity
            Gs.OpenScience.Builder builder = science.toBuilder().setPointNum((int) pointNum);
            this.write(Package.create(cmd,builder.build()));
        }else {
            this.write(Package.fail(cmd, Common.Fail.Reason.numberNotEnough));
        }
    }

    //=========Promotion Company Institute Public Agreement==========
    //Add construction production line (promotion company, research institute)
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
    //Delete production line (promotion company, research institute)
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
                /*Get the next production line*/
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
    //Adjusting the order of eva-type building construction lines (promotion companies, research institutes)
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
    //Research institute or promotion company listed (promotion company, research institute)
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
                    .setCurCount(science.getShelf().getAllNum())                /*Set the total number of shelves*/
                    .setStoreNum(science.getStore().getItemCount(item.getKey()))/*Set the available quantity of the current commodity in the warehouse*/
                    .setItem(itemBuilder).setAutoRepOn(content.autoReplenish);
           /* this.write(Package.create(cmd, builder.build()));*/
            science.sendToAllWatchers(Package.create(cmd, builder.build()));
        }else{
            this.write(Package.fail(cmd, Common.Fail.Reason.numberNotEnough));
            System.err.println("数量不足");
        }
    }
    //Remove the eva class and add something on the construction shelf (promotion company, research institute)
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
            content.autoReplenish = false;//Turn off automatic replenishment
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
    //Revise the information of science and technology points on building shelves of eva class (promotion company, research institute)
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
    //Purchase of scientific and technological materials (promotion companies, research institutes)
    public void buySciencePoint(short cmd,Message message) throws Exception {
        Gs.BuySciencePoint c = (Gs.BuySciencePoint)message;
        if(c.getPrice() <= 0)
            return;
        UUID bid = Util.toUuid(c.getBuildingId().toByteArray());
        UUID buyerId = Util.toUuid(c.getBuyerId().toByteArray());//Since the buyer is the current player, there is no need to pass the player id
        Building sellBuilding = City.instance().getBuilding(bid);
        if(sellBuilding == null || !(sellBuilding instanceof ScienceBuildingBase)||sellBuilding.canUseBy(buyerId)|| sellBuilding.outOfBusiness())
            return;
        Item item = new Item(c.getItem());
        //1. Check whether the quantity purchased is sufficient (the error code is returned if it is not sufficient)
        //2.Purchase technology points (buildings increase revenue today, and log records)
        //3.The purchaser increases the corresponding eva points (need to wait for the eva revision to be completed)
        //4.(Player consumption records), (building) and (building owner) income
        ScienceBuildingBase science = (ScienceBuildingBase) sellBuilding;
        if(science.checkShelfSlots(item.key,item.n)){
            ScienceShelf.Content content = science.getContent(item.key);
            if(content.price!=c.getPrice()){
                this.write(Package.fail(cmd, Common.Fail.Reason.noReason));
                return;
            }
            //calculate cost
            int cost = item.n * content.price;
            /*TODO Calculate absenteeism fee*/
            double minersRatio = MetaData.getSysPara().minersCostRatio;
            long minerCost = (long) Math.floor(cost * minersRatio);
            long totalIncome =cost-minerCost;
            long totalPay = cost+minerCost;
            /*Deduct the buyer amount*/
            if(!player.decMoney(totalPay)){
                this.write(Package.fail(cmd, Common.Fail.Reason.moneyNotEnough));
                return;
            }
            //Number of consumer shelves
            science.delshelf(item.key, item.n, false);
            sellBuilding.updateTodayIncome(totalIncome);
            //Player spending and revenue records
            Player seller = GameDb.getPlayer(science.ownerId());
            /*Increase seller amount*/
            seller.addMoney(totalIncome);//Seller's income (net of absenteeism fees)
            int itemId = item.key.meta.id;
            //Increase player's technology points
            if(sellBuilding.type()==MetaBuilding.TECHNOLOGY) {
                SciencePoint sciencePoint = SciencePointManager.getInstance().updateSciencePoint(player.id(), itemId, item.n);
                SciencePointManager.getInstance().updateSciencePoint(sciencePoint);
                c=c.toBuilder().setTypePointAllNum(sciencePoint.point).build();
            }else{
                PromotePoint promotePoint = PromotePointManager.getInstance().updatePlayerPromotePoint(player.id(), itemId, item.n);
                PromotePointManager.getInstance().updatePromotionPoint(promotePoint);
                c=c.toBuilder().setTypePointAllNum(promotePoint.promotePoint).build();
            }
            //int type = MetaItem.scienceItemId(itemId);//Get product type
            int type = sellBuilding.type();
            //Logging
            LogDb.minersCost(this.player.id(),minerCost, MetaData.getSysPara().minersCostRatio);
            LogDb.minersCost(seller.id(),minerCost, MetaData.getSysPara().minersCostRatio);
            LogDb.playerPay(player.id(),totalPay,sellBuilding.type());
            LogDb.playerIncome(seller.id(),totalIncome,sellBuilding.type());
            LogDb.buyInShelf(player.id(), seller.id(), item.n, content.getPrice(),
                    item.key.producerId, sellBuilding.id(),player.id(),type,itemId,seller.getCompanyName(),0,sellBuilding.type(),minerCost);
            LogDb.buildingIncome(bid,player.id(),totalIncome,0,itemId);
            if(!GameServer.isOnline(seller.id())) {
                LogDb.sellerBuildingIncome(sellBuilding.id(), sellBuilding.type(), seller.id(), item.n, c.getPrice(), itemId);//Offline notification statistics
            }
            GameDb.saveOrUpdate(Arrays.asList(player,seller,sellBuilding));
            //Push product change notification
            sellBuilding.sendToAllWatchers(Package.create(cmd, c));
            Gs.IncomeNotify notify = Gs.IncomeNotify.newBuilder()
                    .setBuyer(Gs.IncomeNotify.Buyer.PLAYER)
                    .setBuyerId(Util.toByteString(player.id()))
                    .setFaceId(player.getFaceId())
                    .setCost(totalIncome)
                    .setType(sellBuilding.type()==MetaBuilding.TECHNOLOGY?Gs.IncomeNotify.Type.LAB:Gs.IncomeNotify.Type.PROMO)
                    .setBid(sellBuilding.metaBuilding.id)
                    .setItemId(item.key.meta.id)
                    .setCount(item.n)
                    .build();
            GameServer.sendIncomeNotity(seller.id(),notify);
           /* this.write(Package.create(cmd, c));*/
        }else{
            System.err.println("货架数量不足");
            this.write(Package.fail(cmd, Common.Fail.Reason.numberNotEnough));
        }
    }
    //Obtain technology and add some shelves to the building (promotion company, research institute)
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
    //Obtain technology and add some construction warehouse data (promotion company, research institute)
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
    //Obtain information on technology plus construction production lines (promotion companies, research institutes)
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
        if(scienceBuildingBase.type()==MetaBuilding.TECHNOLOGY){/*If it is a research institute, the production line also contains treasure chest information*/
            Technology tec = (Technology) scienceBuildingBase;
            builder.addAllBox(tec.getBoxStore().toProto()).setBuildingId(id.getId());
        }
        builder.setBuildingId(id.getId());
        this.write(Package.create(cmd,builder.build()));
    }
    //Obtain the production speed of the technology plus point construction production list (promotion company, research institute)
    public void getScienceItemSpeed(short cmd,Message message){
        Gs.Id id = (Gs.Id) message;
        UUID bid = Util.toUuid(id.getId().toByteArray());
        Building building = City.instance().getBuilding(bid);
        if(building==null||!(building instanceof ScienceBuildingBase))
            return;
        ScienceBuildingBase scienceBuildingBase = (ScienceBuildingBase) building;
        Gs.ScienceItemSpeed.Builder builder = Gs.ScienceItemSpeed.newBuilder();
        builder.setBuildingId(id.getId());
        /*Basic dataBasic data*/
        /*Temporarily unable to query the production speed bonus (need to wait for the completion of Eva revision)*/
        if(scienceBuildingBase.type()==MetaBuilding.TECHNOLOGY) {
            for (MetaScienceItem item : MetaData.getScienceItem().values()) {
                Eva eva = EvaManager.getInstance().getEva(player.id(), item.id, Gs.Eva.Btype.ProduceSpeed_VALUE);//Eva may be wrong here
                builder.addItemSpeedBuilder().setType(item.id)
                        .setSpeed(item.n*scienceBuildingBase.getWorkerNum()* (1 + EvaManager.getInstance().computePercent(eva)));//TODO Need to add eva bonus
            }
        }else if(scienceBuildingBase.type()==MetaBuilding.PROMOTE){
            for (MetaPromotionItem item : MetaData.getPromotionItem().values()) {
                Eva eva = EvaManager.getInstance().getEva(player.id(), item.id, Gs.Eva.Btype.ProduceSpeed_VALUE);//Eva may be wrong here
                builder.addItemSpeedBuilder().setType(item.id)
                        .setSpeed(item.n *scienceBuildingBase.getWorkerNum()*(1 + EvaManager.getInstance().computePercent(eva)));//TODO Need to add eva bonus
            }
        }
        this.write(Package.create(cmd,builder.build()));
    }

    // Get 1*1 map
    public void queryMapProsperity(short cmd) {
        Gs.MapProsperity.Builder builder = Gs.MapProsperity.newBuilder();
        List<Gs.MapProsperity.ProspInfo> list = new ArrayList<>();
        ProsperityManager.instance().allGround.stream().filter(o -> o != null).forEach(g -> {
            //Prosperity
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
        // If it is land for sale
        if (info.inSelling()) {
            int prosperity = ProsperityManager.instance().getGroundProsperity(coordinate);
            Player player = GameDb.getPlayer(info.ownerId);
            builder.setIdx(Gs.MiniIndexCollection.newBuilder().addCoord(coordinate.toProto())).setStatus(Gs.MapBuildingSummary.Status.Selling).setProsperity(prosperity).setRoleName(player.getName()).setCompanyName(player.getCompanyName());
        } else if (info.inStateless()) {
            // If it is idle
            int prosperity = ProsperityManager.instance().getGroundProsperity(coordinate);
            Player player = GameDb.getPlayer(info.ownerId);
            builder.setIdx(Gs.MiniIndexCollection.newBuilder().addCoord(coordinate.toProto())).setStatus(Gs.MapBuildingSummary.Status.Idle).setProsperity(prosperity).setRoleName(player.getName()).setCompanyName(player.getCompanyName());
        } else if (info.inRenting()) {
            // If it is for rent
            int prosperity = ProsperityManager.instance().getGroundProsperity(coordinate);
            Player player = GameDb.getPlayer(info.ownerId);
            builder.setIdx(Gs.MiniIndexCollection.newBuilder().addCoord(coordinate.toProto())).setStatus(Gs.MapBuildingSummary.Status.Renting).setProsperity(prosperity).setRoleName(player.getName()).setCompanyName(player.getCompanyName());
        } else {
            // If it has been built
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
        builder.setTypeId(n.getNum());
        City.instance().forAllGrid((grid -> {
            AtomicInteger num = new AtomicInteger(0);
            grid.forAllBuilding(b->{
                if (!(b.outOfBusiness()) && b instanceof ScienceBuildingBase&&b.type()==MetaBuilding.TECHNOLOGY) {
                    Technology tech = (Technology) b;
                    if (tech.shelf.getSaleNum(item.id) > 0) {
                        num.addAndGet(1);
                    }
                }
            });
            builder.addInfoBuilder()
                    .setItemId(item.id)
                    .setIdx(Gs.GridIndex.newBuilder().setX(grid.getX()).setY(grid.getY()))
                    .setCount(num.intValue());
        }));
        this.write(Package.create(cmd, builder.build()));
    }

    public void queryPromotionSummary(short cmd, Message message) {
        Gs.Num n = (Gs.Num) message;
        MetaPromotionItem item = MetaData.getPromotionItem(n.getNum());
        if (item == null){
            GlobalConfig.cityError("queryPromotionSummary: MetaPromotionItem is not exist!");
            return;
        }
        Gs.TechOrPromSummary.Builder builder = Gs.TechOrPromSummary.newBuilder();
        builder.setTypeId(n.getNum());
        City.instance().forAllGrid((grid -> {
            AtomicInteger num = new AtomicInteger(0);
            grid.forAllBuilding(b->{
                if (!(b.outOfBusiness()) && b instanceof ScienceBuildingBase&&b.type()==MetaBuilding.PROMOTE) {
                    PromotionCompany tech = (PromotionCompany) b;
                    if (tech.shelf.getSaleNum(item.id) > 0) {
                        num.addAndGet(1);
                    }
                }
            });
            builder.addInfoBuilder()
                    .setItemId(item.id)
                    .setIdx(Gs.GridIndex.newBuilder().setX(grid.getX()).setY(grid.getY()))
                    .setCount(num.intValue());
        }));
        this.write(Package.create(cmd, builder.build()));
    }

    /*Check land prosperity*/
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


    public void queryTechnologyDetail(short cmd, Message message) {
        Gs.queryTechnologyDetail c = (Gs.queryTechnologyDetail) message;
        GridIndex center = new GridIndex(c.getCenterIdx().getX(), c.getCenterIdx().getY());
        Gs.TechnologyDetail.Builder builder = Gs.TechnologyDetail.newBuilder();
        builder.setItemId(c.getItemId());
        City.instance().forEachGrid(center.toSyncRange(), (grid) -> {
            Gs.TechnologyDetail.GridInfo.Builder gb = builder.addInfoBuilder();
            gb.getIdxBuilder().setX(grid.getX()).setY(grid.getY());
            grid.forAllBuilding(building -> {
                if (building instanceof ScienceBuildingBase && !building.outOfBusiness()&&building.type()==MetaBuilding.TECHNOLOGY) {
                    Technology base = (Technology) building;
                    ScienceShelf shelf = base.getShelf();
                    if (shelf.getSaleNum(c.getItemId()) > 0) {
                        Gs.TechnologyDetail.GridInfo.Building.Builder bb = gb.addBuildingInfoBuilder();
                        bb.setId(Util.toByteString(building.id()));
                        bb.setPos(building.coordinate().toProto());
                        shelf.getSaleDetail(c.getItemId()).forEach((k, v) -> {
                            Gs.TechnologyDetail.GridInfo.Building.Sale.Builder sale = Gs.TechnologyDetail.GridInfo.Building.Sale.newBuilder();
                            sale.setCount(k.n).setPrice(v).setGuidePrice(GuidePriceMgr.instance().getTechOrPromGuidePrice(c.getItemId(), true));
                            bb.setSale(sale.build());
                        });
                        bb.setOwnerId(Util.toByteString(building.ownerId()));
                        bb.setName(building.getName());
                        bb.setMetaId(base.metaBuilding.id);
                    }
                }
            });
        });
        this.write(Package.create(cmd, builder.build()));
    }

    public void queryPromotionsDetail(short cmd, Message message) {
        Gs.queryPromotionsDetail c = (Gs.queryPromotionsDetail) message;
        GridIndex center = new GridIndex(c.getCenterIdx().getX(), c.getCenterIdx().getY());
        Gs.PromotionsDetail.Builder builder = Gs.PromotionsDetail.newBuilder();
        builder.setItemId(c.getItemId());
        City.instance().forEachGrid(center.toSyncRange(), (grid) -> {
            Gs.PromotionsDetail.GridInfo.Builder gb = builder.addInfoBuilder();
            gb.getIdxBuilder().setX(grid.getX()).setY(grid.getY());
            grid.forAllBuilding(building -> {
                if (building instanceof ScienceBuildingBase && !building.outOfBusiness()&&building.type()==MetaBuilding.PROMOTE) {
                    PromotionCompany promotion = (PromotionCompany) building;
                    ScienceShelf shelf = promotion.getShelf();
                    if (shelf.getSaleNum(c.getItemId()) > 0) {
                        Gs.PromotionsDetail.GridInfo.Building.Builder bb = gb.addBuildingInfoBuilder();
                        bb.setId(Util.toByteString(building.id()));
                        bb.setPos(building.coordinate().toProto());
                        shelf.getSaleDetail(c.getItemId()).forEach((k, v) -> {
                            Gs.PromotionsDetail.GridInfo.Building.Sale.Builder sale = Gs.PromotionsDetail.GridInfo.Building.Sale.newBuilder();
                            sale.setCount(k.n).setPrice(v).setGuidePrice(GuidePriceMgr.instance().getTechOrPromGuidePrice(c.getItemId(), false));
                            bb.setSale(sale.build());
                        });
                        bb.setOwnerId(Util.toByteString(building.ownerId()));
                        bb.setName(building.getName());
                        bb.setMetaId(promotion.metaBuilding.id);
                    }
                }
            });
        });
        this.write(Package.create(cmd, builder.build()));
    }
    //Industry supply and demand
    public void querySupplyAndDemand(short cmd,Message message) {
        Gs.SupplyAndDemand msg = (Gs.SupplyAndDemand) message;
        int type = msg.getType().getNumber();
        List<Document> list = LogDb.querySupplyAndDemand(type);
        Gs.SupplyAndDemand.Builder builder = Gs.SupplyAndDemand.newBuilder();
        builder.setType(msg.getType());
        long demand = IndustryMgr.instance().getTodayDemand(type); // Number of transactions in the industry today
        int supply = IndustryMgr.instance().getTodaySupply(type);  // Industry Remaining Quantity
        // Supply: total transaction amount + remaining quantity
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
    // Commodity supply and demand
    public void queryItemSupplyAndDemand(short cmd, Message message) {
        Gs.queryItemSupplyAndDemand q = (Gs.queryItemSupplyAndDemand) message;
        int industryId = q.getIndustryId();
        int itemId = q.getItemId();
        Gs.SupplyAndDemand.Builder builder = Gs.SupplyAndDemand.newBuilder();
        builder.setType(Gs.SupplyAndDemand.IndustryType.valueOf(industryId));
        long demand = IndustryMgr.instance().getTodayDemand(industryId,itemId); // Number of transactions in the industry today
        int supply = IndustryMgr.instance().getTodaySupply(industryId,itemId);  // Industry Remaining Quantity
        // Supply: total transaction amount + remaining quantity
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


    // Industry ranking
    public void queryIndustryTopInfo(short cmd, Message message) {
        Gs.QueryIndustry m = (Gs.QueryIndustry) message;
        int type = m.getType();  // Industry type id
        UUID id = Util.toUuid(m.getPid().toByteArray()); // Player id
        long industrySumIncome = IndustryMgr.instance().getIndustrySumIncome(type); // Industry revenue
        Gs.IndustryTopInfo.Builder builder = Gs.IndustryTopInfo.newBuilder();
        builder.setTotal(industrySumIncome).setType(type).setOwner(0);
        AtomicInteger owner = new AtomicInteger(0);
        if (type == Gs.SupplyAndDemand.IndustryType.GROUND_VALUE) {
            List<TopInfo> infos = IndustryMgr.instance().queryTop();
            infos.stream().filter(o -> o != null).forEach(d -> {
                owner.incrementAndGet();
                Gs.IndustryTopInfo.TopInfo.Builder info = builder.addTopInfoBuilder();
                info.setPid(Util.toByteString(d.pid)).setName(d.name).setIncome(d.yesterdayIncome).setCount(d.count).setFaceId(d.faceId);
                if (d.pid.equals(id)) {
                    builder.setOwner(owner.intValue());
                }
            });
            TopInfo top = IndustryMgr.instance().queryMyself(id, type);
            builder.addTopInfo(Gs.IndustryTopInfo.TopInfo.newBuilder().setPid(Util.toByteString(top.pid)).setName(top.name).setIncome(top.yesterdayIncome).setCount(top.count).setFaceId(top.faceId).setMyself(true));
        } else {
            long industryStaffNum = IndustryMgr.instance().getIndustryStaffNum(type); // Industry General Staff
            builder.setStaffNum(industryStaffNum).setTotal(industrySumIncome).setOwner(0);
            List<TopInfo> infos = IndustryMgr.instance().queryTop(type);
            infos.stream().filter(o -> o != null).forEach(d -> {
                owner.incrementAndGet();
                Gs.IndustryTopInfo.TopInfo.Builder info = builder.addTopInfoBuilder();
                info.setPid(Util.toByteString(d.pid)).setName(d.name).setIncome(d.yesterdayIncome).setScience(d.science).setPromotion(d.promotion).setWoker(d.workerNum).setFaceId(d.faceId);
                if (d.pid.equals(id)) {
                    builder.setOwner(owner.intValue());
                }
            });
            TopInfo myself = IndustryMgr.instance().queryMyself(id, type);
            builder.addTopInfo(Gs.IndustryTopInfo.TopInfo.newBuilder()
                    .setPid(Util.toByteString(myself.pid))
                    .setIncome(myself.yesterdayIncome)
                    .setName(myself.name)
                    .setWoker(myself.workerNum)
                    .setScience(myself.science)
                    .setPromotion(myself.promotion)
                    .setFaceId(myself.faceId)
                    .setMyself(true).build());
        }
        this.write(Package.create(cmd, builder.build()));
    }

    // Rich List
    public void queryRegalRanking(short cmd, Message message) {
        Gs.Id id = (Gs.Id) message;
        UUID pid = Util.toUuid(id.getId().toByteArray());
        Gs.RegalRanking.Builder builder = Gs.RegalRanking.newBuilder();
        AtomicInteger owner = new AtomicInteger(0);
        builder.setOwner(0);
        List<TopInfo> infos = IndustryMgr.instance().queryRegalRanking();
        infos.stream().filter(o -> o != null).forEach(d -> {
            owner.incrementAndGet();
            Gs.RegalRanking.RankingInfo.Builder info = builder.addInfoBuilder();
            if (d.pid.equals(id.getId())) {
                builder.setOwner(owner.intValue());
            }
            info.setPid(Util.toByteString(d.pid)).setName(d.name).setIncome(d.yesterdayIncome).setScience(d.science).setPromotion(d.promotion).setWoker(d.workerNum).setFaceId(d.faceId);
        });
        TopInfo myself = IndustryMgr.instance().queryMyself(pid);
        builder.addInfo(Gs.RegalRanking.RankingInfo.newBuilder()
                .setPid(Util.toByteString(myself.pid))
                .setName(myself.name)
                .setIncome(myself.yesterdayIncome)
                .setScience(myself.science)
                .setPromotion(myself.promotion)
                .setWoker(myself.workerNum)
                .setFaceId(myself.faceId)
                .setMyself(true).build()

        );
        this.write(Package.create(cmd, builder.build()));
    }

    public void queryCityLevel(short cmd) {
        this.write(Package.create(cmd,CityLevel.instance().toProto()));
    }

    // Commodity list
        public void queryProductRanking(short cmd, Message message) {
        Gs.queryProductRanking q = (Gs.queryProductRanking) message;
        int industryId = q.getIndustryId();
        int itemId = q.getItemId();
        UUID pid = Util.toUuid(q.getPlayerId().toByteArray());
        Gs.ProductRanking.Builder builder = Gs.ProductRanking.newBuilder();
        builder.setItemId(itemId);
        builder.setIndustryId(industryId);
        builder.setOwner(0);
        AtomicInteger owner = new AtomicInteger(0);
        List<TopInfo> list = IndustryMgr.instance().queryProductRanking(industryId, itemId);
        list.stream().filter(o -> o != null).forEach(d -> {
            owner.incrementAndGet();
            Gs.ProductRanking.TopInfo.Builder top = builder.addTopInfoBuilder();
            top.setPid(Util.toByteString(d.pid)).setName(d.name).setIncome(d.yesterdayIncome).setScience(d.science).setPromotion(d.promotion).setWoker(d.workerNum).setFaceId(d.faceId);
            if (pid.equals(d.pid)) {
                builder.setOwner(owner.intValue());
            }
        });
        TopInfo myself = IndustryMgr.instance().queryMyself(pid, industryId, itemId);
        builder.addTopInfo(Gs.ProductRanking.TopInfo.newBuilder()
                .setPid(Util.toByteString(myself.pid))
                .setName(myself.name)
                .setIncome(myself.yesterdayIncome)
                .setScience(myself.science)
                .setPromotion(myself.promotion)
                .setWoker(myself.workerNum)
                .setFaceId(myself.faceId)
                .setMyself(true).build()
        );
        this.write(Package.create(cmd, builder.build()));
    }

    // Eva rank distribution
    public void queryEvaGrade(short cmd, Message message) {
        Gs.queryEvaGrade q = (Gs.queryEvaGrade) message;
        int industryId = q.getIndustryId();
        int itemId = q.getItemId();
        int type = q.getType();   //Types
        Gs.EvaGrade.Builder builder = Gs.EvaGrade.newBuilder();
        Map<Integer, Long> map = EvaGradeMgr.instance().queryEvaGrade(industryId, itemId, type);
        builder.setItemId(itemId);
        if (map != null && !map.isEmpty()) {
            map.forEach((k,v)->{
                Gs.EvaGrade.Grade.Builder grade = builder.addGradeBuilder();
                grade.setLv(k).setSum(v);
            });
        }
        this.write(Package.create(cmd, builder.build()));
    }
}
