package Game.Meta;

import org.bson.Document;

public class MetaTechnology extends MetaBuilding{
    public MetaTechnology(Document d) {
        super(d);
        this.lineNum = d.getInteger("lineNum");
        this.lineMinWorkerNum = d.getInteger("lineMinWorkerNum");
        this.lineMaxWorkerNum = d.getInteger("lineMaxWorkerNum");
        this.minScienceAdd=d.getInteger("minScienceAdd");
        this.maxScienceAdd=d.getInteger("maxScienceAdd");
    }
    public int lineNum;
    public int lineMaxWorkerNum;
    public int lineMinWorkerNum;
    public int minScienceAdd;//Open the treasure box minimum points
    public int maxScienceAdd;//Open the treasure box maximum points


}
