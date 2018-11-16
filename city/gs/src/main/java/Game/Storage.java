package Game;

import gs.Gs;
import org.hibernate.annotations.Cascade;

import javax.persistence.*;
import java.util.*;

@Entity
public class Storage implements IStorage {
    @Id
    private UUID id = UUID.randomUUID();
    public Storage(int capacity) {
        this.capacity = capacity;
    }

    public Storage() {
    }
    //@Id
   // @Column(name = "id", updatable = false, nullable = false)
   // private UUID id = UUID.randomUUID();  // because we map this class to entity rather than Embeddable, so must have a id
//    private byte[] binary() {
//        Db.Store.Builder builder = Db.Store.newBuilder();
//        this.inHand.forEach((k, v)->builder.addExisting(Db.Store.Cargo.newBuilder().setId(k.id).setN(v)));
//        this.reserved.forEach((k, v)->builder.addReserved(Db.Store.Cargo.newBuilder().setId(k.id).setN(v)));
//        this.locked.forEach((k, v)->builder.addLocked(Db.Store.Cargo.newBuilder().setId(k.id).setN(v)));
//        return builder.build().toByteArray();
//    }
//    public Collection<Gs.IntNum> toProto() {
//        Collection<Gs.IntNum> res = new ArrayList<>();
//        this.inHand.forEach((k, v)->{
//            res.add(Gs.IntNum.newBuilder().setId(k.id).setNum(v).build());
//        });
//        return res;
//    }
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
    public boolean offset(ItemKey item, final int n) {
        if(n == 0)
            return true;
        else if(n > 0) {
            if(item.meta.size*n > availableSize())
                return false;
            this.inHand.put(item, this.inHand.getOrDefault(item, 0)+n);
        }
        else if(n < 0) {
            Integer c = this.inHand.get(item);
            if(c == null || c < -n)
                return false;
            this.inHand.put(item, c+n);
        }
        //this._d.dirty();
        return true;
    }
    @Transient
    private int capacity;
//    @ElementCollection(fetch = FetchType.EAGER)
//    @Convert(converter = MetaItem.Converter.class, attributeName = "key")
//    private Map<MetaItem, Integer> inHand = new HashMap<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    private Map<ItemKey, Integer> inHand = new HashMap<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @Convert(converter = MetaItem.Converter.class, attributeName = "key")
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    private Map<MetaItem, Integer> reserved = new HashMap<>();


//    @ElementCollection(fetch = FetchType.EAGER)
//    @Convert(converter = MetaItem.Converter.class, attributeName = "key")
//    private Map<MetaItem, Integer> locked = new HashMap<>();
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
        locked.put(m, reserved.getOrDefault(m, 0) + n);
        return true;
    }

    @Override
    public boolean unLock(ItemKey m, int n) {
        Integer i = this.locked.get(m);
        if(i == null || i < n)
            return false;
        this.locked.put(m, i-n);
        this.inHand.put(m, this.inHand.getOrDefault(m, 0) + n);
        return true;
    }

    @Override
    public void consumeLock(ItemKey m, int n) {
        locked.put(m, locked.get(m) - n);
        inHand.put(m, inHand.get(m) - n);
        if(locked.get(m) == 0)
            locked.remove(m);
        if(inHand.get(m) == 0)
            inHand.remove(m);
    }

    @Override
    public void consumeReserve(ItemKey m, int n) {
        reserved.put(m.meta, reserved.get(m.meta) - n);
        inHand.put(m, inHand.getOrDefault(m, 0) + n);
        if(reserved.get(m.meta) == 0)
            reserved.remove(m.meta);
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

    @Transient
    Set<UUID> order = new HashSet<>();
//    @Embeddable
//    protected static class _D { // private will cause JPA meta class generate fail
//        @Column(name = "storageBin")
//        private byte[] binary;
//
//        void dirty() {
//            binary = null;
//        }
//    }
//    @Embedded
//    private final _D _d = new _D();
//    @PrePersist
//    @PreUpdate
//    protected void _2() {
//        this._d.binary = this.binary();
//    }
//    @PostLoad
//    protected void _1() throws InvalidProtocolBufferException {
//        Db.Store store = Db.Store.parseFrom(this._d.binary);
//        store.getExistingList().forEach(c->this.inHand.put(MetaData.getItem(c.getId()), c.getN()));
//        store.getReservedList().forEach(c->this.reserved.put(MetaData.getItem(c.getId()), c.getN()));
//        store.getLockedList().forEach(c->this.locked.put(MetaData.getItem(c.getId()), c.getN()));
//    }
    public int usedSize() {
        return inHand.entrySet().stream().mapToInt(e->e.getKey().meta.size*e.getValue()).sum() + reserved.entrySet().stream().mapToInt(e->e.getKey().size*e.getValue()).sum();
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
    public boolean has(ItemKey m, int n) {
        return inHand.get(m)==null?false:inHand.get(m) >= n;
    }
    public boolean full() {
        return capacity == usedSize();
    }
}
