package Game;

import DB.Db;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import gs.Gs;

import javax.persistence.*;
import java.util.UUID;

@Entity(name = "ProductingDepartment")
public class ProductingDepartment extends FactoryBase {
    @Transient
    private MetaProductingDepartment meta;

    public ProductingDepartment(MetaProductingDepartment meta, Coord pos, UUID ownerId) {
        super(meta, pos, ownerId);
        this.meta = meta;
    }

    public ProductingDepartment() {
    }

    protected boolean isDirty() {
        return super.isDirty() && _d.dirty();
    }
    public final class Line extends LineBase {

        public Line(Db.Lines.Line d) {
            super(d);
        }

        public Line(MetaGood item, int targetNum, int workerNum) {
            super(item, targetNum, workerNum);
        }
    }

    @Embeddable
    private static class _D {
        @Column(name = "line")
        private byte[] lineBinary;
        void dirtyLine() {
            lineBinary = null;
        }
        boolean dirty() {
            return lineBinary == null;
        }
    }
    @Embedded
    private final _D _d = new _D();
    @PrePersist
    @PreUpdate
    protected void _2() {
        super._2();

        Db.Lines.Builder builder = Db.Lines.newBuilder();
        this.lines.forEach((k, v)->builder.addLines(v.toDbProto()));
        this._d.lineBinary = builder.build().toByteArray();
    }
    @PostLoad
    protected void _1() throws InvalidProtocolBufferException {
        super._1();

        for(Db.Lines.Line l : Db.Lines.parseFrom((this._d.lineBinary)).getLinesList()) {
            Line line = new Line(l);
            this.lines.put(line.id, line);
        }
    }
    @Override
    public Message detailProto() {
        Gs.ProductingDepartmentInfo.Builder builder = Gs.ProductingDepartmentInfo.newBuilder().setCommon(super.commonProto());
        builder.addAllStore(this.store.toProto());
        builder.addAllShelf(this.shelf.toProto());
        this.lines.values().forEach(line -> builder.addLine(line.toProto()));
        return builder.build();
    }
    public LineBase addLine(MetaItem item) {
        if(item instanceof MetaGood)
            return null;
        Line line = new Line((MetaGood)item,0,0);
        lines.put(line.id, line);
        _d.dirtyLine();
        return line;
    }
    protected void _update(long diffNano) {
        super._update(diffNano);

    }
}
