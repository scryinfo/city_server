package Game.Meta;

import Game.Coordinate;
import org.bson.Document;

public class SysPara {
    public SysPara(Document d) {
        this.playerBagCapcaity = d.getInteger("playerBagCapacity");
        this.bagCapacityDelta = d.getInteger("bagCapacityDelta");
        this.transferChargeRatio = d.getInteger("transferChargeRatio");
        this.centerStorePos.x = d.getInteger("centerStoreX");
        this.centerStorePos.y = d.getInteger("centerStoreY");
    }
    public int playerBagCapcaity;
    public int bagCapacityDelta;
    public int transferChargeRatio;
    public Coordinate centerStorePos = new Coordinate();
}
