package Game.Meta;

import org.bson.Document;

/**
 * @Description:Distribution center (warehouse)
 * @Author: yty
 * @CreateDate: 2019/4/4 15:20
 * @UpdateRemark: update content:
 * @Version: 1.0
 */
public class MetaWarehouse extends MetaBuilding {
    public int storeCapacity;//Warehouse capacity
    public int shelfCapacity;//Shelf capacity
    public int maxHourToRent;//Maximum rental time
    public int minHourToRent;//Minimum rental time
    public int output1P1Hour;//Worker output per hour


    public MetaWarehouse(Document d) {
        super(d);
        this.storeCapacity = d.getInteger("storeCapacity");
        this.shelfCapacity = d.getInteger("shelfCapacity");
        this.maxHourToRent = d.getInteger("maxHourToRent");
        this.minHourToRent = d.getInteger("minHourToRent");
        this.output1P1Hour = d.getInteger("output1P1Hour");
    }
}
