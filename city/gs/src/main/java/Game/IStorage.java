package Game;

import java.util.UUID;

public interface IStorage {
    boolean reserve(MetaItem m, int n);
    boolean lock(MetaItem m, int n);
    boolean unLock(MetaItem m, int n);
    void consumeLock(MetaItem m, int n);
    void consumeReserve(MetaItem m, int n);
    // persist building id in order but no in reverse. If server restarted, build the order id which keep in building
    void markOrder(UUID orderId);
    void clearOrder(UUID orderId);
}
