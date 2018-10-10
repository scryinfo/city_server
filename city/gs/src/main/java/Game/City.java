package Game;

import Game.Timers.PeriodicTimer;
import Shared.Package;
import Shared.Util;
import com.google.common.collect.Sets;
import com.google.protobuf.ByteString;
import gs.Gs;
import gscode.GsCode;
import org.apache.log4j.Logger;

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
    private MetaCity meta;
    private Grid[][] grids;
    private static City instance;
    private ScheduledExecutorService e = Executors.newScheduledThreadPool(1);
    private ArrayDeque<Runnable> queue = new ArrayDeque<>();
    private boolean taskIsRunning = false;
    private PeriodicTimer metaAuctionLoadTimer = new PeriodicTimer();
    public int[] timeSection() {
        return meta.timeSection;
    }
    public final static long UpdateIntervalNano = TimeUnit.MILLISECONDS.toNanos(200);
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

    private short[][] terrian;
    private HashMap<UUID, Building> allBuilding = new HashMap<>();

//    private HashMap<ObjectId, TrivialBuilding> allTrivialBuilding = new HashMap<>();
//    private HashMap<ObjectId, Apartment> allApartment;
//    private HashMap<ObjectId, PublicFacility> allPublicFacility;
//    private HashMap<ObjectId, ProduceDepartment> allProductingDepartment;
//    private HashMap<ObjectId, Laboratory> allLaboratory;
//    private HashMap<ObjectId, MaterialFactory> allMaterialFactory;
//    private HashMap<ObjectId, RetailShop> allRetailShop;
    // almost all of player's data are shared all the time
    // like building, ground, product. This requires and index to
    // retrieve those things according to player id
    private HashMap<UUID, HashMap<UUID, Building>> playerBuilding = new HashMap<>();
    //private HashMap<ObjectId, Ground> ground = new HashMap<>();
    public static void init(MetaCity meta) {
        instance = new City(meta);
    }
    public static City instance() {
        return instance;
    }

    private City(MetaCity meta) {
        this.meta = meta;
        GridMaxX = meta.x / meta.gridX;
        GridMaxY = meta.y / meta.gridY;
        grids = new Grid[GridMaxX][GridMaxY];
        for(int i = 0; i < GridMaxX; ++i)
        {
            for(int j = 0; j < GridMaxY; ++j)
                grids[i][j] = new Grid(i,j);
        }
        terrian = new short[meta.x][meta.y];
        this.currentTimeSectionIdx = meta.indexOfHour(this.localTime().getHour());
        // load initial buildings
        loadSysBuildings();
        // load all player buildings, cache them into maps
        loadPlayerBuildings();

        this.metaAuctionLoadTimer.setPeriodic(TimeUnit.DAYS.toMillis(1), Util.getTimerDelay(0, 0));

        this.lastHour = this.localTime().getHour();
    }

    private void loadSysBuildings() {
        for(MetaData.InitialBuildingInfo i : MetaData.getAllInitialBuilding())
        {
            if(MetaBuilding.type(i.id) != MetaBuilding.TRIVIAL)
            {
                logger.error("InitialBuilding contain no trivial building! id: " + i.id);
                continue;
            }
            Building b = Building.create(i.id, new Coord(i.x, i.y), SysRoleId);
            this.calcuTerrian(b);
            // b is useless, discard it
        }
    }
    private void loadPlayerBuildings() {
        for(Building b : GameDb.getAllBuilding()) {
            this.take(b);
        }
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
        // init metaBuilding?
        // calcu parameters
        e.scheduleAtFixedRate(() -> {
            final long now = System.nanoTime();
            this.update(now - lastTs);
            lastTs = now;
        }, 0, UpdateIntervalNano, TimeUnit.NANOSECONDS);
    }
    private long lastTs;
    private int lastHour;

    public void update(long diffNano) {
        if(this.metaAuctionLoadTimer.update(diffNano)) {
            GroundAuction.instance().loadMore();
        }
        GroundAuction.instance().update(diffNano);
        allBuilding.forEach((k,v)->v.update(diffNano));
        NpcManager.instance().update(diffNano);
        GameServer.allGameSessions.forEach((k,v)->{v.update(diffNano);});

        // do this at last
        updateTimeSection(diffNano);
    }
    private long timeSectionAccumlateNano = 0;
    public int currentTimeSectionIdx() {
        return currentTimeSectionIdx;
    }
    private int currentTimeSectionIdx;
    private void updateTimeSection(long diffNano) {
        int nowHour = this.localTime().getHour();
        if(lastHour != nowHour)
        {
            hourTickAction(nowHour);
            lastHour = nowHour;
        }
        timeSectionAccumlateNano += diffNano;
        if(timeSectionAccumlateNano - TimeUnit.SECONDS.toNanos(10) > TimeUnit.HOURS.toNanos(meta.minHour))
        {

            int index = meta.indexOfHour(nowHour);
            if(index == -1) // still in the range
                return;
            if(currentTimeSectionIdx != index) {
                timeSectionAccumlateNano = 0;
                timeSectionTickAction(index, nowHour, meta.timeSectionDuration(index));
            }
        }
    }

    private void hourTickAction(int nowHour) {
        NpcManager.instance().hourTickAction(nowHour);
        allBuilding.forEach((k,v)->v.hourTickAction(nowHour));
    }

    private void timeSectionTickAction(int newIndex, int nowHour, int hours) {
        // maintain waitToUpdate?
        boolean dayPass = newIndex == 0;
        NpcManager.instance().timeSectionTick(newIndex, nowHour, hours);
        allBuilding.forEach((k,v)->v.timeSectionTick(newIndex, nowHour, hours));
    }

    public long leftMsToNextTimeSection() {
        LocalTime now = localTime();
        int nextTimeSectionHour = nextTimeSectionHour(this.currentTimeSectionIdx);
        LocalTime next = LocalTime.of(nextTimeSectionHour, 0);
        return Duration.between(now, next).toMillis();
    }
    public int nextTimeSectionDuration() {
        return meta.nextTimeSectionDuration(this.currentTimeSectionIdx);
    }
    public int nextTimeSectionHour(int nowHour) {
        for(int i = 0; i < meta.timeSection.length; ++i) {
            if(nowHour == meta.timeSection[i]) {
                if(i+1 == meta.timeSection.length)
                    return meta.timeSection[0];
                else
                    return meta.timeSection[i+1];
            }
        }
        return -1;
    }
    LocalTime localTime() {
        return Util.getLocalTime(this.meta.timeZone);
    }
    public void add(Player p) {
        // according to p's property ids, get the objects
        p.setCity(this);
        this.updateVisibilityCreate(p);
    }

    public void relocation(Player p, GridIndex old) {
        this.grids[old.x][old.y].playerLeaving(p.id());
        this.grids[p.getPosition().x][p.getPosition().y].playerComing(p.id());
        this.updateVisibilityRelocate(p, old);
    }
    public void forEachBuilding(List<GridIndex> index, Consumer<Building> f) {
        for(GridIndex idx : index)
            grids[idx.x][idx.y].forAllBuilding(f);
    }
    public void forEachBuilding(GridIndexPair range, Consumer<Building> f) {
        for(int x = range.l.x; x <= range.r.x; ++x)
        {
            for(int y = range.l.y; y <= range.r.y; ++y)
                grids[x][y].forAllBuilding(f);
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

        Gs.UnitRemove.Builder urb = Gs.UnitRemove.newBuilder();
        this.forEachBuilding(leavingGrids, (Building b)->{
            urb.addId(ByteString.copyFrom(Util.toBytes(b.id())));
        });
        p.send(Package.create(GsCode.OpCode.unitRemove_VALUE, urb.build()));
    }
    private void updateVisibilityCreate(Player p) {
        Gs.UnitCreate.Builder ucb = Gs.UnitCreate.newBuilder();
        this.forEachBuilding(p.getPosition().toSyncRange(), (Building b)->{
            ucb.addInfo(b.toProto());
        });
        p.send(Package.create(GsCode.OpCode.unitCreate_VALUE, ucb.build()));
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

    public void mount(UUID id, CoordPair area) {
        //this.ground.getOrDefault(id, new ArrayList);
    }
    public void mount(UUID id, Collection<Coord> area) {
        //this.ground.getOrDefault(id, new ArrayList);
    }
    public boolean delBuilding(UUID id, Player player) {
        Building b = this.allBuilding.get(id);
        if(b == null || b.ownerId() != player.id())
            return false;
        NpcManager.instance().delete(b.getAllNpc());
        b.readyForDestory();
        this.allBuilding.remove(id);
        this.playerBuilding.getOrDefault(player.id(), new HashMap<>()).remove(id);

        GameDb.delete(b);

        return true;
    }
    public boolean addBuilding(int id, Coord pos, Player owner) {
        Building b = Building.create(id, pos, owner.id()); // pass owner into it???
        if(!this.canBuild(b))
            return false;
        int cost = 100;
        if(owner.money() < cost)
            return false;
        take(b);
        owner.decMoney(cost);
        GameDb.saveOrUpdate(Arrays.asList(owner, b));

        GridIndexPair gip = pos.toGridIndex().toSyncRange();
        Package pack = Package.create(GsCode.OpCode.unitCreate_VALUE, Gs.UnitCreate.newBuilder().addInfo(b.toProto()).build());
        for(int x = gip.l.x; x < gip.r.x; ++x) {
            for (int y = gip.l.y; x < gip.r.y; ++y) {
                grids[x][y].send(pack);
            }
        }
        return true;
    }
    private boolean canBuild(Building building) {
        for(int x = building.area().l.x; x <= building.area().r.x; ++x) {
            for(int y = building.area().l.y; y <= building.area().r.y; ++y) {
                if(terrian[x][y] != TERRIAN_NONE)
                    return false;
            }
        }
        return true;
    }
    private void take(Building building) {
        assert building.type() != MetaBuilding.TRIVIAL;
        calcuTerrian(building);
        this.allBuilding.put(building.id(), building);
        HashMap<UUID, Building> bs = this.playerBuilding.getOrDefault(building.ownerId(), new HashMap<>());
        bs.put(building.id(), building);
    }

    private void calcuTerrian(Building building) {
        for(int x = building.area().l.x; x <= building.area().r.x; ++x) {
            for(int y = building.area().l.y; y <= building.area().r.y; ++y) {
                if(building.type() == MetaBuilding.TRIVIAL)
                    terrian[x][y] = TERRIAN_TRIVIAL;
                else
                    terrian[x][y] = TERRIAN_PLAYER;
            }
        }
    }

    class GridDiffs {
        public GridDiffs() {
            l.ensureCapacity(Grid.SYNC_RANGE_NUM);
            r.ensureCapacity(Grid.SYNC_RANGE_NUM);
        }
        ArrayList<GridIndex> l = new ArrayList<>();
        ArrayList<GridIndex> r = new ArrayList<>();
    }
}
