package Game;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import java.util.*;
import java.util.concurrent.ExecutionException;

public class TalentManager {
    private static TalentManager instance = new TalentManager();
    public static TalentManager instance() {
        return instance;
    }
//    public void unload(Collection<UUID> talentIds) {
//        ArrayList<Talent> needToEvict = new ArrayList<>();
//        talentCache.asMap().values().forEach(t->{
//            if(talentIds.contains(t.id()) && t.isFree())
//                needToEvict.add(t);
//        });
//        GameDb.evict(needToEvict);
//    }
    public void del(Talent t) {
        talentCache.invalidate(t.id());
        GameDb.delete(t);
    }
    public void add(Talent t) {
        talentCache.put(t.id(), t);
        GameDb.saveOrUpdate(t);
    }

    private static LoadingCache<UUID, Talent> talentCache = CacheBuilder.newBuilder()
            .concurrencyLevel(1)
            .weakValues()
            .build(
                    new CacheLoader<UUID, Talent>() {
                        @Override
                        public Talent load(UUID key) {
                            return GameDb.getTalent(key);
                        }
                        @Override
                        public Map<UUID, Talent> loadAll(Iterable<? extends UUID> keys) {
                            Collection<UUID> ids = new ArrayList<>();
                            keys.forEach(ids::add);
                            Map<UUID, Talent> res = new HashMap<>();
                            List<Talent> ts = GameDb.getTalent(ids);
                            ts.forEach(t->res.put(t.id(), t));
                            return res;
                        }
                    });

    public boolean hasTalent(UUID playerId, UUID talentId) {
        Talent t = this.get(talentId);
        if(t == null || !t.getOwnerId().equals(playerId))
            return false;
        return true;
    }

    public Talent get(UUID talentId) {
        return talentCache.getUnchecked(talentId);
    }
    public Collection<UUID> get(Collection<UUID> talentIds) throws ExecutionException {
        return talentCache.getAll(talentIds).keySet();
    }

    public Collection<UUID> getTalentIdsByBuildingId(UUID buildingId) {
        Collection<UUID> res = new ArrayList<>();
        Collection<Talent> ts = GameDb.getTalentByBuildingId(buildingId);
        ts.forEach(t->{
            if(talentCache.getIfPresent(t.id()) == null) {
                talentCache.put(t.id(), t);
            }
            res.add(t.id());
        });
        return res;
    }
    public Collection<UUID> getTalentIdsByPlayerId(UUID playerId) {
        Collection<UUID> res = new ArrayList<>();
        Collection<Talent> ts = GameDb.getTalentByPlayerId(playerId);
        ts.forEach(t->{
            if(talentCache.getIfPresent(t.id()) == null) {
                talentCache.put(t.id(), t);
            }
            res.add(t.id());
        });
        return res;
    }
}
