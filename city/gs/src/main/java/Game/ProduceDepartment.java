package Game;

import DB.Db;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import gs.Gs;

import javax.persistence.*;
import java.util.UUID;

@Entity(name = "ProduceDepartment")
public class ProduceDepartment extends FactoryBase {
    @Transient
    private MetaProduceDepartment meta;

    public ProduceDepartment(MetaProduceDepartment meta, Coord pos, UUID ownerId) {
        super(meta, pos, ownerId);
        this.meta = meta;
    }

    public ProduceDepartment() {
    }
    @Entity
    public final static class Line extends LineBase {
//        //public Line(Db.Lines.Line d) {
//            super(d);
//        }
        public Line(MetaGood item, int targetNum, int workerNum) {
            super(item, targetNum, workerNum);
        }


        public Line() {
        }
    }

//    @Embeddable
//    protected static class _D { // private will cause JPA meta class generate fail
//        @Column(name = "line")
//        private byte[] lineBinary;
//        void dirtyLine() {
//            lineBinary = null;
//        }
//        boolean dirty() {
//            return lineBinary == null;
//        }
//    }
//    @Embedded
//    private final _D _d = new _D();
    @PrePersist
    @PreUpdate
    protected void _2() {
//        Db.Lines.Builder builder = Db.Lines.newBuilder();
//        this.lines.forEach((k, v)->builder.addLine(v.toDbProto()));
//        this._d.lineBinary = builder.build().toByteArray();
    }
    @PostLoad
    protected void _1() throws InvalidProtocolBufferException {
        super._1();
        this.meta = (MetaProduceDepartment) super.metaBuilding;
//        for(Db.Lines.Line l : Db.Lines.parseFrom((this._d.lineBinary)).getLineList()) {
//            Line line = new Line(l);
//            this.lines.put(line.id, line);
//        }
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
       // _d.dirtyLine();
        return line;
    }
    protected void _update(long diffNano) {
        super._update(diffNano);

    }
}
