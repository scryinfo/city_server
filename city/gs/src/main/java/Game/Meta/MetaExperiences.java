package Game.Meta;

import org.bson.Document;

public class MetaExperiences {
	
	public int lv;  //level
	public long exp;//Experience
	public int p;   //Percentage value
	
    public MetaExperiences(Document d) {
    	super();
        this.lv = d.getInteger("lv");
        this.exp = d.getLong("exp");
        this.p = d.getInteger("p");
    }
}
