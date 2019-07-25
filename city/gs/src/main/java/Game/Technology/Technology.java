package Game.Technology;

import Game.*;
import Game.Meta.*;
import Game.Timers.PeriodicTimer;
import Shared.Package;
import Shared.Util;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import gs.Gs;
import gscode.GsCode;
import org.hibernate.annotations.Cascade;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/*新版本研究所*/
@Entity
public class Technology extends Building {
    private static final int DB_UPDATE_INTERVAL_MS = 30000;
    @Transient
    private MetaTechnology meta;
    //科技资料库
    @OneToOne(cascade=CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "store_id")
    protected ScienceStore store;
    //宝箱库
    @OneToOne(cascade=CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "boxStore_id")
    protected SciencevBox boxStore;
    //科技资料出售
    @OneToOne(cascade=CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "shelf_id")
    protected ScienceShelf shelf;

    //生产线(完成)
    @OneToMany(fetch = FetchType.EAGER)
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    @MapKeyColumn(name = "line_id")
    public List<Line> line= new ArrayList<>();

    public Technology() { }

    @Transient
    protected PeriodicTimer dbTimer = new PeriodicTimer(DB_UPDATE_INTERVAL_MS, (int) (Math.random()*DB_UPDATE_INTERVAL_MS));

    @Transient
    ScienceLine delLine=null;//当前要删除的生产线

    /*添加生产线*/
    public  ScienceLine addLine(MetaItem item, int workerNum, int targetNum){
        if(!(item instanceof MetaScienceItem) || workerNum < meta.lineMinWorkerNum || workerNum > meta.lineMaxWorkerNum)
            return null;
        Line line = new Line((MetaScienceItem)item, targetNum, workerNum);
        __addLine(line);
        this.sendToWatchers(Package.create(GsCode.OpCode.ftyLineAddInform_VALUE, Gs.FtyLineAddInform.newBuilder().setBuildingId(Util.toByteString(this.id())).setLine(line.toProto()).setTs(line.ts).build()));
        return line;
    }

    protected void __addLine(Line newLine){
        if(line.indexOf(newLine.id) < 0){
            line.add(newLine);
        }
    }

    public ScienceLine delLine(UUID lineId) {
        for (int i = line.size() - 1; i >= 0 ; i--) {
            if (line.get(i).id.equals(lineId)){
                ScienceLine remove = line.remove(i);
                if(line.size() > 0){
                    if(i==0) {//如果删除的就是当前生产线，第一条，则设置移除后的第一条为当前生产时间
                        line.get(0).ts = System.currentTimeMillis();
                    }
                }
                return remove;
            }
        }
        return null;
    }

    public boolean setAutoReplenish(ItemKey key, boolean autoRepOn) {
        ScienceShelf.Content i = this.shelf.getContent(key);
        if(i == null)
            return false;
        this.shelf.add(new Item(key,0),i.price,autoRepOn);
        return  true;
    }

    public boolean shelfSet(Item item, int price) {
        ScienceShelf.Content content = this.shelf.getContent(item.getKey());
        if(content == null)
            return false;
        //不需要在此处处理自动补货，自动补货是一个单独的协议
        int updateNum = content.n - item.getN();//增加或减少：当前货架数量-现在货架数量
        if(content.n==0&&item.getN()==0){//若非自动补货，切货架数量为0，直接删除
            delshelf(item.getKey(), content.n, true);
            return true;
        }
        boolean lock = false;
        if (updateNum < 0) {
            lock = this.store.lock(item.getKey(),Math.abs(updateNum));
        } else {
            lock = this.store.unLock(item.getKey(), updateNum);
        }
        if (lock) {
            content.price = price;
            content.n = item.getN();
            //消息推送货物发生改变
            this.sendToWatchers(id(),item.getKey().meta.id,item.getN(),price,content.autoReplenish,null);
            return true;
        } else
            return false;
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
        this.boxStore = new SciencevBox();
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

    protected  boolean __hasLineRemained(){
        return line.size() > 0;
    }

    @Override
    public void appendDetailProto(Gs.BuildingSet.Builder builder) {

    }

    @Override
    protected void enterImpl(Npc npc) {

    }

    @Override
    protected void leaveImpl(Npc npc) {

    }
    //删除生产线
    protected  ScienceLine __delLine(UUID lineId){
        for (int i = line.size() - 1; i >= 0 ; i--) {
            if (line.get(i).id.equals(lineId)){
                ScienceLine remove = line.remove(i);
                if(line.size() > 0){
                    if(i==0) {//如果删除的就是当前生产线，第一条，则设置移除后的第一条为当前生产时间
                        line.get(0).ts = System.currentTimeMillis();
                    }
                }
                return remove;
            }
        }
        return null;
    }

    /*生产线生产*/
    @Override
    protected void _update(long diffNano) {
        if (getState() == Gs.BuildingState.SHUTDOWN_VALUE) {
            return;
        }
        List<UUID> completedLines = new ArrayList<>();
        if (__hasLineRemained()) {
            Line l = this.line.get(0);
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
                        //处理自动补货(自动补货不在此处理)
                        //ScienceShelf.Content content = this.shelf.getContent(key);
                       /* if(content!= null &&content.autoReplenish){
                          //更新自动补货
                            this.shelf.updateAutoReplenish(this,key);
                        }*/
                    } else {
                        l.count -= add;
                        l.suspend(add);
                    }
                }
            }
        }
        if (completedLines.size() > 0){
            UUID nextId = null;
            if(line.size() >= 2){
                nextId = line.get(1).id; //第二条生产线
            }
            ScienceLine l= __delLine(completedLines.get(0));
            delLine = l;
            if(nextId != null){
                this.sendToWatchers(Package.create(GsCode.OpCode.ftyDelLine_VALUE, Gs.DelLine.newBuilder().setBuildingId(Util.toByteString(id())).setLineId(Util.toByteString(l.id)).setNextlineId(Util.toByteString(nextId)).build()));
            }else{
                this.sendToWatchers(Package.create(GsCode.OpCode.ftyDelLine_VALUE, Gs.DelLine.newBuilder().setBuildingId(Util.toByteString(id())).setLineId(Util.toByteString(l.id)).build()));
            }
            //生产线完成通知
            MailBox.instance().sendMail(Mail.MailType.PRODUCTION_LINE_COMPLETION.getMailType(), ownerId(), new int[]{metaBuilding.id}, new UUID[]{this.id()}, new int[]{l.item.id, l.targetNum});
        }
        if(this.dbTimer.update(diffNano)) {
            GameDb.saveOrUpdate(this); // this will not ill-form other transaction due to all action are serialized
            if(delLine!=null){
                 GameDb.delete(delLine);
                delLine=null;
            }
        }
    }

    public boolean delshelf(ItemKey id, int n, boolean unLock) {
        if(this.shelf.del(id, n)) {
            if(unLock)
                this.store.unLock(id, n);
            else{//如果是消费，那么需要消费lock的数量
                this.store.consumeLock(id, n);
            }
            return true;
        }
        return false;
    }

    public boolean addshelf(Item item, int price, boolean autoReplenish) {
        if(!shelfAddable(item.getKey()) || !this.store.has(item.getKey(),item.getN()))
            return false;
        if(this.shelf.add(item, price,autoReplenish)) {
            this.store.lock(item.getKey(), item.getN());
            return true;
        }
        else
            return false;
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

    public List<Line> getLine() {
        return line;
    }

    public SciencevBox getBoxStore() {
        return boxStore;
    }

    public MetaTechnology getMeta() {
        return meta;
    }
}
