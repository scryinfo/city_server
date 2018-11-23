package Game;

import DB.Db;
import Game.Listener.ConvertListener;
import Shared.Package;
import Shared.Util;
import com.google.common.collect.EvictingQueue;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import gs.Gs;
import gscode.GsCode;
import io.netty.channel.ChannelId;

import javax.persistence.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Entity
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
@EntityListeners({
        ConvertListener.class,
})
public abstract class Building {
    private static final int MAX_FLOW_SIZE = 30*24;
    private static final int PAYMENT_HOUR = 8;

    protected Building() {
    }
    public abstract int quality();
    boolean canUseBy(UUID userId) {
        return ownerId.equals(userId);
    }
    public int type() {
        return MetaBuilding.type(metaBuilding.id);
    }
    public boolean outOfBusiness() {
        return true; // according to employee satisfication
    }
    public static Building create(int id, Coordinate pos, UUID ownerId) {
        switch(MetaBuilding.type(id))
        {
            case MetaBuilding.TRIVIAL:
                return new TrivialBuilding(MetaData.getTrivialBuilding(id), pos, ownerId);
            case MetaBuilding.MATERIAL:
                return new MaterialFactory(MetaData.getMaterialFactory(id), pos, ownerId);
            case MetaBuilding.PRODUCE:
                return new ProduceDepartment(MetaData.getProduceDepartment(id), pos, ownerId);
            case MetaBuilding.RETAIL:
                return new RetailShop(MetaData.getRetailShop(id), pos, ownerId);
            case MetaBuilding.APARTMENT:
                return new Apartment(MetaData.getApartment(id), pos, ownerId);
            case MetaBuilding.LAB:
                return new Laboratory(MetaData.getLaboratory(id), pos, ownerId);
            case MetaBuilding.PUBLIC:
                return new PublicFacility(MetaData.getPublicFacility(id), pos, ownerId);
//            case MetaBuilding.VIRTUAL:
//                return new VirtualBuilding(MetaData.getVirtualBuilding(id), pos, ownerId);
        }
        return null;
    }
    @Column(name = "metaId", updatable = false, nullable = false)
    @Convert(converter = MetaBuilding.Converter.class)
    protected MetaBuilding metaBuilding;

    public void watchDetailInfo(GameSession s) {
        if(detailWatchers.contains(s.channelId()))
            detailWatchers.remove(s.channelId());
        else
            detailWatchers.add(s.channelId());
    }
    @Transient
    Set<ChannelId> detailWatchers = new HashSet<>();

    public void broadcastCreate() {
        GridIndexPair gip = this.coordinate().toGridIndex().toSyncRange();
        Package pack = Package.create(GsCode.OpCode.unitCreate_VALUE, Gs.UnitCreate.newBuilder().addInfo(this.toProto()).build());
        City.instance().send(gip, pack);
    }
    public void broadcastChange() {
        GridIndexPair gip = this.coordinate().toGridIndex().toSyncRange();
        Package pack = Package.create(GsCode.OpCode.unitChange_VALUE, this.toProto());
        City.instance().send(gip, pack);
    }
    public void broadcastDelete() {
        GridIndexPair gip = this.coordinate().toGridIndex().toSyncRange();
        Package pack = Package.create(GsCode.OpCode.unitRemove_VALUE, Gs.Bytes.newBuilder().addIds(Util.toByteString(id)).build());
        City.instance().send(gip, pack);
    }

    public List<Building> getAllBuildingInEffectRange() {
        List<Building> res = new ArrayList<>();
        GridIndexPair gip = this.coordinate().toGridIndex().toSyncRange();
        CoordPair thisRange = this.effectRange();
        City.instance().forEachBuilding(gip, building -> {
            if(CoordPair.overlap(building.area(), thisRange))
                res.add(building);
        });
        return res;
    }
    public List<Building> getAllBuildingEffectMe(int type) {
        List<Building> res = new ArrayList<>();
        GridIndexPair gip = this.coordinate().toGridIndex().toSyncRange();
        City.instance().forEachBuilding(gip, building -> {
            if(building.type() == type && CoordPair.overlap(building.effectRange(), this.area()))
                res.add(building);
        });
        return res;
    }

