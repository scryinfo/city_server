package Game;

import Game.Meta.MetaBuilding;
import Game.Meta.MetaData;
import Game.Meta.MetaTalent;
import Game.Meta.MetaTalentCenter;
import Game.Timers.PeriodicTimer;
import Shared.Package;
import Shared.Util;
import com.google.protobuf.Message;
import gs.Gs;
import gscode.GsCode;

import javax.persistence.PostLoad;
import javax.persistence.Transient;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class TalentCenter extends Building {
    public TalentCenter(MetaTalentCenter meta, Coordinate pos, UUID ownerId) {
        super(meta, pos, ownerId);
        this.meta = meta;
        this.qty = meta.qty;
    }
    private int qty;
    @Transient
    private MetaTalentCenter meta;

    protected TalentCenter() {}

    @Override
    public int quality() {
        return this.qty;
    }

    @PostLoad
    private void _1() {
        this.meta = (MetaTalentCenter) super.metaBuilding;
    }

    @Override
    public Message detailProto() {
        return null;
    }

    @Override
    public void appendDetailProto(Gs.BuildingSet.Builder builder) {

    }

    @Override
    protected void enterImpl(Npc npc) {

    }

    @Override
    protected void leaveImpl(Npc npc) {

    }
    private static final int DB_UPDATE_INTERVAL_MS = 30000;
    @Transient
    protected PeriodicTimer dbTimer = new PeriodicTimer(DB_UPDATE_INTERVAL_MS, (int) (Math.random()*DB_UPDATE_INTERVAL_MS));
    @Override
    protected void _update(long diffNano) {
        this.lines.values().forEach(l -> l.update(diffNano));
        if(this.dbTimer.update(diffNano)) {
            GameDb.saveOrUpdate(this); // this will not ill-form other transaction due to all action are serialized
        }
    }

    public static final class Line {
        public Line(int workerNum, int buildingType, long leftNano) {
            this.workerNum = workerNum;
            this.buildingType = buildingType;
            this.leftNano = leftNano;
            this.createTs = System.currentTimeMillis();
        }

        UUID id = UUID.randomUUID();
        int workerNum;
        int buildingType;
        long leftNano;
        final long createTs;
        void update(long diffNano) {
            if(this.leftNano <= 0)
                this.leftNano = 0;
            else
                this.leftNano -= diffNano * workerNum;
        }
        Gs.TalentCenter.Line toProto() {
            return Gs.TalentCenter.Line.newBuilder()
                    .setCreateTs(createTs)
                    .setId(Util.toByteString(id))
                    .setLeftSec((int) TimeUnit.NANOSECONDS.toSeconds(this.leftNano))
                    .setType(this.buildingType)
                    .setWorkerNum(this.workerNum)
                    .build();
        }
    }
    private Map<UUID, Line> lines = new HashMap<>();
    public boolean addLine(int workerNum, int buildingType) {
        if(freeWorkerNum() < workerNum)
            return false;
        Line l = new Line(workerNum, buildingType, TimeUnit.SECONDS.toNanos(meta.createSecPreWorker));
        this.lines.put(l.id, l);
        Package p = Package.create(GsCode.OpCode.talentLineAddInform_VALUE, Gs.TalentLineAddInform.newBuilder().setBuildingId(Util.toByteString(this.id())).setLine(l.toProto()).build());
        this.sendToWatchers(p);
        return true;
    }
    public boolean delLine(UUID id) {
        if(this.lines.remove(id) != null) {
            Package p = Package.create(GsCode.OpCode.talentLineDelInform_VALUE, Gs.Id.newBuilder().setId(Util.toByteString(id)).build());
            this.sendToWatchers(p);
            return true;
        }
        return false;
    }
    public int freeWorkerNum() {
        return this.meta.workerNum - lines.values().stream().mapToInt(l -> l.workerNum).reduce(0, Integer::sum);
    }
    public Talent finishLine(UUID id) {
        Line line = lines.get(id);
        if(line == null || line.leftNano > 0)
            return null;
        return this.randomTalent(line.buildingType);
    }
    private Talent randomTalent(int buildingType) {
        int score = 0;
        int lv = MetaData.getTalentLv(score);
        MetaTalent metaTalent = MetaData.getTalent(buildingType, lv);
        return new Talent(metaTalent, this.ownerId());
    }
    public static boolean inapplicable(int buildingType) {
        return buildingType == MetaBuilding.TRIVIAL || buildingType == MetaBuilding.APARTMENT || buildingType == MetaBuilding.LAB;
    }
}
