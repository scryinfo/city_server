package Game.Meta;

import org.bson.Document;

public abstract class MetaFactoryBase extends MetaBuilding {
    public int lineNum;
    public int lineMaxWorkerNum;
    public int lineMinWorkerNum;
    public int storeCapacity;
    public int shelfCapacity;

    MetaFactoryBase(Document d) {
        super(d);
        this.lineNum = d.getInteger("lineNum");
        this.lineMinWorkerNum = d.getInteger("lineMinWorkerNum");
        this.lineMaxWorkerNum = d.getInteger("lineMaxWorkerNum");
        this.storeCapacity = d.getInteger("storeCapacity");
        this.shelfCapacity = d.getInteger("shelfCapacity");
    }
}
