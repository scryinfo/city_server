package Game;

import com.google.protobuf.Message;
import org.bson.Document;
import org.bson.types.ObjectId;

import javax.persistence.Entity;
import java.util.UUID;

@Entity(name = "RetailShop")
public class RetailShop extends Building {
    public RetailShop(MetaRetailShop meta, Coord pos, UUID ownerId) {
        super(meta, pos, ownerId);
        this.meta = meta;
    }

    private MetaRetailShop meta;

    @Override
    public Message detailProto() {
        return null;
    }
}
