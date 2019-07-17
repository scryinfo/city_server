package Game;

import java.util.Map;

public interface IShelf {

    boolean addshelf(Item mi, int price, boolean autoReplenish);

    boolean delshelf(ItemKey id, int n, boolean unLock);

    Shelf.Content getContent(ItemKey id);

    boolean setPrice(ItemKey id, int price);
    boolean shelfSet(Item item, int price,boolean autoRepOn);
    boolean setAutoReplenish(ItemKey id, boolean autoRepOn);

    int getSaleCount(int itemId);

    static void updateAutoReplenish(IShelf shelf, ItemKey k){
        //更新货架： 执行一次下架上架操作
        Shelf.Content i = shelf.getContent(k);
        IStorage storage = (IStorage) shelf;
        if(i != null){
            shelf.delshelf(k, i.n, true);
        }
        Item itemInStore = new Item(k,storage.getItemCount(k));
        shelf.addshelf(itemInStore,i.price, i.autoReplenish);
    }
    Map<Item, Integer> getSaleDetail(int itemId);

    int getTotalSaleCount();//获取总的出售数量
}