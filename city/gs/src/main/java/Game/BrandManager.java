package Game;

import Game.CityInfo.CityManager;
import Game.Eva.Eva;
import Game.Eva.EvaManager;
import Game.League.BrandLeague;
import Game.League.LeagueManager;
import Game.Meta.MetaBuilding;
import Game.Meta.MetaData;
import Game.Meta.MetaGood;
import Game.Meta.MetaItem;
import Game.Timers.PeriodicTimer;
import Shared.Util;
import gs.Gs;
import org.apache.log4j.Logger;
import org.hibernate.annotations.Cascade;

import javax.persistence.*;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Entity
public class BrandManager {
    private static BrandManager instance;
    public static BrandManager instance() {
        return instance;
    }
    public static final int ID = 0;
    public static final int BASE_BRAND=100;//Building foundation visibility value
    private static final Logger logger = Logger.getLogger(BrandManager.class);
    protected BrandManager() {}
    public static void init(){
        GameDb.initBrandManager();
        instance = GameDb.getBrandManager();
        instance.refineCache();
        instance.getAllBuildingBrandOrQuality();
    }

    @Id
    private final int id = ID;

    @Embeddable
    public static final class BrandKey implements Serializable {
        UUID playerId;
        int mid;

        public BrandKey(UUID playerId, int mid) {
            this.playerId = playerId;
            this.mid = mid;
        }

        protected BrandKey() {}

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BrandKey brandKey = (BrandKey) o;
            return mid == brandKey.mid &&
                    Objects.equals(playerId, brandKey.playerId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(playerId, mid);
        }

        public UUID getPlayerId() {
            return playerId;
        }

        public int getMid() {
            return mid;
        }
    }

    @Entity(name = "brandname")
    public static final class BrandName {
        @Id
        @GeneratedValue
        UUID id;
        BrandName(){}
        BrandName(String name){
            brandName = name;
        }
        public String getBrandName() {
            return brandName;
        }

        public void setBrandName(String brandName) {
            this.brandName = brandName;
        }

        //@OneToOne(mappedBy="brandName",cascade=CascadeType.ALL)
        @OneToOne(mappedBy="brandName",cascade=CascadeType.ALL)
        private BrandInfo brandinfo;

        private String brandName = "";
    }
    @Entity
    public static final class BrandInfo {
        @EmbeddedId
        BrandKey key;
        int v;
        @OneToOne(cascade={CascadeType.ALL})
        public BrandName brandName = null;

        //Record the timestamp of the name modification, and the current time is greater than 7 days before it can be modified, used to prevent squatting
        //Since each player's BrandKey is limited, it is not necessary to process this data for the time being, and it is necessary to implement it when necessary
        /*
        There is no limit to the amount of squatting. For example, 100 kinds of products, then each player can have more than 100 BrandKey,
         Then there should be no squatting; besides, players frequently modify their brand names, which will actually damage
         My own brand recognition; if it is maliciously aimed at other competitors, how do I know what name people want to take?
         Therefore, the requirement to limit squatting is actually a pseudo-need.
        */
        Long nameChangedTs=0L;

        public boolean hasBrandName(){
            return brandName != null;
        }
        public BrandInfo(BrandKey key, String newBrandName) {
            this.key = key;
            brandName = new BrandName(newBrandName);
            this.nameChangedTs = 0L;
        }
        public BrandInfo(BrandKey key) {
            this.key = key;
            this.nameChangedTs = 0L;
        }

        protected BrandInfo() {}


        public String getBrandName() {
            return brandName.getBrandName();
        }
        public void setBrandName(String newBrandName) {
            this.nameChangedTs = new Date().getTime();
            if(brandName == null){
                brandName = new BrandName(newBrandName);
            }else
                brandName.setBrandName(newBrandName);
        }
        public boolean canBeModify(){//Whether it is greater than 7 days since the last modification, true can be modified, false cannot be modified
            Long now = new Date().getTime();
            long day = 24 * 60 * 60 * 1000;
            if(this.nameChangedTs+7*day<=now){
                return true;
            }else
                return false;
        }

        public BrandKey getKey() {
            return key;
        }

        public int getV() {
            return v;
        }

        public void setV(int v) {
            this.v = v;
        }
    }
    public void update(long diffNano) {
        if(dbSaveTimer.update(diffNano))
        	GameDb.saveOrUpdate(this);
        if(brandQualityTimer.update(diffNano)){
        	getAllBuildingBrandOrQuality();
        }
    }
    @Transient
    private PeriodicTimer dbSaveTimer = new PeriodicTimer(2*60*1000);
    @Transient
    private PeriodicTimer brandQualityTimer = new PeriodicTimer((int) TimeUnit.HOURS.toMillis(1));
    public void update(UUID playerId, int mid, int add) {
        BrandInfo i = allBrandInfo.computeIfAbsent(new BrandKey(playerId, mid), k->new BrandInfo(k));
        i.v+=add;
        refineCache(i, add);
    }
    public static final class BuildingRatio {
        public double apartment = 1.d;
        public double publicFacility = 1.d;
        public double retail = 1.d;

