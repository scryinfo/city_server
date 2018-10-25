package Game;

import java.util.UUID;

public interface IShelf {

    UUID addshelf(MetaItem mi, int num, int price);

    boolean delshelf(UUID id);

    Shelf.ItemInfo getContent(UUID id);

    boolean setNum(UUID id, int num);
}
