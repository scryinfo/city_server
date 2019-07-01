package Game;

import Game.Meta.MetaData;
import Game.Meta.MetaItem;

import java.util.UUID;

public interface IStorage {
    boolean reserve(MetaItem m, int n);
    boolean lock(ItemKey m, int n);
    boolean unLock(ItemKey m, int n);
    Storage.AvgPrice consumeLock(ItemKey m, int n);
    void consumeReserve(ItemKey m, int n, int price);
    // persist building id in order but no in reverse. If server restarted, build the order id which keep in building
    void markOrder(UUID orderId);
    void clearOrder(UUID orderId);
    static IStorage get(UUID bid, Player p) {
        if(p != null && Player.BAG_ID.equals(bid)) {
            return p.getBag();
        }
        else {
            Building building = City.instance().getBuilding(bid);
            if (building == null || !building.canUseBy(p.id()) || !(building instanceof IStorage) || building.outOfBusiness())
                return null;
            return (IStorage) building;
        }
    }
    static double distance(IStorage src, IStorage dst) {
        if(src == dst)
            return 0;
        Coordinate a, b;
        if(src instanceof Building)
            a = ((Building)src).coordinate();
        else
            a = MetaData.getSysPara().centerStorePos;

        if(dst instanceof Building)
            b = ((Building)dst).coordinate();
        else
            b = MetaData.getSysPara().centerStorePos;
        return Coordinate.distance(a, b);
    }
    boolean delItem(ItemKey mi);
    int availableQuantity(MetaItem m);
    boolean has(ItemKey m, int n);

    boolean offset(ItemKey item, int n);
    boolean offset(MetaItem item, int n, UUID pid, int typeId);
    boolean delItem(Item item);
    int getItemCount(ItemKey key);//获取当前不同商品种类的数量
}
