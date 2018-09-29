package Game;

import com.google.protobuf.Message;
import org.bson.Document;
import org.bson.types.ObjectId;

import javax.persistence.Entity;
import java.util.UUID;

@Entity(name = "Laboratory")
public class Laboratory extends Building {
    public Laboratory(MetaLaboratory meta, Coord pos, UUID ownerId) {
        super(meta, pos, ownerId);
    }

    @Override
    public Message detailProto() {
        return null;
    }
}
