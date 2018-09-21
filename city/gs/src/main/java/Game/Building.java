package Game;

import Shared.Util;
import com.google.common.collect.EvictingQueue;
import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import com.mongodb.client.ClientSession;
import gs.Gs;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

public abstract class Building {
    private static final int MAX_FLOW_SIZE = 30*24;
    private static final int PAYMENT_HOUR = 8;
    public int type() {
        return MetaBuilding.type(metaBuilding.id);
    }

    public static Building create(int id, Coord pos, ObjectId ownerId) {
        switch(MetaBuilding.type(id))
        {
            case MetaBuilding.TRIVIAL:
                return new TrivialBuilding(MetaData.getTrivialBuilding(id), pos, ownerId);
            case MetaBuilding.MATERIAL:
                return new MaterialFactory(MetaData.getMaterialFactory(id), pos, ownerId);
            case MetaBuilding.PRODUCTING:
                return new ProductingDepartment(MetaData.getProductingDepartment(id), pos, ownerId);
            case MetaBuilding.RETAIL:
                return new RetailShop(MetaData.getRetailShop(id), pos, ownerId);
            case MetaBuilding.APARTMENT:
                return new Apartment(MetaData.getApartment(id), pos, ownerId);
            case MetaBuilding.LAB:
                return new Laboratory(MetaData.getLaboratory(id), pos, ownerId);
            case MetaBuilding.PUBLIC:
                return new PublicFacility(MetaData.getPublicFacility(id), pos, ownerId);
        }
        return null;
    }
    public static Building create(Document d) {
        int id = d.getInteger("mid");
        switch(MetaBuilding.type(id))
        {
            case MetaBuilding.TRIVIAL:
                return new TrivialBuilding(MetaData.getTrivialBuilding(id), d);
            case MetaBuilding.MATERIAL:
                return new MaterialFactory(MetaData.getMaterialFactory(id), d);
            case MetaBuilding.PRODUCTING:
                return new ProductingDepartment(MetaData.getProductingDepartment(id), d);
            case MetaBuilding.RETAIL:
                return new RetailShop(MetaData.getRetailShop(id), d);
            case MetaBuilding.APARTMENT:
                return new Apartment(MetaData.getApartment(id), d);
            case MetaBuilding.LAB:
                return new Laboratory(MetaData.getLaboratory(id), d);
            case MetaBuilding.PUBLIC:
                return new PublicFacility(MetaData.getPublicFacility(id), d);
        }
        return null;
    }
    protected MetaBuilding metaBuilding;
    private ObjectId id;
    private ObjectId ownerId;
    private Coord coord;
    private int flow = 0;
    private int flowCount = 0;
    private int salary;
    private int happy = 0;
    private Set<Npc> allNpc = new HashSet<>();
    Set<Npc> getAllNpc() {
        return allNpc;
    }
    public final void readyForDestory() {
        allNpc.clear();

    }
    protected void destoryImpl(){}
    static class FlowInfo {
        long ts;
        int n;

        public FlowInfo(long ts, int n) {
            this.ts = ts;
            this.n = n;
        }
    }
    private EvictingQueue<FlowInfo> flowHistory = EvictingQueue.create(MAX_FLOW_SIZE);
    public ObjectId id() {
        return id;
    }
    public ObjectId ownerId() {
        return ownerId;
    }
    public Building(MetaBuilding meta, Coord pos, ObjectId ownerId) {
        this.id = new ObjectId();
        this.ownerId = ownerId;
        this.coord = pos;
        this.metaBuilding = meta;
    }
    public Building(MetaBuilding meta, Document doc) {
        this.id = doc.getObjectId("id");
        this.ownerId = doc.getObjectId("owner");
        this.coord = new Coord((Document) doc.get("coord"));
        this.metaBuilding = meta;
        this.salary = doc.getInteger("salary");
        this.happy = doc.getInteger("happy");

        int lastFlow = 0;
        for(Document d : (List<Document>) doc.get("flowHis")) {
            long ts = d.getLong("t");
            int n = d.getInteger("n");
            this.flowHistory.add(new FlowInfo(ts, n));
            lastFlow = n;
        }
        this.flow = lastFlow; // don't blame me, java's silly design, no rbegin
    }
    public void hireNpc(int initSalary) {
        for(Npc npc : NpcManager.instance().create(this.metaBuilding.maxWorkerNum, this, initSalary))
        {
            npc.visit(this);
        }
    }
    public CoordPair effectRange() {
        Coord l = coord.shiftLU(this.metaBuilding.effectRange);
        Coord r = coord.offset(this.metaBuilding.x, this.metaBuilding.y).shiftRB(this.metaBuilding.effectRange);
       return new CoordPair(l, r);
    }
    public CoordPair area() {
        return new CoordPair(coord, coord.offset(metaBuilding.x, metaBuilding.y));
    }
    public Document toBson() {
        Document res = new Document();
        res.append("id", id);
        res.append("ownerId", ownerId());
        res.append("coord", coord.toBson());
        res.append("salary", salary);
        res.append("happy", happy);

        List<Document> flowHistoryArr = new ArrayList<>();
        for(FlowInfo i : this.flowHistory) {
            Document d = new Document();
            d.append("t", i.ts);
            d.append("n", i.n);
            flowHistoryArr.add(d);
        }
        res.append("flowHis", flowHistoryArr);
        return res;
    }
    public Coord coordinate() {
        return coord;
    }
    public Gs.BuildingInfo toProto() {
        return Gs.BuildingInfo.newBuilder()
                .setId(ByteString.copyFrom(id.toByteArray()))
                .setMId(metaBuilding.id)
                .setPos(Gs.MiniIndex.newBuilder()
                        .setX(this.coord.x)
                        .setY(this.coord.y))
                .setData(Gs.BuildingInfo.MutableData.newBuilder()
                        .setOwnerId(Util.toByteString(ownerId))
                        .build())
                .build();
    }
    public abstract Message detailProto();
    // there is no need to remember which npc is in this building now
    public void enter(Npc npc) {
        allNpc.add(npc);
        flowCount += 1;
    }
    public void leave(Npc npc) {
        allNpc.remove(npc);
    }
    protected Gs.BuildingDetailCommon commonProto() {
        return Gs.BuildingDetailCommon.newBuilder()
                .setId(Util.toByteString(id()))
                .setOwnerId(Util.toByteString(ownerId()))
                .setFlow(flow)
                .setPos(coord.toProto())
                .setSalary(salary)
                .setHappy(happy)
                .build();
    }
    void update(long diffNano) {
    }
    void timeSectionTick(int newIdx, int nowHour, int hours) {

    }

    public void setSalary(int salary) {
        this.salary = salary;
    }

    public void hourTickAction(int nowHour) {
        flow = flowCount;
        flowCount = 0;
        flowHistory.add(new FlowInfo(System.currentTimeMillis(), flow));
        assert this.type() != MetaBuilding.TRIVIAL;
        if(nowHour == PAYMENT_HOUR && !this.allNpc.isEmpty()) {
            Player p = GameDb.getPlayer(ownerId);
            if(p != null) {
                if(p.decMoney(this.salary * this.allNpc.size())) {
                    allNpc.forEach(npc -> npc.addMoney(this.salary));
                    ClientSession session = GameDb.startTransaction();
                    p.save();
                    GameDb.offsetNpcMoney(allNpc.stream().map(Npc::id).collect(Collectors.toList()), this.salary);
                    GameDb.commit(session);
                }
            }
        }
    }

}
