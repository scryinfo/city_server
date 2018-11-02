package Game;

import Game.Timers.PeriodicTimer;
import Shared.Util;
import com.google.protobuf.InvalidProtocolBufferException;
import gs.Gs;
import gscode.GsCode;
import org.bson.types.ObjectId;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.MapKeyType;

import javax.persistence.*;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;

@Entity
public abstract class FactoryBase extends Building implements IStorage, IShelf {
    public FactoryBase(MetaFactoryBase meta, Coord pos, UUID ownerId) {
        super(meta, pos, ownerId);
        this.meta = meta;
        this.store = new Storage(meta.storeCapacity);
        this.shelf = new Shelf(meta.shelfCapacity);
    }

    protected FactoryBase() {
    }

    public abstract LineBase addLine(MetaItem m);

    @Override
    public boolean reserve(MetaItem m, int n) {
        return store.reserve(m, n);
    }

    @Override
    public boolean lock(MetaItem m, int n) {
        return store.lock(m, n);
    }

    @Override
    public boolean unLock(MetaItem m, int n) {
        return store.unLock(m, n);
    }

    @Override
    public void consumeLock(MetaItem m, int n) {
        store.consumeLock(m, n);
    }

    @Override
    public void consumeReserve(MetaItem m, int n) {
        store.consumeReserve(m, n);
    }

    @Override
    public void markOrder(UUID orderId) {
        store.markOrder(orderId);
    }

    @Override
    public void clearOrder(UUID orderId) {
        store.clearOrder(orderId);
    }

    @Transient
    protected PeriodicTimer dbTimer = new PeriodicTimer(30000, 30000);

    //@OneToOne
    //@JoinColumn(name="STORE_ID", unique=true, nullable=false, updatable=false)
    @Embedded
    protected Storage store;

    //@OneToOne
    //@JoinColumn(name="SHELF_ID", unique=true, nullable=false, updatable=false)
    @Embedded
    protected Shelf shelf;

    @OneToMany(fetch = FetchType.EAGER)
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    @MapKeyColumn(name = "line_id")
    Map<UUID, LineBase> lines = new HashMap<>();

    public boolean lineFull() {
        return lines.size() >= meta.lineNum;
    }
    public int freeWorkerNum() {
        return this.meta.workerNum - lines.values().stream().mapToInt(l -> l.workerNum).reduce(0, Integer::sum);
    }

    protected void _update(long diffNano) {
        this.lines.values().forEach(l -> {
            int add = l.update(diffNano);
            if(add > 0) {
                this.store.offset(l.item, add);
                Gs.LineInfo i = Gs.LineInfo.newBuilder()
                        .setId(Util.toByteString(l.id))
                        .setNowCount(l.count)
                        .build();
                GameServer.sendTo(this.detailWatchers, Shared.Package.create(GsCode.OpCode.lineChangeInform_VALUE, i));
            }
        });

        if(this.dbTimer.update(diffNano)) {
            GameDb.saveOrUpdate(this); // this will not ill-form other transaction due to all action are serialized
        }
    }
    @Transient
    private MetaFactoryBase meta;

    public boolean changeLine(ObjectId lineId, OptionalInt targetNum, OptionalInt workerNum) {
        LineBase line = this.lines.get(lineId);
        if(line == null)
            return false;
        if(targetNum.isPresent()) {
            int t = targetNum.getAsInt();
            if(t < 0 || t > this.store.availableSize())
                return false;
            line.targetNum = t;
        }
        else if(workerNum.isPresent()) {
            int w = workerNum.getAsInt();
            if(w < 0 || (w > line.workerNum && this.freeWorkerNum() < w - line.workerNum))
                return false;
            line.workerNum = w;
        }
        return true;
    }

    @PostLoad
    protected void _1() throws InvalidProtocolBufferException {
        this.meta = (MetaFactoryBase) this.metaBuilding;
        this.store.setCapacity(meta.storeCapacity); // this is hibernate design problem...
        this.shelf.setCapacity(meta.shelfCapacity);
    }

    @Override
    public UUID addshelf(MetaItem mi, int num, int price) {
        if(!this.store.lock(mi, num))
            return null;
        return this.shelf.add(mi, num, price);
    }

    @Override
    public boolean delshelf(UUID id) {
        Shelf.ItemInfo i = this.shelf.getContent(id);
        if(i == null)
            return false;
        this.store.unLock(i.item, i.n);
        this.shelf.del(id);
        return true;
    }

    @Override
    public boolean setNum(UUID id, int num) {
        Shelf.ItemInfo i = this.shelf.getContent(id);
        if(i == null)
            return false;
        int delta = num - i.n;
        boolean ok = false;
        if(delta > 0)
            ok = this.store.lock(i.item, delta);
        else if(delta < 0)
            ok = this.store.unLock(i.item, delta);

        if(ok) {
            i.n += delta;
        }
        return ok;
    }

    @Override
    public Shelf.ItemInfo getContent(UUID id) {
        return this.shelf.getContent(id);
    }
}
