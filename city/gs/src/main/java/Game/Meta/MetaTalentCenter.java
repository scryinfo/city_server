package Game.Meta;

import org.bson.Document;

public class MetaTalentCenter extends MetaBuilding {
    public int qty;
    public int createSecPreWorker;
    MetaTalentCenter(Document d) {
        super(d);
        this.qty = d.getInteger("qty");
        this.createSecPreWorker = d.getInteger("createSecPreWorker");
    }
}