    public void serializeBinaryMembers() {
        if(this.flowHistory.isEmpty())
            return;
        Db.FlowHistory.Builder builder = Db.FlowHistory.newBuilder();
        this.flowHistory.forEach(f -> builder.addI(Db.FlowHistory.Info.newBuilder().setTs(f.ts).setN(f.n)));
        this.flowHistoryBinary = builder.build().toByteArray();
    }

    public void deserializeBinaryMembers() throws InvalidProtocolBufferException {
        if(this.flowHistoryBinary == null)
            return;
        for(Db.FlowHistory.Info i : Db.FlowHistory.parseFrom(this.flowHistoryBinary).getIList())
            this.flowHistory.add(new FlowInfo(i.getTs(), i.getN()));
    }

    @Column(name = "flow_binary")
    private byte[] flowHistoryBinary;


    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private UUID ownerId;

    @Embedded
    private Coordinate coordinate;

    @Transient
    private int flow = 0;

    @Transient
    private int flowCount = 0;

    @Column(nullable = false)
    private int salary;

    @Column(nullable = false)
    private int happy = 0;

    @Transient
    private Set<Npc> allStaff = new HashSet<>();

    public int salary() {
        return salary;
    }

    public int cost() {
        return 0;
    }

    @Embeddable
    static class FlowInfo {
        int ts;
        int n;

        public FlowInfo(int ts, int n) {
            this.ts = ts;
            this.n = n;
        }

        protected FlowInfo() {}
    }
    // this doesn't fucking works
//    @Column
//    @Convert(converter = FlowHistoryConverter.class)
    @Transient
    private EvictingQueue<FlowInfo> flowHistory = EvictingQueue.create(MAX_FLOW_SIZE);

    public static final class FlowHistoryConverter implements AttributeConverter<EvictingQueue<FlowInfo>, byte[]> {

        @Override
        public byte[] convertToDatabaseColumn(EvictingQueue<FlowInfo> attribute) {
            Db.FlowHistory.Builder builder = Db.FlowHistory.newBuilder();
            attribute.forEach(f -> builder.addI(Db.FlowHistory.Info.newBuilder().setTs(f.ts).setN(f.n)));
            return builder.build().toByteArray();
        }

        @Override
        public EvictingQueue<FlowInfo> convertToEntityAttribute(byte[] dbData) {
            EvictingQueue<FlowInfo> res = EvictingQueue.create(MAX_FLOW_SIZE);
            try {
                for(Db.FlowHistory.Info i : Db.FlowHistory.parseFrom(dbData).getIList()) {
                    res.add(new FlowInfo(i.getTs(), i.getN()));
                }
            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }
            return res;
        }
    }

    public UUID id() {
        return id;
    }
    public UUID ownerId() {
        return ownerId;
    }
    public Building(MetaBuilding meta, Coordinate pos, UUID ownerId) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.coordinate = pos;
        this.metaBuilding = meta;
    }
    Set<Npc> getAllStaff() {
        return allStaff;
    }
    public final void destroy() {
        NpcManager.instance().delete(allStaff);
        allStaff.clear();
    }
    protected void destoryImpl(){}
