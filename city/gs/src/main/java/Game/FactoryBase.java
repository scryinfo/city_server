package Game;

import Game.Timers.PeriodicTimer;
import Shared.Util;
import com.google.protobuf.InvalidProtocolBufferException;
import gs.Gs;
import gscode.GsCode;
import org.bson.types.ObjectId;

import javax.persistence.*;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;

@MappedSuperclass
public abstract class FactoryBase extends Building {
    public FactoryBase(MetaFactoryBase meta, Coord pos, UUID ownerId) {
        super(meta, pos, ownerId);
        this.meta = meta;
        this.store = new Storage(meta.storeCapacity);
        this.shelf = new Storage(meta.shelfCapacity);
    }

    protected FactoryBase() {
    }

    public abstract LineBase addLine(MetaItem m);

    @Transient
    protected PeriodicTimer dbTimer = new PeriodicTimer(30000, 30000);

    @Transient
    protected Storage store;

    @Transient
    protected Storage shelf;

    @Transient
    Map<ObjectId, LineBase> lines = new HashMap<>();
    public boolean lineFull() {
        return lines.size() >= meta.lineNum;
    }
    public int freeWorkerNum() {
        return this.meta.maxWorkerNum - lines.values().stream().mapToInt(l -> l.workerNum).reduce(0, Integer::sum);
    }
    protected boolean isDirty() {
        return _d.dirty();
    }
    protected void _update(long diffNano) {
        this.lines.values().forEach(l -> {
            int add = l.update(diffNano);
            if(add > 0) {
                this.store.offset(l.item, add);
                this._d.dirtyStore();
                Gs.LineInfo i = Gs.LineInfo.newBuilder()
                        .setId(Util.toByteString(l.id))
                        .setNowCount(l.count)
                        .build();
                GameServer.sendTo(this.detailWatchers, Shared.Package.create(GsCode.OpCode.lineChangeInform_VALUE, i));
            }
        });

        if(this.dbTimer.update(diffNano) && isDirty()) {
            GameDb.saveOrUpdate(this);
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
            if(t < 0 || t > this.store.availSize())
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

    @Embeddable
    private static class _D {
        @Column(name = "store")
        private byte[] storeBinary;
        @Column(name = "shelf")
        private byte[] shelfBinary;

        void dirtyStore() {
            storeBinary = null;
        }
        void dirtyShelf() {
            shelfBinary = null;
        }
        boolean dirty() {
            return shelfBinary == null || storeBinary == null;
        }
    }
    @Embedded
    private final _D _d = new _D();
    @PrePersist
    @PreUpdate
    protected void _2() {
        this._d.storeBinary = this.store.binary();
        this._d.shelfBinary = this.shelf.binary();
    }
    @PostLoad
    protected void _1() throws InvalidProtocolBufferException {
        this.store = new Storage(this._d.storeBinary, meta.storeCapacity);
        this.shelf = new Storage(this._d.shelfBinary, meta.shelfCapacity);
    }
}
