package Game.Meta;

import org.bson.Document;

public class MetaPublicFacility extends MetaBuilding {
    public int adNum;
    public int qty;
    public int maxNpcFlow;
    public int minDayToRent;
    public int maxDayToRent;
    public int maxRentPreDay;
    public int depositRatio;
    MetaPublicFacility(Document d) {
        super(d);
        this.adNum = d.getInteger("adNum");
        this.qty = d.getInteger("qty");
        this.maxNpcFlow = d.getInteger("maxNpcFlow");
        this.minDayToRent = d.getInteger("minDayToRent");
        this.maxDayToRent = d.getInteger("maxDayToRent");
        this.maxRentPreDay = d.getInteger("maxRentPreDay");
        this.depositRatio = d.getInteger("depositRatio");
    }
}
