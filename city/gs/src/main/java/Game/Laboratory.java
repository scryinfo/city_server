package Game;

import com.google.protobuf.Message;
import org.bson.Document;
import org.bson.types.ObjectId;

public class Laboratory extends Building {
    public Laboratory(MetaLaboratory meta, Coord pos, ObjectId ownerId) {
        super(meta, pos, ownerId);
    }

    public Laboratory(MetaLaboratory meta, Document d) {
        super(meta, d);
    }

    @Override
    public Message detailProto() {
        return null;
    }
}
