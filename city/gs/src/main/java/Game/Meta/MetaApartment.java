package Game.Meta;

import org.bson.Document;

public class MetaApartment extends MetaBuilding {
    MetaApartment(Document d) {
        super(d);
        this.npc = d.getInteger("npc");
        this.qty = d.getInteger("qty");
    }
	public int npc;
    public int qty;
}
