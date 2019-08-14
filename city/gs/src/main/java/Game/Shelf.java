package Game;

import java.util.*;
import java.util.stream.Collectors;

import javax.persistence.ElementCollection;
import javax.persistence.Embeddable;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Transient;

import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.SelectBeforeUpdate;

import Game.Meta.MetaGood;
import Game.Meta.MetaItem;
import gs.Gs;

@Entity
@SelectBeforeUpdate(false)
public class Shelf {
    @Id
    @GeneratedValue
    private UUID id;
    @Transient
    private int capacity;

    public Shelf(int shelfCapacity) {
        this.capacity = shelfCapacity;
    }

    protected Shelf() {}

    void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public int size() {
        return slots.size();
    }
    Content getContent(ItemKey id) {
        return slots.get(id);
    }
    public int getContentCount(ItemKey id){
        Content ct = slots.get(id);
        return ct != null ? ct.getCount(): -1;
    }

    public Map<Item, Integer> getSaleDetail(int itemId) {
        Map<Item, Integer> res = new HashMap<>();
        this.slots.forEach((k,v)->{
            if(k.meta.id == itemId)
                res.put(new Item(k, v.n), v.price);
        });
        return res;
    }

    public final static class SellInfo {
        public UUID producerId;
        public int qty;
        public int price;
        public MetaItem meta;
    }

    public List<SellInfo> getSellInfoCache() {
        return sellInfoCache;
    }

    @Transient
    public List<SellInfo> sellInfoCache = new ArrayList<>();

    public void initSellInfoCache(){
        slots.forEach((k,v)->{
            SellInfo s = new SellInfo();
            s.producerId = k.producerId;
            s.qty = k.qty;
            s.price = v.price;
            s.meta = k.meta;
            sellInfoCache.add(s);
        });
    }

    public void updateSellInfoToCache(ItemKey k){
        //TODO 生产线发送改变时更新
    }


    public List<SellInfo> getSellInfo(int metaId) {
        return getSellInfoCache();
        /*return slots.entrySet().stream().filter(e->e.getKey().meta.id==metaId).map(e->{
            SellInfo s = new SellInfo();
            s.producerId = e.getKey().producerId;
            s.qty = e.getKey().qty;
            s.price = e.getValue().price;
            s.meta = e.getKey().meta;
            return s;
        }).collect(Collectors.toList());*/
    }

    class luxCache{
        Map<Integer,ArrayList<Integer>> _cache = new HashMap<Integer,ArrayList<Integer>>();
    }

    @Transient
    private Map<Integer,luxCache>  MetaIdsCache = new HashMap<Integer,luxCache>();
    @Transient
    private ArrayList<Integer> temp = new ArrayList<>();

    public void initMetaIdCache(){
        slots.forEach((k,v)->{
            if(k.meta instanceof MetaGood) {
                MetaGood mg = (MetaGood)k.meta;
                addMetaIdtoCache(MetaGood.goodType(mg.id),mg.lux,mg.id);
            }
        });
    }
    private void addMetaIdtoCache(MetaGood.Type type, int lux, int id){

        if(MetaIdsCache.containsKey(type.ordinal()) == false){
            MetaIdsCache.put(type.ordinal(),new luxCache());
        }
        if(MetaIdsCache.get(type.ordinal())._cache.containsKey(lux)){
            //已经有的，不管
            MetaIdsCache.get(type.ordinal())._cache.get(lux).add(id);
        }else{
            ArrayList<Integer> list = new ArrayList<>();
            list.add(id);
            MetaIdsCache.get(type.ordinal())._cache.put(lux,list);
        }
    }

    private void delMetaIdfromCache(MetaGood.Type type, int lux){
        if(MetaIdsCache.containsKey(type.ordinal()) == false){
            return;
        }
        if(MetaIdsCache.get(type.ordinal())._cache.containsKey(lux)){
            MetaIdsCache.get(type.ordinal())._cache.get(lux).remove(lux);
            if(MetaIdsCache.get(type.ordinal())._cache.get(lux).size() == 0){
                MetaIdsCache.get(type.ordinal())._cache.remove(lux);
            }
        }
    }

