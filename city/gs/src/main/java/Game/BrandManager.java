package Game;

import Game.Timers.PeriodicTimer;
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
        instance.sum();
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
        sum(i);
    }
    public static final class BuildingRatio {
        double apartment = 1.d;
        double publicFacility = 1.d;
        double retail = 1.d;
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
        return buildingSum.get(type);
    }
    public int getGood(int mid) {
        return goodSum.get(mid);
    }
    private void sum() {
        for(BrandInfo i : allBrandInfo.values()) {
            sum(i);
        }
    }

    private void sum(BrandInfo i) {
        if(i.key.mid < MetaBuilding.MAX_TYPE_ID) {
            int t = MetaBuilding.type(i.key.mid);
            buildingSum.put(t, buildingSum.getOrDefault(t, 0) + i.v);
        }
        else if(MetaItem.isItem(i.key.mid)) {
            goodSum.put(i.key.mid, goodSum.getOrDefault(i.key.mid, 0) + i.v);
        }
    }
    public int allBuilding() {
        return buildingSum.values().stream().reduce(0, Integer::sum);
    }
    public int allGood() {
        return goodSum.values().stream().reduce(0, Integer::sum);
    }
    @Transient  // <buildingSum type, sum value>
    private Map<Integer, Integer> buildingSum = new TreeMap<>();

    @Transient  // <goodSum meta id, sum value>
    private Map<Integer, Integer> goodSum = new HashMap<>();

    @Transient
    private Map<UUID, Map<Integer, Integer>> cache = new HashMap<>();

    @OneToMany(fetch = FetchType.EAGER)
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    @MapKey(name = "key")
    @JoinColumn(name = "brand_manager_id")
    private Map<BrandKey, BrandInfo> allBrandInfo = new HashMap<>();
}
