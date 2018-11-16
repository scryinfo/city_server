package Game;

import gs.Gs;

public interface IShelf {

    Gs.Shelf.Content addshelf(Item mi, int price);

    boolean delshelf(ItemKey id, int n);

    Shelf.Content getContent(ItemKey id);

    boolean setPrice(ItemKey id, int price);
}
