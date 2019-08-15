package Game;

import Game.Contract.ContractManager;
import Game.Eva.Eva;
import Game.Eva.EvaManager;
import Game.Meta.*;
import Game.Timers.PeriodicTimer;
import Shared.GlobalConfig;
import Shared.Package;
import Shared.PackageEncoder;
import Shared.Util;
import com.google.protobuf.Message;
import gs.Gs;
import gscode.GsCode;
import org.apache.log4j.Logger;
import org.hibernate.annotations.Cascade;

import javax.persistence.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Entity(name = "PublicFacility")
@DiscriminatorValue("1")
public class PublicFacility extends Building{
    PublicFacility(){}

    @Override
    public  void tick(long deltaTime){
        if(deltaTime == 0)
            return;
        updatePromoAbility();
    }
    @Override
    protected void finalize(){
        System.out.println("PublicFacility finalize");
    }

    public PublicFacility(MetaPublicFacility meta, Coordinate pos, UUID ownerId) {
        super(meta, pos, ownerId);
        this.meta = meta;
        this.qty = meta.qty;
        this.curflowPromoAbTotall = -1;
    }
    @Override
    public void postAddToWorld(){
        TickManager.instance().unRegisterTick(this, false);
        TickManager.instance().registerTick(City.senond2Ns(25),this, true);
    };
    private static final Logger logger = Logger.getLogger(PackageEncoder.class);

    public int getCurPromPricePerHour() {
        return curPromPricePerHour;
    }

    //获取每毫秒价格
    public int getCurPromPricePerMs() {
        return curPromPricePerHour/3600000;
    }
    public int getCurPromPricePerS() {
        return curPromPricePerHour/3600;
    }

    public void setCurPromPricePerHour(int curPromPricePerHour) {
        this.curPromPricePerHour = curPromPricePerHour;
    }

    private int curPromPricePerHour = 0;		//推广价格

    public long getPromRemainTime() {
        return promRemainTime;
    }

    public List<UUID> getSelledPromotion() {
        return selledPromotion;
    }

    public void setPromRemainTime(long promRemainTime) {
        this.promRemainTime = promRemainTime;
    }

    private long promRemainTime = 0;		//可推广时间

    public boolean isTakeOnNewOrder() {
        return takeOnNewOrder;
    }

    public void setTakeOnNewOrder(boolean takeOnNewOrder) {
        this.takeOnNewOrder = takeOnNewOrder;
    }

    private boolean takeOnNewOrder = false;	//接受新订单

    public static Logger getLogger() {
        return logger;
    }

    private long GuidedPrice = 0;			//缓存的指导价格

    public long getNewPromoStartTs() {
        return newPromoStartTs;
    }

    public void setNewPromoStartTs(long startTs) {
        this.newPromoStartTs = startTs;
    }

    private long newPromoStartTs = -1;			    //新推广开始时间，如果之前有推广，那么startTs为最后一个推广结束之时


    public List<UUID> getSelledPromotions() {
        return selledPromotion;
    }

    public int getFlowPromoCur() {
        return flowPromoCur;
    }

    public void setFlowPromoCur(int flowPromoCur) {
        this.flowPromoCur = flowPromoCur;
    }

    //流量推广力缓存
    @Transient
    private int flowPromoCur = 0;
    //eva推广力缓存
    @Transient
    private Map<Integer,Integer>evaPromoCur = new HashMap<>();

    public float getCurPromoAbility() {
        return curPromoAbility;
    }

