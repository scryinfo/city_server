package Game.Technology;

import Game.*;
import Game.Meta.MetaItem;
import Game.Meta.MetaScienceItem;
import Game.Meta.MetaTechnology;
import Shared.Package;
import Shared.Util;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import gs.Gs;
import gscode.GsCode;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/*New Version Institute*/
@Entity
public class Technology extends ScienceBuildingBase {
    @Transient
    private MetaTechnology meta;
    //Treasure chest
    @OneToOne(cascade=CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "boxStore_id")
    protected ScienceBox boxStore;

    public Technology() { }
    public Technology(MetaTechnology meta, Coordinate pos, UUID ownerId) {
        super(meta, pos, ownerId);
        this.meta = meta;
        this.boxStore = new ScienceBox();
    }

    /*Add production line*/
    public ScienceLineBase addLine(MetaItem item, int workerNum, int targetNum){
        if(!(item instanceof MetaScienceItem) || workerNum < meta.lineMinWorkerNum || workerNum > meta.lineMaxWorkerNum)
            return null;
        Line line = new Line((MetaScienceItem)item, targetNum, workerNum);
        __addLine(line);
        this.sendToWatchers(Package.create(GsCode.OpCode.ftyLineAddInform_VALUE, Gs.FtyLineAddInform.newBuilder().setBuildingId(Util.toByteString(this.id())).setLine(line.toProto(ownerId())).setTs(line.ts).build()));
        return line;
    }

    @Entity
    public final static class Line extends ScienceLineBase {
        public Line(MetaScienceItem item, int targetNum, int workerNum) {
            super(item, targetNum, workerNum);
        }
        public Line() {
        }

        public ItemKey newItemKey(UUID pid) {
            return new ItemKey(item,pid);
        }

    }

    @PostLoad
    protected void _1() throws InvalidProtocolBufferException {
        this.meta = (MetaTechnology) super.metaBuilding;
    }

    @Override
    public int quality() {
        return 0;
    }

    @Override
    public Gs.Technology detailProto() {
        Gs.Technology.Builder builder = Gs.Technology.newBuilder();
        builder.setInfo(super.toProto())
                .setStoreNum(this.store.getAllNum())
                .setShelfNum(this.shelf.getAllNum());
        if(!line.isEmpty())
            builder.addLine(line.get(0).toProto(ownerId()));
        return builder.build();
    }

    @Override
    public void appendDetailProto(Gs.BuildingSet.Builder builder) {
        builder.addTechnology(this.detailProto());
    }//TODO

    @Override
    protected void enterImpl(Npc npc) {

    }

    @Override
    protected void leaveImpl(Npc npc) {

    }

    /*Production line production*/
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
                if (this.boxStore.offSet(key, l.left())) {
                    l.resume();
                    broadcastLineInfo(l,key,0);//broadcast
                }
            } else {
                int add = l.update(diffNano,this.ownerId()); //Added player id as eva query
                if (add > 0) {
                   ItemKey key = l.newItemKey(ownerId());
                    if (this.boxStore.offSet(key, add)) {//Add to unopened treasure chest
                        broadcastLineInfo(l,key,add);//broadcast
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

    /*Product adaptation*/
    protected boolean shelfAddable(ItemKey k) {
        return k.meta instanceof MetaScienceItem;
    }
    //broadcast
    private void broadcastLineInfo(ScienceLineBase line, ItemKey key,int add) {
        Gs.LineInfo i = Gs.LineInfo.newBuilder()
                .setId(Util.toByteString(line.id))
                .setNowCount(line.count)
                .setBuildingId(Util.toByteString(this.id()))
                .setIKey(key.toProto())
                .setNowCountInBox(this.boxStore.getTypeBoxNum(key))
                .setTargetCount(line.targetNum)
                .setStartTime(line.ts)
                .setProduceNum(add)
                .build();
        sendToWatchers(Shared.Package.create(GsCode.OpCode.ftyLineChangeInform_VALUE, i));
    }

    public boolean hasEnoughBox(ItemKey key,int num) {
        Integer count = this.boxStore.getTypeBoxNum(key);
        if(count<num){
            return false;
        }else {
            return true;
        }
    }

    /*Parameter 1: Treasure box type, Parameter 2: Quantity*/
    public int useScienceBox(ItemKey key,int num){
        Integer boxNum = this.boxStore.getAllBox().getOrDefault(key, 0);
        if(boxNum==0||boxNum<num){
            return 0;
        }
        int min = this.meta.minScienceAdd;
        int max = this.meta.maxScienceAdd;
        int totalPoint=0;
        //1.Step, how many times to use, how many times to loop, get randomly
        //2.Randomly obtain the number of points in the interval from minScience to maxSciencd in the meta (how to get each randomly)
        for (int i = 0; i < num; i++) {
            totalPoint+= Prob.random(min, max);
        }
        //3.Deduct the number of treasure chests
        this.boxStore.offSet(key,-num);
        //4.Add to warehouse opened points
        this.store.offset(key, totalPoint);
        return totalPoint;
    }




    public ScienceStore getStore() {
        return store;
    }

    public ScienceShelf getShelf() {
        return shelf;
    }

    public ScienceBox getBoxStore() {
        return boxStore;
    }

    public MetaTechnology getMeta() {
        return meta;
    }
    /*Clear all the data of the building*/

    @Override
    public void cleanData() {
        super.cleanData();
        this.boxStore.cleanData();
    }
}
