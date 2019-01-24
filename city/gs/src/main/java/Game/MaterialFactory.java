package Game;

import Game.Meta.MetaItem;
import Game.Meta.MetaMaterial;
import Game.Meta.MetaMaterialFactory;
import Shared.Package;
import Shared.Util;
import com.google.protobuf.InvalidProtocolBufferException;
import gs.Gs;
import gscode.GsCode;

import javax.persistence.Entity;
import javax.persistence.PostLoad;
import javax.persistence.Transient;
import java.util.UUID;

@Entity(name = "MaterialFactory")
public class MaterialFactory extends FactoryBase {
    public MaterialFactory() {
    }

    @Entity
    public final static class Line extends LineBase {
        protected Line(){}

        @Override
        public ItemKey newItemKey(UUID producerId, int qty) {
            return new ItemKey(item);
        }

        public Line(MetaMaterial item, int targetNum, int workerNum, int itemLevel) {
            super(item, targetNum, workerNum, itemLevel);
        }
    }

    public MaterialFactory(MetaMaterialFactory meta, Coordinate pos, UUID ownerId) {
        super(meta, pos, ownerId);
        this.meta = meta;
    }

    @Transient
    private MetaMaterialFactory meta;

    @PostLoad
    protected void _1() throws InvalidProtocolBufferException {
        super._1();
        this.meta = (MetaMaterialFactory) super.metaBuilding;
    }

    @Override
    protected boolean shelfAddable(ItemKey k) {
        return k.meta instanceof MetaMaterial;
    }

    @Override
    public Gs.MaterialFactory detailProto() {
        Gs.MaterialFactory.Builder builder = Gs.MaterialFactory.newBuilder().setInfo(super.toProto());
        builder.setStore(this.store.toProto());
        builder.setShelf(this.shelf.toProto());
        this.lines.values().forEach(line -> builder.addLine(line.toProto()));
        return builder.build();
    }
    @Override
    public void appendDetailProto(Gs.BuildingSet.Builder builder) {
        builder.addMaterialFactory(this.detailProto());
    }

    @Override
    protected void enterImpl(Npc npc) {

    }

    @Override
    protected void leaveImpl(Npc npc) {

    }

    @Override
    protected LineBase addLineImpl(MetaItem item, int workerNum, int targetNum, int itemLevel) {
        if(!(item instanceof MetaMaterial) || workerNum > this.freeWorkerNum() || workerNum < meta.lineMinWorkerNum || workerNum > meta.lineMaxWorkerNum)
            return null;
        Line line = new Line((MetaMaterial)item, targetNum, workerNum, itemLevel);
        lines.put(line.id, line);
        return line;
    }

    @Override
    protected boolean consumeMaterial(LineBase line) {
        return true;
    }

//    protected void _update(long diffNano) {
//        super._update(diffNano);
//    }
}
