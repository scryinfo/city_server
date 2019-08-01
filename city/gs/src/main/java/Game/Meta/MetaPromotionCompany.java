package Game.Meta;

import org.bson.Document;
/*新版推广公司*/
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