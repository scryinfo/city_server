package Game;

import com.google.protobuf.Message;

import javax.persistence.Entity;
import javax.persistence.PostLoad;
import javax.persistence.Transient;
import java.util.UUID;

@Entity(name = "MaterialFactory")
public class MaterialFactory extends Building{
    public MaterialFactory(MetaMaterialFactory meta, Coord pos, UUID ownerId) {
        super(meta, pos, ownerId);
        this.meta = meta;
    }

    @Transient
    private MetaMaterialFactory meta;
    @PostLoad
    private void _1() {
        this.meta = MetaData.getMaterialFactory(this._d.metaId);
        this.metaBuilding = this.meta;
    }
    @Override
    public Message detailProto() {
        return null;
    }
}
