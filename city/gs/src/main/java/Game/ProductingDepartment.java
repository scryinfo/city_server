package Game;

import org.bson.Document;
import org.bson.types.ObjectId;

public class ProductingDepartment extends Building {
    private MetaProductingDepartment meta;
    public ProductingDepartment(MetaProductingDepartment meta, Coord pos, ObjectId ownerId) {
        super(meta, pos, ownerId);
        this.meta = meta;
    }

    public ProductingDepartment(MetaProductingDepartment meta, Document d) {
        super(meta, d);
        this.meta = meta;
    }
}
