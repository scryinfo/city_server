package Game;

import Game.Eva.Eva;
import Game.Eva.EvaKey;
import Game.Eva.EvaManager;
import Game.Meta.GoodFormula;
import Game.Meta.MetaBuilding;
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
            line.eva_transition_time = meta.evaTransitionTime;
            line.invent_transition_time = meta.inventTransitionTime;
            if (line.isRunning())
                line.currentRoundPassNano = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis() - line.beginProcessTs) % TimeUnit.HOURS.toNanos(1);
        }
        calcuProb();
    }
    @Override
    public Gs.Laboratory detailProto() {
        calcuProb();
        getEvaAdd();//更新Eva加成信息
        Map<Integer, Double> successMap = getTotalSuccessProb();//研究成功率的总值
        Gs.Laboratory.Builder builder = Gs.Laboratory.newBuilder().setInfo(super.toProto());
        this.inProcess.forEach(line -> builder.addInProcess(line.toProto()));
        this.completed.values().forEach(line -> builder.addCompleted(line.toProto()));
        /*Integer evaprob = successMap.get(Gs.Eva.Btype.EvaUpgrade_VALUE).intValue();
        Integer goodProb = successMap.get(Gs.Eva.Btype.EvaUpgrade_VALUE).intValue();*/
        return builder.setSellTimes(this.sellTimes)
                .setPricePreTime(this.pricePreTime)
                .setProbEva(successMap.get(Gs.Eva.Btype.EvaUpgrade_VALUE))
                .setProbGood(successMap.get(Gs.Eva.Btype.InventionUpgrade_VALUE))
               /* .setProbEva(evaprob)
                .setProbGood(goodProb)*/
                .setRecommendPrice(0)
                .setExclusive(this.exclusiveForOwner)
                .setTotalEvaIncome(this.totalEvaIncome)
                .setTotalEvaTimes(this.totalEvaTimes)
                .setTotalGoodIncome(this.totalGoodIncome)
                .setTotalGoodTimes(this.totalGoodTimes)
                .setProbEvaAdd(evaMap.get(Gs.Eva.Btype.EvaUpgrade_VALUE))
                .setProbGoodAdd(evaMap.get(Gs.Eva.Btype.InventionUpgrade_VALUE))
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
        if (!this.inProcess.isEmpty()) {
            Line line = this.inProcess.get(0);
            if (line.update(diffNano)) {
                if (line.goodCategory > 0) {
                    //完成商品发明
                    MailBox.instance().sendMail(Mail.MailType.INVENT_FINISH.getMailType(), line.proposerId, new int[]{line.usedRoll+line.availableRoll,line.times}, new UUID[]{this.id()}, null);
                } else {
                    //完成点数研究
                    MailBox.instance().sendMail(Mail.MailType.EVA_POINT_FINISH.getMailType(), line.proposerId, new int[]{line.usedRoll+line.availableRoll,line.times}, new UUID[]{this.id()}, null);
                }
                if (line.isComplete()) {
                    this.inProcess.remove(0);
                    this.completed.put(line.id, line);
                }
                broadcastLine(line);
            }
        }
        if (this.dbTimer.update(diffNano)) {
            GameDb.saveOrUpdate(this); // this will not ill-form other transaction due to all action are serialized
        }
    }

    public Line addLine(int goodCategory, int times, UUID proposerId, long cost) {
        if(exclusiveForOwner && !proposerId.equals(this.ownerId()))
            return null;
        Line line = new Line(goodCategory, times, proposerId, cost);
        //设置开始时间
        //获取到最后一个的开始时间
        Line lastLine = getLastLine();
        if(lastLine==null){
            lastLine = new Line();
            lastLine.beginProcessTs=System.currentTimeMillis();;
            lastLine.times=0;
        }
        //设置开始时间
        long beginTime=lastLine.beginProcessTs+ TimeUnit.HOURS.toMillis(lastLine.times);
        line.beginProcessTs = beginTime;
        line.eva_transition_time = this.meta.evaTransitionTime;
        line.invent_transition_time = this.meta.inventTransitionTime;
        inProcess.add(line);
        //this.sendToWatchers(Shared.Package.create(GsCode.OpCode.labLineAddInform_VALUE, Gs.LabLineInform.newBuilder().setBuildingId(Util.toByteString(this.id())).setLine(line.toProto()).build()));
        return line;
    }

    public boolean delLine(UUID lineId) {
        Line line = this.findInProcess(lineId);
        if(line != null) {//因为队列的所有开始时间在加入队列的时候已经设置好了，因此，不必要判断开始时间是否大于0
//            this.sendToWatchers(Shared.Package.create(GsCode.OpCode.labLineDel_VALUE,
//                            Gs.LabDelLine.newBuilder()
//                                    .setBuildingId(Util.toByteString(id()))
//                                    .setLineId(Util.toByteString(lineId))
//                                    .build()));
            this.inProcess.remove(line);
            GameDb.delete(line);
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
        //成功率还需要加上eva的加成信息
        getEvaAdd();//更新Eva加成信息
        Map<Integer, Double> successMap = getTotalSuccessProb();//研究成功率的总值
        RollResult res = null;
        Line l = this.findInProcess(lineId);
        if(l == null)
             l = this.completed.get(lineId);
        if(l != null && l.availableRoll > 0) {
            res = new RollResult();
            l.useRoll();
            if(l.eva()) {//是否是eva发明提升
                //1次开启5个成果，所以循环5次
                for (int i = 0; i <5 ; i++) {//新增===========================================
                    if(Prob.success(successMap.get(Gs.Eva.Btype.EvaUpgrade_VALUE), RADIX)) {//成功概率（第一个参数表示成功概率,后一个为基数）
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
                if(Prob.success(successMap.get(Gs.Eva.Btype.InventionUpgrade_VALUE), RADIX)) {
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
    //获取最后一条的研究信息
    public Line getLastLine(){
        if(inProcess.size()==0){
            return null;
        }else
        return  inProcess.get(inProcess.size() - 1);
    }

    /*public Map<String,Integer> getTotalSuccessProb(){
        Map<String, Integer> map = new HashMap<>();
        //首先获取eva信息
        Set<Integer> buildingTech = MetaData.getBuildingTech();
        buildingTech
    }*/
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
        private  int eva_transition_time;//eva提升的过度时间
        @Transient
        private  int invent_transition_time;//发明过渡时间
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
            currentRoundPassNano += diffNano;//当前通过的纳秒
            //TODO:研究的过渡时间是从配置表读取，以后可能会区分Eva的过渡时间和发明的过渡时间，目前用的是一个值
            if (currentRoundPassNano >= TimeUnit.SECONDS.toNanos(eva_transition_time)) { //从配置表读取的过渡时间
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
    @Transient
    Map<Integer, Double> evaMap = new HashMap<>();
    //Eva总的成功率(map的key为btype)

    public void getEvaAdd(){//更新eva
        for (Integer type : MetaData.getBuildingTech(type())) {//atype,因为研究所一个atype只对应1个eva，所以取第一个
            Eva eva = EvaManager.getInstance().getEva(ownerId(), type).get(0);
            evaMap.put(eva.getBt(), EvaManager.getInstance().computePercent(eva));
        }
    }
    //总的成功率数据（包含eva加成）
    public Map<Integer,Double> getTotalSuccessProb(){
        Map<Integer, Double> map = new HashMap<>();
        //eva成功率
        double evaProb = this.evaProb * (1 + evaMap.get(Gs.Eva.Btype.EvaUpgrade_VALUE));
        double goodProb = this.goodProb * (1 + evaMap.get(Gs.Eva.Btype.InventionUpgrade_VALUE));
        map.put(Gs.Eva.Btype.EvaUpgrade_VALUE, evaProb);
        map.put(Gs.Eva.Btype.InventionUpgrade_VALUE,goodProb);
        return map;
    }
}
