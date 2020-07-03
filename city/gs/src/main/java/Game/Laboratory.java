package Game;

import Game.Eva.Eva;
import Game.Eva.EvaManager;
import Game.Meta.GoodFormula;
import Game.Meta.MetaData;
import Game.Meta.MetaLaboratory;
import Game.Timers.PeriodicTimer;
import Shared.Util;
import gs.Gs;
import gscode.GsCode;
import org.hibernate.annotations.Cascade;

import javax.persistence.*;
import java.text.SimpleDateFormat;
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

    @Override
    public int getTotalSaleCount() {
        return 0;
    }

    private void calcuProb() {
        /*this.evaProb = (int) (this.meta.evaProb * this.salaryRatio / 100.f * this.getAllStaffSize());
        this.goodProb = (int) (this.meta.goodProb * this.salaryRatio / 100.f * this.getAllStaffSize());*/
        this.evaProb = (int) (this.meta.evaProb*100/ 100.f * this.getAllStaffSize());
        this.goodProb = (int) (this.meta.goodProb*100/ 100.f * this.getAllStaffSize());
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
        Map<Integer, Double> successMap = getTotalSuccessProb();//Total value of research success rate
        Gs.Laboratory.Builder builder = Gs.Laboratory.newBuilder().setInfo(super.toProto());
        this.inProcess.forEach(line -> builder.addInProcess(line.toProto()));
        /*this.completed.values().forEach(line -> {
            //If the owner of the completed line is the current player, the completed line is displayed
           builder.addCompleted(line.toProto()));
        );*/

        return builder.setSellTimes(this.getRemainingTime())
                .setPricePreTime(this.pricePreTime)
                .setProbEva(successMap.get(Gs.Eva.Btype.EvaUpgrade_VALUE))
                .setProbGood(successMap.get(Gs.Eva.Btype.InventionUpgrade_VALUE))
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
                    //Completion of commodity invention
                    MailBox.instance().sendMail(Mail.MailType.INVENT_FINISH.getMailType(), line.proposerId, new int[]{line.usedRoll+line.availableRoll,line.times,line.goodCategory}, new UUID[]{this.id()}, null);
                } else {
                    //Complete the point study
                    MailBox.instance().sendMail(Mail.MailType.EVA_POINT_FINISH.getMailType(), line.proposerId, new int[]{line.usedRoll+line.availableRoll,line.times,line.goodCategory}, new UUID[]{this.id()}, null);
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
        //Set start time
        //Get the start time of the last one
        Line lastLine = getLastLine();
        if(lastLine==null){
            lastLine = new Line();
            lastLine.beginProcessTs=System.currentTimeMillis();
            lastLine.times=0;
        }
        //Set start time
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
        if(line != null) {//Because all the start time of the queue has been set when joining the queue, it is not necessary to judge whether the start time is greater than 0
//            this.sendToWatchers(Shared.Package.create(GsCode.OpCode.labLineDel_VALUE,
//                            Gs.LabDelLine.newBuilder()
//                                    .setBuildingId(Util.toByteString(id()))
//                                    .setLineId(Util.toByteString(lineId))
//                                    .build()));
            List<Line> lines = removeAndUpdateLine(lineId, true);
            //this.inProcess.remove(line);
            //Update all queues
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

    //Todo:Maybe the future transition time is not once every hour, but different types have different transition times
    public Long getLastQueuedCompleteTime(){
        int size = this.inProcess.size();
        long endTime = -1;
        //The queue setting time is the last queue completion time, and the no queue setting is -1
        if(size>0){
            //Get the last queue
            Line line = this.inProcess.get(size - 1);
            //Then the entire completion time is the start time
            int labTime = line.times - line.usedRoll - line.availableRoll;//Research time remaining
            endTime = line.beginProcessTs + TimeUnit.HOURS.toMillis(labTime);
        }
        return endTime;
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
        List<Integer> labResult = new ArrayList(5);//The results of the Eva point study include 5 pieces of information (1 indicates success, 0 indicates failure)
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
        Map<Integer, Double> successMap = getTotalSuccessProb();//Total value of research success rate
        RollResult res = null;//Research results
        Line l = this.findInProcess(lineId);
        if(l == null)
             l = this.completed.get(lineId);
        if(l != null && l.availableRoll > 0) {
            res = new RollResult();
            l.useRoll();
            if(l.eva()) {//Whether it is eva invention promotion
                //Open 5 achievements at a time, so loop 5 times
                for (int i = 0; i <5 ; i++) {//Add===========================================
                    if(Prob.success(successMap.get(Gs.Eva.Btype.EvaUpgrade_VALUE), RADIX)) {//Probability of success (the first parameter indicates the probability of success, the latter is the cardinality)
                        res.evaPoint+=10;
                        player.addEvaPoint(10);
                        //Need to save the result every time
                        res.labResult.add(1);
                    }else{//failure
                        res.evaPoint+=1;
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
                        res.itemIds.add(newId);
                        GoodFormula f = MetaData.getFormula(newId);
                        for (GoodFormula.Info info : f.material) {
                            if(info != null && !player.hasItem(info.item.id)) {
                                player.addItem(info.item.id, 0);
                                res.itemIds.add(info.item.id);
                            }
                        }
                    }
                }
            }
        }else{//Indicates that the data passed is incorrect
            return null;
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
        this.usedTime=0;
    }
    //Get the last research information
    public Line getLastLine(){
        if(inProcess.size()==0){
            return null;
        }else
        return  inProcess.get(inProcess.size() - 1);
    }

    /*public Map<String,Integer> getTotalSuccessProb(){
        Map<String, Integer> map = new HashMap<>();
        //First get eva information
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
        private  int eva_transition_time;//Excessive time of eva promotion
        @Transient
        private  int invent_transition_time;//Invention transition time
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
            //In order to ensure that the server can complete the research results on time even when the server is disconnected, add judgment here. If it is now> the end time of the research, directly complete all the remaining points.
            int transitionTime=0;//Transition time for current study
            if(goodCategory>0){
                transitionTime = (int) TimeUnit.SECONDS.toHours(this.invent_transition_time);
            }else{
                transitionTime=(int) TimeUnit.SECONDS.toHours(this.eva_transition_time);
            }
            long endTime = this.beginProcessTs + TimeUnit.HOURS.toMillis(this.times*transitionTime);//End Time
            long now = System.currentTimeMillis();
            if(now>endTime){//It should have been completed, but it hasn’t been completed yet, and all results have been achieved
                //Get the remaining amount of results
                int addPoint = this.times - usedRoll - availableRoll;
                this.availableRoll += addPoint;
                return true;
            }
            currentRoundPassNano += diffNano;//Nanoseconds currently passed
            //TODO: The transition time of the study is read from the configuration table, and the transition time of Eva and the transition time of the invention may be distinguished in the future. At present, a value is used.
            if (currentRoundPassNano >= TimeUnit.SECONDS.toNanos(this.eva_transition_time)) { //Transition time read from configuration table
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

    private int usedTime=0;//Elapsed time

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

    //Eva addition information (map key is btype), updated by updateEvaAdd
    @Transient
    Map<Integer, Double> evaMap = new HashMap<>();

    public void updateEvaAdd(){//Update eva
        for (Integer type : MetaData.getBuildingTech(type())) {//atype, because one atype in the research institute only corresponds to 1 eva, so take the first
            Eva eva = EvaManager.getInstance().getEva(ownerId(), type).get(0);
            evaMap.put(eva.getBt(), EvaManager.getInstance().computePercent(eva));
        }
    }
    //Total success rate data (including eva bonus)
    public Map<Integer,Double> getTotalSuccessProb(){
        calcuProb();
        updateEvaAdd();//Update eva information
        Map<Integer, Double> map = new HashMap<>();
        //eva success rate
        double evaProb = this.evaProb * (1 + evaMap.get(Gs.Eva.Btype.EvaUpgrade_VALUE));
        double goodProb = this.goodProb * (1 + evaMap.get(Gs.Eva.Btype.InventionUpgrade_VALUE));
        map.put(Gs.Eva.Btype.EvaUpgrade_VALUE, evaProb);
        map.put(Gs.Eva.Btype.InventionUpgrade_VALUE,goodProb);
        return map;
    }

    public void useTime(int time){
        this.usedTime +=time;
    }

    public void resetUseTime(){
        this.usedTime =0;
    }
    public int getRemainingTime(){
        //Get the remaining time
        return this.sellTimes - usedTime;
    }
    //Clear research cohort
    public void clear() {
        this.completed.clear();
        this.inProcess.clear();
    }

    public  List<Line> removeAndUpdateLine(UUID delId,boolean delOrder){
        long nextTs = 0;//Next start time
        int findPos = -1 ;//Positioning
        Line delLine=null;
        List<Line> changed = new ArrayList<>();
        SimpleDateFormat sm = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
        for (int i = 0; i < inProcess.size(); i++) {
            Line line = inProcess.get(i);
            if(delId.equals(line.id)){
                if(i==0){
                    line.beginProcessTs = System.currentTimeMillis();
                }
                line.times=0;
                findPos=i;
                delLine=line;
                System.err.println("已找到要删除的生产线,设置开始时间为"+sm.format(new Date(line.beginProcessTs)));
            }else if(findPos >= 0){
                line.beginProcessTs = nextTs;
                changed.add(line);
                System.err.println("下一个开始时间设置为"+sm.format(new Date(line.beginProcessTs)));
            }
            nextTs = line.beginProcessTs + TimeUnit.HOURS.toMillis(line.times);
        }
        //After the update is complete, remove the promotion you want to delete.
        if(delOrder){
            if(delLine!=null){
                inProcess.remove(delLine);
            }
        }
        return changed;
    }
    public List<Gs.Laboratory.Line> getAllLineProto(UUID proposerId){
        List<Gs.Laboratory.Line> lines = new ArrayList<>();
        inProcess.forEach(l->{
            lines.add(l.toProto());
        });
        completed.values().forEach(l->{
           if(l.proposerId.equals(proposerId)){
               lines.add(l.toProto());
           }
        });
        return lines;
    }
    /*Get your own line in the completed production line*/
    public List<Gs.Laboratory.Line> getOwnerLine(UUID proposerId){
        List<Gs.Laboratory.Line> lines = new ArrayList<>();
        this.completed.values().forEach(line->{
            if(line.proposerId.equals(proposerId)){
                lines.add(line.toProto());
            }
        });
        return lines;
    }
}
