package Game.Meta;

import org.bson.Document;

public class MetaRetailShop extends MetaPublicFacility {
    public int saleTypeNum;
    public int storeCapacity;
    public int shelfCapacity;
    MetaRetailShop(Document d) {
        super(d);
        this.saleTypeNum = d.getInteger("saleTypeNum");
        this.storeCapacity = d.getInteger("storeCapacity");
        this.shelfCapacity = d.getInteger("shelfCapacity");
    }
}
