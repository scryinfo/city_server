package Game;

import Game.Timers.PeriodicTimer;
import Shared.Util;
import com.google.protobuf.Message;
import gs.Gs;
import gscode.GsCode;
import org.hibernate.annotations.Cascade;

import javax.persistence.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Entity(name = "Laboratory")
public class Laboratory extends Building {
    private static final int DB_UPDATE_INTERVAL_MS = 30000;
    @Transient
    private MetaLaboratory meta;

    public Laboratory(MetaLaboratory meta, Coordinate pos, UUID ownerId) {
        super(meta, pos, ownerId);
    }

    public Laboratory() {
    }

    @Override
    public int quality() {
        return 0;
    }

    @PostLoad
    private void _1() {
        this.meta = (MetaLaboratory) super.metaBuilding;
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

    @Override
    protected void _update(long diffNano) {
        this.lines.values().forEach(l -> {
            Line.UpdateResult r = l.update(diffNano);
            if(r != null) {
                if(r.phaseChange) {
                    GameServer.sendTo(this.detailWatchers, Shared.Package.create(GsCode.OpCode.labLineChange_VALUE, l.toProto()));
                }
                else {
                    Player owner = GameDb.queryPlayer(ownerId());
                    if(r.type == Formula.Type.INVENT)
                        owner.addItem(l.formula.key.targetId, 0);
                    if(r.type == Formula.Type.RESEARCH)
                        owner.addItem(l.formula.key.targetId, r.v);
                    GameServer.sendTo(this.detailWatchers,
                            Shared.Package.create(GsCode.OpCode.labLineDel_VALUE,
                                    Gs.LabDelLine.newBuilder()
                                        .setBuildingId(Util.toByteString(id()))
                                        .setLineId(Util.toByteString(l.id))
                                        .build()));
                }
            }
        });
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
        return line;
    }

    public boolean delLine(UUID lineId) {
        return lines.remove(lineId) != null;
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

    @Entity
    public static final class Line {
        public Line(int workerNum, Formula formula) {
            this.workerNum = workerNum;
            this.formula = formula;
            this.leftNano = TimeUnit.SECONDS.toNanos(formula.phaseSec);

            this.type = formula.key.type.ordinal();
            this.targetId = formula.key.targetId;
            this.targetLv = formula.key.targetLv;
        }

        protected Line() {
        }
        @PostLoad
        void _1() {
            this.formula = MetaData.getFormula(new Formula.Key(Formula.Type.values()[type], targetId, targetLv));
        }
        @Id
        final UUID id = UUID.randomUUID();
        int workerNum;

        @Transient
        Formula formula;

        int type;
        int targetId;
        int targetLv;
        long leftNano;
        int phase;
        private static final int RADIX = 100000;
        Gs.Laboratory.Line toProto() {
            return Gs.Laboratory.Line.newBuilder()
                    .setId(Util.toByteString(id))
                    .setItemId(formula.key.targetId)
                    .setType(formula.key.type.ordinal())
                    .setLv(formula.key.targetLv)
                    .setDurationSec((int) TimeUnit.NANOSECONDS.toSeconds(leftNano))
                    .setPhase(phase)
                    .setWorkerNum(workerNum)
                    .build();
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
        UpdateResult update(long diffNano) {
            leftNano -= diffNano*workerNum;
            if(leftNano <= 0) {
                ++phase;

                if(formula.key.type == Formula.Type.RESEARCH) {
                    if(Prob.success(formula.critiChance, RADIX))
                        return new UpdateResult(formula.key.type, formula.critiV);
                    return new UpdateResult(formula.key.type, 1);
                }
                if(formula.key.type == Formula.Type.INVENT) {
                    if(phase < formula.phase) {
                        int phaseAdd = 1;
                        if(Prob.success(formula.critiChance, RADIX))
                            phaseAdd = formula.critiV;
                        phase += phaseAdd;
                        if(phase > formula.phase) {
                            phase = formula.phase;
                            return new UpdateResult(formula.key.type, formula.key.targetId);
                        }
                        else
                            return new UpdateResult(true);
                    }
                }

            }
            return null;
        }
    }

    @OneToMany(fetch = FetchType.EAGER)
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    @MapKeyColumn(name = "line_id")
    Map<UUID, Line> lines = new HashMap<>();

    @Transient
    protected PeriodicTimer dbTimer = new PeriodicTimer(DB_UPDATE_INTERVAL_MS, (int) (Math.random()*DB_UPDATE_INTERVAL_MS));
}
