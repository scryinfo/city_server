package Game;

import java.util.Map;

public interface IShelf {

    boolean addshelf(Item mi, int price);

    boolean delshelf(ItemKey id, int n, boolean unLock);

    Shelf.Content getContent(ItemKey id);

    boolean setPrice(ItemKey id, int price);

    int getSaleCount(int itemId);

    Map<Item, Integer> getSaleDetail(int itemId);
}