        @Override
        public String toString() {
            return "apartment " + apartment + ", park " + publicFacility + ", retail " + retail;
        }
    }
    BuildingRatio getBuildingRatio() {
        BuildingRatio res = new BuildingRatio();
        double all = allBuilding();
        res.apartment += all==0?0:((double)getBuilding(MetaBuilding.APARTMENT) / all);
        res.publicFacility += all==0?0:((double)getBuilding(MetaBuilding.PUBLIC) / all);
        res.retail += all==0?0:((double)getBuilding(MetaBuilding.RETAIL) / all);
        return res;
    }
    public int getBuilding(int type) {
        return allBuilding.get(type);
    }
    public int getGood(int mid) {
        return allGood.getOrDefault(mid, 0);
    }
    private void refineCache() {
        for(BrandInfo i : allBrandInfo.values()) {
            refineCache(i, i.v);
        }
    }

    private void refineCache(BrandInfo i, int add) {
        if(i.key.mid < MetaBuilding.MAX_TYPE_ID) {
            int t = MetaBuilding.type(i.key.mid);
            allBuilding.put(t, allBuilding.getOrDefault(t, 0) + add);
            Map<Integer, Integer> m = playerBuilding.computeIfAbsent(i.key.playerId, k->new HashMap<>());
            m.put(i.key.mid, m.getOrDefault(i.key.mid, 0) + add);
        }
        else if(MetaItem.isItem(i.key.mid)) {
            allGood.put(i.key, allGood.getOrDefault(i.key.mid, 0) + add);
            Map<Integer, Integer> m = playerGood.computeIfAbsent(i.key.playerId, k->new HashMap<>());
            m.put(i.key.mid, m.getOrDefault(i.key.mid, 0) + add);
        }
    }

    public double buildingBrandScore(int type) {
        Integer v = allBuilding.get(type);
        if(v == null)
            return 0;
        return (double)v / maxBuilding(type) * 100;
    }
    public int goodBrandScoreByType(MetaGood.Type type) {
        return allGood.entrySet().stream().filter(e->MetaGood.goodType(e.getKey().mid) == type).mapToInt(e->e.getValue()).reduce(0, Integer::sum);
    }
    public int goodBrandScoreByLux(int lux) {
        return allGood.entrySet().stream().filter(e->MetaData.getGood(e.getKey().mid).lux == lux).mapToInt(e->e.getValue()).reduce(0, Integer::sum);
    }
    public double[] getGoodWeightRatioWithType() {
        double[] res = new double[MetaGood.Type.ALL.ordinal()];
        Arrays.fill(res, 1.d);
        int all = allGood();
        if(all > 0) {
            for(int i = 0; i < MetaGood.Type.ALL.ordinal(); ++i) {
                res[i] += (double)goodBrandScoreByType(MetaGood.Type.values()[i]) / (double)all;
            }
        }
        return res;
    }
    public double[] getGoodWeightRatioWithLux() {
        double[] res = new double[MetaGood.LUX_SIZE];
        Arrays.fill(res, 1.d);
        int all = allGood();
        if(all > 0) {
            for(int i = 0; i < MetaGood.LUX_SIZE; ++i) {
                res[i] += (double)goodBrandScoreByLux(i) / (double)all;
            }
        }
        return res;
    }
    private double buildingRatio(int type) {
        Integer v = allBuilding.get(type);
        if(v == null)
            return 1.d;
        return 1.d + v / allBuilding();
    }
    private double goodRatio(int mId) {
        Integer v = allGood.get(mId);
        if(v == null)
            return 1.d;
        return 1.d + v / allGood();
    }
    public double spendMoneyRatioBuilding(int type) {
        return MetaData.getBuildingSpendMoneyRatio(type) * buildingRatio(type);
    }
    public double spendMoneyRatioGood(int mId) {
        return MetaData.getGoodSpendMoneyRatio(mId) * goodRatio(mId);
    }
    private double maxBuilding(int type) {
        // O(n), however the size is small, so not need to optimize
        return allBuilding.entrySet().stream().filter(e->e.getKey()==type).mapToInt(e->e.getValue()).max().orElse(0);
    }
    private double maxGood(int mId) {
        return allGood.entrySet().stream().filter(e->e.getKey().mid==mId).mapToInt(e->e.getValue()).max().orElse(0);
    }
    public int allBuilding() {
        return allBuilding.values().stream().reduce(0, Integer::sum);
    }
    public int allGood() {
        return allGood.values().stream().reduce(0, Integer::sum);
    }
    public int allGood(int metaId) {
        return allGood.entrySet().stream().filter(e->e.getKey().mid == metaId).mapToInt(e->e.getValue()).reduce(0, Integer::sum);
    }
    @Transient  // <building type, refineCache value>
    private Map<Integer, Integer> allBuilding = new TreeMap<>();

