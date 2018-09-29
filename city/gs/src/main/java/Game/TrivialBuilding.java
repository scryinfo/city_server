package Game;

import com.google.protobuf.Message;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.UUID;

public class TrivialBuilding extends Building {
    private MetaBuilding meta;
    public TrivialBuilding(MetaBuilding meta, Coord pos, UUID ownerId) {
        super(meta, pos, ownerId);
        this.meta = meta;
    }

    @Override
    public Message detailProto() {
        return null;
    }
}
