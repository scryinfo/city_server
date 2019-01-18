package Game;

import Game.Meta.MetaItem;
import com.google.protobuf.InvalidProtocolBufferException;
import gs.Gs;

import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.PostLoad;
import javax.persistence.Transient;
import java.util.UUID;

@Entity(name = "ProduceDepartment")
public class ProduceDepartment extends FactoryBase {
    @Transient
    private MetaProduceDepartment meta;

    public ProduceDepartment(MetaProduceDepartment meta, Coordinate pos, UUID ownerId) {
        super(meta, pos, ownerId);
        this.meta = meta;
    }

    public ProduceDepartment() {
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
        public ItemKey newItemKey(UUID producerId, int qty) {
            return new ItemKey(item, producerId, qty);
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
        this.lines.values().forEach(line -> builder.addLine(line.toProto()));
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

    public LineBase addLine(MetaItem item, int workerNum, int targetNum, int itemLevel) {
        if(!(item instanceof MetaGood) || workerNum > this.freeWorkerNum() || workerNum < meta.lineMinWorkerNum || workerNum > meta.lineMaxWorkerNum)
            return null;
        Line line = new Line((MetaGood)item, targetNum, workerNum, itemLevel);
        lines.put(line.id, line);
        return line;
    }

    @Override
    protected boolean consumeMaterial(LineBase line) {
        Line l = (Line)line;
        for(GoodFormula.Info i : l.formula.material) {
            if(i.item == null)
                continue;
            if(!this.store.has(new ItemKey(i.item), i.n)) {
                return false;
            }
        }
        for(GoodFormula.Info i : l.formula.material) {
            if (i.item == null)
                continue;
            this.store.offset(new ItemKey(i.item), -i.n);
        }
        return true;
    }

//    protected void _update(long diffNano) {
//        super._update(diffNano);
//    }
}
