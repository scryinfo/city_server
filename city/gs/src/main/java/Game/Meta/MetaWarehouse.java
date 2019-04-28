package Game.Meta;

import org.bson.Document;

/**
 * @Description:集散中心（仓库）
 * @Author: yty
 * @CreateDate: 2019/4/4 15:20
 * @UpdateRemark: 更新内容：
 * @Version: 1.0
 */
public class MetaWarehouse extends MetaBuilding {
    public int storeCapacity;//仓库容量
    public int shelfCapacity;//货架容量
    public int maxHourToRent;//最大出租时间
    public int minHourToRent;//最小出租时间


    public MetaWarehouse(Document d) {
        super(d);
        this.storeCapacity = d.getInteger("storeCapacity");
        this.shelfCapacity = d.getInteger("shelfCapacity");
        this.maxHourToRent = d.getInteger("maxHourToRent");
        this.minHourToRent = d.getInteger("minHourToRent");
    }
}
