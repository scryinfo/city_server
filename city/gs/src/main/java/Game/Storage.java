package Game;

import DB.Db;
import com.google.protobuf.InvalidProtocolBufferException;
import gs.Gs;

import javax.persistence.*;
import java.util.*;

@Embeddable
public class Storage implements Storagable {
    public Storage(int capacity) {
        this.capacity = capacity;
    }

    public Storage() {
    }

    private byte[] binary() {
        Db.Store.Builder builder = Db.Store.newBuilder();
        this.inHand.forEach((k, v)->builder.addExisting(Db.Store.Cargo.newBuilder().setId(k.id).setN(v)));
        this.reserved.forEach((k, v)->builder.addReserved(Db.Store.Cargo.newBuilder().setId(k.id).setN(v)));
        this.locked.forEach((k, v)->builder.addLocked(Db.Store.Cargo.newBuilder().setId(k.id).setN(v)));
        return builder.build().toByteArray();
    }
    public Collection<Gs.IntNum> toProto() {
        Collection<Gs.IntNum> res = new ArrayList<>();
        this.inHand.forEach((k, v)->{
            res.add(Gs.IntNum.newBuilder().setId(k.id).setNum(v).build());
        });
        return res;
    }
    public boolean offset(MetaItem item, final int n) {
        if(n == 0)
            return true;
        else if(n > 0) {
            if(item.size*n > availableSize())
                return false;
            this.inHand.put(item, this.inHand.getOrDefault(item, 0)+n);
        }
        else if(n < 0) {
            Integer c = this.inHand.get(item);
            if(c == null || c < -n)
                return false;
            this.inHand.put(item, c+n);
        }
        this._d.dirty();
        return true;
    }
    @Transient
    private int capacity;
    @Transient
    private Map<MetaItem, Integer> inHand = new HashMap<>();
    @Transient
    private Map<MetaItem, Integer> reserved = new HashMap<>();
    @Transient
    private Map<MetaItem, Integer> locked = new HashMap<>();

    void setCap(int capacity) {
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
    public boolean lock(MetaItem m, int n) {
        if(!has(m, n))
            return false;
        locked.put(m, reserved.getOrDefault(m, 0) + n);
        return true;
    }

    @Override
    public void consumeLock(MetaItem m, int n) {
        locked.put(m, locked.get(m) - n);
        inHand.put(m, inHand.get(m) - n);
        if(locked.get(m) == 0)
            locked.remove(m);
        if(inHand.get(m) == 0)
            inHand.remove(m);
    }

    @Override
    public void consumeReserve(MetaItem m, int n) {
        reserved.put(m, reserved.get(m) - n);
        inHand.put(m, inHand.getOrDefault(m, 0) + n);
        if(reserved.get(m) == 0)
            reserved.remove(m);
    }

    @Override
    public void markOrder(UUID orderId) {
        order.add(orderId);
    }

    @Override
    public void clearOrder(UUID orderId) {
        order.remove(orderId);
    }
    @Transient
    Set<UUID> order = new HashSet<>();
    @Embeddable
    protected static class _D { // private will cause JPA meta class generate fail
        @Column(name = "storageBin")
        private byte[] binary;

        void dirty() {
            binary = null;
        }
    }
    @Embedded
    private final _D _d = new _D();
    @PrePersist
    @PreUpdate
    protected void _2() {
        this._d.binary = this.binary();
    }
    @PostLoad
    protected void _1() throws InvalidProtocolBufferException {
        Db.Store store = Db.Store.parseFrom(this._d.binary);
        store.getExistingList().forEach(c->this.inHand.put(MetaData.getItem(c.getId()), c.getN()));
        store.getReservedList().forEach(c->this.reserved.put(MetaData.getItem(c.getId()), c.getN()));
        store.getLockedList().forEach(c->this.locked.put(MetaData.getItem(c.getId()), c.getN()));
    }
    public int usedSize() {
        return inHand.entrySet().stream().mapToInt(e->e.getKey().size*e.getValue()).sum() + reserved.entrySet().stream().mapToInt(e->e.getKey().size*e.getValue()).sum();
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
    public boolean has(MetaItem m, int n) {
        return inHand.get(m)==null?false:inHand.get(m) >= n;
    }
    public boolean full() {
        return capacity == usedSize();
    }
}
