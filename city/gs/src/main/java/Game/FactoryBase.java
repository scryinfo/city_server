package Game;

import Game.Meta.MetaFactoryBase;
import Game.Meta.MetaItem;
import Game.Timers.PeriodicTimer;
import Shared.Package;
import Shared.Util;
import com.google.protobuf.InvalidProtocolBufferException;
import gs.Gs;
import gscode.GsCode;
import org.hibernate.annotations.Cascade;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;

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
        // 推送货架数量变更
        Gs.salesNotice.Builder builder = Gs.salesNotice.newBuilder();
        builder.setBuildingId(Util.toByteString(id())).setItemId(m.meta.id).setSelledCount(n);
        this.sendToWatchers(Package.create(GsCode.OpCode.salesNotice_VALUE, builder.build())
        );
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
                return lines.remove(i);
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

    protected void _update(long diffNano) {
        List<UUID> completedLines = new ArrayList<>();
        if(__hasLineRemained()){
            LineBase l =  lines.get(0);
            if(l.isPause()) {
                if(l.isComplete()){
                    completedLines.add(l.id);
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
                if (l.materialConsumed == false)
                l.materialConsumed = this.consumeMaterial(l,ownerId());
                    if (!l.materialConsumed)
                        return;
                    int add = l.update(diffNano,this.ownerId()); //新增了玩家id，作为eva查询
                    if (add > 0) {
                        l.materialConsumed = false;
                        ItemKey key = l.newItemKey(ownerId(), l.itemLevel,ownerId());
                        if (this.store.offset(key, add)) {
                            IShelf s = (IShelf)this;
                            Shelf.Content i = s.getContent(key);
                            broadcastLineInfo(l,key);
                            //处理自动补货
                            if(i != null && i.autoReplenish){
                                IShelf.updateAutoReplenish(s,key);
                            }
                            //绑定品牌
                            if(BrandManager.instance().getBrand(ownerId(),l.item.id) == null){
                                Player owner = GameDb.getPlayer(ownerId());
                                //这里之所以直接用公司名字，是因为公司名字是唯一的,而公司与类型的组合也是唯一的
                                //目前使用 公司名字+产品类型id 的组合作为服务器的品牌名字，客户端需要解析出 _ 之后的id，找到对应的多语言字符串来表现
                                BrandManager.instance().addBrand(ownerId(), l.item.id);
                            }
                        } else {
                            //(加工厂/原料厂)仓库已满通知
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
                nextId = lines.get(1).id; //第二条生产线
            }
            LineBase l = __delLine(completedLines.get(0));
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
        }
    }

    private void broadcastLineInfo(LineBase l,ItemKey key) {
        Gs.LineInfo i = Gs.LineInfo.newBuilder()
                .setId(Util.toByteString(l.id))
                .setNowCount(l.count)
                .setBuildingId(Util.toByteString(this.id()))
                .setIKey(key.toProto())
                .setNowCountInStore(this.availableQuantity(key.meta))
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
            else{//如果是消费，那么需要消费lock的数量
                this.store.consumeLock(id, n);
            }
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
        //仓库数量需变化
        if(content == null)
            return false;
        if(!autoRepOn) {//非自动补货
            int updateNum = content.n - item.n;//要增加或减少的就是以前货架数量-现在货架数量
            //首先判断是否存的下
            if (this.store.canSave(item.key, updateNum)) {
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
                    return true;
                } else {
                    return false;
                }
            } else
                return false;
        }else {
            //1.判断容量是否已满
            if(this.shelf.full())
                return false;
            content.autoReplenish = autoRepOn;
            //2.设置价格
            content.price = price;
            IShelf shelf=this;
            //重新上架
            shelf.delshelf(item.key, content.n, true);
            Item itemInStore = new Item(item.key,this.store.availableQuantity(item.key.meta));
            shelf.addshelf(itemInStore,price,autoRepOn);
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
}
