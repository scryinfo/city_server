package Game.Meta;

import org.bson.Document;

public class MetaLaboratory extends MetaBuilding {

    MetaLaboratory(Document d) {
        super(d);
        this.evaProb = d.getInteger("evaProb");
        this.goodProb = d.getInteger("goodProb");
        this.evaTransitionTime = d.getInteger("evaTransitionTime");
        this.inventTransitionTime = d.getInteger("inventTransitionTime");
    }

    public int evaProb;
    public int goodProb;
    public int evaTransitionTime;   //eva study transition time
    public int inventTransitionTime;//Invention transition time

}
