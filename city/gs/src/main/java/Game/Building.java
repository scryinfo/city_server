package Game;

import DB.Db;
import Game.Contract.IBuildingContract;
import Game.Listener.ConvertListener;
import Game.Meta.MetaBuilding;
import Game.Meta.MetaData;
import Game.Util.GlobalUtil;
import Game.Util.NpcUtil;
import Shared.LogDb;
import Shared.Package;
import Shared.Util;
import com.google.common.collect.EvictingQueue;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import gs.Gs;
import gscode.GsCode;
import io.netty.channel.ChannelId;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.SelectBeforeUpdate;

import javax.persistence.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Entity
@SelectBeforeUpdate(false)
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
@EntityListeners({
        ConvertListener.class,
})
public abstract class Building implements Ticker{
    private static final int MAX_FLOW_SIZE = 30*24;
    private static final int PAYMENT_HOUR = 8;

    private static final int HAPPY_MAX = 0;
    private static final int HAPPY_MIN = 4;

    protected Building() {

    }

    public static double distance(Building a, Building b) {
        return Coordinate.distance(a.coordinate(), b.coordinate());
    }

    public TickGroup getTickGroup() {
        return tickGroup;
    }

    public void setTickGroup(TickGroup tickGroup) {
        this.tickGroup = tickGroup;
    }

    @ManyToOne
    private TickGroup tickGroup;

    public void tick(long deltaTime){};
    public abstract int quality();
    boolean canUseBy(UUID userId) {
        return ownerId.equals(userId);
    }
    public int type() {
        return MetaBuilding.type(metaBuilding.id);
    }