    @Transient  // <good meta id, refineCache value>
    private Map<BrandKey, Integer> allGood = new HashMap<>();

    public List<Gs.IntNum> getBuildingBrandProto(UUID playerId) {
        List<Gs.IntNum> res = new ArrayList<>();
        Map<Integer, Integer> m = playerBuilding.get(playerId);
        if(m != null) {
            m.forEach((k,v)->res.add(Gs.IntNum.newBuilder().setId(k).setNum(v).build()));
        }
        return res;
    }
    public List<Gs.IntNum> getGoodBrandProto(UUID playerId) {
        List<Gs.IntNum> res = new ArrayList<>();
        Map<Integer, Integer> m = playerGood.get(playerId);
        if(m != null) {
            m.forEach((k,v)->res.add(Gs.IntNum.newBuilder().setId(k).setNum(v).build()));
        }
        return res;
    }

    public int getBuilding(UUID playerId, int type) {
        Map<Integer, Integer> m = playerBuilding.get(playerId);
        if(m == null)
            return 0;
        return m.getOrDefault(type, 0);
    }
    public int getGood(UUID playerId, int mId) {
        Map<Integer, Integer> m = playerGood.get(playerId);
        if(m == null)
            return 0;
        return m.getOrDefault(mId, 0);
    }
    @Transient
    private Map<UUID, Map<Integer, Integer>> playerBuilding = new HashMap<>();

    @Transient
    private Map<UUID, Map<Integer, Integer>> playerGood = new HashMap<>();

    @OneToMany(fetch = FetchType.EAGER)
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    @MapKey(name = "key")
    @JoinColumn(name = "brand_manager_id")
    private Map<BrandKey, BrandInfo> allBrandInfo = new HashMap<>();

    //Add a brand, if there is already a brand using the BrandKey, return false
    //The current rule is that BrandKey only increases but does not decrease. For example, 10,000 players and 200 products, then the maximum is 2 million BrandKeys.
    //The server only generates the brand key by default, but does not generate the brand name. The client gets the brand key and generates the brand default brand name by itself
    public boolean addBrand(UUID pid, int typeId){
        BrandKey bk = new BrandKey(pid,typeId);
        BrandInfo bInfo = allBrandInfo.get(bk);
        if(bInfo == null){
            bInfo = new BrandInfo(bk);
            bInfo.setV(BrandManager.BASE_BRAND);
            allBrandInfo.put(bk,bInfo);
            GameDb.saveOrUpdate(this);
            return true;
        }
        return  false;
    }

    public BrandInfo getBrand(UUID pid, int typeId){
        BrandKey bk = new BrandKey(pid,typeId);
        return allBrandInfo.getOrDefault(bk,new BrandInfo(bk));
    }

    //The brand name can be changed, but to ensure that the incoming validNewName is unique (-1 brand name is repeated, 1 is modified successfully, other (indicating that the time is not passed))
    public Long changeBrandName(UUID pid, int typeId, String validNewName){
        //If the new name is the name in use, the operation fails and -1 is returned
        if(GameDb.brandNameIsInUsing(validNewName)){
            return -1l;
        }
        BrandInfo bInfo = getBrand(pid,typeId);
        if(!bInfo.canBeModify()) {
            return bInfo.nameChangedTs;
        }else {
            //If the name is available
            bInfo.setBrandName(validNewName);
            //Cascade save
            this.allBrandInfo.put(bInfo.key, bInfo);
            GameDb.saveOrUpdate(this);
            return 1l;
        }
    }

    @Transient
    private Map<Integer,Map<Integer,Double>> totalBrandQualityMap=new HashMap<Integer,Map<Integer,Double>>();