    public Collection<Integer> getMetaIds(MetaGood.Type type, int lux) {
        if(MetaIdsCache.containsKey(type.ordinal())){
            if(MetaIdsCache.get(type.ordinal())._cache.containsKey(lux)){
                return  MetaIdsCache.get(type.ordinal())._cache.get(lux);
            }else{
                return temp;
            }
        }else{
            return temp;
        }

        /*return this.slots.entrySet().stream().filter(e-> {
            if(e.getKey().meta instanceof MetaGood) {
                MetaGood mg = (MetaGood)e.getKey().meta;
                return mg.lux == lux && MetaGood.goodType(mg.id) == type;
            }
            return false;
        }).map(e->e.getKey().meta.id).collect(Collectors.toList());*/
        // .mapToInt(e->e.getKey().meta.id).boxed().collect(Collectors.toList());
        // .mapToInt(e->e.getKey().meta.id).toArray();
        // .mapToInt(e->e.getKey().meta.id).collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        // this is most stupid api design what I have ever seen
    }
    public Collection<ItemKey> getGoodsItemKey() {
        return this.slots.entrySet().stream().filter(e-> {
            if(e.getKey().meta instanceof MetaGood) {
              return true;
            }
            return false;
        }).map(e->e.getKey()).collect(Collectors.toList());
    }
    @Embeddable
    public static final class Content {
        public Content(int n, int price,boolean autoReplenish) {
            this.n = n;
            this.price = price;
            this.autoReplenish = autoReplenish;
        }
        public int getCount(){return  n; }
        int n;
        int price;
        boolean autoReplenish;
        protected Content() {}

        public int getN() {
            return n;
        }

        public int getPrice() {
            return price;
        }

        public boolean isAutoReplenish() {
            return autoReplenish;
        }
    }

    @ElementCollection(fetch = FetchType.EAGER)
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    private Map<ItemKey, Content> slots = new HashMap<>();

    public boolean add(Item item, int price, boolean autoReplenish) {
        if(full())
            return false;
        Content content = slots.get(item.key);
        if(content != null) {
            if(content.price != price)
                return false;
            content.n += item.n;
            content.autoReplenish = autoReplenish;
        }
        else {
            content = new Content(item.n, price, autoReplenish);
            slots.put(item.key, content);
            if(item.key.meta instanceof MetaGood) {
                MetaGood mg = (MetaGood)item.key.meta;
                addMetaIdtoCache(MetaGood.goodType(mg.id),mg.lux,mg.id);
            }
        }
        return true;
    }

    private Gs.Shelf.Content toProto(ItemKey k, Content content) {
        return Gs.Shelf.Content.newBuilder()
                .setK(k.toProto())
                .setN(content.n)
                .setPrice(content.price)
                .setAutoReplenish(content.autoReplenish)
                .build();
    }
    public boolean del(ItemKey k, int n) {
        Shelf.Content i = this.getContent(k);
        if(i == null || i.n < n)
            return false;
        i.n -= n;
        if(i.n == 0 && i.autoReplenish == false){
            slots.remove(k);
            if(k.meta instanceof MetaGood) {
                MetaGood mg = (MetaGood)k.meta;
                delMetaIdfromCache(MetaGood.goodType(mg.id),mg.lux);
            }
        }
        return true;
    }
    public boolean full() {
        return slots.size() >= capacity;
    }
    public boolean has(int mId) {
        //return this.slots.keySet().stream().anyMatch(k->k.meta.id == mId);
        return SaleNumCache.containsKey(mId);
    }
    public Gs.Shelf toProto() {
        Gs.Shelf.Builder builder = Gs.Shelf.newBuilder();
        slots.forEach((k,v)->builder.addGood(toProto(k, v)));
        return builder.build();
    }
    @Transient
    HashMap<Integer, Integer> SaleNumCache = new HashMap<Integer, Integer>();

    public void initSaleNumCache(){
        SaleNumCache.clear();
        slots.forEach((k,v)->{
           if(SaleNumCache.containsKey(k.meta.id)){
               SaleNumCache.put(k.meta.id,SaleNumCache.get(k.meta.id)+v.n);
           }else{
               SaleNumCache.put(k.meta.id,v.n);
           }
        });
    }

    public int getSaleNum(int itemid) {
        return SaleNumCache.getOrDefault(itemid,0);
        /*int res = 0;
        for (Map.Entry<ItemKey, Content> e : slots.entrySet()) {
            if(e.getKey().meta.id == itemid)
                res += e.getValue().n;
        }
        return res;*/
    }

    public void clearData(){//清除货架数据
        this.slots.clear();
    }
}
