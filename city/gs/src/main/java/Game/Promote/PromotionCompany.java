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

/*新版推广公司*/
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
        this.sendToWatchers(Package.create(GsCode.OpCode.ftyLineAddInform_VALUE, Gs.FtyLineAddInform.newBuilder().setBuildingId(Util.toByteString(this.id())).setLine(line.toProto(id())).setTs(line.ts).build()));
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

    //广播
    private void broadcastLineInfo(ScienceLineBase line, ItemKey key) {
        Gs.LineInfo i = Gs.LineInfo.newBuilder()
                .setId(Util.toByteString(line.id))
                .setNowCount(line.count)
                .setBuildingId(Util.toByteString(this.id()))
                .setIKey(key.toProto())
                .setNowCountInStore(this.store.getItemCount(key))
                .build();
        sendToWatchers(Shared.Package.create(GsCode.OpCode.ftyLineChangeInform_VALUE, i));
    }

    @Override
    public int quality() {
        return 0;
    }

    @Override
    public Message detailProto() {
        Gs.PromotionCompany.Builder builder = Gs.PromotionCompany.newBuilder();
        builder.setInfo(super.toProto())
                .setStoreNum(this.store.getAllNum())
                .setShelfNum(this.shelf.getAllNum());
        if(!line.isEmpty())
            builder.addLine(line.get(0).toProto(id()));
        return builder.build();
    }

    @Override
    public void appendDetailProto(Gs.BuildingSet.Builder builder) {

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
                    System.err.println("生产线已完成");
                    completedLines.add(l.id);
                }
            }
            if (l.isSuspend()) {
                assert l.left() > 0;
                ItemKey key = l.newItemKey(ownerId());
                if (this.store.offset(key, l.left())) {
                    l.resume();
                    broadcastLineInfo(l, key);//广播
                }
            } else {
                int add = l.update(diffNano, this.ownerId()); //新增了玩家id，作为eva查询
                if (add > 0) {
                    System.err.println("增加："+add);
                    System.err.println("当前生产线："+l.item.id+"已生产："+l.count);
                    ItemKey key = l.newItemKey(ownerId());
                    if (this.store.offset(key, add)) {//添加到未开启宝箱中
                        broadcastLineInfo(l, key);//广播
                    } else {
                        l.count -= add;
                        l.suspend(add);
                    }
                }
            }
        }
        delComplementLine(completedLines);//删除已完成线
        saveAndUpdate(diffNano);//定时更新
    }
}