    public float getAllPromoTypeAbility(int inObjType){
        //计算公式：
			/*
				* 基础推广力 = 发放工资比例 *建筑NPC数量 * 1个工人1小时产出
				* 单项推广能力 = 基础推广力 * （1 + %单项eva能力提升） *（1+%流量提升）
					单项eva能力提升 = 从eva等级表中取出的p（百分百 percent）的值除以 10 万
			*/

        //1、 发放工资比例 *建筑NPC数量
        PublicFacility fcySeller = this ;
        int salaryAdd = fcySeller.getSalaryRatio()*fcySeller.getWorkerNum();
        //private MetaPublicFacility meta;
        //2、 1个工人1小时产出
        int workerAdd1H = meta.output1P1Hour;
			/*
				gs.proto
				message Eva
				   required bytes pid = 1; //playerid
				   required int32 at = 2;  //a类型：只能填  原料itemid（2101001） 商品itemid（2251001） 建筑大类ID（13-零售店，14-住宅,15-研究所,16-推广公司,17-仓库） 商品大类ID（2251-主食。。。）
				   //推广公司的买家是零售店 1613 ；推广公司的买家是住宅 1614 ；   研究所下面的两项能力155，156
				   required Btype bt = 3;  //b类型：1=品质   2=品牌   3=生产速度  4=推广能力    5=发明提升  6=EVA提升    7=仓库提升
				   required int32 lv = 4;  //级别   -1为品牌加成  级别>= 1为可生产   级别 = 0 不可生产,可依靠发明提升为1，-1和0时，不计算等级
				   required int64 cexp = 5;//当前经验值
				   optional int64 b = 6;   //品牌
			*/
        //3、 计算Eva单项提升能力
        //查看是否有该广告商推广能力的Eva提升
        int evaAdd = evaPromoCur.getOrDefault(inObjType,0);
        //4、 流量提升
        float flowRatios = getFlowPromoCur();
        return salaryAdd * workerAdd1H * (1 + (float)evaAdd /100000) * (1 + flowRatios);
    }

    /*推广能力=基础推广值*员工人数*(1+eva加成)*/
    public long getLocalPromoAbility(int type){
        int atype=0;
        //确定a类型
        if(type/100==type()) {//如果传递的是eva中的类型，直接查询
            atype = type;
        }
        else{
            if (MetaBuilding.isBuildingByBaseType(type / 100)) { //如果是建筑,基础类型等于type/100
                type = type / 100;
                atype = Integer.parseInt(new StringBuilder().append(this.type()).append(type).toString()); //确定Eva的a类型,拼接a类型，建筑类型+type
            } else {//商品  基础类型%100
                type = type % 100;
                atype = Integer.parseInt(new StringBuilder().append(this.type()).append(type).toString());//确定Eva的a类型,拼接a类型，建筑类型+type
            }
        }
        double evaAdd = EvaManager.getInstance().computePercent(EvaManager.getInstance().getEva(this.ownerId(), atype, Gs.Eva.Btype.PromotionAbility_VALUE));
        return (long) (this.meta.output1P1Hour * this.getWorkerNum() * (1 + evaAdd));
    }

    public void updatePromoAbility() {
        //3、 计算Eva单项提升能力
        Set<Eva> sellerEvas = EvaManager.getInstance().getEvaList(this.ownerId());
        int abType = Gs.Eva.Btype.PromotionAbility.getNumber();
        //查看是否有该广告商推广能力的Eva提升
        int evaAdd = 0;
        Iterator<Eva> it = sellerEvas.iterator();
        while (it.hasNext()){
            Eva eva =  it.next();
            //根据取到的eva的等级，取出对应的能力值
            Integer level=eva.getLv();
            Map<Integer,MetaExperiences> map=MetaData.getAllExperiences();
            MetaExperiences evaAddMe= map.get(level);
            if(evaAddMe != null){
                evaAdd = evaAddMe.p;
                evaPromoCur.put(eva.getAt(),evaAddMe.p);
                if(eva.getAt() >= 1600 && eva.getAt() <= 1699){
                    addPromoAbRecord(this.id(),(short) eva.getAt(),evaAdd);
                }
            }
        }
        //4、 流量提升
        flowPromoCur = (int)ContractManager.getInstance().getPlayerADLift(this.ownerId());
        addPromoAbRecord(this.id(),(short)(0),flowPromoCur);
    }

    //当前各个基础类型的推广能力值，随Eva值、流量值、工资比例发生改变
    private float curPromoAbility = 0;
    private int curflowPromoAbTotall = -1;
    /*@ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "PubFacility_promo", joinColumns = @JoinColumn(name = "selled_id"))
    @OrderColumn*/

