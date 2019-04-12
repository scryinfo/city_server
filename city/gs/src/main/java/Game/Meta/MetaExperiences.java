package Game.Meta;

import org.bson.Document;

public class MetaExperiences {
	
	public int lv;  //级别
	public long exp;//经验值
	public int p;   //百分比值
	
    public MetaExperiences(Document d) {
    	super();
        this.lv = d.getInteger("lv");
        this.exp = d.getLong("exp");
        this.p = d.getInteger("p");
    }
}
