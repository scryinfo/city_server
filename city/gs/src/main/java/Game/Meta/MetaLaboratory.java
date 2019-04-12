package Game.Meta;

import org.bson.Document;

public class MetaLaboratory extends MetaBuilding {

    MetaLaboratory(Document d) {
        super(d);
        this.evaProb = d.getInteger("evaProb");
        this.goodProb = d.getInteger("goodProb");
    }

    public int evaProb;
    public int goodProb;
}
