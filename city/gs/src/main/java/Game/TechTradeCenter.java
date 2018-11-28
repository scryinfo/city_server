package Game;

import Shared.Util;
import gs.Gs;
import org.apache.log4j.Logger;
import org.hibernate.annotations.Cascade;

import javax.persistence.*;
import java.util.*;

@Entity
public class TechTradeCenter {
    private static TechTradeCenter instance;
    public static TechTradeCenter instance() {
        return instance;
    }
    public static final int ID = 0;
    private static final Logger logger = Logger.getLogger(TechTradeCenter.class);
    protected TechTradeCenter() {}
    public static void init() {
        GameDb.initTechTradeCenter();
        instance = GameDb.getTechTradeCenter();
        instance.buildCache();
    }
    @Id
    private final int id = ID;
    public boolean add(UUID id, MetaGood mi, int lv, int price) {
        if (has(id, mi))
            return false;
        Sell sell = new Sell(id, mi, lv, price);
        allSelling.put(sell.id, sell);
        setSummary(sell);
        return true;
    }

    private boolean has(UUID id, MetaItem mi) {
        for (Sell s : allSelling.values()) {
            if(s.ownerId.equals(id) && s.metaId == mi.id)
                return true;
        }
        return false;
    }

    public boolean add(UUID id, MetaMaterial mi, int price) {
        if (has(id, mi))
            return false;
        Sell sell = new Sell(id, mi, 0, price);
        allSelling.put(sell.id, sell);
        setSummary(sell);
        return true;
    }
    public void del(UUID playerId, UUID sellId) {
        Sell s = allSelling.get(sellId);
        if(s != null && s.ownerId.equals(playerId)) {
            allSelling.remove(sellId);
            cache.get(s.metaId).sellId.remove(sellId);
        }
    }
    public Sell get(UUID id) {
        return allSelling.get(id);
    }
    public Gs.TechTradeSummary getSummary() {
        Gs.TechTradeSummary.Builder builder = Gs.TechTradeSummary.newBuilder();
        cache.values().forEach(s->{
            TechInfo i = techInfo.get(s.itemId);
            builder.addInfo(Gs.TechTradeSummary.Info.newBuilder()
                    .setMetaId(s.itemId)
                    .setOwnerNum(i.owners)
                    .setTopLv(i.topLv));
        });
        return builder.build();
    }
    public Gs.TechTradeDetail getDetail(int metaId) {
        Gs.TechTradeDetail.Builder builder = Gs.TechTradeDetail.newBuilder();
        Summary s = cache.get(metaId);
        if(s != null) {
            for (UUID id : s.sellId) {
                Sell sell = allSelling.get(id);
                builder.addInfo(Gs.TechTradeDetail.Info.newBuilder()
                    .setId(Util.toByteString(id))
                    .setLv(sell.lv)
                    .setPrice(sell.price)
                    .setOwnerId(Util.toByteString(sell.ownerId))
                );
            }
        }
        return builder.build();
    }
    @Entity
    public static final class Sell {
        public Sell(UUID ownerId, MetaItem meta, int lv, int price) {
            this.ownerId = ownerId;
            this.metaId = meta.id;
            this.lv = lv;
            this.price = price;
        }

        @Id
        UUID id = UUID.randomUUID();
        UUID ownerId;
        int metaId;
        int lv;
        int price;

        protected Sell() {}
    }


    private static class Summary {
        public Summary(int itemId) {
            this.itemId = itemId;
        }

        int itemId;
        Set<UUID> sellId = new HashSet<>();
    }

    private void buildCache() {
        allSelling.values().forEach(s->{
            setSummary(s);
        });
    }

    private void setSummary(Sell s) {
        cache.computeIfAbsent(s.metaId, k->new Summary(s.metaId)).sellId.add(s.id);
    }

    @Transient
    private Map<Integer, Summary> cache = new HashMap<>();

    @OneToMany(fetch = FetchType.EAGER)
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    @MapKey(name = "id")
    @JoinColumn(name = "tech_trade_center")
    private Map<UUID, Sell> allSelling = new HashMap<>();

    public void techCompleteAction(int metaId, int lv) {
        TechInfo i = techInfo.get(metaId);
        if(i == null) {
            techInfo.put(metaId, new TechInfo(1, lv));
        }
        else {
            i.owners++;
            if(i.topLv < lv) {
                i.topLv = lv;
            }
        }
    }

    @Embeddable
    protected static class TechInfo {
        public TechInfo(int owners, int toplv) {
            this.owners = owners;
            this.topLv = toplv;
        }

        int owners;
        int topLv;

        protected TechInfo() {
        }
    }
    @ElementCollection(fetch = FetchType.EAGER)
    @JoinColumn(name = "tech_trade_center")
    @MapKeyColumn(name = "item_meta_id")
    private Map<Integer, TechInfo> techInfo = new HashMap<>();
}
