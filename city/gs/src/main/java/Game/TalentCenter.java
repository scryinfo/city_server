package Game;

import com.google.protobuf.Message;
import gs.Gs;

import javax.persistence.PostLoad;
import javax.persistence.Transient;
import java.util.UUID;

public class TalentCenter extends Building {
    public TalentCenter(MetaTalentCenter meta, Coordinate pos, UUID ownerId) {
        super(meta, pos, ownerId);
        this.meta = meta;
        this.qty = meta.qty;
    }
    private int qty;
    @Transient
    private MetaTalentCenter meta;

    protected TalentCenter() {}

    @Override
    public int quality() {
        return this.qty;
    }

    @PostLoad
    private void _1() {
        //this.meta = MetaData.getRetailShop(this._d.metaId);
        //this.metaBuilding = this.meta;
        this.meta = (MetaTalentCenter) super.metaBuilding;
    }

    @Override
    public Message detailProto() {
        return null;
    }

    @Override
    public void appendDetailProto(Gs.BuildingSet.Builder builder) {

    }

    @Override
    protected void enterImpl(Npc npc) {

    }

    @Override
    protected void leaveImpl(Npc npc) {

    }

    @Override
    protected void _update(long diffNano) {

    }
}
