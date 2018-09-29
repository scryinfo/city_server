package Game;

import Shared.Util;
import com.google.common.collect.EvictingQueue;
import com.google.protobuf.Message;
import gs.Gs;
import org.bson.Document;

import javax.persistence.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// this is better. 1, all building will in memory all time, so there is no requirement for
// reference building in other class maintain by hibernate. 2, performance is better due to each
// subclass has its own table
@MappedSuperclass
public abstract class Building {
    private static final int MAX_FLOW_SIZE = 30*24;
    private static final int PAYMENT_HOUR = 8;
    public int type() {
        return MetaBuilding.type(metaBuilding.id);
    }

    public static Building create(int id, Coord pos, UUID ownerId) {
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
    @Transient
    protected MetaBuilding metaBuilding;

    @Embeddable //hide those members, the only purpose is to mapping to the table
    protected static class AdapterData {
        @Column(name = "mid", updatable = false, nullable = false)
        protected int metaId;
        @Column(name = "flowHistory")
        private byte[] flowHistoryBinary;
    }
    @Embedded
    protected final AdapterData adapterData = new AdapterData();

    // don't override this in subclass, or else this function will not gets called unless call super._base1()
    // so I name this function names strange in purpose
    @PrePersist
    @PreUpdate
    private void _base1() {
        Document d = new Document();
        d.append("1",  this.flowHistory.stream().flatMap(f -> Stream.of(f.n, f.ts)).collect(Collectors.toList()));
        this.adapterData.flowHistoryBinary = Util.toBytes(d);
    }
    @PostLoad
    private void _base2() {
        Document d = Util.toDocument(this.adapterData.flowHistoryBinary);
        List<Integer> l = (List<Integer>) d.get("1");
        for(int i = 0; i < l.size(); i+=2) {
            this.flowHistory.add(new FlowInfo(l.get(i), l.get(i+1)));
        }
    }
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private UUID ownerId;

    @Embedded
    private Coord coord;

    @Transient
    private int flow = 0;

    @Transient
    private int flowCount = 0;

    @Column(nullable = false)
    private int salary;

    @Column(nullable = false)
    private int happy = 0;

    @Transient
    private Set<Npc> allNpc = new HashSet<>();

    static class FlowInfo {
        int ts;
        int n;

        public FlowInfo(int ts, int n) {
            this.ts = ts;
            this.n = n;
        }
    }
    @Transient
    private EvictingQueue<FlowInfo> flowHistory = EvictingQueue.create(MAX_FLOW_SIZE);



    public UUID id() {
        return id;
    }
    public UUID ownerId() {
        return ownerId;
    }
    public Building(MetaBuilding meta, Coord pos, UUID ownerId) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.coord = pos;
        this.metaBuilding = meta;
        this.adapterData.metaId = meta.id;
    }
    Set<Npc> getAllNpc() {
        return allNpc;
    }
    public final void readyForDestory() {
        allNpc.clear();

    }
    protected void destoryImpl(){}
//    public Building(MetaBuilding meta, Document doc) {
//        this.id = doc.getObjectId("id");
//        this.ownerId = doc.getObjectId("owner");
//        this.coord = new Coord((Document) doc.get("coord"));
//        this.metaBuilding = meta;
//        this.salary = doc.getInteger("salary");
//        this.happy = doc.getInteger("happy");
//
//        int lastFlow = 0;
//        for(Document d : (List<Document>) doc.get("flowHis")) {
//            long ts = d.getLong("t");
//            int n = d.getInteger("n");
//            this.flowHistory.add(new FlowInfo(ts, n));
//            lastFlow = n;
//        }
//        this.flow = lastFlow; // don't blame me, java's silly design, no rbegin
//    }
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
//    public Document toBson() {
//        Document res = new Document();
//        res.append("id", id);
//        res.append("ownerId", ownerId());
//        res.append("coord", coord.toBson());
//        res.append("salary", salary);
//        res.append("happy", happy);
//
//        List<Document> flowHistoryArr = new ArrayList<>();
//        for(FlowInfo i : this.flowHistory) {
//            Document d = new Document();
//            d.append("t", i.ts);
//            d.append("n", i.n);
//            flowHistoryArr.add(d);
//        }
//        res.append("flowHis", flowHistoryArr);
//        return res;
//    }
    public Coord coordinate() {
        return coord;
    }
    public Gs.BuildingInfo toProto() {
        return Gs.BuildingInfo.newBuilder()
                .setId(Util.toByteString(id))
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
        flowHistory.add(new FlowInfo((int) TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()), flow));
        assert this.type() != MetaBuilding.TRIVIAL;
        if(nowHour == PAYMENT_HOUR && !this.allNpc.isEmpty()) {
            Player p = GameDb.getPlayer(ownerId);
            if(p != null) {
                if(p.decMoney(this.salary * this.allNpc.size())) {
                    allNpc.forEach(npc -> npc.addMoney(this.salary));
                    List<Object> updates = allNpc.stream().map(Object.class::cast).collect(Collectors.toList());
                    updates.add(p);
                    GameDb.saveOrUpdate(updates);
                }
            }
        }
    }

}
