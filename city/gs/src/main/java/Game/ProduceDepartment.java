package Game;

import Game.Meta.*;
import com.google.protobuf.InvalidProtocolBufferException;
import gs.Gs;

import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.PostLoad;
import javax.persistence.Transient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity(name = "ProduceDepartment")
public class ProduceDepartment extends FactoryBase {
    @Transient
    private MetaProduceDepartment meta;

    @Transient
    List<Item> consumedCache = new ArrayList();

    public ProduceDepartment(MetaProduceDepartment meta, Coordinate pos, UUID ownerId) {
        super(meta, pos, ownerId);
        this.meta = meta;
    }
    @Override
    public int getSaleCount(int itemId) {
        return this.shelf.getSaleNum(itemId);
    }
    protected ProduceDepartment() {}

    @Override
    public Map<Item, Integer> getSaleDetail(int itemId) {
        return this.shelf.getSaleDetail(itemId);
    }
    @Entity
    public final static class Line extends LineBase {
        public Line(MetaGood item, int targetNum, int workerNum, int itemLevel) {
            super(item, targetNum, workerNum, itemLevel);
            this.formula = MetaData.getFormula(item.id);
        }
        protected Line() {}
        @Convert(converter = GoodFormula.Converter.class)
        GoodFormula formula;
        @Override
        public ItemKey newItemKey(UUID producerId, int qty,UUID pid) {
            return new ItemKey(item, producerId, qty, pid);
        }
    }

    @PostLoad
    protected void _1() throws InvalidProtocolBufferException {
        super._1();
        this.meta = (MetaProduceDepartment) super.metaBuilding;
    }

    @Override
    protected boolean shelfAddable(ItemKey k) {
        return k.meta instanceof MetaGood;
    }

    @Override
    public Gs.ProduceDepartment detailProto() {
        Gs.ProduceDepartment.Builder builder = Gs.ProduceDepartment.newBuilder().setInfo(super.toProto());
        builder.setStore(this.store.toProto());
        builder.setShelf(this.shelf.toProto());
        this.lines.forEach(line -> {
            ItemKey itemKey = new ItemKey(line.item,ownerId(), line.itemLevel,ownerId());
            Gs.ItemKey key = itemKey.toProto();
            Gs.Line.Builder l = line.toProto().toBuilder()
                    .setBrandScore(key.getBrandScore())
                    .setQtyScore(key.getQualityScore())
                    .setBrandName(key.getBrandName());
            builder.addLine(l.build());
        });
        return builder.build();
    }
    @Override
    public void appendDetailProto(Gs.BuildingSet.Builder builder) {
        builder.addProduceDepartment(this.detailProto());
    }

    @Override
    protected void enterImpl(Npc npc) {

    }

    @Override
    protected void leaveImpl(Npc npc) {

    }

    protected LineBase addLineImpl(MetaItem item, int workerNum, int targetNum, int itemLevel) {
        if(!(item instanceof MetaGood) || workerNum < meta.lineMinWorkerNum || workerNum > meta.lineMaxWorkerNum)
            return null;
        Line line = new Line((MetaGood)item, targetNum, workerNum, itemLevel);
        __addLine(line);
        return line;
    }

    @Override
    protected boolean consumeMaterial(LineBase line,UUID pid) {
        Line l = (Line)line;
        for(GoodFormula.Info i : l.formula.material) {  //此步判断数量是否足够
            if(i.item == null)
                continue;
            if(!this.store.has(new ItemKey(i.item,pid), i.n)) {
                return false;
            }
        }
        for(GoodFormula.Info i : l.formula.material) { //此步消耗材料
            if (i.item == null)
                continue;
            ItemKey key = new ItemKey(i.item,pid);
            this.store.offset(key, -i.n);
            consumedCache.add(new Item(key,i.n));
        }
        //如果有材料消耗，需要通知客户端更新
        if(consumedCache.size() > 0 ){
            GameDb.saveOrUpdate(this);
            broadcastMaterialConsumed(this.id(),consumedCache);
            //通知完之后，清掉缓存
            consumedCache.clear();
        }
        return true;
    }

    @Override
    protected boolean hasEnoughMaterial(LineBase line, UUID pid) {//是否有足够的数量
        Line l = (Line)line;
        for(GoodFormula.Info i : l.formula.material) {
            if(i.item == null)
                continue;
            if(!this.store.has(new ItemKey(i.item,pid), i.n)) {
                return false;
            }
        }
        return true;
    }

    //    protected void _update(long diffNano) {
//        super._update(diffNano);
//    }
}