    public void getBuildingBrandOrQuality(Building b,Map<Integer,Double> brandMap,Map<Integer,Double> qtyMap){
    	UUID playerId=b.ownerId();
    	//The techId for residential and retail stores is 13 and 14
    	BrandLeague bl=LeagueManager.getInstance().getBrandLeague(b.id(), b.type());
    	if(bl!=null){//Priority inquiry for joining player technology
    		playerId=bl.getPlayerId();
    	}

    	Eva brandEva=EvaManager.getInstance().getEva(playerId, b.type(), Gs.Eva.Btype.Brand_VALUE);
    	Eva qualityEva=EvaManager.getInstance().getEva(playerId, b.type(), Gs.Eva.Btype.Quality_VALUE);
        int buildingBrand= (int) (BASE_BRAND *(1+EvaManager.getInstance().computePercent(brandEva)));//Brand value is also increased through Eva

		brandMap.put(b.type(), getValFromMap(brandMap,b.type())+new Double(buildingBrand));
		brandMap.put(Gs.ScoreType.BasicBrand_VALUE, new Double(BASE_BRAND));
		brandMap.put(Gs.ScoreType.AddBrand_VALUE, EvaManager.getInstance().computePercent(brandEva));
        //Initial quality of residential and retail stores = qty(yty)
        qtyMap.put(b.type(), getValFromMap(qtyMap,b.type())+new Double((b.quality())*(1+EvaManager.getInstance().computePercent(qualityEva))));
		qtyMap.put(Gs.ScoreType.BasicQuality_VALUE, new Double(b.quality()));
		qtyMap.put(Gs.ScoreType.AddQuality_VALUE, EvaManager.getInstance().computePercent(qualityEva));
    }

    public void getAllBuildingBrandOrQuality(){ //Not in use
    	Map<Integer,Double> brandMap=new HashMap<Integer,Double>();
    	Map<Integer,Double> qtyMap=new HashMap<Integer,Double>();
    	City.instance().typeBuilding.getOrDefault(MetaBuilding.APARTMENT,new HashSet<>()).forEach(b->{
            getBuildingBrandOrQuality(b,brandMap,qtyMap);
        });
        City.instance().typeBuilding.getOrDefault(MetaBuilding.RETAIL,new HashSet<>()).forEach(b->{
            getBuildingBrandOrQuality(b,brandMap,qtyMap);
        });
    	/*City.instance().forEachBuilding((Building b)->{
    		if(b.type()==MetaBuilding.APARTMENT||b.type()==MetaBuilding.RETAIL){
    			getBuildingBrandOrQuality(b,brandMap,qtyMap);
    		}
    	});*/
    	totalBrandQualityMap.put(Gs.Eva.Btype.Brand_VALUE, brandMap);
    	totalBrandQualityMap.put(Gs.Eva.Btype.Quality_VALUE, qtyMap);
    }

    public Map<Integer,Map<Integer,Double>> getTotalBrandQualityMap(){
    	Map<Integer,Double> newBrandMap=new HashMap<Integer,Double>();
    	Map<Integer,Double> newQtyMap=new HashMap<Integer,Double>();
    	totalBrandQualityMap.get(Gs.Eva.Btype.Brand_VALUE).forEach((k,v)->{
    		int num=City.instance().getBuildingNumByType(k);
    		if(num>0){
    			newBrandMap.put(k,v/num);
    		}
    	});
    	totalBrandQualityMap.get(Gs.Eva.Btype.Quality_VALUE).forEach((k,v)->{
    		int num=City.instance().getBuildingNumByType(k);
    		if(num>0){
    			newQtyMap.put(k,v/num);
    		}
    	});
    	totalBrandQualityMap.put(Gs.Eva.Btype.Brand_VALUE, newBrandMap);
    	totalBrandQualityMap.put(Gs.Eva.Btype.Quality_VALUE, newQtyMap);
    	return totalBrandQualityMap;
    }
    public double getValFromMap(Map<Integer,Double> map,int type){
    	return ((map!=null&&map.size()>0&&map.get(type)!=null)?map.get(type):0);
    }
    //Get brand information based on building type
    public List<Gs.MyBrands.Brand> getBrandByType(int type,UUID pid){
        List<Gs.MyBrands.Brand> brands = new ArrayList<>();
        MetaData.getBuildingTech(type).forEach(itemId->{
            Gs.MyBrands.Brand.Builder band = Gs.MyBrands.Brand.newBuilder();
            band.setItemId(itemId).setPId(Util.toByteString(pid));
            BrandManager.BrandInfo binfo = BrandManager.instance().getBrand(pid,itemId);
            if(binfo.hasBrandName()){
                band.setBrandName(binfo.getBrandName());
            }
            EvaManager.getInstance().getEva(pid,itemId).forEach(eva -> {
                band.addEva(eva.toProto());
            });
            //optimization
           /* GameDb.getEvaInfoList(pid, itemId).forEach(eva -> {
                band.addEva(eva.toProto());
            });*/
            brands.add(band.build());
        });
        return brands;
    }
    public List<BrandInfo> getAllBrandInfoByItem(int item){
        ArrayList<BrandInfo> infos = new ArrayList<>();
        allBrandInfo.values().forEach(b->{
            if(b.key.mid==item)
                infos.add(b);
        });
        return infos;
    }

    public boolean brandIsExist(UUID pid, int typeId){//Check if the brand information exists
        BrandKey bk = new BrandKey(pid,typeId);
        return null==allBrandInfo.get(bk)?false:true;
    }
}
