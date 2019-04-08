package Game.Meta;

import org.bson.Document;

public class MetaEva {
	
	public long cexp;//当前经验值
	public int lv;   //级别
	public long b;   //品牌值
	public int bt;   //b类
	public int at;   //a类
	
    public MetaEva(Document d) {
    	super();
        this.cexp = d.getLong("cexp");
        this.lv = d.getInteger("lv");
        this.b = d.getLong("b");
        this.bt = d.getInteger("bt");
        this.at = d.getInteger("at");
    }
}
