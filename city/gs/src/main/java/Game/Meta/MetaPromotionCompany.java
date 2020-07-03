package Game.Meta;

import org.bson.Document;
/*New promotion company*/
public class MetaPromotionCompany extends MetaBuilding{
    public MetaPromotionCompany(Document d) {
        super(d);
        this.lineNum = d.getInteger("lineNum");
        this.lineMinWorkerNum = d.getInteger("lineMinWorkerNum");
        this.lineMaxWorkerNum = d.getInteger("lineMaxWorkerNum");
    }
    public int lineNum;
    public int lineMaxWorkerNum;
    public int lineMinWorkerNum;
}
