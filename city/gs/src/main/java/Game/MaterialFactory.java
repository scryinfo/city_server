package Game;

import com.google.protobuf.InvalidProtocolBufferException;
import gs.Gs;

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
        public Line(MetaMaterial item, int targetNum, int workerNum) {
            super(item, targetNum, workerNum);
        }
    }

    public MaterialFactory(MetaMaterialFactory meta, Coordinate pos, UUID ownerId) {
        super(meta, pos, ownerId);
        this.meta = meta;
    }
    @Override
    public boolean delItem(MetaItem mi) {
        return this.store.delItem(mi);
    }
    @Transient
    private MetaMaterialFactory meta;

    @PostLoad
    protected void _1() throws InvalidProtocolBufferException {
        super._1();
        this.meta = (MetaMaterialFactory) super.metaBuilding;
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
    public LineBase addLine(MetaItem item) {
        if(!(item instanceof MetaMaterial))
            return null;
        Line line = new Line((MetaMaterial)item,0,0);
        lines.put(line.id, line);
        return line;
    }
    protected void _update(long diffNano) {
        super._update(diffNano);
    }
}
