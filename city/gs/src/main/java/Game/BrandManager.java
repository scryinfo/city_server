package Game;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.persistence.*;

import org.apache.log4j.Logger;
import org.hibernate.annotations.Cascade;

import Game.Eva.Eva;
import Game.Eva.EvaManager;
import Game.League.BrandLeague;
import Game.League.LeagueManager;
import Game.Meta.MetaBuilding;
import Game.Meta.MetaData;
import Game.Meta.MetaGood;
import Game.Meta.MetaItem;
import Game.Timers.PeriodicTimer;
import gs.Gs;

@Entity
public class BrandManager {
    private static BrandManager instance;
    public static BrandManager instance() {
        return instance;
    }
    public static final int ID = 0;
    private static final Logger logger = Logger.getLogger(BrandManager.class);
    protected BrandManager() {}
    public static void init() {
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

        //记录名字修改的时间戳，与当前时间大于7天才可以修改，用于防止抢注的情况
        //鉴于每个玩家的BrandKey是有限的所以暂时不用处理这个数据，有必要的时候在实现
        /*
        数量没有限制才会导致抢注这种情况。比如100种商品，那么每个玩家顶多100多个BrandKey,
        那么应该不会发生抢注的情况；况且玩家频繁修改自己的品牌名字，实际上会损害
        自己的品牌认知度；如果是恶意针对对其它竞争玩家，那么我怎么知道人家要取啥名名字？
        所以限制抢注的这个需求其实是伪需求。
        */
        Long nameChangedTs;

        public BrandInfo(BrandKey key, String newBrandName) {
            this.key = key;
            brandName = new BrandName(newBrandName);
        }
        public BrandInfo(BrandKey key) {
            this.key = key;
        }

        protected BrandInfo() {}


        public BrandName getBrandName() {
            return brandName;
        }
        public void setBrandName(BrandName brandName) {

            this.brandName = brandName;
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
        i.v += add;
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

    //添加品牌，如果已经有使用该BrandKey的品牌了，返回false
    //现在的规则是，BrandKey 只增不减，比如1万个玩家200种商品，那么最多就是200万个BrandKey
    //服务器默认是只产生品牌key，不产生品牌名字的，客户端拿到品牌key自己生成品牌默认品牌名字
    public boolean addBrand(UUID pid, int typeId){
        BrandKey bk = new BrandKey(pid,typeId);
        BrandInfo bInfo = allBrandInfo.get(bk);
        if(bInfo == null){
            bInfo = new BrandInfo(bk);
            allBrandInfo.put(bk,bInfo);
            GameDb.saveOrUpdate(this);
            return true;
        }
        return  false;
    }

    public BrandInfo getBrand(UUID pid, int typeId){
        BrandKey bk = new BrandKey(pid,typeId);
        return allBrandInfo.get(bk);
    }

    //品牌名字是可以改变的，但要保证传入的validNewName是唯一性的
    public boolean changeBrandName(UUID pid, int typeId, String validNewName){
        BrandKey bk = new BrandKey(pid,typeId);
        BrandInfo bInfo = allBrandInfo.get(bk);
        if(bInfo == null){
            return false;
        }
        //如果新名字是使用中的名字，那么操作失败，返回false
        if(GameDb.brandNameIsInUsing(validNewName)){
            return false;
        }
        //如果名字可用
        bInfo.setBrandName(new BrandName(validNewName));
        allBrandInfo.put(bk,bInfo);
        GameDb.saveOrUpdate(this);
        return  true;
    }

    @Transient
    private Map<Integer,Map<Integer,Double>> totalBrandQualityMap=new HashMap<Integer,Map<Integer,Double>>();
	
    public void getBuildingBrandOrQuality(Building b,Map<Integer,Double> brandMap,Map<Integer,Double> qtyMap){
    	UUID playerId=b.ownerId();
    	//住宅和零售店的techId是13和14
    	BrandLeague bl=LeagueManager.getInstance().getBrandLeague(b.id(), b.type());
    	if(bl!=null){//优先查询加盟玩家技术
    		playerId=bl.getPlayerId();
    	}
    	int buildingBrand = BrandManager.instance().getBuilding(playerId, b.type());
    	Eva brandEva=EvaManager.getInstance().getEva(playerId, b.type(), Gs.Eva.Btype.Brand_VALUE);
    	Eva qualityEva=EvaManager.getInstance().getEva(playerId, b.type(), Gs.Eva.Btype.Quality_VALUE);
    	
		brandMap.put(b.type(), getValFromMap(brandMap,b.type())+new Double(buildingBrand*(1+EvaManager.getInstance().computePercent(brandEva))));
		brandMap.put(Gs.ScoreType.BasicBrand_VALUE, new Double(buildingBrand));
		brandMap.put(Gs.ScoreType.AddBrand_VALUE, EvaManager.getInstance().computePercent(brandEva));
		
		qtyMap.put(b.type(), getValFromMap(qtyMap,b.type())+new Double(b.quality()*(1+EvaManager.getInstance().computePercent(qualityEva))));
		qtyMap.put(Gs.ScoreType.BasicQuality_VALUE, new Double(b.quality()));
		qtyMap.put(Gs.ScoreType.AddQuality_VALUE, EvaManager.getInstance().computePercent(qualityEva));
    }
    
    public void getAllBuildingBrandOrQuality(){
    	Map<Integer,Double> brandMap=new HashMap<Integer,Double>();
    	Map<Integer,Double> qtyMap=new HashMap<Integer,Double>();
    	City.instance().forEachBuilding((Building b)->{
    		if(b.type()==MetaBuilding.APARTMENT||b.type()==MetaBuilding.RETAIL){
    			getBuildingBrandOrQuality(b,brandMap,qtyMap);
    		}
    	});
    	totalBrandQualityMap.put(Gs.Eva.Btype.Brand_VALUE, brandMap);
    	totalBrandQualityMap.put(Gs.Eva.Btype.Quality_VALUE, qtyMap);
    }
    
    public Map<Integer,Map<Integer,Double>> getTotalBrandQualityMap(){
    	return totalBrandQualityMap;
    }
    public double getValFromMap(Map<Integer,Double> map,int type){
    	return ((map!=null&&map.size()>0&&map.get(type)!=null)?map.get(type):0);
    }
}
