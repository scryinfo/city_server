package Game;

import com.google.protobuf.Message;
import gs.Gs;

import javax.persistence.Entity;
import javax.persistence.PostLoad;
import javax.persistence.Transient;
import java.util.UUID;

@Entity
public class RetailShop extends Building {
    public RetailShop(MetaRetailShop meta, Coordinate pos, UUID ownerId) {
        super(meta, pos, ownerId);
        this.meta = meta;
        this.qty = meta.qty;
    }
    private int qty;
    @Transient
    private MetaRetailShop meta;

    protected RetailShop() {}

    @PostLoad
    private void _1() {
        //this.meta = MetaData.getRetailShop(this._d.metaId);
        //this.metaBuilding = this.meta;
        this.meta = (MetaRetailShop) super.metaBuilding;
    }

    @Override
    public Message detailProto() {
        return null;
    }

    @Override
    public void appendDetailProto(Gs.BuildingSet.Builder builder) {

    }

    @Override
    protected void visitImpl(Npc npc) {

    }

    @Override
    protected void _update(long diffNano) {

    }
}
