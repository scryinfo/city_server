package Game;

import com.google.protobuf.Message;

import javax.persistence.Entity;
import javax.persistence.PostLoad;
import javax.persistence.Transient;
import java.util.UUID;

@Entity(name = "Laboratory")
public class Laboratory extends Building {
    @Transient
    private MetaLaboratory meta;

    public Laboratory(MetaLaboratory meta, Coord pos, UUID ownerId) {
        super(meta, pos, ownerId);
    }
    @PostLoad
    private void _1() {
        this.meta = MetaData.getLaboratory(this._d.metaId);
        this.metaBuilding = this.meta;
    }
    @Override
    public Message detailProto() {
        return null;
    }
}
