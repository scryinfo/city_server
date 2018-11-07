package Game;

import com.google.protobuf.Message;
import gs.Gs;

import javax.persistence.Entity;
import javax.persistence.PostLoad;
import javax.persistence.Transient;
import java.util.UUID;

@Entity(name = "Laboratory")
public class Laboratory extends Building {
    @Transient
    private MetaLaboratory meta;

    public Laboratory(MetaLaboratory meta, Coordinate pos, UUID ownerId) {
        super(meta, pos, ownerId);
    }

    public Laboratory() {
    }

    @PostLoad
    private void _1() {
        //this.meta = MetaData.getLaboratory(this._d.metaId);
       // this.metaBuilding = this.meta;
        this.meta = (MetaLaboratory) super.metaBuilding;
    }
    @Override
    public Message detailProto() {
        return null;
    }

    @Override
    public void appendDetailProto(Gs.BuildingSet.Builder builder) {

    }

    @Override
    protected void _update(long diffNano) {

    }
}
