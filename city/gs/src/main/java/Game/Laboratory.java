package Game;

import Game.Timers.PeriodicTimer;
import Shared.Util;
import gs.Gs;
import gscode.GsCode;
import org.hibernate.annotations.Cascade;

import javax.persistence.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Entity(name = "Laboratory")
public class Laboratory extends Building implements IStorage {
    private static final int DB_UPDATE_INTERVAL_MS = 30000;
    @Transient
    private MetaLaboratory meta;

    public Laboratory(MetaLaboratory meta, Coordinate pos, UUID ownerId) {
        super(meta, pos, ownerId);
        this.store = new Storage(meta.storeCapacity);
        this.meta = meta;
    }

    protected Laboratory() {}

    @Override
    public int quality() {
        return 0;
    }

    @PostLoad
    private void _1() {
        this.meta = (MetaLaboratory) super.metaBuilding;
    }
    @Override
    public Gs.Laboratory detailProto() {
        Gs.Laboratory.Builder builder = Gs.Laboratory.newBuilder().setInfo(super.toProto());
        builder.setStore(this.store.toProto());
        this.lines.values().forEach(line -> builder.addLine(line.toProto()));
        return builder.build();
    }
    @Override
    public void appendDetailProto(Gs.BuildingSet.Builder builder) {
        builder.addLaboratory(this.detailProto());
    }

    @Override
    protected void enterImpl(Npc npc) {

    }

    @Override
    protected void leaveImpl(Npc npc) {

    }

    @Override
    protected void _update(long diffNano) {
        this.lines.values().forEach(l->l.update(diffNano));
        if(this.dbTimer.update(diffNano)) {
            GameDb.saveOrUpdate(this); // this will not ill-form other transaction due to all action are serialized
        }
    }
    public int freeWorkerNum() {
        return this.meta.workerNum - lines.values().stream().mapToInt(l -> l.workerNum).reduce(0, Integer::sum);
    }
    public Line addLine(Formula formula, int workerNum) {
        if(workerNum < meta.lineMinWorkerNum || workerNum > meta.lineMaxWorkerNum)
            return null;
        if(this.freeWorkerNum() < workerNum)
            return null;
        Line line = new Line(workerNum, formula);
        lines.put(line.id, line);
        this.sendToWatchers(Shared.Package.create(GsCode.OpCode.labLineAddInform_VALUE, Gs.LabLineInform.newBuilder().setBuildingId(Util.toByteString(this.id())).setLine(line.toProto()).build()));
        return line;
    }

    public boolean delLine(UUID lineId) {
        if(lines.remove(lineId) != null) {
            this.sendToWatchers(Shared.Package.create(GsCode.OpCode.labLineDel_VALUE,
                            Gs.LabDelLine.newBuilder()
                                    .setBuildingId(Util.toByteString(id()))
                                    .setLineId(Util.toByteString(lineId))
                                    .build()));
            return true;
        }
        return false;
    }

    public boolean setLineWorkerNum(UUID lineId, int n) {
        Line line = lines.get(lineId);
        if(line == null)
            return false;
        if(line.workerNum < n) {
            if(this.freeWorkerNum() < n - line.workerNum)
                return false;
        }
        line.workerNum = n;
        return true;
    }

    public Line getLine(UUID lineId) {
        return lines.get(lineId);
    }

    public void broadcastLine(Line line) {
        Gs.LabLineInform.Builder builder = Gs.LabLineInform.newBuilder();
        builder.setBuildingId(Util.toByteString(this.id()));
        builder.setLine(line.toProto());
        this.sendToWatchers(Shared.Package.create(GsCode.OpCode.labLineChange_VALUE, builder.build()));
    }

    @Entity
    public static final class Line {
        public Line(int workerNum, Formula formula) {
            this.workerNum = workerNum;
            this.formula = formula;
            this.leftNano = TimeUnit.SECONDS.toNanos(formula.phaseSec);

            this.type = formula.key.type.ordinal();
            this.targetId = formula.key.targetId;
            this.targetLv = formula.key.targetLv;

            this.run = false;
            this.createTs = System.currentTimeMillis();
        }

        protected Line() {}
        @PostLoad
        void _1() {
            this.formula = MetaData.getFormula(new Formula.Key(Formula.Type.values()[type], targetId, targetLv));
        }
        @Id
        final UUID id = UUID.randomUUID();
        int workerNum;

        @Transient
        Formula formula;

