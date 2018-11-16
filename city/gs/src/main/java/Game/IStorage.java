package Game;

import java.util.UUID;

public interface IStorage {
    boolean reserve(MetaItem m, int n);
    boolean lock(ItemKey m, int n);
    boolean unLock(ItemKey m, int n);
    void consumeLock(ItemKey m, int n);
    void consumeReserve(ItemKey m, int n);
    // persist building id in order but no in reverse. If server restarted, build the order id which keep in building
    void markOrder(UUID orderId);
    void clearOrder(UUID orderId);
    static IStorage get(UUID bid, Player p) {
        if(p != null && Player.BAG_ID.equals(bid)) {
            return p.getBag();
        }
        else {
            Building building = City.instance().getBuilding(bid);
            if (building == null || !building.canUseBy(p.id()) || !(building instanceof IStorage))
                return null;
            return (IStorage) building;
        }
    }

    boolean delItem(ItemKey mi);
}
