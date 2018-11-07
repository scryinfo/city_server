package Game;

import com.google.protobuf.InvalidProtocolBufferException;
import gs.Gs;

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

    @Override
    public boolean delItem(MetaItem mi) {
        return this.store.delItem(mi);
    }

    @Entity
    public final static class Line extends LineBase {
        public Line(MetaGood item, int targetNum, int workerNum) {
            super(item, targetNum, workerNum);
        }
        protected Line() {}
    }

    @PostLoad
    protected void _1() throws InvalidProtocolBufferException {
        super._1();
        this.meta = (MetaProduceDepartment) super.metaBuilding;
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
    public LineBase addLine(MetaItem item) {
        if(item instanceof MetaGood)
            return null;
        Line line = new Line((MetaGood)item,0,0);
        lines.put(line.id, line);
        return line;
    }
    protected void _update(long diffNano) {
        super._update(diffNano);
    }
}