        Formula.Consume[] getConsumes() {
            if(this.run)
                return null;
            return formula.consumes;
        }
        boolean isComplete() {
            return phase == formula.phase;
        }
        boolean isRunning() {
            return this.run;
        }
        void launch(int phase) {
            this.run = true;
            this.rollTarget = phase;
        }
        int type;
        int targetId;
        int targetLv;
        long leftNano;
        int phase;
        int rollTarget;
        boolean run;
        long createTs;
        int roll;
        private static final int RADIX = 100000;
        Gs.Laboratory.Line toProto() {
            return Gs.Laboratory.Line.newBuilder()
                    .setId(Util.toByteString(id))
                    .setItemId(formula.key.targetId)
                    .setType(formula.key.type.ordinal())
                    .setLv(formula.key.targetLv)
                    .setLeftSec((int) TimeUnit.NANOSECONDS.toSeconds(leftNano))
                    .setPhase(phase)
                    .setWorkerNum(workerNum)
                    .setCreateTs(createTs)
                    .setRun(run)
                    .setRoll(roll)
                    .setRollTarget(rollTarget)
                    .build();
        }

        public int leftPhase() {
            return formula.phase - phase;
        }

        public static final class UpdateResult {
            public UpdateResult(Formula.Type type, int v) {
                this.type = type;
                this.v = v;
            }

            public UpdateResult(boolean phaseChange) {
                this.phaseChange = phaseChange;
            }

            Formula.Type type;
            int v;
            boolean phaseChange;
        }
        UpdateResult roll() {
            if(roll > 0) {
                --roll;
                if(!Prob.success(formula.successChance[phase], 10000))
                    return null;

                if(formula.key.type == Formula.Type.RESEARCH) {
                    if(Prob.success(formula.critiChance, RADIX))
                        return new UpdateResult(formula.key.type, formula.critiV);
                    return new UpdateResult(formula.key.type, 1);
                }
                if(formula.key.type == Formula.Type.INVENT) {
                    int phaseAdd = 1;
                    if(Prob.success(formula.critiChance, RADIX))
                        phaseAdd = formula.critiV;
                    phase += phaseAdd;
                    phase = phase > formula.phase?formula.phase:phase;
                    if(phase == formula.phase)
                        return new UpdateResult(formula.key.type, formula.key.targetId);
                    else
                        return new UpdateResult(true);
                }
            }
            return null;
        }
        void update(long diffNano) {
            if (!run)
                return;

            leftNano -= diffNano * workerNum;
            if (leftNano <= 0) {
                leftNano = TimeUnit.SECONDS.toNanos(formula.phaseSec);
                roll++;
                if(roll == rollTarget)
                    run = false;
            }
        }
    }

    @OneToMany(fetch = FetchType.EAGER)
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    @MapKeyColumn(name = "line_id")
    Map<UUID, Line> lines = new HashMap<>();

    @OneToOne(cascade= CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "store_id")
    private Storage store;

    @Transient
    protected PeriodicTimer dbTimer = new PeriodicTimer(DB_UPDATE_INTERVAL_MS, (int) (Math.random()*DB_UPDATE_INTERVAL_MS));


    @Override
    public boolean reserve(MetaItem m, int n) {
        return store.reserve(m, n);
    }

    @Override
    public boolean lock(ItemKey m, int n) {
        return store.lock(m, n);
    }

    @Override
    public boolean unLock(ItemKey m, int n) {
        return store.unLock(m, n);
    }

    @Override
    public Storage.AvgPrice consumeLock(ItemKey m, int n) {
        return store.consumeLock(m, n);
    }

    @Override
    public void consumeReserve(ItemKey m, int n, int price) {
        store.consumeReserve(m, n, price);
    }

    @Override
    public void markOrder(UUID orderId) {
        store.markOrder(orderId);
    }

    @Override
    public void clearOrder(UUID orderId) {
        store.clearOrder(orderId);
    }

    @Override
    public boolean delItem(ItemKey k) { return this.store.delItem(k); }

    @Override
    public int availableQuantity(MetaItem m) { return this.store.availableQuantity(m); }

    @Override
    public boolean has(ItemKey m, int n) { return this.store.has(m, n); }

    @Override
    public boolean offset(ItemKey item, int n) { return this.store.offset(item, n); }

    @Override
    public boolean offset(MetaItem item, int n) { return this.store.offset(item, n); }
}
