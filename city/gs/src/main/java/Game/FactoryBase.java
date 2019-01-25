package Game;

import Game.Meta.MetaFactoryBase;
import Game.Meta.MetaItem;
import Game.Timers.PeriodicTimer;
import Shared.Package;
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

    protected abstract LineBase addLineImpl(MetaItem m, int workerNum, int targetNum, int itemLevel);
    public LineBase addLine(MetaItem m, int workerNum, int targetNum, int itemLevel) {
        LineBase l = this.addLineImpl(m, workerNum, targetNum, itemLevel);
        if(l != null)
            this.sendToWatchers(Package.create(GsCode.OpCode.ftyLineAddInform_VALUE, Gs.FtyLineAddInform.newBuilder().setBuildingId(Util.toByteString(this.id())).setLine(l.toProto()).build()));
        return l;
    }
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
    public Storage.AvgPrice consumeLock(ItemKey m, int n) {
        return store.consumeLock(m, n);
    }

    @Override
    public void consumeReserve(ItemKey m, int n, int price) {
        store.consumeReserve(m, n, price);
    }

    @Override
    public void markOrder(UUID orderId) {
        store.markOrder(orderId);
    }

    @Override
    public void clearOrder(UUID orderId) {
        store.clearOrder(orderId);
    }

    @Override
    public boolean delItem(ItemKey k) { return this.store.delItem(k); }
    // random delay avoid all factory update itself at same time point
    @Transient
    protected PeriodicTimer dbTimer = new PeriodicTimer(DB_UPDATE_INTERVAL_MS, (int) (Math.random()*DB_UPDATE_INTERVAL_MS));

    @OneToOne(cascade=CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "store_id")
    protected Storage store;

    @OneToOne(cascade=CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "shelf_id")
    protected Shelf shelf;

    @OneToMany(fetch = FetchType.EAGER)
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    @MapKeyColumn(name = "line_id")
    Map<UUID, LineBase> lines = new HashMap<>();

    public void updateLineQuality(int metaId, int lv) {
        this.lines.values().forEach(l->{
            if(l.item.id == metaId)
                l.itemLevel = lv;
        });
    }
    public boolean lineFull() {
        return lines.size() >= meta.lineNum;
    }
    public int freeWorkerNum() {
        return this.meta.workerNum - lines.values().stream().mapToInt(l -> l.workerNum).reduce(0, Integer::sum);
    }
    protected abstract boolean consumeMaterial(LineBase line);

    protected void _update(long diffNano) {
        List<UUID> completedLines = new ArrayList<>();
        this.lines.values().forEach(l -> {
            if(l.isPause()) {
                if(l.isComplete())
                    completedLines.add(l.id);
                return;
            }
            if(l.isSuspend()) {
                assert l.left() > 0;
                if(this.store.offset(l.newItemKey(ownerId(), l.itemLevel), l.left())) {
                    l.resume();
                    broadcastLineInfo(l);
                }
            }
            else {
                if (l.materialConsumed == false)
                l.materialConsumed = this.consumeMaterial(l);
                    if (!l.materialConsumed)
                        return;
                    int add = l.update(diffNano);
                    if (add > 0) {
                        l.materialConsumed = false;
                        if (this.store.offset(l.newItemKey(ownerId(), l.itemLevel), add)) {
                            broadcastLineInfo(l);
                        } else {
                            //(加工厂/原料厂)仓库已满通知
                            MailBox.instance().sendMail(Mail.MailType.STORE_FULL.getMailType(), ownerId(), null, new UUID[]{ownerId(), this.id()}, null);

                            l.suspend(add);
                        }
                    }
                }
        });
        for (UUID id : completedLines) {
            LineBase l = this.lines.remove(id);
            //this.sendToWatchers(Package.create(GsCode.OpCode.ftyDelLine_VALUE, Gs.DelLine.newBuilder().setBuildingId(Util.toByteString(id())).setLineId(Util.toByteString(l.id)).build()));
            MailBox.instance().sendMail(Mail.MailType.PRODUCTION_LINE_COMPLETION.getMailType(), ownerId(), new int[]{metaBuilding.id}, new UUID[]{this.id()}, new int[]{l.item.id, l.targetNum});
        }
        if(this.dbTimer.update(diffNano)) {
            GameDb.saveOrUpdate(this); // this will not ill-form other transaction due to all action are serialized
        }
    }

    private void broadcastLineInfo(LineBase l) {
        Gs.LineInfo i = Gs.LineInfo.newBuilder()
                .setId(Util.toByteString(l.id))
                .setNowCount(l.count)
                .build();
        sendToWatchers(Shared.Package.create(GsCode.OpCode.ftyLineChangeInform_VALUE, i));
    }

    @Transient
    private MetaFactoryBase meta;

    public boolean changeLine(ObjectId lineId, OptionalInt targetNum, OptionalInt workerNum) {
        LineBase line = this.lines.get(lineId);
        if(line == null)
            return false;
        Gs.LineInfo.Builder builder = Gs.LineInfo.newBuilder();
        if(targetNum.isPresent()) {
            int t = targetNum.getAsInt();
            if(t < 0)
                return false;
            line.targetNum = t;
            builder.setTargetCount(t);
        }
        else if(workerNum.isPresent()) {
            int w = workerNum.getAsInt();
            if(w < 0 || (w > line.workerNum && this.freeWorkerNum() < w - line.workerNum))
                return false;
            line.workerNum = w;
            builder.setWorkerNum(w);
        }
        this.sendToWatchers(Shared.Package.create(GsCode.OpCode.ftyLineChangeInform_VALUE, builder.build()));
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
    public boolean addshelf(Item mi, int price) {
        if(!shelfAddable(mi.key) || !this.store.has(mi.key, mi.n))
            return false;
        if(this.shelf.add(mi, price)) {
            this.store.lock(mi.key, mi.n);
            return true;
        }
        else
            return false;
    }

    @Override
    public boolean delshelf(ItemKey id, int n, boolean unLock) {
        if(this.shelf.del(id, n)) {
            if(unLock)
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

    @Override
    public int quality() {
        return 0;
    }

    @Override
    public int availableQuantity(MetaItem m) { return this.store.availableQuantity(m); }

    @Override
    public boolean has(ItemKey m, int n) { return this.store.has(m, n); }

    @Override
    public boolean offset(ItemKey item, int n) { return this.store.offset(item, n); }

    @Override
    public boolean offset(MetaItem item, int n) { return this.store.offset(item, n); }

    public boolean delLine(UUID lineId) {
        return this.lines.remove(lineId) != null;
    }
}