    public boolean outOfBusiness() {
        if(state != Gs.BuildingState.OPERATE_VALUE)
            return true;
        return happy == HAPPY_MIN;
    }
    public boolean npcSelectable() {
        return true;
    }
    public boolean onStrike() {
        return this.happy > HAPPY_MAX && this.happy < HAPPY_MIN;
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
            case MetaBuilding.WAREHOUSE:
                return new WareHouse(MetaData.getWarehouse(id), pos, ownerId);
        }
        return null;
    }
    @Column(name = "metaId", updatable = false, nullable = false)
    @Convert(converter = MetaBuilding.Converter.class)
    protected MetaBuilding metaBuilding;

    public void watchDetailInfoAdd(GameSession s) {
        detailWatchers.add(s.channelId());
    }
    public void watchDetailInfoDel(GameSession s) {
        detailWatchers.remove(s.channelId());
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
        Package pack = Package.create(GsCode.OpCode.unitRemove_VALUE, Gs.UnitRemove.newBuilder().setId(Util.toByteString(id)).setX(this.coordinate.x).setY(this.coordinate.y).build());
        City.instance().send(gip, pack);
    }
    protected void sendToWatchers(Shared.Package p) {
        GameServer.sendTo(this.detailWatchers, p);
    }
    protected void sendToWatchers(UUID buidlingId,int itemId,int count,int price,boolean autoReplenish,UUID producerId) {
        Gs.salesNotice.Builder builder = Gs.salesNotice.newBuilder();
        builder.setBuildingId(Util.toByteString(buidlingId)).setItemId(itemId).setSelledCount(count).setSelledPrice(price).setAutoRepOn(autoReplenish);
        if(producerId!=null){
            builder.setProducerId(Util.toByteString(producerId));
        }
        GameServer.sendTo(this.detailWatchers, Package.create(GsCode.OpCode.salesNotice_VALUE, builder.build()));
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
        City.instance().forAllGrid(f-> f.forAllBuilding(building -> {
            if(building.npcSelectable() && !building.outOfBusiness() && building.type() == type)
                res.add(building);
        }));
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
        long ts = 0;
        for(Db.FlowHistory.Info i : Db.FlowHistory.parseFrom(this.flowHistoryBinary).getIList())
        {
            this.flowHistory.add(new FlowInfo(i.getTs(), i.getN()));
            if (i.getTs() >= ts) {
                ts = i.getTs();
                this.flow = i.getN();
            }
        }
    }

    public boolean hasTalent(UUID id) {
        return talentId.contains(id);
    }
    public boolean canTake(Talent talent) {
        if(talentId.size() >= metaBuilding.talentNum)
            return false;
        if(this.type() != talent.buildingType())
            return false;
        return true;
    }
    public boolean take(Talent talent, List<Object> updates) {
        talent.inBuilding(this.id());
        talentId.add(talent.id());
        if(talent.money() > 0) {
            Npc npc = NpcManager.instance().create(talent.id(), this, talent.money());
            allStaff.add(npc);
            npc.goWork();
            updates.add(npc);
        }
        return true;
    }
    private Npc findNpc(UUID id) {
        for (Npc npc : allStaff) {
            if(npc.id().equals(id))
                return npc;
        }
        return null;
    }
    public Npc untake(Talent talent) {
        talent.outBuilding(this.id());
        this.talentId.remove(talent.id());
        Npc npc = this.findNpc(talent.id());
        if(npc != null) {
            talent.setMoney(npc.money());
            NpcManager.instance().delete(Arrays.asList(npc));
        }
        return npc;
    }

    @Transient  // init this from TalentManager!!!
    private Collection<UUID> talentId = new HashSet<>();

    @Column(name = "flow_binary")
    private byte[] flowHistoryBinary;


    @Id
    @GeneratedValue
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

    @Transient
    protected boolean working;

    public int getSalaryRatio() {
        return salaryRatio;
    }

    @Column(nullable = false)
    protected int salaryRatio;

    @Column(nullable = false)
    private long salaryRatioTs;

    @Column(nullable = false)
    private int happy = 0;

    @Column(nullable = false)
    protected int state = Gs.BuildingState.WAITING_OPEN_VALUE;

    @Column(name = "ts", nullable = false)
    long openingTs = 0;    //最新的开业时间

    @Column(nullable = false)
    private long constructCompleteTs;

    private String name;
    private String des;
    private int emoticon;
    private boolean showBubble;

    @Column(nullable = false)
    private long todayIncome = 0;

    @Column(nullable = false)
    private long todayIncomeTs = 0;

    private static final long DAY_MILLISECOND = 1000 * 3600 * 24;

    @Transient
    private int todayVisitor = 0;

    @Transient
    private long todayVisitorTs = 0;

    @Column(nullable = false)
    @ColumnDefault("0")
    private long last_modify_time;

    public void increaseTodayVisit()
    {
        if (System.currentTimeMillis() - todayVisitorTs >= DAY_MILLISECOND)
        {
            todayVisitor = 1;
            todayVisitorTs = Util.getTodayStartTs();
        }
        else
        {
            todayVisitor++;
        }
    }

    public int getTodayVisitor()
    {
        if (System.currentTimeMillis() - todayVisitorTs >= DAY_MILLISECOND)
        {
           return 0;
        }
        return todayVisitor;
    }

    public void updateTodayIncome(long income)
    {
        if (System.currentTimeMillis() - todayIncomeTs >= DAY_MILLISECOND)
        {
            todayIncome = income;
            todayIncomeTs = Util.getTodayStartTs();

        }
        else
        {
            todayIncome += income;
        }
    }

    public Gs.PrivateBuildingInfo getPrivateBuildingInfo()
    {
        long now = System.currentTimeMillis();
        Gs.PrivateBuildingInfo.Builder builder = Gs.PrivateBuildingInfo.newBuilder()
                .setBuildId(Util.toByteString(id))
                .setTime(now);
        if (now - todayIncomeTs >= DAY_MILLISECOND)
        {
            builder.setTodayIncome(0);
        }
        else
        {
            builder.setTodayIncome(todayIncome);
        }
        return builder.build();
    }

    @Transient
    private Set<Npc> allStaff = new HashSet<>();

    public int getWorkerNum(){
        return metaBuilding.workerNum;
    }

    public long getAllStaffSize()
    {
        return allStaff.size();
    }

    public int allSalary() {
        return singleSalary() * metaBuilding.workerNum;
    }
    public int singleSalary() {
        return (int) (salaryRatio / 100.d * City.instance().getAvgIndustrySalary());
    }
    public int allTax() {
    	return singleTax() * metaBuilding.workerNum;
    }
    public int singleTax() {
    	return (int)(MetaData.getCity().taxRatio / 100.d * singleSalary());
    }
    public int singleSalary(Talent talent) {
        return (int) (talent.getSalaryRatio() / 100.d * metaBuilding.salary);
    }
    public int cost() {
        return 0;
    }

    public boolean startBusiness(Player player) {
        if(state == Gs.BuildingState.OPERATE_VALUE)
            return false;
        if(!this.payOff(player))
            return false;
        state = Gs.BuildingState.OPERATE_VALUE;
        openingTs = System.currentTimeMillis();
        this.calcuWorking(City.instance().currentHour());
        this.broadcastChange();
        return true;
    }

    public int metaId() {
        return metaBuilding.id;
    }

    public void init() {
        //this.talentId = TalentManager.instance().getTalentIdsByBuildingId(this.id());
        this.calcuWorking(City.instance().currentHour());
    }

    public void shutdownBusiness() {
        this.happy = HAPPY_MIN;
        this.state = Gs.BuildingState.SHUTDOWN_VALUE;
        this.todayIncome = 0;
        this.broadcastChange();
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName()
    {
        return name == null ? "" : name;
    }

	// ugly, but due to hibernate inject way, there is no better ways to build the reference relationship
    public void takeAsWorker(Npc npc) {
        if(npc.canWork())
            this.allStaff.add(npc);
    }

    public void setDes(String des) {
        this.des = des;
    }

    public void setEmoticon(int emoticon) {
        this.emoticon = emoticon;
    }

    public void setShowBubble(boolean showBubble) {
        this.showBubble = showBubble;
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
        //this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.coordinate = pos;
        this.metaBuilding = meta;
        this.constructCompleteTs = System.currentTimeMillis();
        this.last_modify_time = 0L;
    }
    public final List<Npc> destroy() {
        List<Npc> npcs = new ArrayList<>(allStaff);
        NpcManager.instance().delete(allStaff);
        allStaff.clear();
        return npcs;
    }

    public final void addUnEmployeeNpc() {
    	NpcManager.instance().addUnEmployeeNpc(allStaff);
    	allStaff.clear();
    }

    public List<Npc> hireNpc() {
        List<Npc> npcs = new ArrayList<>();
        this.metaBuilding.npc.forEach((k,v)->{
            for(Npc npc : NpcManager.instance().create(k, v, this, 0))
            {
                npcs.add(npc);
                takeAsWorker(npc);
                npc.goWork();
            }
        });
        return npcs;
    }

    public List<Npc> createNpc() {
    	Map<UUID, Npc> unEmployeeNpc=NpcManager.instance().getUnEmployeeNpc();
    	int unEmployeeNpcNum=unEmployeeNpc.size();
    	List<Npc> npcList = new ArrayList<Npc>(unEmployeeNpc.values());
    	List<Npc> npcs = new ArrayList<>();

    	int requireWorkNum=0;
    	for (Map.Entry<Integer, Integer> n : this.metaBuilding.npc.entrySet()) {
    		requireWorkNum+=n.getValue();
    	}
    	if(requireWorkNum>unEmployeeNpcNum){//需求工人数量 > 失业人口  失业人口全部转化为工人  不足部分新增人口
    		NpcManager.instance().addWorkNpc(npcList,this);
    		npcs.addAll(npcList);
    		int num=(requireWorkNum-unEmployeeNpcNum)/4; //每种类型还差npc数量
    	  	this.metaBuilding.npc.forEach((k,v)->{
        		for(Npc npc : NpcManager.instance().create(k, num, this, 0))
        		{
        			npcs.add(npc);
        		}
        	});
    	}else if(requireWorkNum==unEmployeeNpcNum){//需求工人数量 = 失业人口  失业人口全部转化为工人
    		NpcManager.instance().addWorkNpc(npcList,this);
    		npcs.addAll(npcList);
    	}else{//需求工人数量 < 失业人口  在失业人口中随机抽取需求工人数量的人口转化为工人
    		int num=requireWorkNum/4;
    		Map<Integer, List<Npc>> map=NpcManager.instance().getUnEmployeeNpcByType();
    		List<Npc> totalNpc = new ArrayList<Npc>();
      	  	this.metaBuilding.npc.forEach((k,v)->{
      	  		List<Npc> list=map.get(k);
      	  		//每一种类型的npc随机抽取num个
      	  		List<Npc> sub=new ArrayList<Npc>();
      	  		int[] idxArray=NpcUtil.getDifferentIndex(0,list.size()-1,num);
      	  		for (int i = 0; i < idxArray.length; i++) {
					int j = idxArray[i];
					Npc npc=list.get(j);
					sub.add(npc);
				}
      	  		NpcManager.instance().addWorkNpc(sub,this);
      	  		totalNpc.addAll(sub);
        	});
      	  	npcs.addAll(totalNpc);
    	}

    	npcs.forEach(npc->{
    		takeAsWorker(npc);
			npc.goWork();
    	});
    	return npcs;
    }

    public CoordPair effectRange() {
        Coordinate l = coordinate.shiftLU(this.metaBuilding.effectRange);
        Coordinate r = coordinate.offset(this.metaBuilding.x-1, this.metaBuilding.y-1).shiftRB(this.metaBuilding.effectRange);
       return new CoordPair(l, r);
    }
    public CoordPair area() {
        return metaBuilding.area(coordinate);
    }

    public Coordinate coordinate() {
        return coordinate;
    }

    public Gs.TechBuildingPos toPositionProto()
    {
        return Gs.TechBuildingPos.newBuilder()
                .setBid(Util.toByteString(id))
                .setMId(metaBuilding.id)
                .setPId(Util.toByteString(this.ownerId))
                .setPos(Gs.MiniIndex.newBuilder()
                        .setX(this.coordinate.x)
                        .setY(this.coordinate.y))
                .build();
    }

    public Gs.BuildingInfo toProto() {
        Gs.BuildingInfo.Builder builder = Gs.BuildingInfo.newBuilder();
        builder.setId(Util.toByteString(id))
                .setMId(metaBuilding.id)
                .setPos(Gs.MiniIndex.newBuilder()
                        .setX(this.coordinate.x)
                        .setY(this.coordinate.y))
                .setOwnerId(Util.toByteString(ownerId))
                .setNpcFlow(this.flow)
                .setState(Gs.BuildingState.valueOf(state))
                .setSalary(salaryRatio)
                .setSetSalaryTs(salaryRatioTs)
                .setHappy(happy)
                .setConstructCompleteTs(constructCompleteTs)
                .setTodayVisitor(this.getTodayVisitor())
                .setOpeningTs(openingTs);
        if(this.name != null && this.name.length() > 0)
            builder.setName(this.name);
        if(this.des != null && this.des.length() > 0)
            builder.setDes(this.des);
        if(this.emoticon > 0)
            builder.setEmoticon(this.emoticon);
        builder.setBubble(this.showBubble);
        int type=MetaBuilding.type(metaBuilding.id);
    	builder.setType(type);
    	if(type==MetaBuilding.APARTMENT||type==MetaBuilding.RETAIL){//只有住宅和零售店才有知名度和品质
    	  	Map<Integer,Double> brandMap=new HashMap<Integer,Double>();
        	Map<Integer,Double> qtyMap=new HashMap<Integer,Double>();
    	   	//单个建筑
        	BrandManager.instance().getBuildingBrandOrQuality(this, brandMap, qtyMap);
           	double brand=BrandManager.instance().getValFromMap(brandMap, type());
        	double quality=BrandManager.instance().getValFromMap(qtyMap, type());
        	//知名度评分
            int brandScore =(int)GlobalUtil.getBrandScore(brand, type);
            //品质评分
            int qtyScore = (int)GlobalUtil.getBuildingQtyScore(quality, type);
            builder.setBrand(brandScore).setQuality(qtyScore);
    	}
        return builder.build();
    }

    public abstract Message detailProto();
    public abstract void appendDetailProto(Gs.BuildingSet.Builder builder);

    protected abstract void enterImpl(Npc npc);

    // for current requirements, there is no leaving actions
    protected abstract void leaveImpl(Npc npc);
    // there is no need to remember which npc is in this building now
    public void enter(Npc npc) {
        addFlowCount();
//        flowCount += 1;
        enterImpl(npc);
    }

    public void addFlowCount()
    {
        flowCount += 1;
    }
    public int getFlow() {
        return this.flow;
    }

    @Transient
    private float lift = 0;

    public float getLift()
    {
        if (this.lift == 0)
        {
           updateLift();
        }
        return lift;
    }

    public void updateLift()
    {
        float f =0;
        long npcCount = NpcManager.instance().getNpcCount();
        if(npcCount!=0) {
            f = (float) this.flow / npcCount;
        }
        lift = new BigDecimal(f).setScale(2, 1).floatValue();
    }

    public void leave(Npc npc) {
        leaveImpl(npc);
    }
    void update(long diffNano) {
        if(this.outOfBusiness() || !working)
            return;
        this._update(diffNano);
    }
    protected abstract void _update(long diffNano);
    void timeSectionTick(int newIdx, int nowHour, int hours) {

    }

    public void setSalaryRatio(int salaryRatio, long ts) {
        this.salaryRatio = salaryRatio;
        this.salaryRatioTs = ts;
        this.setSalaryRatioAction();
    }

    protected void setSalaryRatioAction(){}

    public void spectialTickAction(long nowHour) {

    }
    public void hourTickAction(int nowHour) {
        flow = flowCount;
        flowCount = 0;
        flowHistory.add(new FlowInfo((int) TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()), flow));
        updateLift();
        //this._d.dirty(); // isDirty the field, or else hibernate won't update this field!!!
        assert this.type() != MetaBuilding.TRIVIAL;
        if(MetaData.getDayId() != 0 && nowHour == PAYMENT_HOUR && !this.allStaff.isEmpty()) {
            Player p = GameDb.getPlayer(ownerId);
            if(p != null) {
                this.payOff(p);
            }
        }
        if(!outOfBusiness()) {
            Boolean w = isWorking(nowHour);
            this.working = (w == null ? this.working:w);
        }
        this.broadcastChange();
        this.recordFlowAndLift();
    }

    protected void recordFlowAndLift()
    {
        if (this instanceof IBuildingContract)
        {
            LogDb.flowAndLift(this.id, this.flow, this.lift);
        }
    }

    public boolean salaryRatioVerification(int salaryRatio){
        return (salaryRatio == 50 || salaryRatio == 75 || salaryRatio == 100);
    }
    private Boolean isWorking(int nowHour) {
        Boolean res = null;
        for (int i : metaBuilding.endWorkHour[happy]) {
            if (i == nowHour) {
                res = false;
                break;
            }
        }
        for (int i : metaBuilding.startWorkHour[happy]) {
            if (i == nowHour) {
                res = true;
                break;
            }
        }
        return res;
    }
    private void calcuWorking(int nowHour) {
        if(happy == HAPPY_MIN) {
            this.working = false;
        }
        else {
            Boolean w = isWorking(nowHour);
            if (w == null) {
                int startIdx = Arrays.binarySearch(metaBuilding.startWorkHour[happy], nowHour);
                if(startIdx == -1)
                    this.working = false;
                else {
                    int endHour = metaBuilding.endWorkHour[happy][-(startIdx + 2)];
                    if (nowHour < endHour)
                        this.working = true;
                    else if (nowHour >= endHour)
                        this.working = false;
                }
            } else {
                this.working = w;
            }
        }
    }

    private boolean payOff(Player p) {
        if (p.decMoney(this.allSalary())) {
            LogDb.playerPay(p.id(), this.allSalary());
            calcuHappy();
            allStaff.forEach(npc -> {
                npc.addMoney(this.singleSalary());
                //缴纳社保
                npc.decMoney(this.singleTax());
                MoneyPool.instance().add(this.singleTax());
            });
            List<Object> updates = allStaff.stream().map(Object.class::cast).collect(Collectors.toList());
            updates.add(p);
            GameDb.saveOrUpdate(updates);
            LogDb.paySalary(p.id(), id(), this.singleSalary(), this.allStaff.size());
            return true;
        } else {
            shutdownBusiness();
            //停工通知(不足支付工资)
            UUID[] ownerIdAndBuildingId = {this.ownerId(), this.id()};
            MailBox.instance().sendMail(Mail.MailType.LOCKOUT.getMailType(), this.ownerId(), null, ownerIdAndBuildingId, null);
            return false;
        }
    }
    private void calcuHappy() {
        if(salaryRatio == 100)
            happy = HAPPY_MAX;
        else if(salaryRatio < 100 && salaryRatio >= 80)
            happy = 1;
        else if(salaryRatio < 80 && salaryRatio >= 60)
            happy = 2;
        else if(salaryRatio < 60 && salaryRatio >= 40)
            happy = 3;
        else if(salaryRatio < 40 && salaryRatio >= 0)
            happy = HAPPY_MIN;
    }

    public long getTodayIncomeTs() {
        return todayIncomeTs;
    }

    public void setTodayIncomeTs(long todayIncomeTs) {
        this.todayIncomeTs = todayIncomeTs;
    }

    public long getTodayIncome() {
        return todayIncome;
    }

    public void setTodayIncome(long todayIncome) {
        this.todayIncome = todayIncome;
    }

    public long getLast_modify_time() {
        return last_modify_time;
    }

    public void setLast_modify_time(long last_modify_time) {
        this.last_modify_time = last_modify_time;
    }
    
    public int getState() {
		return state;
	}

	//是否到了规定时间可以修改
    public boolean canBeModify(){
        Long now = new Date().getTime();
        long day = 24 * 60 * 60 * 1000;
        if(this.last_modify_time+7*day<=now){
            return true;
        }else
            return false;
    }
}