//    public Building(MetaBuilding meta, Document doc) {
//        this.id = doc.getObjectId("id");
//        this.ownerId = doc.getObjectId("owner");
//        this.coordinate = new Coordinate((Document) doc.get("coordinate"));
//        this.metaBuilding = meta;
//        this.salary = doc.getInteger("salary");
//        this.happy = doc.getInteger("happy");
//
//        int lastFlow = 0;
//        for(Document _d : (List<Document>) doc.get("flowHis")) {
//            long ts = _d.getLong("t");
//            int n = _d.getInteger("n");
//            this.flowHistory.consumeReserve(new FlowInfo(ts, n));
//            lastFlow = n;
//        }
//        this.flow = lastFlow; // don't blame me, java's silly design, no rbegin
//    }
    public void hireNpc(int initSalary) {
        for(Npc npc : NpcManager.instance().create(this.metaBuilding.workerNum, this, initSalary))
        {
            npc.goFor(this);
        }
    }
    public CoordPair effectRange() {
        Coordinate l = coordinate.shiftLU(this.metaBuilding.effectRange);
        Coordinate r = coordinate.offset(this.metaBuilding.x, this.metaBuilding.y).shiftRB(this.metaBuilding.effectRange);
       return new CoordPair(l, r);
    }
    public CoordPair area() {
        return new CoordPair(coordinate, coordinate.offset(metaBuilding.x, metaBuilding.y));
    }
//    public Document toBson() {
//        Document res = new Document();
//        res.append("id", id);
//        res.append("ownerId", ownerId());
//        res.append("coordinate", coordinate.toBson());
//        res.append("salary", salary);
//        res.append("happy", happy);
//
//        List<Document> flowHistoryArr = new ArrayList<>();
//        for(FlowInfo i : this.flowHistory) {
//            Document _d = new Document();
//            _d.append("t", i.ts);
//            _d.append("n", i.n);
//            flowHistoryArr.consumeReserve(_d);
//        }
//        res.append("flowHis", flowHistoryArr);
//        return res;
//    }
    public Coordinate coordinate() {
        return coordinate;
    }
    public Gs.BuildingInfo toProto() {
        return Gs.BuildingInfo.newBuilder()
                .setId(Util.toByteString(id))
                .setMId(metaBuilding.id)
                .setPos(Gs.MiniIndex.newBuilder()
                        .setX(this.coordinate.x)
                        .setY(this.coordinate.y))
                .setOwnerId(Util.toByteString(ownerId))
                .setNpcFlow(this.flow)
                .setState(Gs.BuildingState.valueOf(state))
                .setSalary(salary)
                .setHappy(happy)
                .build();
    }
    protected int state = Gs.BuildingState.WAITING_OPEN_VALUE;
    public abstract Message detailProto();
    public abstract void appendDetailProto(Gs.BuildingSet.Builder builder);

    protected abstract void enterImpl(Npc npc);

    // for current requirements, there is no leaving actions
    protected abstract void leaveImpl(Npc npc);
    // there is no need to remember which npc is in this building now
    public void enter(Npc npc) {
        flowCount += 1;
        enterImpl(npc);
    }
    public int getFlow() {
        return this.flow;
    }
    public void leave(Npc npc) {
        leaveImpl(npc);
    }
    void update(long diffNano) {
        if(this.outOfBusiness())
            return;
        this._update(diffNano);
    }
    protected abstract void _update(long diffNano);
    void timeSectionTick(int newIdx, int nowHour, int hours) {

    }

    public void setSalary(int salary) {
        this.salary = salary;
    }

    public void hourTickAction(int nowHour) {
        flow = flowCount;
        flowCount = 0;
        flowHistory.add(new FlowInfo((int) TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()), flow));
        //this._d.dirty(); // isDirty the field, or else hibernate won't update this field!!!
        assert this.type() != MetaBuilding.TRIVIAL;
        if(nowHour == PAYMENT_HOUR && !this.allStaff.isEmpty()) {
            Player p = GameDb.queryPlayer(ownerId);
            if(p != null) {
                if(p.decMoney(this.salary * this.allStaff.size())) {
                    allStaff.forEach(npc -> npc.addMoney(this.salary));
                    List<Object> updates = allStaff.stream().map(Object.class::cast).collect(Collectors.toList());
                    updates.add(p);
                    GameDb.saveOrUpdate(updates);
                }
            }
        }
    }

}
