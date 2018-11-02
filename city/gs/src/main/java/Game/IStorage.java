package Game;

import Shared.Util;

import java.util.UUID;

public interface IStorage extends ISessionCache {
    boolean reserve(MetaItem m, int n);
    boolean lock(MetaItem m, int n);
    boolean unLock(MetaItem m, int n);
    void consumeLock(MetaItem m, int n);
    void consumeReserve(MetaItem m, int n);
    // persist building id in order but no in reverse. If server restarted, build the order id which keep in building
    void markOrder(UUID orderId);
    void clearOrder(UUID orderId);
    static IStorage get(UUID bid, Player p) {
        if(p != null && Util.bagId.equals(bid)) {
            return p.getBag();
        }
        else {
            Building building = City.instance().getBuilding(bid);
            if (building == null || !building.canUseBy(p.id()) || !(building instanceof IStorage))
                return null;
            return (IStorage) building;
        }
    }
}
