package Game;

import Game.CityInfo.CityLevel;
import Game.CityInfo.TopInfo;
import Game.Contract.ContractManager;
import Game.Eva.EvaManager;
import Game.Gambling.FlightManager;
import Game.CityInfo.IndustryMgr;
import Game.League.LeagueManager;
import Game.Meta.MetaBuilding;
import Game.Meta.MetaCity;
import Game.Meta.MetaData;
import Game.Meta.MetaGood;
import Game.RecommendPrice.GuidePriceMgr;
import Game.Timers.PeriodicTimer;
import Game.Util.BuildingUtil;
import Game.Util.DateUtil;
import Shared.LogDb;
import Shared.Package;
import Shared.Util;
import com.google.common.base.Throwables;
import com.google.common.collect.Sets;
import gs.Gs;
import gscode.GsCode;
import org.apache.log4j.Logger;

import java.lang.reflect.Array;
import java.time.Duration;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class City {
    public static final UUID SysRoleId = UUID.nameUUIDFromBytes(new byte[16]);
    private static final Logger logger = Logger.getLogger(City.class);
    public static int GridMaxX;
    public static int GridMaxY;
    public static int GridX;
    public static int GridY;
    private MetaCity meta;
    private Grid[][] grids;
    private static City instance;
    private TreeMap<Integer, Integer> topGoodQty;
    private Map<Integer, Integer> topBuildingQty = new HashMap<>();
    private Map<Integer, IndustryIncrease> industryMoneyMap = new HashMap<>();
    private Map<Building,Double> moveKnownApartmentMap=new HashMap<Building,Double>(); //The cache should be loaded when the player's residential building is loaded or the building is opened, and the cache should be deleted when the building is demolished and closed
    Map<GoodFilter,Set<GoodSellInfo>> retailShopGoodMap=new HashMap<GoodFilter,Set<GoodSellInfo>>();
    private ScheduledExecutorService e = Executors.newScheduledThreadPool(1);
    private ArrayDeque<Runnable> queue = new ArrayDeque<>();
    private boolean taskIsRunning = false;
    private PeriodicTimer metaAuctionLoadTimer = new PeriodicTimer();
    private PeriodicTimer insuranceTimer = new PeriodicTimer((int)TimeUnit.HOURS.toMillis(24),(int)TimeUnit.SECONDS.toMillis((DateUtil.getTodayEnd()-nowTime)/1000));
    private PeriodicTimer industryMoneyTimer = new PeriodicTimer((int)TimeUnit.DAYS.toMillis(7),(int)TimeUnit.SECONDS.toMillis((DateUtil.getSundayOfThisWeek()-nowTime)/1000));

    public int[] timeSection() {
        return meta.timeSection;
    }
    public final static long UpdateIntervalNano = TimeUnit.MILLISECONDS.toNanos(200);//1 minute to 200 nanosecond particles
    public MetaCity getMeta() {
        return meta;
    }
    public Building getBuilding(UUID bId) {
        return allBuilding.get(bId);
    }

    public static final int TERRIAN_NONE = 0;
    public static final int TERRIAN_TRIVIAL = 0x00000001;
    public static final int TERRIAN_PLAYER = 0x00000002;
    public static final int TERRIAN_BUILDING = TERRIAN_TRIVIAL | TERRIAN_PLAYER;

    public void updateTopGoodQty(int mId, int qty) {
        topGoodQty.compute(mId, (k, oldQty)->{
            if(oldQty == null)
                return qty;
            else if(oldQty < qty)
                return qty;
            else
                return oldQty;
        });
    }
    public double goodQtyScore(int mId, int qty) {
        if(qty == 0)
            return 0;
        int top = topGoodQty(mId);
        if(top == 0)
            return 0;
        return (double)qty / top * 100.d;
    }
    public double buildingQtyScore(int type, int qty) {
        if(qty == 0)
            return 0;
        int top = topBuildingQty(type);
        if(top == 0)
            return 0;
        return (double)qty / top * 100.d;
    }
    private int topGoodQty(int mId) {
        Integer v = topGoodQty.get(mId);
        if(v == null)
            return 0;
        return v;
    }
    private int topBuildingQty(int type) {
        Integer v = topBuildingQty.get(type);
        if(v == null)
            return 0;
        return v;
    }

    public int weather() {
        return 0;
    }

    public int getSumFlow() {
        return allBuilding.values().stream().mapToInt(b->b.getFlow()).reduce(Integer::sum).orElse(0);
    }


    public static final class TerrianInfo {
        UUID ownerId;
        enum State {

        }
    }
    private short[][] terrain;
    private HashMap<UUID, Building> allBuilding = new HashMap<>();

    private HashMap<UUID, HashMap<UUID, Building>> playerBuilding = new HashMap<>();
    public HashMap<UUID, Ground> playerGround = new HashMap<>();
    public HashMap<Integer, Set<Building>> typeBuilding = new HashMap<>();
    public static void init(MetaCity meta) {
        instance = new City(meta);
        instance.initAllBuildings();
    }

    private void initAllBuildings() {
        this.playerBuilding.values().forEach(m->m.values().forEach(b->b.init()));
        instance.initTypeBuildings();//Initialize different building types, building classification
    }

    public static City instance() {
        return instance;
    }

    public  int getPlayerBcountByBtype(UUID playerId,int btype)
    {
        Map<UUID, Building> bmap = this.playerBuilding.get(playerId);
        if (bmap != null)
        {
            return (int) bmap.values().stream().filter(building -> building.type() == btype).count();
        }
        return 0;
    }
    // Get all construction workers from the player
    public  long getPlayerStaffNum(UUID playerId)
    {
        try {
            return playerBuilding.get(playerId).values().stream().filter(b -> b != null && !b.outOfBusiness()).mapToLong(Building::getWorkerNum).sum();
        } catch (Exception e) {
            return 0;
        }
    }

    public List<Building> getPlayerBListByBtype(UUID playerId, int btype)
    {
        Map<UUID, Building> bmap = this.playerBuilding.get(playerId);
        if (bmap != null)
        {
            return Arrays.asList(bmap.values().stream()
                    .filter(building -> building.type() == btype)
                    .toArray(Building[]::new));
        }
        return new ArrayList<>();
    }

    private City(MetaCity meta) {
        this.meta = meta;
        GridX = meta.gridX;
        GridY = meta.gridY;
        GridMaxX = meta.x / meta.gridX;
        GridMaxY = meta.y / meta.gridY;
        grids = new Grid[GridMaxX][GridMaxY];
        for(int i = 0; i < GridMaxX; ++i)
        {
            for(int j = 0; j < GridMaxY; ++j)
                grids[i][j] = new Grid(i,j);
        }
        terrain = new short[meta.x][meta.y];
        this.currentTimeSectionIdx = meta.indexOfHour(this.localTime().getHour());
        // load initial buildings
        loadSysBuildings();
        // load all player buildings, cache them into maps
        loadPlayerBuildings();
        this.topGoodQty = GameDb.getTopGoodQuality();
        this.metaAuctionLoadTimer.setPeriodic(TimeUnit.DAYS.toMillis(1), Util.getTimerDelay(0, 0));

        this.lastHour = this.localTime().getHour();

        //Industry salary increase index
        //loadIndustryIncrease();
    }
    private void loadSysBuildings() {
        for(MetaData.InitialBuildingInfo i : MetaData.getAllInitialBuilding())
        {
            if(MetaBuilding.type(i.id) != MetaBuilding.TRIVIAL)
            {
                logger.error("InitialBuilding contain no trivial building! id: " + i.id);
                continue;
            }
            Building b = Building.create(i.id, new Coordinate(i.x, i.y), SysRoleId);
            this.calcuTerrain(b);
            // b is useless, discard it
        }
    }
    private void loadPlayerBuildings() {
        for(Building b : GameDb.getAllBuilding()) {
            this.take(b);
        }
    }
    private void loadIndustryIncrease() {
        List<IndustryIncrease> list=GameDb.getAllIndustryIncrease();
        if(list!=null&&list.size()>0){
            list.forEach(industry->{
                industryMoneyMap.put(industry.getBuildingType(),industry);
            });
        }else{//Initial salary
            MetaData.getSalaryMap().forEach((k,v)->{
                GameDb.saveOrUpdate(new IndustryIncrease(k,0));
                industryMoneyMap.put(k,new IndustryIncrease(k,0));
            });
        }
    }
    public void addIndustryMoney(int type,int money){
        IndustryIncrease ii=industryMoneyMap.get(type);
        long industryMoney=ii.getIndustryMoney();
        industryMoney+=money;
        ii.setIndustryMoney(industryMoney);
        industryMoneyMap.put(type,ii);
        GameDb.saveOrUpdate(new IndustryIncrease(type,industryMoney));
    }
    private void consumeQueue() {
        if (!queue.isEmpty()) {
            if(!taskIsRunning) {
                taskIsRunning = true;
                Runnable a = queue.poll();
                CompletableFuture.runAsync(a, e).thenRunAsync(() -> {
                    taskIsRunning = false;
                    consumeQueue();
                }, e);
            }
        }
    }
    public void execute(Runnable task) {
        e.execute(()->{
            queue.add(task);
            consumeQueue();
        });
    }
    public void run() {
        // startUp metaBuilding?
        // calcu parameters
        lastTs = System.nanoTime() - UpdateIntervalNano;
        e.scheduleAtFixedRate(() -> {
            try {
                final long now = System.nanoTime();
                this.update(now - lastTs);
                lastTs = now;
            } catch (Exception e) {
                logger.fatal(Throwables.getStackTraceAsString(e));
            }
        }, 0, UpdateIntervalNano, TimeUnit.NANOSECONDS);
    }
    private long lastTs;
    private int lastHour;

    public void update(long diffNano) {
        GroundAuction.instance().update(diffNano);
        GroundManager.instance().update(diffNano);
        Exchange.instance().update(diffNano);
        allBuilding.forEach((k,v)->v.update(diffNano));
        ContractManager.getInstance().update(diffNano);
        AiBaseAvgManager.getInstance().update(diffNano);
        NpcManager.instance().update(diffNano);
        GameServer.allGameSessions.forEach((k,v)->{v.update(diffNano);});
        MailBox.instance().update(diffNano);
        FlightManager.instance().update(diffNano);
        NpcManager.instance().countNpcNum(diffNano);
        LeagueManager.getInstance().update(diffNano);
        WareHouseManager.instance().update(diffNano);
        BrandManager.instance().update(diffNano);
        // do this at last
        updateTimeSection(diffNano);
        specialTick(diffNano);
        TickManager.instance().tick(diffNano);
        PromotionMgr.instance().update(diffNano);
        //Pay unemployment benefits
        updateInsurance(diffNano);
        //Calculate industry wages next week
        updateIndustryMoney(diffNano);
        BuildingUtil.instance().update(diffNano);
        //Prosperity statistics
        ProsperityManager.instance().totalProsperity(diffNano);
        // Historical deal
        GuidePriceMgr.instance().update(diffNano);
        IndustryMgr.instance().update(diffNano);
        CityLevel.instance().update(diffNano);
    }
    private long timeSectionAccumlateNano = 0;
    public int currentTimeSectionIdx() {
        return currentTimeSectionIdx;
    }
    private int currentTimeSectionIdx;
    public int currentHour() {
        return lastHour;
    }

    private void updateTimeSection(long diffNano) {
        int nowHour = this.localTime().getHour();
        if(nowHour != lastHour)
        {
            hourTickAction(nowHour);
            if(lastHour > nowHour)
                dayTickAction();
            lastHour = nowHour;
            int index = meta.indexOfHour(nowHour);
            if(index == -1) // still in the range
                ;
            else if(currentTimeSectionIdx != index) {
                timeSectionAccumlateNano = 0;
                timeSectionTickAction(index, nowHour, meta.timeSectionDuration(index));
                currentTimeSectionIdx = index;
            }
        }
        timeSectionAccumlateNano += diffNano;
    }

    private void updateInsurance(long diffNano) {
        if (this.insuranceTimer.update(diffNano)) {
            Map<UUID, Npc> map=NpcManager.instance().getUnEmployeeNpc();
            map.forEach((k,v)->{
                if(v.getUnEmployeddTs()>=TimeUnit.HOURS.toMillis(24)){ //Unemployed for more than 24 hours
                    v.addMoney((int) (City.instance().getAvgIndustrySalary()*MetaData.getCity().insuranceRatio));  //Unemployed people receive social security
                }
            });
            GameDb.saveOrUpdate(map.values());
        }
    }

    private void updateIndustryMoney(long diffNano) {
        if (this.industryMoneyTimer.update(diffNano)) {
            //Decrease = (1-Social Security Distribution Ratio-Taxation Ratio) * Unemployment Rate
            double unEmpRatio=NpcManager.instance().getUnEmployeeNpcCount()/(double)(NpcManager.instance().getUnEmployeeNpcCount()+NpcManager.instance().getNpcCount());
            double decrMoney=(1-MetaData.getCity().taxRatio / 100.d-MetaData.getCity().insuranceRatio / 100.d)*unEmpRatio;
            // Industry salary increase index / industry number <0.001 not clear 0
            industryMoneyMap.forEach((k,v)->{
                Map<Integer, Long> industryNpcNumMap=NpcManager.instance().countNpcByBuildingType();
                double r=v.getIndustryMoney()/(double)industryNpcNumMap.get(k);
                if(r>=0.001){
                    v.setIndustryMoney(0l);
                    industryMoneyMap.put(k, v);
                    GameDb.saveOrUpdate(new IndustryIncrease(k,0l));
                }
                //Industry salary next week = industry salary * (1-decrease) + industry salary increase index / industry number
                double nextIndustrySalary=v.getIndustrySalary()*(1-decrMoney)+ r;
                IndustryIncrease ii=new IndustryIncrease(k,0l,nextIndustrySalary);
                industryMoneyMap.put(k, ii);
                GameDb.saveOrUpdate(ii);  //Update industry wages
            });

        }
    }

    public double getAvgIndustrySalary(){
        double a=0;
        for(Map.Entry<Integer, IndustryIncrease> map:industryMoneyMap.entrySet()){
            a+=map.getValue().getIndustrySalary();
        }
        return a/6.d;
    }

    private void dayTickAction() {
        MetaData.updateDayId();
    }

    private void hourTickAction(int nowHour) {
        NpcManager.instance().hourTickAction(nowHour);
        allBuilding.forEach((k,v)->v.hourTickAction(nowHour));
        ContractManager.getInstance().hourTickAction(nowHour);
        PromotionMgr.instance().update(nowHour);
    }

    private void timeSectionTickAction(int newIndex, int nowHour, int hours) {
        // maintain waitToUpdate?
        boolean dayPass = newIndex == 0;
        NpcManager.instance().timeSectionTick(newIndex, nowHour, hours);
        allBuilding.forEach((k,v)->v.timeSectionTick(newIndex, nowHour, hours));
    }
    //Seconds to nanoseconds
    public static long senond2Ns(int sd){
        return TimeUnit.SECONDS.toNanos(sd);
    }

    //Special tick
    private static long _elapsedtime = 0 ;      //Last update time
    public static final int second = 20;        //tick interval, in seconds
    public static final long _upDeltaNs = TimeUnit.MILLISECONDS.toNanos(1000*second); //Interval time converted to nanoseconds
    private void specialTick(long diffNano){
        /*if(_elapsedtime < _upDeltaNs){
            _elapsedtime += diffNano;
            return;
        }else{
            _elapsedtime = 0;
        }
        TickManager.instance().tick(diffNano);*/
    }

    public long leftMsToNextTimeSection() {
        LocalTime now = localTime();
        int nextTimeSectionHour = meta.nextTimeSectionHour(this.currentTimeSectionIdx);
        LocalTime next = LocalTime.of(nextTimeSectionHour, 0);
        if(now.isBefore(next))
            return Duration.between(now, next).toMillis();
        else
            return Duration.between(now, LocalTime.MAX).toMillis();
    }
    public int nextTimeSectionDuration() {
        return meta.nextTimeSectionDuration(this.currentTimeSectionIdx);
    }
    public int currentTimeSectionDuration() {
        return meta.timeSectionDuration(this.currentTimeSectionIdx);
    }
    //    public int nextTimeSectionHour(int nowHour) {
//        for(int i = 0; i < meta.timeSection.length; ++i) {
//            if(nowHour == meta.timeSection[i]) {
//                if(i+1 == meta.timeSection.length)
//                    return meta.timeSection[0];
//                else
//                    return meta.timeSection[i+1];
//            }
//        }
//        return -1;
//    }
    LocalTime localTime() {
        return LocalTime.now();//Util.getLocalTime(this.meta.timeZone);
    }
    public void add(Player p) {
        this.grids[p.getPosition().x][p.getPosition().y].playerComing(p.id());
        this.updateVisibilityCreate(p);
    }

    public void relocation(Player p, GridIndex old) {
        this.grids[old.x][old.y].playerLeaving(p.id());
        this.grids[p.getPosition().x][p.getPosition().y].playerComing(p.id());
        this.updateVisibilityRelocate(p, old);
    }
    public void forEachBuilding(Consumer<Building> f) {
        this.allBuilding.values().forEach(f);
    }
    public void forEachBuilding(List<GridIndex> index, Consumer<Building> f) {
        index.forEach(i->this.forEachBuilding(i, f));
    }
    public void forEachBuilding(GridIndex index, Consumer<Building> f) {
        grids[index.x][index.y].forAllBuilding(f);
    }
    public void forEachBuilding(GridIndexPair range, Consumer<Building> f) {
        for(int x = range.l.x; x <= range.r.x; ++x)
        {
            for(int y = range.l.y; y <= range.r.y; ++y)
                grids[x][y].forAllBuilding(f);
        }
    }
    public void forEachBuilding(UUID playerId, Consumer<Building> f) {
        HashMap<UUID, Building> bs = playerBuilding.get(playerId);
        if(bs != null) {
            bs.values().forEach(f);
        }
    }
    public void forEachGrid(GridIndexPair range, Consumer<Grid> f) {
        for(int x = range.l.x; x <= range.r.x; ++x)
        {
            for(int y = range.l.y; y <= range.r.y; ++y)
                f.accept(grids[x][y]);
        }
    }
    public void forAllGrid(Consumer<Grid> f) {
        for(int i = 0; i < this.grids.length; ++i) {
            for(int j = 0; j < this.grids[i].length; ++j) {
                f.accept(this.grids[i][j]);
            }
        }
    }
    private void updateVisibilityRelocate(Player p, GridIndex old) {
        GridDiffs diffs = this.diff(p.getPosition().toSyncRange(), old.toSyncRange());
        ArrayList<GridIndex> goingGrids = diffs.l;
        ArrayList<GridIndex> leavingGrids = diffs.r;
        Gs.UnitCreate.Builder ucb = Gs.UnitCreate.newBuilder();
        this.forEachBuilding(goingGrids, (Building b)->{
            ucb.addInfo(b.toProto());
        });
        p.send(Package.create(GsCode.OpCode.unitCreate_VALUE, ucb.build()));

//        Gs.Bytes.Builder urb = Gs.Bytes.newBuilder();
//        this.forEachBuilding(leavingGrids, (Building b)->{
//            urb.addIds(ByteString.copyFrom(Util.toBytes(b.id())));
//        });
//        p.send(Package.create(GsCode.OpCode.unitRemove_VALUE, urb.build()));

        p.send(Package.create(GsCode.OpCode.groundChange_VALUE, GroundManager.instance().getGroundProto(goingGrids)));
    }
    private void updateVisibilityCreate(Player p) {
        Gs.UnitCreate.Builder ucb = Gs.UnitCreate.newBuilder();
        this.forEachBuilding(p.getPosition().toSyncRange(), (Building b)->{
            ucb.addInfo(b.toProto());
        });
        p.send(Package.create(GsCode.OpCode.unitCreate_VALUE, ucb.build()));
        p.send(Package.create(GsCode.OpCode.groundChange_VALUE, GroundManager.instance().getGroundProto(p.getPosition().toSyncRange().toIndexList())));
    }
    public GridDiffs diff(GridIndexPair l, GridIndexPair r) {
        GridDiffs res = new GridDiffs();
        Sets.SetView<GridIndex> diffGrids = Sets.symmetricDifference(l.toIndexSet(), r.toIndexSet());
        diffGrids.forEach((GridIndex gi)->{
            if(gi.x > l.r.x || gi.x < l.l.x || gi.y < l.l.y || gi.y > l.r.y)
                res.r.add(gi);
            else
                res.l.add(gi);
        });
        return res;
    }

    public void delBuilding(Building building) {
        //   List npcs = building.destroy();
        building.addUnEmployeeNpc();//Add unemployed
        this.allBuilding.remove(building.id());
        Map<UUID, Building> buildings = this.playerBuilding.get(building.ownerId());
        assert buildings != null;
        buildings.remove(building.id());
        //Type buildings should also be deleted
        this.typeBuilding.get(building.type()).remove(building);
        moveKnownApartmentMap.remove(building);
        updateRetailShopGoodMapOrBuilding(building);
        GridIndex gi = building.coordinate().toGridIndex();
        this.grids[gi.x][gi.y].del(building);
        //Reset land construction
        for(int x = building.area().l.x; x <= building.area().r.x; ++x) {
            for(int y = building.area().l.y; y <= building.area().r.y; ++y) {
                terrain[x][y] = TERRIAN_NONE;
            }
        }
        //Update the highest and lowest quality of retail stores or residential buildings
        if(building.type()==MetaBuilding.APARTMENT||building.type()==MetaBuilding.RETAIL){
            BuildingUtil.instance().updateMaxOrMinTotalQty();
        }
        building.broadcastDelete();
        //   GameDb.delete(npcs.add(building));
        GameDb.delete(building);
    }
    public void send(GridIndexPair range, Package pack) {
        for(int x = range.l.x; x <= range.r.x; ++x) {
            for (int y = range.l.y; y <= range.r.y; ++y) {
                grids[x][y].send(pack);
            }
        }
    }
    public boolean addBuilding(Building b) {
        if(!this.canBuild(b))
            return false;
        GameDb.saveOrUpdate(b); // let hibernate generate the id value
        //     List updates = b.hireNpc();
        List updates = new ArrayList();
        take(b);
        //City building breakthrough, the number of buildings reaches 100, send broadcast to the front end, including the number of citizens, time
        if(allBuilding!=null&&allBuilding.size()>=100){
            GameServer.sendToAll(Package.create(GsCode.OpCode.cityBroadcast_VALUE,Gs.CityBroadcast.newBuilder()
                    .setType(5)
                    .setNum(allBuilding.size())
                    .setTs(System.currentTimeMillis())
                    .build()));
            LogDb.cityBroadcast(null,null,0l,allBuilding.size(),5);
        }
        b.init();
        //Update the highest and lowest retail store or residential building quality in the city
        if(b.type()==MetaBuilding.APARTMENT||b.type()==MetaBuilding.RETAIL){
            BuildingUtil.instance().updateMaxOrMinTotalQty();
        }
        updates.add(b);
        GameDb.saveOrUpdate(updates);
        b.broadcastCreate();
        return true;
    }
    private boolean canBuild(Building building) {
        for(int x = building.area().l.x; x <= building.area().r.x; ++x) {
            for(int y = building.area().l.y; y <= building.area().r.y; ++y) {
                if(terrain[x][y] != TERRIAN_NONE)
                    return false;
            }
        }
        return true;
    }
    private void take(Building building) {
        assert building.type() != MetaBuilding.TRIVIAL;
        calcuTerrain(building);
        this.allBuilding.put(building.id(), building);
        this.playerBuilding.computeIfAbsent(building.ownerId(), k->new HashMap<>()).put(building.id(), building);
        //Sync type building map
        this.typeBuilding.computeIfAbsent(building.type(), k -> new HashSet<>()).add(building);
        GridIndex gi = building.coordinate().toGridIndex();
        this.grids[gi.x][gi.y].add(building);
        this.topBuildingQty.compute(building.type(), (k, oldV)->{
            if(oldV == null)
                return building.quality();
            if(oldV < building.quality())
                return building.quality();
            return oldV;
        });
        //Buy a house moveKnownValue
        if(building.type()== MetaBuilding.APARTMENT){
            buildApartmentMoveKnownValue(building);
        }
        //Buy retail store moveKnownValue
        if(building.type()== MetaBuilding.RETAIL){
            buildRetailMoveKnownValue(building);
        }
    }

    private void calcuTerrain(Building building) {
       /* for(int x = building.area().l.x; x <= building.area().r.x; ++x) {
            for(int y = building.area().l.y; y <= building.area().r.y; ++y) {
                if(building.type() == MetaBuilding.TRIVIAL)
                    terrain[x][y] = TERRIAN_TRIVIAL;
                else
                    terrain[x][y] = TERRIAN_PLAYER;
            }
        }*/
    }

    public long calcuPlayerStaff(UUID playerId)
    {
        if (playerBuilding.get(playerId) != null)
        {
            return playerBuilding.get(playerId)
                    .values().stream()
                    .mapToLong(Building::getAllStaffSize)
                    .sum();
        }
        return 0;
    }

    class GridDiffs {
        public GridDiffs() {
            l.ensureCapacity(Grid.SYNC_RANGE_NUM);
            r.ensureCapacity(Grid.SYNC_RANGE_NUM);
        }
        ArrayList<GridIndex> l = new ArrayList<>();
        ArrayList<GridIndex> r = new ArrayList<>();
    }
    static long nowTime=0;
    static{
        nowTime = System.currentTimeMillis();
    }

    public Map<Integer, IndustryIncrease> getIndustryMoneyMap() {
        return industryMoneyMap;
    }
    public List<Building> getAllBuilding(){
        ArrayList list = new ArrayList(allBuilding.values());
        return list;
    }

    public double getIndustrySalary(int type) {
        return industryMoneyMap.get(type) != null ? industryMoneyMap.get(type).getIndustrySalary() : 0.0;
    }

    //Get the open number of the building type
    public int getOpentNumByType(int type){
        int count=0;
        Set<Building> buildings = typeBuilding.getOrDefault(type,new HashSet<>());
        for (Building building : buildings) {
            if(building.type()==type&&!building.outOfBusiness())
                count++;
        }
        return count;
    }

    //Encapsulated building type building
    private void initTypeBuildings(){
        forEachBuilding(b->{
            typeBuilding.computeIfAbsent(b.type(), k -> new HashSet<>()).add(b);
        });
    }

    //Get all buildings based on building type
    public int getBuildingNumByType(int type){
        Map<Integer,List<Building>> map=new HashMap<Integer,List<Building>>();
        getAllBuilding().forEach(b->{
            map.computeIfAbsent(b.type(),
                    k -> new ArrayList<Building>()).add(b);
        });
        List<Building> list=map.get(type);
        return list!=null?list.size():0;
    }

    public int getTraffic(int x,int y){//Get location traffic (single block of land)
        List<Integer> sum = new ArrayList<>();
        Coordinate local = new Coordinate(x, y);
        //1. Find out whether there is a building at this coordinate point, or 0 if there is no building
        GridIndex gridIndex = local.toGridIndex();
        City.instance().forEachBuilding(gridIndex, b -> {
            Collection<Coordinate> coordinates = b.area().toCoordinates();
            if(coordinates.contains(local)){
                sum.add(b.getFlow());
            }
        });
        return sum.stream().reduce(Integer::sum).orElse(0);
    }
    //When buying a house moveKnownValue starts
    public void buildApartmentMoveKnownValue(Building b){
        moveKnownApartmentMap(b);
    }
    //Update when changes (modify pricing, modify residential eva change to upgrade degree, and prosperity change)
    public void moveKnownApartmentMap(Building b){
        Apartment apartment=(Apartment)b;
        //moveKnownValue = ((1 + residential quality / average value of residential quality in the city) + (1 + residential popularity / average value of residential recognition in the city)) * prosperity * 100
        double moveKnownValue = ((1 + apartment.getTotalQty() / AiBaseAvgManager.getInstance().getQualityMapVal(MetaBuilding.APARTMENT)) + (1 + apartment.getTotalBrand() / AiBaseAvgManager.getInstance().getBrandMapVal(MetaBuilding.APARTMENT))) * ProsperityManager.instance().getBuildingProsperity(b) * 100;
        logger.info("moveKnownApartmentMap moveKnownValue: " + moveKnownValue);
        moveKnownApartmentMap.put(b,moveKnownValue);
    }
    public Map<Building,Double> getMoveKnownaApartmentMap(){
        return moveKnownApartmentMap;
    }
    //At the start of the purchase of the moveKnownValue of the retail store (when the retail store shelf is modified, the retail store is put on the shelf, and the shelf is removed)
    public void buildRetailMoveKnownValue(Building b){
        moveKnownRetailMap(b);
    }
    //Update when changes (modify pricing, modify retail store-related eva change to upgrade level, and prosperity change)
    public void moveKnownRetailMap(Building b){
        RetailShop shop = (RetailShop)b;
        //moveKnownValue = ((1 + retail store quality / average retail store quality across the city) + (1 + retail store popularity / average retail store visibility across the city)) * prosperity * 100
        double moveKnownValue = ((1 + shop.getTotalQty() / AiBaseAvgManager.getInstance().getQualityMapVal(MetaBuilding.RETAIL)) + (1 + shop.getTotalBrand() / AiBaseAvgManager.getInstance().getBrandMapVal(MetaBuilding.RETAIL))) * ProsperityManager.instance().getBuildingProsperity(b) * 100;

        Shelf shelf=shop.getShelf();
        Collection<ItemKey> itemKeys=shelf.getGoodsItemKey();
        itemKeys.forEach(itemKey -> {
            MetaGood goods=MetaData.getGood(itemKey.meta.id);
            MetaGood.Type type=MetaGood.goodType(goods.id);

            GoodFilter filter=new GoodFilter();
            filter.type=type.ordinal();
            filter.lux=goods.lux;

            GoodSellInfo goodSellInfo=new GoodSellInfo();
            goodSellInfo.b=b;
            goodSellInfo.itemKey=itemKey;
            goodSellInfo.content=shelf.getContent(itemKey);
            goodSellInfo.moveKnownValue=moveKnownValue;
            retailShopGoodMap.computeIfAbsent(filter,k -> new HashSet<GoodSellInfo>()).add(goodSellInfo); //The key is a large type of luxury and product type, and the value of the retail store and product information corresponding to this type of luxury
        });
    }
    public Map<GoodFilter,Set<GoodSellInfo>> getRetailShopGoodMap(){
        return  retailShopGoodMap;
    }
    /*This method is called when the business is closed and the building is demolished*/
    public void updateRetailShopGoodMapOrBuilding(Building b){
        if(b instanceof RetailShop||b.outOfBusiness())
            return;
        RetailShop retail = (RetailShop) b;
        Shelf shelf = retail.getShelf();
        Collection<ItemKey> goodsItemKey = shelf.getGoodsItemKey();//All goods on this shelf
        goodsItemKey.forEach(key->{
            Integer goodId = key.meta.id;
            MetaGood goods=MetaData.getGood(goodId);
            MetaGood.Type type = MetaGood.goodType(goodId);
            GoodFilter goodFilter = new GoodFilter();
            goodFilter.lux = goods.lux;
            goodFilter.type = type.ordinal();
            GoodSellInfo sellInfo= new GoodSellInfo();
            sellInfo.itemKey = key;
            sellInfo.b = b;
            Set<GoodSellInfo> goodSellInfos = retailShopGoodMap.getOrDefault(goodFilter, new HashSet<>());
            if(goodSellInfos.contains(sellInfo))
                goodSellInfos.remove(sellInfo);//Update products corresponding to luxury and type
        });
    }
    public static final class GoodFilter {
        public int type = 0; //Commodity categories
        public int lux = 0;  //Commodity luxury
        @Override
        public int hashCode() {
            return Integer.parseInt(type+""+lux);
        }
        @Override
        public boolean equals(Object obj) {
            if(obj instanceof GoodFilter){
                GoodFilter other = (GoodFilter) obj;
                if((this.type==other.type)&&(this.lux==other.lux)){
                    return true;
                }else{
                    return false;
                }
            }
            return false;
        }
        @Override
        public String toString() {
            return "type " + type + ", lux " + lux;
        }
    }

    /*It can also be used to remove an unlimited number of items when the retail store is off the shelf (remove a single item)*/
    public void removeRetailGoodInfo(Building b,ItemKey key){
        MetaGood goods=MetaData.getGood(key.meta.id);
        MetaGood.Type type=MetaGood.goodType(goods.id);
        GoodFilter filter=new GoodFilter();
        filter.type=type.ordinal();
        filter.lux=goods.lux;
        GoodSellInfo sellInfo= new GoodSellInfo();
        sellInfo.itemKey = key;
        sellInfo.b = b;
        retailShopGoodMap.getOrDefault(filter, new HashSet<>()).remove(sellInfo);
    }

    public static final class GoodSellInfo {
        public Building b;   //Retail store
        public ItemKey itemKey; //Product information
        public Shelf.Content content; //selling price
        public double moveKnownValue;
        public double buyKnownValue;
        public int cost; //Consumption expectations
        public double r; //Purchase weight
        public int buyNum;//Purchase quantity

        @Override
        public int hashCode() {
            return  Objects.hash(b, itemKey);
        }

        @Override
        public boolean equals(Object obj) {
            if(this==obj)
                return true;
            if(obj instanceof GoodSellInfo){
                GoodSellInfo other = (GoodSellInfo) obj;
                if((this.b.equals(other.b))&&(this.itemKey.equals(other.itemKey))){
                    return true;
                }else{
                    return false;
                }
            }
            return false;
        }
    }

    public void removeKnownApartmentMap(Building b){
        moveKnownApartmentMap.remove(b);
    }
}
