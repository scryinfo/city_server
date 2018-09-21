package Game;

import com.google.protobuf.Message;
import org.bson.Document;
import org.bson.types.ObjectId;

public class TrivialBuilding extends Building {
    private MetaBuilding meta;
    public TrivialBuilding(MetaBuilding meta, Coord pos, ObjectId ownerId) {
        super(meta, pos, ownerId);
        this.meta = meta;
    }

    public TrivialBuilding(MetaBuilding meta, Document d) {
        super(meta, d);
        this.meta = meta;
    }

    @Override
    public Message detailProto() {
        return null;
    }
}
