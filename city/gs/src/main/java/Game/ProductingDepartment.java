package Game;

import com.google.protobuf.Message;

import javax.persistence.Entity;
import javax.persistence.PostLoad;
import javax.persistence.Transient;
import java.util.UUID;

@Entity(name = "ProductingDepartment")
public class ProductingDepartment extends Building {
    @Transient
    private MetaProductingDepartment meta;

    @PostLoad
    private void _1() {
        this.meta = MetaData.getProductingDepartment(this._d.metaId);
        this.metaBuilding = this.meta;
    }
    public ProductingDepartment(MetaProductingDepartment meta, Coord pos, UUID ownerId) {
        super(meta, pos, ownerId);
        this.meta = meta;
    }
    @Override
    public Message detailProto() {
        return null;
    }
}
