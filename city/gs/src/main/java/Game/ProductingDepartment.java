package Game;

import com.google.protobuf.Message;
import org.bson.Document;
import org.bson.types.ObjectId;

import javax.persistence.Entity;
import java.util.UUID;

@Entity(name = "ProductingDepartment")
public class ProductingDepartment extends Building {
    private MetaProductingDepartment meta;
    public ProductingDepartment(MetaProductingDepartment meta, Coord pos, UUID ownerId) {
        super(meta, pos, ownerId);
        this.meta = meta;
    }

    @Override
    public Message detailProto() {
        return null;
    }
}
