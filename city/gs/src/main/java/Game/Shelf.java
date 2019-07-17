package Game;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    public List<SellInfo> getSellInfo(int metaId) {
        return slots.entrySet().stream().filter(e->e.getKey().meta.id==metaId).map(e->{
            SellInfo s = new SellInfo();
            s.producerId = e.getKey().producerId;
            s.qty = e.getKey().qty;
            s.price = e.getValue().price;
            s.meta = e.getKey().meta;
            return s;
        }).collect(Collectors.toList());
    }
    public Collection<Integer> getMetaIds(MetaGood.Type type, int lux) {
        return this.slots.entrySet().stream().filter(e-> {
            if(e.getKey().meta instanceof MetaGood) {
                MetaGood mg = (MetaGood)e.getKey().meta;
                return mg.lux == lux && MetaGood.goodType(mg.id) == type;
            }
            return false;
        }).map(e->e.getKey().meta.id).collect(Collectors.toList());
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
        if(i.n == 0 && i.autoReplenish == false)
            slots.remove(k);
        return true;
    }
    public boolean full() {
        return slots.size() >= capacity;
    }
    public boolean has(int mId) {
        return this.slots.keySet().stream().anyMatch(k->k.meta.id == mId);
    }
    public Gs.Shelf toProto() {
        Gs.Shelf.Builder builder = Gs.Shelf.newBuilder();
        slots.forEach((k,v)->builder.addGood(toProto(k, v)));
        return builder.build();
    }
    public int getSaleNum(int itemid) {
        int res = 0;
        for (Map.Entry<ItemKey, Content> e : slots.entrySet()) {
            if(e.getKey().meta.id == itemid)
                res += e.getValue().n;
        }
        return res;
    }

    public void clearData(){//清除货架数据
        this.slots.clear();
    }

    public Integer getTotalContentNum(){
        int sum = this.slots.values().stream().mapToInt(c -> c.n).sum();
        return sum;
    }
}
