package Game;

import Game.Timers.PeriodicTimer;
import gs.Gs;
import org.apache.log4j.Logger;
import org.hibernate.annotations.Cascade;

import javax.persistence.*;
import java.io.Serializable;
import java.util.*;

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
    @Entity
    public static final class BrandInfo {

        public BrandInfo(BrandKey key) {
            this.key = key;
        }

        protected BrandInfo() {}

        @EmbeddedId
        BrandKey key;
        int v;
    }
    public void update(long diffNano) {
        if(dbSaveTimer.update(diffNano))
            GameDb.saveOrUpdate(this);
    }
    @Transient
    private PeriodicTimer dbSaveTimer = new PeriodicTimer(2*60*1000);
    public void update(UUID playerId, int mid, int add) {
        BrandInfo i = allBrandInfo.computeIfAbsent(new BrandKey(playerId, mid), k->new BrandInfo(k));
        i.v += add;
        refineCache(i, add);
    }
    public static final class BuildingRatio {
        double apartment = 1.d;
        double publicFacility = 1.d;
        double retail = 1.d;

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
        return allGood.get(mid);
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
}
