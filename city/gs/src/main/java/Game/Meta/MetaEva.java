package Game.Meta;

import org.bson.Document;

public class MetaEva {
	
	public long cexp;//Current experience
	public int lv;   //level
	public long b;   //Brand value
	public int bt;   //Class b
	public int at;   //Class a
	
    public MetaEva(Document d) {
    	super();
        this.cexp = d.getLong("cexp");
        this.lv = d.getInteger("lv");
        this.b = d.getLong("b");
        this.bt = d.getInteger("bt");
        this.at = d.getInteger("at");
    }
}
