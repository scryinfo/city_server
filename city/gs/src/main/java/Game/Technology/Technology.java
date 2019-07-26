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

/*新版本研究所*/
@Entity
public class Technology extends ScienceBase {
    @Transient
    private MetaTechnology meta;
    //宝箱库
    @OneToOne(cascade=CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "boxStore_id")
    protected ScienceBox boxStore;

    public Technology() { }

    /*添加生产线*/
    public  ScienceLine addLine(MetaItem item, int workerNum, int targetNum){
        if(!(item instanceof MetaScienceItem) || workerNum < meta.lineMinWorkerNum || workerNum > meta.lineMaxWorkerNum)
            return null;
        Line line = new Line((MetaScienceItem)item, targetNum, workerNum);
        __addLine(line);
        this.sendToWatchers(Package.create(GsCode.OpCode.ftyLineAddInform_VALUE, Gs.FtyLineAddInform.newBuilder().setBuildingId(Util.toByteString(this.id())).setLine(line.toProto()).setTs(line.ts).build()));
        return line;
    }

    @Entity
    public final static class Line extends ScienceLine {
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
        this.meta = (MetaTechnology) this.metaBuilding;
    }

    public Technology(MetaTechnology meta, Coordinate pos, UUID ownerId) {
        super(meta, pos, ownerId);
        this.meta = meta;
        this.store = new ScienceStore();
        this.shelf = new ScienceShelf();
        this.boxStore = new ScienceBox();
    }
    @Override
    public int quality() {
        return 0;
    }

    @Override
    public Message detailProto() {
        Gs.Technology.Builder builder = Gs.Technology.newBuilder();
        builder.setInfo(super.toProto())
                .setStoreNum(this.store.getAllNum())
                .setShelfNum(this.shelf.getAllNum());
        if(!line.isEmpty())
            builder.addLine(line.get(0).toProto());
        return null;
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

    /*生产线生产*/
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
                    broadcastLineInfo(l,key);//广播
                }
            } else {
                int add = l.update(diffNano,this.ownerId()); //新增了玩家id，作为eva查询
                if (add > 0) {
                   ItemKey key = l.newItemKey(ownerId());
                    if (this.boxStore.offSet(key, add)) {//添加到未开启宝箱中
                        broadcastLineInfo(l,key);//广播
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

    /*商品适配*/
    protected boolean shelfAddable(ItemKey k) {
        return k.meta instanceof MetaScienceItem;
    }
    //广播
    private void broadcastLineInfo(ScienceLine line,ItemKey key) {
        Gs.LineInfo i = Gs.LineInfo.newBuilder()
                .setId(Util.toByteString(line.id))
                .setNowCount(line.count)
                .setBuildingId(Util.toByteString(this.id()))
                .setIKey(key.toProto())
                .setNowCountInStore(this.boxStore.getTypeBoxNum(key))
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

    public boolean hasEnoughSciencePointInStore(ItemKey key,int num){
       return this.store.has(key, num);
    }

    /*参数1：宝箱类型，参数2：数量*/
    public int useScienceBox(ItemKey key,int num){
        Integer boxNum = this.boxStore.getAllBox().getOrDefault(key, 0);
        if(boxNum==0||boxNum<num){
            return 0;
        }
        int min = this.meta.minScienceAdd;
        int max = this.meta.maxScienceAdd;
        int totalPoint=0;
        //1.步骤，使用多少数量，就要循环多少次，随机获取
        //2.随机获取meta里面的minScience  到maxSciencd 区间的点数（怎么随机，获取每个）
        for (int i = 0; i < num; i++) {
            totalPoint+= Prob.random(min, max);
        }
        //3.扣减宝箱数量
        this.boxStore.offSet(key,-num);
        //4.添加到仓库已开启点数
        this.store.offset(key, totalPoint);
        return totalPoint;
    }

    public ScienceShelf.Content getContent(ItemKey key){
        return  this.shelf.getContent(key);
    }

    public void updateAutoReplenish(ItemKey key){
        this.shelf.updateAutoReplenish(this,key);
    }

    public boolean checkBuyScience(ItemKey key,int num){
        ScienceShelf.Content content = this.shelf.getContent(key);
        if(content==null||content.n<num){
            return false;
        }else{
            return true;
        }
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
}