    /*@Column(name="pid")
    private UUID pid;*/

    //@ElementCollection(fetch = FetchType.EAGER)
    /*@ElementCollection()
    @JoinColumn(name = "id_selled")*/
    @ElementCollection(fetch = FetchType.EAGER)
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    @OrderColumn
    @JoinColumn(name = "id_selled")
    private List<UUID> selledPromotion = new ArrayList<>();

    public UUID getLastPromotion(){
        if(selledPromotion.size() == 0){
            return null;
        }
        return selledPromotion.get(selledPromotion.size()-1);
    }
    public void addSelledPromotion(UUID promoId){
        if(selledPromotion.indexOf(promoId) < 0){
            selledPromotion.add(promoId);
        }
    }

    public List<PromoOdTs> delSelledPromotion(UUID promoId ,boolean delOrder){
        List<PromoOdTs> ret = PromotionMgr.instance().AdRemovePromoOrder(promoId,selledPromotion, delOrder);
        //删除缓存的推广ID
        selledPromotion.remove(promoId);
        //更新推广公司广告列表中所有推广的起点
        return ret;
    }

    public void setNewOrderOn(boolean on){
        takeOnNewOrder = on;
    }
    public void updateNewOrderPrice(int Price){
        curPromPricePerHour = Price;
    }
    public boolean comsumeAvaliableTime(long newOrderTime){
        if(promRemainTime - newOrderTime < 0){
            return  false;
        }
        promRemainTime -= newOrderTime;
        return  true;
    }
    private long getGuidedPrice(){
        //TODO
        //这个价格应该从 统计服 上请求
        return GuidedPrice;
    }

    /*
		* 每小时计算一次，广告主品牌值根据当前广告公司推广能力进行累计
			* 基础推广力 = 发放工资比例 *建筑NPC数量 * 1个工人1小时产出
			* 单项推广能力 = 基础推广力 * （1 + %单项eva能力提升） *（1+%流量提升）
				单项eva能力提升 = 从eva等级表中取出的p（百分百 percent）的值除以 10 万
			* 1个工人1小时能增加的知名度（新增字段到PublicFacility）
		* 依赖数据分析
			* 数据
				* 工资发放比例、建筑NPC数量
				* 1个工人1小时能增加的知名度
				* 流量提升
			* 有的
				* 工资发放比例、建筑NPC数量
					allSalary()
				* 1个工人1小时能增加的知名度
					meta.output1P1Hour;
				* 单项eva能力提升
					* 配置表
						* Eva 能力表
						* Eva 等级表
					* 二者关系
						* Eva能力表共享Eva等级表
						player 维护
							List<Eva> list = new ArrayList<Eva>();
							提升的是二级类，比如 食品
					* 计算
						public class Eva {
						queryMyEva
				* 流量提升
					Game.Contract.ContractManager#getPlayerADLift
		* 行为分析
			* 不用通知客户端，直接更新数据库
		*/
    public float excutePromotion(PromoOrder promo){
        return getAllPromoTypeAbility(promo.buildingType > 0 ? promo.buildingType : promo.productionType);
    }

    @Column(nullable = false)
    protected int qty;

    public int getMaxDayToRent() {
        return meta.maxDayToRent;
    }
    public int getMinDayToRent() { return meta.minDayToRent; }
    public int getMaxRentPreDay() {
        return meta.maxRentPreDay;
    }

    @Transient
    private MetaPublicFacility meta;
    private static final int MAX_SLOT_NUM = 999;

    public void setTickPrice(int price) {
        this.tickPrice = price;
    }

    @Override
    public int cost() {
        return this.tickPrice;
    }
    @Entity
    public static final class Slot {
        protected Slot(){}
        public Slot(int maxDayToRent, int minDayToRent, int rentPreDay, int deposit) {
            this.maxDayToRent = maxDayToRent;
            this.minDayToRent = minDayToRent;
            this.rentPreDay = rentPreDay;
            this.deposit = deposit;
        }
        @Id
        final UUID id = UUID.randomUUID();
        int maxDayToRent;
        int minDayToRent;
        int rentPreDay;
        int deposit;

