package Game;

import java.util.Map;

public interface IShelf {

    boolean addshelf(Item mi, int price);

    boolean delshelf(ItemKey id, int n, boolean unLock);

    Shelf.Content getContent(ItemKey id);

    boolean setPrice(ItemKey id, int price);

    boolean setAutoReplenish(ItemKey id, boolean autoRepOn);

    int getSaleCount(int itemId);

    void updateAutoReplenish(ItemKey k, int count);
    Map<Item, Integer> getSaleDetail(int itemId);
}
