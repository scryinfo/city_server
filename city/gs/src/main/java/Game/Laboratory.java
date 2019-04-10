package Game;

import Game.Meta.GoodFormula;
import Game.Meta.MetaData;
import Game.Meta.MetaLaboratory;
import Game.Timers.PeriodicTimer;
import Shared.Util;
import gs.Gs;
import gscode.GsCode;
import org.hibernate.annotations.Cascade;

import javax.persistence.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Entity(name = "Laboratory")
public class Laboratory extends Building {
    private static final int DB_UPDATE_INTERVAL_MS = 30000;
    private static final int RADIX = 100000;
    @Transient
    private MetaLaboratory meta;

    public Laboratory(MetaLaboratory meta, Coordinate pos, UUID ownerId) {
        super(meta, pos, ownerId);
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
        this.inProcess.values().forEach(line -> builder.addInProcess(line.toProto()));
        this.completed.values().forEach(line -> builder.addCompleted(line.toProto()));
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
        Iterator<Line> iterator = this.inProcess.values().iterator();
        while(iterator.hasNext()) {
            Line l = iterator.next();
            if(l.update(diffNano)) {
                if(l.isComplete()) {
                    iterator.remove();
                    this.completed.put(l.id, l);
                }
            }
        }
        if(this.dbTimer.update(diffNano)) {
            GameDb.saveOrUpdate(this); // this will not ill-form other transaction due to all action are serialized
        }
    }
    public Line addLine(int goodCategory, int times) {
        Line line = new Line(goodCategory, times);
        inProcess.put(line.id, line);
        //this.sendToWatchers(Shared.Package.create(GsCode.OpCode.labLineAddInform_VALUE, Gs.LabLineInform.newBuilder().setBuildingId(Util.toByteString(this.id())).setLine(line.toProto()).build()));
        return line;
    }

    public boolean delLine(UUID lineId) {
        Line line = this.inProcess.get(lineId);
        if(line != null && line.isLaunched()) {
//            this.sendToWatchers(Shared.Package.create(GsCode.OpCode.labLineDel_VALUE,
//                            Gs.LabDelLine.newBuilder()
//                                    .setBuildingId(Util.toByteString(id()))
//                                    .setLineId(Util.toByteString(lineId))
//                                    .build()));
            this.inProcess.remove(lineId);
            return true;
        }
        return false;
    }

    public static final class RollResult {
        List<Integer> itemIds;
        int evaPoint;
    }
    public RollResult roll(UUID lineId, Player player) {
        RollResult res = null;
        Line l = this.inProcess.get(lineId);
        if(l == null)
             l = this.completed.get(lineId);
        if(l != null && l.availableRoll > 0) {
            res = new RollResult();
            l.useRoll();
            if(l.eva()) {
                if(Prob.success(this.evaProb, RADIX)) {
                    res.evaPoint++;
                }
            }
            else {
                if(Prob.success(this.goodProb, RADIX)) {
                    res.itemIds = new ArrayList<>();
                    Integer newId = MetaData.randomGood(l.goodCategory, player.itemIds());
                    if(newId != null) {
                        player.addItem(newId, 0);
                        res.itemIds.add(newId);
                        GoodFormula f = MetaData.getFormula(newId);
                        for (GoodFormula.Info info : f.material) {
                            if(!player.hasItem(info.item.id)) {
                                player.addItem(info.item.id, 0);
                                res.itemIds.add(info.item.id);
                            }
                        }
                    }
                }
            }
        }
        return res;
    }

    public void broadcastLine(Line line) {
        Gs.LabLineInform.Builder builder = Gs.LabLineInform.newBuilder();
        builder.setBuildingId(Util.toByteString(this.id()));
        builder.setLine(line.toProto());
        this.sendToWatchers(Shared.Package.create(GsCode.OpCode.labLineChangeInform_VALUE, builder.build()));
    }

    public void setting(int maxTimes, int pricePreTime) {
        this.maxTimes = maxTimes;
        this.pricePreTime = pricePreTime;
    }

    @Entity
    public static final class Line {
        public Line(int goodCategory, int times) {
            this.goodCategory = goodCategory;
            this.times = times;
            this.createTs = System.currentTimeMillis();
        }

        protected Line() {}

        public boolean eva() {
            return goodCategory == 0;
        }
        @Id
        final UUID id = UUID.randomUUID();

        boolean isComplete() {
            return times == usedRoll+availableRoll;
        }
        boolean isRunning() {
            return isLaunched() && !isComplete();
        }
        void launch() {
            beginProcessTs = System.currentTimeMillis();
        }
        int goodCategory;
        long beginProcessTs;
        long createTs;
        int times;
        int availableRoll;
        int usedRoll;
        @Transient
        long currentRoundPassNano = 0;
        @PostLoad
        private void _1() {
            if(isRunning())
                currentRoundPassNano = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis() - beginProcessTs)%TimeUnit.HOURS.toNanos(1);
        }

        Gs.Laboratory.Line toProto() {
            return Gs.Laboratory.Line.newBuilder()
                    .setId(Util.toByteString(id))


                    .setCreateTs(createTs)

                    .build();
        }


        boolean update(long diffNano) {
            if (!isRunning())
                return false;

            currentRoundPassNano += diffNano;
            if (currentRoundPassNano >= TimeUnit.HOURS.toNanos(1)) {
                currentRoundPassNano = 0;
                this.availableRoll++;
                return true;
            }
            return false;
        }

        public boolean isLaunched() {
            return beginProcessTs > 0;
        }

        public void useRoll() {
            this.availableRoll--;
            this.usedRoll++;
        }
    }

    @OneToMany(fetch = FetchType.EAGER)
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    @MapKeyColumn(name = "line_id")
    private Map<UUID, Line> inProcess = new HashMap<>();

    @OneToMany(fetch = FetchType.EAGER)
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    @MapKeyColumn(name = "line_id")
    private Map<UUID, Line> completed = new HashMap<>();

    @Transient
    protected PeriodicTimer dbTimer = new PeriodicTimer(DB_UPDATE_INTERVAL_MS, (int) (Math.random()*DB_UPDATE_INTERVAL_MS));

    private int pricePreTime;
    private int maxTimes;

    public int getMaxTimes() {
        return maxTimes;
    }
    public int getPricePreTime() {
        return pricePreTime;
    }

    private int goodProb;
    private int evaProb;
}