        Gs.Advertisement.Slot toProto() {
            Gs.Advertisement.Slot.Builder builder = Gs.Advertisement.Slot.newBuilder();
            return builder.setId(Util.toByteString(id))
                    .setMaxDayToRent(maxDayToRent)
                    .setMinDayToRent(minDayToRent)
                    .setRentPreDay(rentPreDay)
                    .setDeposit(deposit)
                    .build();
        }
    }
    @OneToMany(fetch = FetchType.EAGER)
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    @MapKey(name = "id")
    @JoinColumn(name = "public_facility_id")
    private Map<UUID, Slot> slot = new HashMap<>();

    @Entity
    public static final class SlotRent {
        protected SlotRent(){}
        public SlotRent(Slot slot, int day, UUID renterId) {
            this.slot = slot;
            this.day = day;
            this.beginTs = System.currentTimeMillis();
            this.renterId = renterId;
            this.payTs = beginTs;
        }

        @Id
        private UUID id; // for hibernate only, don't use it

        @OneToOne(cascade=CascadeType.ALL, fetch = FetchType.EAGER)
        @JoinColumn(name = "slot_id")
        @MapsId
        Slot slot;
        int day;
        long beginTs;
        UUID renterId;
        long payTs;
        Gs.Advertisement.SoldSlot toProto() {
            Gs.Advertisement.SoldSlot.Builder builder = Gs.Advertisement.SoldSlot.newBuilder();
            return builder.setS(slot.toProto())
                    .setBeginTs(beginTs)
                    .setDays(day)
                    .setRenterId(Util.toByteString(renterId))
                    .build();
        }
    }
    @Transient
    PeriodicTimer adTimer = new PeriodicTimer(5000);

    @OneToMany(fetch = FetchType.EAGER)
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    @MapKeyColumn(name = "slot_id")
    @JoinColumn(name = "public_facility_id")
    private Map<UUID, SlotRent> rent = new HashMap<>();

    @Entity
    public static final class Ad {
        public Ad(SlotRent sr, int metaId, Type type) {
            this.sr = sr;
            this.metaId = metaId;
            this.type = type;
            this.beginTs = System.currentTimeMillis();
        }

        @Id
        final UUID id = UUID.randomUUID();
        @OneToOne(cascade=CascadeType.ALL, fetch = FetchType.EAGER)
        @JoinColumn(name = "slot_rent_id")
        SlotRent sr;

        // building type or good meta id
        int metaId;

        protected Ad() {
        }

        enum Type {
            GOOD,
            BUILDING
        }
        Type type;
        long beginTs;
        int npcFlow;

        Gs.Advertisement.Ad toProto() {
            Gs.Advertisement.Ad.Builder builder = Gs.Advertisement.Ad.newBuilder();
            if(sr != null)
                builder.setSlot(sr.toProto());
            return builder.setId(Util.toByteString(id))
                    .setMetaId(metaId)
                    .setType(Gs.Advertisement.Ad.Type.valueOf(type.ordinal()))
                    .setBeginTs(beginTs)
                    .setNpcFlow(npcFlow)
                    .build();
        }
    }

    @OneToMany(fetch = FetchType.EAGER)
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    @MapKey(name = "id")
    @JoinColumn(name = "public_facility_id")
    // key is ad.id not slot.id
    private Map<UUID, Ad> ad = new HashMap<>();


    public Slot addSlot(int maxDay, int minDay, int rent) {
        if(slot.size() >= MAX_SLOT_NUM)
            return null;
        Slot s = new Slot(maxDay, minDay, rent, rent*meta.depositRatio);
        this.slot.put(s.id, s);
        return s;
    }
    public boolean delSlot(UUID id) {
        if(rent.containsKey(id))
            return false;
        this.slot.remove(id);
        return true;
    }

