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
        this.auctionDelay = d.getInteger("groundAuctionSec")*1000;
        this.minersCostRatio = d.getInteger("minersCostRatio")/10000.0;
    }
    final public int playerBagCapcaity;
    final public int bagCapacityDelta;
    final public int transferChargeRatio;
    final public int auctionDelay;
    final public double minersCostRatio;//矿工费用
    public Coordinate centerStorePos = new Coordinate();
}
