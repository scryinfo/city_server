package Game;

import org.bson.Document;
import org.bson.types.ObjectId;

public class RetailShop extends Building {
    public RetailShop(MetaRetailShop meta, Coord pos, ObjectId ownerId) {
        super(meta, pos, ownerId);
        this.meta = meta;
    }

    public RetailShop(MetaRetailShop meta, Document d) {
        super(meta, d);
        this.meta = meta;
    }
    private MetaRetailShop meta;
}
