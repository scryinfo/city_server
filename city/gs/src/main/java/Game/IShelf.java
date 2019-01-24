package Game;

public interface IShelf {

    boolean addshelf(Item mi, int price);

    boolean delshelf(ItemKey id, int n, boolean unLock);

    Shelf.Content getContent(ItemKey id);

    boolean setPrice(ItemKey id, int price);
}
