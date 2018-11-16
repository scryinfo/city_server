package Game;

import Game.Timers.PeriodicTimer;
import Shared.Util;
import com.google.protobuf.InvalidProtocolBufferException;
import gs.Gs;
import gscode.GsCode;
import org.bson.types.ObjectId;
import org.hibernate.annotations.Cascade;

import javax.persistence.*;
import java.util.*;

@Entity
public abstract class FactoryBase extends Building implements IStorage, IShelf {
    public FactoryBase(MetaFactoryBase meta, Coordinate pos, UUID ownerId) {
        super(meta, pos, ownerId);
        this.meta = meta;
        this.store = new Storage(meta.storeCapacity);
        this.shelf = new Shelf(meta.shelfCapacity);
    }
    private static final int DB_UPDATE_INTERVAL_MS = 30000;
    protected FactoryBase() {
    }

    public abstract LineBase addLine(MetaItem m, int workerNum, int targetNum);

    @Override
    public boolean reserve(MetaItem m, int n) {
        return store.reserve(m, n);
    }

    @Override
    public boolean lock(ItemKey m, int n) {
        return store.lock(m, n);
    }

    @Override
    public boolean unLock(ItemKey m, int n) {
        return store.unLock(m, n);
    }

    @Override
    public void consumeLock(ItemKey m, int n) {
        store.consumeLock(m, n);
    }

    @Override
    public void consumeReserve(ItemKey m, int n) {
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

    // random delay avoid all factory update itself at same time point
    @Transient
    protected PeriodicTimer dbTimer = new PeriodicTimer(DB_UPDATE_INTERVAL_MS, (int) (Math.random()*DB_UPDATE_INTERVAL_MS));

    @OneToOne(cascade=CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "store_id")
    protected Storage store;

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
                this.store.offset(l.newItemKey(ownerId(), 0), add);
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
    protected abstract boolean shelfAddable(ItemKey k);
    @Override
    public Gs.Shelf.Content addshelf(Item mi, int price) {
        if(!shelfAddable(mi.key) || !this.store.lock(mi.key, mi.n))
            return null;
        return this.shelf.add(mi, price);
    }

    @Override
    public boolean delshelf(ItemKey id, int n) {
        if(this.shelf.del(id, n)) {
            this.store.unLock(id, n);
            return true;
        }
        return false;
    }

    @Override
    public boolean setPrice(ItemKey id, int price) {
        Shelf.Content i = this.shelf.getContent(id);
        if(i == null)
            return false;
        i.price = price;
        return true;
    }

    @Override
    public Shelf.Content getContent(ItemKey id) {
        return this.shelf.getContent(id);
    }
}
