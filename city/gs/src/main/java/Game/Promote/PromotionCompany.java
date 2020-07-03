package Game.Promote;

import Game.*;
import Game.Meta.*;
import Shared.Package;
import Shared.Util;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import gs.Gs;
import gscode.GsCode;

import javax.persistence.Entity;
import javax.persistence.PostLoad;
import javax.persistence.Transient;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/*New promotion company*/
@Entity
public class PromotionCompany extends ScienceBuildingBase {
    @Transient
    private MetaPromotionCompany meta;

    public PromotionCompany() {}
    public PromotionCompany(MetaPromotionCompany meta, Coordinate pos, UUID ownerId) {
        super(meta, pos, ownerId);
        this.meta = meta;
    }

    @Override
    protected ScienceLineBase addLine(MetaItem item, int workerNum, int targetNum) {
        if(!(item instanceof MetaPromotionItem) || workerNum < meta.lineMinWorkerNum || workerNum > meta.lineMaxWorkerNum)
            return null;
        Line line = new Line((MetaPromotionItem)item, targetNum, workerNum);
        __addLine(line);
        this.sendToWatchers(Package.create(GsCode.OpCode.ftyLineAddInform_VALUE, Gs.FtyLineAddInform.newBuilder().setBuildingId(Util.toByteString(this.id())).setLine(line.toProto(ownerId())).setTs(line.ts).build()));
        return line;
    }

    @Entity
    public final static class Line extends ScienceLineBase {
        public Line(MetaPromotionItem item, int targetNum, int workerNum) {
            super(item, targetNum, workerNum);
        }
        public Line() {
        }
        public ItemKey newItemKey(UUID pid) {
            return new ItemKey(item,pid);
        }
    }

    @Override
    protected boolean shelfAddable(ItemKey k) {
        return k.meta instanceof MetaPromotionItem;
    }

    @PostLoad
    protected void _1() throws InvalidProtocolBufferException {
        this.meta = (MetaPromotionCompany) this.metaBuilding;
    }

    //broadcast
    private void broadcastLineInfo(ScienceLineBase line, ItemKey key) {
        Gs.LineInfo i = Gs.LineInfo.newBuilder()
                .setId(Util.toByteString(line.id))
                .setNowCount(line.count)
                .setBuildingId(Util.toByteString(this.id()))
                .setIKey(key.toProto())
                .setNowCountInStore(this.store.getItemCount(key))
                .setSpeed(line.getItemSpeed(ownerId()))
                .setStartTime(line.ts)
                .setTargetCount(line.targetNum)
                .setStoreAllNum(this.store.getAllNum())
                .setNowCountInLocked(this.store.getLockedNum(key))
                .build();
        sendToAllWatchers(Shared.Package.create(GsCode.OpCode.ftyLineChangeInform_VALUE, i));
    }

    @Override
    public int quality() {
        return 0;
    }

    @Override
    public Gs.PromotionCompany detailProto() {
        Gs.PromotionCompany.Builder builder = Gs.PromotionCompany.newBuilder();
        builder.setInfo(super.toProto())
                .setStoreNum(this.store.getAllNum())
                .setShelfNum(this.shelf.getAllNum());
        if(!line.isEmpty())
            builder.addLine(line.get(0).toProto(ownerId()));
        return builder.build();
    }

    @Override
    public void appendDetailProto(Gs.BuildingSet.Builder builder) {
        builder.addPromotionCompany(this.detailProto());

    }//TODO

    @Override
    protected void enterImpl(Npc npc) {

    }

    @Override
    protected void leaveImpl(Npc npc) {

    }

    @Override
    protected void _update(long diffNano) {
        if (getState() == Gs.BuildingState.SHUTDOWN_VALUE) {
            return;
        }
        List<UUID> completedLines = new ArrayList<>();
        if (__hasLineRemained()) {
            Line l = (Line) this.line.get(0);
            if (l.isPause()) {
                if (l.isComplete()) {
                    completedLines.add(l.id);
                }
            }
            if (l.isSuspend()) {
                assert l.left() > 0;
                ItemKey key = l.newItemKey(ownerId());
                if (this.store.offset(key, l.left())) {
                    l.resume();
                    broadcastLineInfo(l, key);//broadcast
                }
            } else {
                int add = l.update(diffNano, this.ownerId()); //Added player id as eva query
                if (add > 0) {
                    ItemKey key = l.newItemKey(ownerId());
                    if (this.store.offset(key, add)) {//Add to unopened treasure chest
                        /*Update automatic replenishment*/
                        updateAutoReplenish(key);
                        broadcastLineInfo(l, key);//broadcast
                    } else {
                        l.count -= add;
                        l.suspend(add);
                    }
                }
            }
        }
        delComplementLine(completedLines);//Delete completed line
        saveAndUpdate(diffNano);//Update regularly
    }

    @Override
    public int getTotalSaleCount() {
        return this.shelf.getTotalContentNum();
    }
}
