package Game;

import com.google.protobuf.Message;
import org.bson.Document;
import org.bson.types.ObjectId;

public class MaterialFactory extends Building{
    public MaterialFactory(MetaMaterialFactory meta, Coord pos, ObjectId ownerId) {
        super(meta, pos, ownerId);
        this.metaBuilding = meta;
    }
    public MaterialFactory(MetaMaterialFactory meta, Document d) {
        super(meta,
                d);
        this.meta = meta;
    }
    private MetaMaterialFactory meta;

    @Override
    public Message detailProto() {
        return null;
    }
}
