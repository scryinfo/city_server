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

    //Get price per millisecond
    public int getCurPromPricePerMs() {
        return curPromPricePerHour/3600000;
    }
    public int getCurPromPricePerS() {
        return curPromPricePerHour/3600;
    }

    public void setCurPromPricePerHour(int curPromPricePerHour) {
        this.curPromPricePerHour = curPromPricePerHour;
    }

    private int curPromPricePerHour = 0;		//Promotion price

    public long getPromRemainTime() {
        return promRemainTime;
    }

    public List<UUID> getSelledPromotion() {
        return selledPromotion;
    }

    public void setPromRemainTime(long promRemainTime) {
        this.promRemainTime = promRemainTime;
    }

    private long promRemainTime = 0;		//Available time

    public boolean isTakeOnNewOrder() {
        return takeOnNewOrder;
    }

    public void setTakeOnNewOrder(boolean takeOnNewOrder) {
        this.takeOnNewOrder = takeOnNewOrder;
    }

    private boolean takeOnNewOrder = false;	//Accept new orders

    public static Logger getLogger() {
        return logger;
    }

    private long GuidedPrice = 0;			//Guided price of cache

    public long getNewPromoStartTs() {
        return newPromoStartTs;
    }

    public void setNewPromoStartTs(long startTs) {
        this.newPromoStartTs = startTs;
    }

    private long newPromoStartTs = -1;			    //New promotion start time, if there is a promotion before, then startTs is the end of the last promotion


    public List<UUID> getSelledPromotions() {
        return selledPromotion;
    }

    public int getFlowPromoCur() {
        return flowPromoCur;
    }

    public void setFlowPromoCur(int flowPromoCur) {
        this.flowPromoCur = flowPromoCur;
    }

    //Traffic promotion force cache
    @Transient
    private int flowPromoCur = 0;
    //eva promotion force cache
    @Transient
    private Map<Integer,Integer>evaPromoCur = new HashMap<>();

    public float getCurPromoAbility() {
        return curPromoAbility;
    }

    public float getAllPromoTypeAbility(int inObjType){
        //Calculation formula：
			/*
				* Basic promotion force = Wage distribution ratio * Number of construction NPCs * 1 worker output per hour
				* Single promotion ability = basic promotion ability * (1 +% single eva ability promotion) * (1+% flow promotion)
					Single eva capacity improvement = the value of p (percent percent) taken from the eva rating table divided by 100,000
			*/

        //1、 Wage distribution ratio * Number of construction NPCs
        PublicFacility fcySeller = this ;
        int salaryAdd = fcySeller.getSalaryRatio()*fcySeller.getWorkerNum();
        //private MetaPublicFacility meta;
        //2、 1 worker 1 hour output
        int workerAdd1H = meta.output1P1Hour;
			/*
				gs.proto
				message Eva
				   required bytes pid = 1; //playerid
				   required int32 at = 2;  //a Type: Only fill in raw itemitem (2101001) Commodity itemid (2251001) Construction category ID (13-retail store, 14-residential, 15-research institute, 16-promotion company, 17-warehouse) Commodity category ID (2251 -Staple food...)
				   //The buyer of the promotion company is a retail store 1613; the buyer of the promotion company is a residential 1614; the two capabilities below the institute 155,156
				   required Btype bt = 3;  //Type b: 1=quality 2=brand 3=production speed 4=promotion ability 5=invention improvement 6=EVA promotion 7=warehouse promotion
				   required int32 lv = 4;  //Level -1 is a brand bonus. Level >= 1 is manufacturable. Level = 0 is not manufacturable. It can be upgraded to 1, -1 and 0 by invention.
				   required int64 cexp = 5;//Current experience
				   optional int64 b = 6;   //Brand
			*/
        //3、 Calculate Eva's single lift ability
        //Check if there is an Eva improvement of the advertiser's promotion ability
        int evaAdd = evaPromoCur.getOrDefault(inObjType,0);
        //4、 Increased traffic
        float flowRatios = getFlowPromoCur();
        return salaryAdd * workerAdd1H * (1 + (float)evaAdd /100000) * (1 + flowRatios);
    }

    /*Promotion ability = basic promotion value * number of employees * (1+eva bonus)*/
    public long getLocalPromoAbility(int type){
        int atype=0;
        //Determine a type
        if(type/100==type()) {//If the type passed is eva, query directly
            atype = type;
        }
        else{
            if (MetaBuilding.isBuildingByBaseType(type / 100)) { //If it is a building, the base type is equal to type/100
                type = type / 100;
                atype = Integer.parseInt(new StringBuilder().append(this.type()).append(type).toString()); //Determine the type of Eva, splice a type, building type +type
            } else {//Commodity base type%100
                type = type % 100;
                atype = Integer.parseInt(new StringBuilder().append(this.type()).append(type).toString());//Determine the type of Eva, splice a type, building type +type
            }
        }
        double evaAdd = EvaManager.getInstance().computePercent(EvaManager.getInstance().getEva(this.ownerId(), atype, Gs.Eva.Btype.PromotionAbility_VALUE));
        return (long) (this.meta.output1P1Hour * this.getWorkerNum() * (1 + evaAdd));
    }

    public void updatePromoAbility() {
        //3、 Calculate Eva's single lift ability
        Set<Eva> sellerEvas = EvaManager.getInstance().getEvaList(this.ownerId());
        int abType = Gs.Eva.Btype.PromotionAbility.getNumber();
        //Check if there is an Eva improvement of the advertiser's promotion ability
        int evaAdd = 0;
        Iterator<Eva> it = sellerEvas.iterator();
        while (it.hasNext()){
            Eva eva =  it.next();
            //According to the level of eva, take out the corresponding ability value
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
        //4、 Increased traffic
        flowPromoCur = (int)ContractManager.getInstance().getPlayerADLift(this.ownerId());
        addPromoAbRecord(this.id(),(short)(0),flowPromoCur);
    }

    //The current promotion ability value of each basic type changes with the Eva value, flow value, and salary ratio
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
        //Delete cached promotion ID
        selledPromotion.remove(promoId);
        //Update the starting point of all promotions in the company's advertising list
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
        //This price should be requested from the statistical service
        return GuidedPrice;
    }

    /*
		* Calculated every hour, the advertiser’s brand value is accumulated based on the current advertising company’s promotion capabilities
			* Basic promotion power = ratio of wages paid *Number of building NPCs * 1 worker 1 hour output
			* Single promotion ability = basic promotion ability * (1 +% single eva ability improvement) *(1+% flow increase)
				Single eva capacity improvement = the value of p (percent percent) taken from the eva rating table divided by 100,000
			* 1 worker can increase visibility in 1 hour (new field added to PublicFacility)
		* Rely on data analysis
			* data
				* Wage distribution ratio, number of construction NPCs
				* 1 worker can increase visibility in 1 hour
				* Increased traffic
			* some
				* Wage distribution ratio, number of construction NPCs
					allSalary()
				* 1 worker can increase visibility in 1 hour
					meta.output1P1Hour;
				* Single eva capacity improvement
					* Configuration table
						* Eva capability table
						* Eva rating table
					* The relationship between the two
						* Eva capability table shared Eva rating table
						player maintenance
							List<Eva> list = new ArrayList<Eva>();
							The promotion is the second class, such as food
					* Calculation
						public class Eva {
						queryMyEva
				* Increased traffic
					Game.Contract.ContractManager#getPlayerADLift
		* Behavior analysis
			* Update the database directly without notifying the client
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
        //Re-open service, we need to get at the last record
        return GameDb.getlastEvaRecord(bid,tid);
    }
    public FlowRecord getlastFlowRecord(UUID inPid){
        return GameDb.getlastFlowRecord(inPid);
    }

    public void addPromoAbRecord( UUID buildingId, short typeId, int value ){
        //The recording interval is PromotionMgr._upDeltaMsint ts = (int)(System.currentTimeMillis() / PromotionMgr._upDeltaMs);
        int ts = (int)(System.currentTimeMillis() / PromotionMgr._upDeltaMs/1000);
        if(typeId < 1){
            //human traffic
            Building bd = City.instance().getBuilding(buildingId);
            if(bd == null){
                //if(GlobalConfig.DEBUGLOG){
                    //GlobalConfig.cityError("PublicFacility.addPromoAbRecord: building not exist!");
                //}
                return;
            }
            UUID pid = bd.ownerId();
            FlowRecord lastRecord = getlastFlowRecord(pid);
            //Only record changes, reduce the amount of data
            if(lastRecord.value == value){
                return;
            }
            FlowRecord newRecord = new FlowRecord(pid,ts, value);
            GameDb.saveOrUpdateAndClear( newRecord );
            GlobalConfig.cityError("PublicFacility.addPromoAbRecord: saveOrUpdateAndClear FlowRecord");
        }else{
            //eva
            EvaRecord lastRecord = getlastEvaRecord(buildingId,typeId);
            //Only record changes, reduce the amount of data
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

    //Basic ability bonus
    public double getBaseAbility(){
        //Basic promotion force = Wage distribution ratio * Number of construction NPCs * 1 worker output per hour
        //1.Wage distribution ratio * Number of construction NPCs
        int salaryAdd = this.getSalaryRatio()*this.getWorkerNum();
        //2、 1 worker 1 hour output
        int workerAdd1H = meta.output1P1Hour;
        return salaryAdd * workerAdd1H;
    }

    public void clear() {
        this.selledPromotion.clear();
    }
}
