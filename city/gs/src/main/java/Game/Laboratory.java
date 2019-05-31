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
import java.io.*;
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
    protected void setSalaryRatioAction(){
        calcuProb();
    }

    private void calcuProb() {
        this.evaProb = (int) (this.meta.evaProb * this.salaryRatio / 100.f * this.getAllStaffSize());
        this.goodProb = (int) (this.meta.goodProb * this.salaryRatio / 100.f * this.getAllStaffSize());
    }

    @Override
    public int quality() {
        return 0;
    }

    @PostLoad
    private void _1() {
        this.meta = (MetaLaboratory) super.metaBuilding;
        if(!this.inProcess.isEmpty()) {
            Line line = this.inProcess.get(0);
            if (line.isRunning())
                line.currentRoundPassNano = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis() - line.beginProcessTs) % TimeUnit.HOURS.toNanos(1);
        }
        calcuProb();
    }
    @Override
    public Gs.Laboratory detailProto() {
        Gs.Laboratory.Builder builder = Gs.Laboratory.newBuilder().setInfo(super.toProto());
        this.inProcess.forEach(line -> builder.addInProcess(line.toProto()));
        this.completed.values().forEach(line -> builder.addCompleted(line.toProto()));
        return builder.setSellTimes(this.sellTimes)
                .setPricePreTime(this.pricePreTime)
                .setProbEva(this.evaProb)
                .setProbGood(this.goodProb)
                .setRecommendPrice(0)
                .setExclusive(this.exclusiveForOwner)
                .setTotalEvaIncome(this.totalEvaIncome)
                .setTotalEvaTimes(this.totalEvaTimes)
                .setTotalGoodIncome(this.totalGoodIncome)
                .setTotalGoodTimes(this.totalGoodTimes)
                .build();
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
        if(!this.inProcess.isEmpty()) {
            Line line = this.inProcess.get(0);
            if(line.update(diffNano)) {
                if(line.isComplete()) {
                    this.inProcess.remove(0);
                    this.completed.put(line.id, line);
                }
                broadcastLine(line);
            }
        }
        if(this.dbTimer.update(diffNano)) {
            GameDb.saveOrUpdate(this); // this will not ill-form other transaction due to all action are serialized
        }
    }

    public Line addLine(int goodCategory, int times, UUID proposerId, long cost) {
        if(exclusiveForOwner && !proposerId.equals(this.ownerId()))
            return null;
        Line line = new Line(goodCategory, times, proposerId, cost);
        inProcess.add(line);
        //this.sendToWatchers(Shared.Package.create(GsCode.OpCode.labLineAddInform_VALUE, Gs.LabLineInform.newBuilder().setBuildingId(Util.toByteString(this.id())).setLine(line.toProto()).build()));
        return line;
    }

    public boolean delLine(UUID lineId) {
        Line line = this.findInProcess(lineId);
        if(line != null && !line.isLaunched()) {
//            this.sendToWatchers(Shared.Package.create(GsCode.OpCode.labLineDel_VALUE,
//                            Gs.LabDelLine.newBuilder()
//                                    .setBuildingId(Util.toByteString(id()))
//                                    .setLineId(Util.toByteString(lineId))
//                                    .build()));
            this.inProcess.remove(line);
            return true;
        }
        return false;
    }

    public void setExclusive(boolean exclusive) {
        this.exclusiveForOwner = exclusive;
    }
    public boolean isExclusiveForOwner() {
        return this.exclusiveForOwner;
    }

    public int getEvaProb() {
        return this.evaProb;
    }

    public int getGoodProb() {
        return this.goodProb;
    }

    public int getQueuedTimes() {
        int times = this.inProcess.stream().mapToInt(l->l.times).sum();
        times -= this.inProcess.isEmpty()?0:this.inProcess.get(0).usedRoll+this.inProcess.get(0).availableRoll;
        return times;
    }

    public void updateTotalGoodIncome(long cost, int times) {
        this.totalGoodIncome += cost;
        this.totalGoodTimes += times;
    }

    public void updateTotalEvaIncome(long cost, int times) {
        this.totalEvaIncome += cost;
        this.totalEvaTimes += times;
    }

    public static final class RollResult {
        List<Integer> itemIds;
        int evaPoint;
        List<Integer> labResult = new ArrayList(5);//Eva点数研究的成果包含5个信息（1表成功，0表示失败）
    }
    private Line findInProcess(UUID lineId) {
        for (Line line : this.inProcess) {
            if(line.id.equals(lineId)) {
                return line;
            }
        }
        return null;
    }
    public RollResult roll(UUID lineId, Player player) {
        calcuProb();
        RollResult res = null;
        Line l = this.findInProcess(lineId);
        if(l == null)
             l = this.completed.get(lineId);
        if(l != null && l.availableRoll > 0) {
            res = new RollResult();
            l.useRoll(l.eva());
            if(l.eva()) {//是否是eva发明提升
                //1次开启5个成果，所以循环5次
                for (int i = 0; i <5 ; i++) {//新增===========================================
                    if(Prob.success(this.evaProb, RADIX)) {//成功概率（第一个参数表示成功概率,后一个为基数）
                        res.evaPoint++;
                        player.addEvaPoint(10);
                        //每次都需要把结果保存起来
                        res.labResult.add(1);
                    }else{//失败
                        player.addEvaPoint(1);
                        res.labResult.add(0);
                    }
                }
            }
            else {
                if(Prob.success(this.goodProb, RADIX)) {
                    res.itemIds = new ArrayList<>();
                    Integer newId = MetaData.randomGood(l.goodCategory, player.itemIds());
                    if(newId != null) {
                        player.addItem(newId, 0);
                        player.addEvaPoint(1);
                        res.itemIds.add(newId);
                        GoodFormula f = MetaData.getFormula(newId);
                        for (GoodFormula.Info info : f.material) {
                            if(info != null && !player.hasItem(info.item.id)) {
                                player.addItem(info.item.id, 0);
                                res.itemIds.add(info.item.id);
                            }
                        }
                    }
                }else{ //失败
                    player.addEvaPoint(1);
                }
            }
        }
        if(l.isComplete() && l.availableRoll == 0)
            this.completed.remove(l.id);
        return res;
    }

    public void broadcastLine(Line line) {
        Gs.LabLineInform.Builder builder = Gs.LabLineInform.newBuilder();
        builder.setBuildingId(Util.toByteString(this.id()));
        builder.setLine(line.toProto());
        this.sendToWatchers(Shared.Package.create(GsCode.OpCode.labLineChangeInform_VALUE, builder.build()));
    }

    public void setting(int maxTimes, int pricePreTime) {
        this.sellTimes = maxTimes;
        this.pricePreTime = pricePreTime;
    }

    @Entity
    public static final class Line {
        public Line(int goodCategory, int times, UUID proposerId, long cost) {
            this.goodCategory = goodCategory;
            this.times = times;
            this.createTs = System.currentTimeMillis();
            this.proposerId = proposerId;
            this.payCost = cost;
        }

        protected Line() {}

        public boolean eva() {
            return goodCategory == 0;
        }
        @Id
        @GeneratedValue
        UUID id;
        UUID proposerId;
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
        long payCost;
        @Transient
        long currentRoundPassNano = 0;


        Gs.Laboratory.Line toProto() {
            return Gs.Laboratory.Line.newBuilder()
                    .setId(Util.toByteString(id))
                    .setCreateTs(this.createTs)
                    .setAvailableRoll(this.availableRoll)
                    .setBeginProcessTs(this.beginProcessTs)
                    .setGoodCategory(this.goodCategory)
                    .setProposerId(Util.toByteString(this.proposerId))
                    .setTimes(this.times)
                    .setUsedRoll(this.usedRoll)
                    .setPay((int) this.payCost)
                    .build();
        }


        boolean update(long diffNano) {
            if(!isLaunched())
                this.launch();
            currentRoundPassNano += diffNano;
            if (currentRoundPassNano >= TimeUnit.MINUTES.toNanos(1)) {
                currentRoundPassNano = 0;
                this.availableRoll++;
                return true;
            }
            return false;
        }

        public boolean isLaunched() {
            return beginProcessTs > 0;
        }

        public void useRoll(boolean isEva) {//如果是eva点数宝箱开启，一次减5个可用点数，商品发明是一次减少1个
           if(isEva){
               this.availableRoll-=5;
               this.usedRoll+=5;
           }else {
               this.availableRoll--;
               this.usedRoll++;
           }
        }
    }

    @OneToMany(fetch = FetchType.EAGER)
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    @OrderColumn
    @JoinColumn(name = "labId1")
    private List<Line> inProcess = new ArrayList<>();

    @OneToMany(fetch = FetchType.EAGER, orphanRemoval=true)
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    @MapKey(name = "id")
    @JoinColumn(name = "labId2")
    private Map<UUID, Line> completed = new HashMap<>();

    @Transient
    protected PeriodicTimer dbTimer = new PeriodicTimer(DB_UPDATE_INTERVAL_MS, (int) (Math.random()*DB_UPDATE_INTERVAL_MS));

    private int pricePreTime;
    private int sellTimes;

    public int getSellTimes() {
        return sellTimes;
    }
    public int getPricePreTime() {
        return pricePreTime;
    }

    @Transient
    private int goodProb;
    @Transient
    private int evaProb;
    private boolean exclusiveForOwner = true;

    private long totalEvaIncome;
    private int totalEvaTimes;
    private long totalGoodIncome;
    private int totalGoodTimes;
}
