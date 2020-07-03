package Game;

import Game.Meta.MetaFactoryBase;
import Game.Meta.MetaGood;
import Game.Meta.MetaItem;
import Game.Timers.PeriodicTimer;
import Shared.Package;
import Shared.Util;
import com.google.protobuf.InvalidProtocolBufferException;
import gs.Gs;
import gscode.GsCode;
import org.hibernate.annotations.Cascade;

import javax.persistence.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

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
            this.sendToWatchers(Package.create(GsCode.OpCode.ftyLineAddInform_VALUE, Gs.FtyLineAddInform.newBuilder().setBuildingId(Util.toByteString(this.id())).setLine(l.toProto()).setTs(l.ts).build()));
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
        //Push change information
//        this.sendToWatchers(id(), m.meta.id, n, price);
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
    //@OrderColumn
    @MapKeyColumn(name = "line_id")
    List<LineBase> lines = new ArrayList<>();

    protected void __addLine(LineBase newLine){
        if(lines.indexOf(newLine.id) < 0){
            lines.add(newLine);
        }
    }

    protected  LineBase __delLine(UUID lineId){
        for (int i = lines.size() - 1; i >= 0 ; i--) {
            if (lines.get(i).id.equals(lineId)){
                LineBase remove = lines.remove(i);
                if(lines.size() > 0){
                    if(i==0) {//If the deletion is the current production line, the first one, then set the first one after removal as the current production time
                        lines.get(0).ts = System.currentTimeMillis();
                    }
                }
                return remove;
            }
        }
        return null;
    }

    protected  boolean __hasLineRemained(){
        return lines.size() > 0;
    }

    public void updateLineQuality(int metaId, int lv) {
        this.lines.forEach(l->{
            if(l.item.id == metaId)
                l.itemLevel = lv;
        });
    }
    public boolean lineFull() {
        return lines.size() >= meta.lineNum;
    }
    public int freeWorkerNum() {
        return this.meta.workerNum - lines.stream().map(l -> l.workerNum).reduce(0, Integer::sum);
    }
    protected abstract boolean consumeMaterial(LineBase line, UUID pid);

    protected abstract boolean hasEnoughMaterial(LineBase line, UUID pid);

    protected void _update(long diffNano) {
        if(getState()== Gs.BuildingState.SHUTDOWN_VALUE){
            return;
        }

        List<UUID> completedLines = new ArrayList<>();
        if(__hasLineRemained()){
            LineBase l =  lines.get(0);
            if(l.isPause()) {
                if(l.isComplete()){
                    completedLines.add(l.id);
                }
            }
            if(l.isPauseStatue()) {
                if (this.store.availableSize() > 0 && this.hasEnoughMaterial(l, ownerId())) {//When the warehouse capacity is enough and there is enough quantity, start again
                    l.start();
                    l.ts = System.currentTimeMillis();
                }
            }
            if(l.isPauseStatue()){//If currently suspended, cancel execution
                if(completedLines.size()>0) {
                    delComplementLine(completedLines);//Delete the completed production line
                }
                return;
            }else{
                if(this.store.availableSize()<=0){
                    //Stop production, insufficient push capacity
                    l.pause();
                    List<UUID> owner = Arrays.asList(ownerId());
                    Gs.ByteBool.Builder builder = Gs.ByteBool.newBuilder().setB(true).setId(Util.toByteString(id()));
                    GameServer.sendTo(owner,Package.create(GsCode.OpCode.storeIsFullNotice_VALUE,builder.build()));
                    if(completedLines.size()>0) {
                        delComplementLine(completedLines);//Delete the completed production line
                    }
                    return;
                }
            }
            if(l.isSuspend()) {
                assert l.left() > 0;
                ItemKey key = l.newItemKey(ownerId(), l.itemLevel,ownerId());
                if(this.store.offset(key, l.left())) {
                    l.resume();
                    broadcastLineInfo(l,key);
                }
            }
            else {
                if (l.materialConsumed == false) {
                    l.materialConsumed = this.consumeMaterial(l, ownerId());
                }
                if (!l.materialConsumed) {
                    //Not enough push materials
                    l.pause();
                    List<UUID> owner = Arrays.asList(ownerId());
                    Gs.ByteBool.Builder builder = Gs.ByteBool.newBuilder().setB(true).setId(Util.toByteString(id()));
                    GameServer.sendTo(owner,Package.create(GsCode.OpCode.materialNotEnough_VALUE,builder.build()));
                    return;
                }
                int add = l.update(diffNano,this.ownerId()); //Added player id as eva query
                if (add > 0) {
                    l.materialConsumed = false;
                    ItemKey key = l.newItemKey(ownerId(), l.itemLevel,ownerId());
                    if (this.store.offset(key, add)) {
                        IShelf s = (IShelf)this;
                        Shelf.Content i = s.getContent(key);
                        broadcastLineInfo(l,key);
                        //Handling automatic replenishment
                        if(i != null && i.autoReplenish){
                            IShelf.updateAutoReplenish(s,key);
                        }
                        //Bound brand (if it is a product)
                        if(MetaGood.isItem(l.item.id)) {
                            if (!BrandManager.instance().brandIsExist(ownerId(), l.item.id)) {
                                Player owner = GameDb.getPlayer(ownerId());
                                //The company name is used directly here because the company name is unique, and the combination of company and type is also unique
                                //At present, the combination of company name + product type id is used as the brand name of the server. The client needs to parse out the id after _ and find the corresponding multilingual string to represent
                                BrandManager.instance().addBrand(ownerId(), l.item.id);
                            }
                        }
                    } else {
                        l.count -= add;
                        MailBox.instance().sendMail(Mail.MailType.STORE_FULL.getMailType(), ownerId(), new int[]{metaBuilding.id}, new UUID[]{this.id()}, null);
                        l.suspend(add);
                    }
                }
            }
        }
        if (completedLines.size() > 0){
            UUID nextId = null;
            if(lines.size() >= 2){
                nextId = lines.get(1).id; //The second production line
            }
            LineBase l = __delLine(completedLines.get(0));
            if(nextId != null){
                this.sendToWatchers(Package.create(GsCode.OpCode.ftyDelLine_VALUE, Gs.DelLine.newBuilder().setBuildingId(Util.toByteString(id())).setLineId(Util.toByteString(l.id)).setNextlineId(Util.toByteString(nextId)).build()));
            }else{
                this.sendToWatchers(Package.create(GsCode.OpCode.ftyDelLine_VALUE, Gs.DelLine.newBuilder().setBuildingId(Util.toByteString(id())).setLineId(Util.toByteString(l.id)).build()));
            }
            //Production line completion notice
            MailBox.instance().sendMail(Mail.MailType.PRODUCTION_LINE_COMPLETION.getMailType(), ownerId(), new int[]{metaBuilding.id}, new UUID[]{this.id()}, new int[]{l.item.id, l.targetNum});
        }
        if(this.dbTimer.update(diffNano)) {
            GameDb.saveOrUpdate(this); // this will not ill-form other transaction due to all action are serialized
        }
    }

    private void broadcastLineInfo(LineBase l,ItemKey key) {

        Gs.LineInfo i = Gs.LineInfo.newBuilder()
                .setId(Util.toByteString(l.id))
                .setNowCount(l.count)
                .setBuildingId(Util.toByteString(this.id()))
                .setIKey(key.toProto())
                .setNowCountInStore(this.getItemCount(key)+this.getSaleCount(key.meta.id))
                .build();
        sendToWatchers(Shared.Package.create(GsCode.OpCode.ftyLineChangeInform_VALUE, i));
    }
    protected void broadcastMaterialConsumed(UUID bid, List<Item> changedMats) {
        Gs.materialConsumedInform.Builder i = Gs.materialConsumedInform.newBuilder()
                .setBuildingId(Util.toByteString(bid));
        changedMats.forEach(item -> i.addItems(item.toProto()));
        sendToWatchers(Shared.Package.create(GsCode.OpCode.materialConsumedInform_VALUE, i.build()));
    }

    @Transient
    private MetaFactoryBase meta;

    public boolean changeLine(UUID lineId, OptionalInt targetNum, OptionalInt workerNum) {
        //LineBase line = this.lines.get(lineId);
        LineBase line = null;
        for (LineBase l: lines){
            if (l.id == lineId){
                line = l;
            }
        }
        if(line == null)
            return false;
        Gs.LineInfo.Builder builder = Gs.LineInfo.newBuilder();

        builder.setId(Util.toByteString(line.id));
        builder.setBuildingId(Util.toByteString(this.id()));
        if(targetNum.isPresent()) {
            int t = targetNum.getAsInt();
            if(t < 0)
                return false;
            line.targetNum = t;
            builder.setTargetCount(t);
        }
        if(workerNum.isPresent()) {
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
    public boolean addshelf(Item mi, int price, boolean autoReplenish) {
        if(!shelfAddable(mi.key) || !this.store.has(mi.key, mi.n))
            return false;
        if(this.shelf.add(mi, price,autoReplenish)) {
            this.store.lock(mi.key, mi.n);
            //Push shelf information
            //this.sendToWatchers(id(), mi.key.meta.id, mi.n, price);
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
            else{//If it is consumption, then the number of locks that need to be consumed
                this.store.consumeLock(id, n);
            }
            //Push shelf information
            //this.sendToWatchers(id(), id.meta.id, n, 0);
            return true;
        }
        return false;
    }

    @Override
    public boolean setAutoReplenish(ItemKey id, boolean autoRepOn){
        Shelf.Content i = this.shelf.getContent(id);
        if(i == null)
            return false;
        this.shelf.add(new Item(id,0),i.price,autoRepOn);
        return  true;
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
    public boolean shelfSet(Item item, int price,boolean autoRepOn) {
        Shelf.Content content = this.shelf.getContent(item.key);
        //The number of warehouses needs to be changed
        if(content == null)
            return false;
        if(!autoRepOn) {//Non-automatic replenishment
            int updateNum = content.n - item.n;//Increase or decrease: current number of shelves-current number of shelves
            if(content.n==0&&item.n==0){//If it is not automatic replenishment, the number of cut shelves is 0, directly delete
                content.autoReplenish=autoRepOn;
                IShelf shelf=this;
                shelf.delshelf(item.key, content.n, true);
                return true;
            }
            boolean lock = false;
            if (updateNum < 0) {
                lock = this.store.lock(item.key, Math.abs(updateNum));
            } else {
                lock = this.store.unLock(item.key, updateNum);
            }
            if (lock) {
                content.price = price;
                content.n = item.n;
                content.autoReplenish = autoRepOn;
                //Message push goods change
                UUID produceId=null;
                if(MetaGood.isItem(item.key.meta.id)){
                    produceId = item.key.producerId;
                }
                this.sendToWatchers(id(),item.key.meta.id,item.n,price,autoRepOn,produceId);
                return true;
            } else
                return false;
        }else {//Automatic replenishment
            //1.Determine whether the capacity is full
            if(this.shelf.full())
                return false;
            //2.Set price
            content.price = price;
            IShelf shelf=this;
            //Relist
            shelf.delshelf(item.key, content.n, true);
            Item itemInStore = new Item(item.key,this.store.getItemCount(item.key));
            shelf.addshelf(itemInStore,price,autoRepOn);
            int count = shelf.getSaleCount(item.key.meta.id);
            UUID produceId=null;
            if(MetaGood.isItem(item.key.meta.id)){
                produceId = item.key.producerId;
            }
            //Message push goods change
            this.sendToWatchers(id(),item.key.meta.id,count,price,autoRepOn,produceId);//forward news
            return true;
        }
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
    public boolean offset(MetaItem item, int n, UUID pid, int typeId) { return this.store.offset(item, n,pid,typeId); }

    public boolean delLine(UUID lineId) {
        return this.__delLine(lineId) != null ;
    }

    @Override
    public boolean delItem(Item item) {
        return this.store.delItem(item);
    }

    public Shelf getShelf() {
        return shelf;
    }

    public void cleanData(){
        //Delete production line
        GameDb.delete(lines);
        this.lines.clear();
        this.store.clearData();
        this.shelf.clearData();
    }

    public void delComplementLine(List<UUID> completedLines){
        if(completedLines.size()>0) {
            UUID nextId = null;
            if (lines.size() >= 2) {
                nextId = lines.get(1).id; //The second production line
            }
            LineBase l = __delLine(completedLines.get(0));
            if (nextId != null) {
                this.sendToWatchers(Package.create(GsCode.OpCode.ftyDelLine_VALUE, Gs.DelLine.newBuilder().setBuildingId(Util.toByteString(id())).setLineId(Util.toByteString(l.id)).setNextlineId(Util.toByteString(nextId)).build()));
            } else {
                this.sendToWatchers(Package.create(GsCode.OpCode.ftyDelLine_VALUE, Gs.DelLine.newBuilder().setBuildingId(Util.toByteString(id())).setLineId(Util.toByteString(l.id)).build()));
            }
            //Production line completion notice
            MailBox.instance().sendMail(Mail.MailType.PRODUCTION_LINE_COMPLETION.getMailType(), ownerId(), new int[]{metaBuilding.id}, new UUID[]{this.id()}, new int[]{l.item.id, l.targetNum});
        }
    }

    @Override
    public int getItemCount(ItemKey key) {
        return this.store.getItemCount(key);
    }

    @Override
    public int getTotalSaleCount() {
        return this.shelf.getTotalContentNum();
    }
}