    public Slot getSlot(UUID id) {
        return slot.get(id);
    }
    public boolean isSlotRentOut(UUID id) {return getRentSlot(id) != null;}
    public SlotRent getRentSlot(UUID id) {
        return rent.get(id);
    }
    public boolean slotCanBuy(UUID id) {
        return slot.containsKey(id) && !rent.containsKey(id);
    }
    public void buySlot(UUID id, int day, UUID renterId) {
        rent.put(id, new SlotRent(slot.get(id), day, renterId));
    }
    public boolean hasAd(UUID slotId) {
        for(Ad ad : ad.values()) {
            if(ad.sr != null && ad.sr.slot.id.equals(slotId))
                return true;
        }
        return false;
    }
    public PublicFacility.Ad getAd(UUID id) {
        return ad.get(id);
    }
    public void delAd(UUID id) {
        ad.remove(id);
        qty -= 1;
    }
    public Ad addAd(SlotRent sr, MetaItem m) {
        Ad ad = new Ad(sr, m.id, Ad.Type.GOOD);
        this.ad.put(ad.id, ad);
        return ad;
    }
    public Ad addAd(SlotRent sr, int buildingType) {
        Ad ad = new Ad(sr, buildingType, Ad.Type.BUILDING);
        this.ad.put(ad.id, ad);
        qty += 1;
        return ad;
    }
    //protected PublicFacility() {}

    @Override
    public int quality() {
        return this.qty;
    }

    int tickPrice;
    @Override
    protected void enterImpl(Npc npc){
        this.ad.values().forEach(ad->{
            ad.npcFlow++;
            BrandManager.instance().update(ad.sr.renterId, ad.metaId, 1);
        });
        ++visitorCount;
    }

    @Override
    protected void leaveImpl(Npc npc) {
        --visitorCount;
    }
    private int visitorCount;

    @PostLoad
    protected void _1() {
        this.meta = (MetaPublicFacility) super.metaBuilding;
    }

    @Override
    public Message detailProto() {
        Gs.PublicFacility.Builder builder = Gs.PublicFacility.newBuilder();
        builder.setInfo(this.toProto());
        builder.setAd(genAdPart());
        builder.setQty(qty);
        builder.setTicketPrice(this.tickPrice);
        builder.setVisitorCount(visitorCount);
        builder.setSelledPromCount(this.getSelledPromotion().size());
        builder.setNewPromoStartTs(this.getNewPromoStartTs());
        builder.setCurPromPricePerHour(this.getCurPromPricePerHour());
        builder.setPromRemainTime(this.getPromRemainTime());
        builder.setTakeOnNewOrder(this.isTakeOnNewOrder());
        builder.setCurflowPromoAbTotall(updateflowPromoTotall ());
        return builder.build();
    }
    protected Gs.Advertisement genAdPart() {
        Gs.Advertisement.Builder builder = Gs.Advertisement.newBuilder();
        this.slot.values().forEach(v->builder.addAvailableSlot(v.toProto()));
        this.rent.values().forEach(v->builder.addSoldSlot(v.toProto()));
        this.ad.values().forEach(v->builder.addAd(v.toProto()));
        return builder.build();
    }
    private int updateflowPromoTotall (){
        List<UUID> promoIDs = new ArrayList<>();
        Player player =  GameDb.getPlayer(ownerId());
        curflowPromoAbTotall = (int)ContractManager.getInstance().getPlayerADLift(player.id());
        return curflowPromoAbTotall;
    }
    @Override
    public void appendDetailProto(Gs.BuildingSet.Builder builder) {
        builder.addPublicFacility((Gs.PublicFacility) this.detailProto());
    }

    public EvaRecord getlastEvaRecord(UUID bid, short tid){
        //重新开服,需要获取一下上次的记录
        return GameDb.getlastEvaRecord(bid,tid);
    }
    public FlowRecord getlastFlowRecord(UUID inPid){
        return GameDb.getlastFlowRecord(inPid);
    }

