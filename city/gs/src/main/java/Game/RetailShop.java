package Game;

import com.google.protobuf.Message;

import javax.persistence.Entity;
import javax.persistence.PostLoad;
import javax.persistence.Transient;
import java.util.UUID;

@Entity(name = "RetailShop")
public class RetailShop extends Building {
    public RetailShop(MetaRetailShop meta, Coord pos, UUID ownerId) {
        super(meta, pos, ownerId);
        this.meta = meta;
    }

    @Transient
    private MetaRetailShop meta;

    public RetailShop() {
    }

    @PostLoad
    private void _1() {
        this.meta = MetaData.getRetailShop(this._d.metaId);
        this.metaBuilding = this.meta;
    }

    @Override
    public Message detailProto() {
        return null;
    }

    @Override
    protected void _update(long diffNano) {

    }
}
