package Game;

import Game.Meta.MetaItem;
import Game.Meta.MetaMaterial;
import gs.Gs;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.SelectBeforeUpdate;

import javax.persistence.*;
import java.util.*;

@Entity
@SelectBeforeUpdate(false)
public class Storage implements IStorage {
    @Id
    @GeneratedValue
    private UUID id;
    public Storage(int capacity) {
        this.capacity = capacity;
    }

    public Storage() {
    }
    public Gs.Store toProto() {
        Gs.Store.Builder builder = Gs.Store.newBuilder();
        this.inHand.forEach((k, v)->{
            builder.addInHand(Gs.Item.newBuilder().setKey(k.toProto()).setN(v));
        });
        this.reserved.forEach((k, v)->{
            builder.addReserved(Gs.IntNum.newBuilder().setId(k.id).setNum(v));
        });
        this.locked.forEach((k, v)->{
            builder.addLocked(Gs.Item.newBuilder().setKey(k.toProto()).setN(v));
        });
        return builder.build();
    }
    public boolean offset(MetaItem item, int n) {
        if(n == 0)
            return true;
        else if(n > 0) {
            if(item.size*n > availableSize())
                return false;
            if(!(item instanceof MetaMaterial))
                throw new IllegalArgumentException();
            ItemKey k = new ItemKey(item);
            this.inHand.put(k, this.inHand.getOrDefault(k, 0)+n);
        }
        else if(n < 0) {
            if(this.availableQuantity(item) < -n)
                return false;
            int left = -n;
            Iterator<Map.Entry<ItemKey, Integer>> iterator = this.inHand.entrySet().iterator();
            while(iterator.hasNext()) {
                Map.Entry<ItemKey, Integer> e = iterator.next();
                if(e.getKey().meta == item) {
                    if(e.getValue() < left) {
                        left -= e.getValue();
                        iterator.remove();
                    }
                    else {
                        e.setValue(e.getValue() - left);
                        if(e.getValue() == 0)
                            iterator.remove();
                        break;
                    }
                }
            }
        }
        return true;
    }
    public boolean offset(ItemKey item, final int n) {
        if(n == 0)
            return true;
        else if(n > 0) {
            if(item.meta.size*n > availableSize())
                return false;
            this.inHand.put(item, this.inHand.getOrDefault(item, 0)+n);
            inHandPrice.computeIfAbsent(item, k->new AvgPrice()).update(0, n);
        }
        else if(n < 0) {
            Integer c = this.inHand.get(item);
            if(c == null || c < -n)
                return false;
            this.inHand.put(item, c+n);
            if(this.inHand.get(item) == 0)
                this.inHand.remove(item);
        }
        return true;
    }
    @Transient
    private int capacity;

    @ElementCollection(fetch = FetchType.EAGER)
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    private Map<ItemKey, Integer> inHand = new HashMap<>();

    @Embeddable
    public static final class AvgPrice {
        long avg;
        long n;// this will be overflow, however, planner don't care then I don't care either
        void update(int price, int n) {
            avg = avg*this.n+price*n/(this.n+n);
            this.n += n;
        }
    }
    @ElementCollection(fetch = FetchType.EAGER)
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    private Map<ItemKey, AvgPrice> inHandPrice = new HashMap<>();


    @ElementCollection(fetch = FetchType.EAGER)
    @Convert(converter = MetaItem.Converter.class, attributeName = "key")
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    private Map<MetaItem, Integer> reserved = new HashMap<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    private Map<ItemKey, Integer> locked = new HashMap<>();

    void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    @Override
    public boolean reserve(MetaItem m, int n) {
        if(availableSize() < m.size*n)
            return false;
        reserved.put(m, reserved.getOrDefault(m, 0) + n);
        return true;
    }

    @Override
    public boolean lock(ItemKey m, int n) {
        if(!has(m, n))
            return false;
        locked.put(m, locked.getOrDefault(m, 0) + n);
        this.inHand.put(m, this.inHand.getOrDefault(m, 0) - n);
        if(this.inHand.get(m) == 0)
            this.inHand.remove(m);
        return true;
    }

    @Override
    public boolean unLock(ItemKey m, int n) {
        Integer i = this.locked.get(m);
        if(i == null || i < n)
            return false;
        this.locked.put(m, i-n);
        if(i == n)
            this.locked.remove(m);
        this.inHand.put(m, this.inHand.getOrDefault(m, 0) + n);
        return true;
    }

    @Override
    public AvgPrice consumeLock(ItemKey m, int n) {
        locked.put(m, locked.get(m) - n);
        if(locked.get(m) == 0)
            locked.remove(m);
        return inHandPrice.get(m);
    }

    @Override
    public void consumeReserve(ItemKey m, int n, int price) {
        reserved.put(m.meta, reserved.get(m.meta) - n);
        if(reserved.get(m.meta) == 0)
            reserved.remove(m.meta);

        inHand.put(m, inHand.getOrDefault(m, 0) + n);
        inHandPrice.computeIfAbsent(m, k->new AvgPrice()).update(price, n);
    }

    @Override
    public void markOrder(UUID orderId) {
        order.add(orderId);
    }

    @Override
    public void clearOrder(UUID orderId) {
        order.remove(orderId);
    }

    @Override
    public boolean delItem(ItemKey mi) {
        if(!this.has(mi, 1))
            return false;
        this.inHand.remove(mi);
        return true;
    }

    @Override
    public int availableQuantity(MetaItem m) {
        return inHand.entrySet().stream().filter(e->e.getKey().meta == m).mapToInt(e->e.getValue()).sum();
    }
    @Override
    public boolean has(ItemKey m, int n) {
        return inHand.get(m)==null?false:inHand.get(m) >= n;
    }
    @Transient
    Set<UUID> order = new HashSet<>();

    public int usedSize() {
        return inHand.entrySet().stream().mapToInt(e->e.getKey().meta.size*e.getValue()).sum() + locked.entrySet().stream().mapToInt(e->e.getKey().meta.size*e.getValue()).sum() + reserved.entrySet().stream().mapToInt(e->e.getKey().size*e.getValue()).sum();
    }
    public int availableSize() {
        return capacity - usedSize();
    }
    public int size(MetaItem m) {
        Integer n = inHand.get(m);
        if(n == null)
            return 0;
        return n;
    }

    public boolean full() {
        return capacity == usedSize();
    }
}