    public void addPromoAbRecord( UUID buildingId, short typeId, int value ){
        //记录的时间间隔为 PromotionMgr._upDeltaMsint ts = (int)(System.currentTimeMillis() / PromotionMgr._upDeltaMs);
        int ts = (int)(System.currentTimeMillis() / PromotionMgr._upDeltaMs/1000);
        if(typeId < 1){
            //人流量
            Building bd = City.instance().getBuilding(buildingId);
            if(bd == null){
                //if(GlobalConfig.DEBUGLOG){
                    //GlobalConfig.cityError("PublicFacility.addPromoAbRecord: building not exist!");
                //}
                return;
            }
            UUID pid = bd.ownerId();
            FlowRecord lastRecord = getlastFlowRecord(pid);
            //只记录变化的，减少数据量
            if(lastRecord.value == value){
                return;
            }
            FlowRecord newRecord = new FlowRecord(pid,ts, value);
            GameDb.saveOrUpdateAndClear( newRecord );
            GlobalConfig.cityError("PublicFacility.addPromoAbRecord: saveOrUpdateAndClear FlowRecord");
        }else{
            //eva
            EvaRecord lastRecord = getlastEvaRecord(buildingId,typeId);
            //只记录变化的，减少数据量
            if(lastRecord.value == value){
                return;
            }
            EvaRecord newRecord = new EvaRecord(buildingId,typeId, ts, value);
            GameDb.saveOrUpdateAndClear( newRecord );
            GlobalConfig.cityError("PublicFacility.addPromoAbRecord: saveOrUpdateAndClear EvaRecord");
        }
    }
    @Override
    protected void _update(long diffNano) {
        final long now = System.currentTimeMillis();
        if(adTimer.update(diffNano)){
            Set<UUID> ids = new HashSet<>();
            rent.forEach((k,v)->{
                if(now - v.beginTs >= TimeUnit.DAYS.toMillis(v.day)) {
                    ids.add(k);
                }
                if(now - v.payTs >= TimeUnit.DAYS.toMillis(1)) {
                    Player renter = GameDb.getPlayer(v.renterId);
                    Player owner = GameDb.getPlayer(this.ownerId());
                    if(!renter.decMoney(v.slot.rentPreDay)) {
                        long deposit = renter.spentLockMoney(v.slot.id);
                        owner.addMoney(deposit);
                        ids.add(v.slot.id);
                    }
                    else {
                        owner.addMoney(v.slot.rentPreDay);
                        v.payTs = now;
                    }
                    GameDb.saveOrUpdate(Arrays.asList(renter, owner, this)); // seems we should disable select-before-update
                }
            });
            rent.entrySet().removeIf(e -> ids.contains(e.getKey()));
            for(UUID id : ids) {
                this.ad.entrySet().removeIf(e->{
                    if(e.getValue().sr != null && e.getValue().sr.slot.id.equals(id))
                        return true;
                    return false;
                });
            }
            if(!ids.isEmpty()) {
                GameDb.saveOrUpdate(this); // update the delete
                Gs.AdSlotTimeoutInform.Builder builder = Gs.AdSlotTimeoutInform.newBuilder();
                builder.setBuildingId(Util.toByteString(this.id()));
                ids.forEach(e->builder.addSlotId(Util.toByteString(e)));
                this.sendToWatchers(Package.create(GsCode.OpCode.adSlotTimeoutInform_VALUE, builder.build()));
            }
        }
    }

    @Override
    public int getTotalSaleCount() {
        return 0;
    }

    //基础能力加成
    public double getBaseAbility(){
        //基础推广力 = 发放工资比例 *建筑NPC数量 * 1个工人1小时产出
        //1.发放工资比例 *建筑NPC数量
        int salaryAdd = this.getSalaryRatio()*this.getWorkerNum();
        //2、 1个工人1小时产出
        int workerAdd1H = meta.output1P1Hour;
        return salaryAdd * workerAdd1H;
    }

    public void clear() {
        this.selledPromotion.clear();
    }
}
