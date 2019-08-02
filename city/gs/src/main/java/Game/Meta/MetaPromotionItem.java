package Game.Meta;

import org.bson.Document;
/*推广公司推广选项*/
public class MetaPromotionItem extends MetaItem{
    public MetaPromotionItem(Document d) {
        super(d);
    }

    public static boolean isItem(int id) {
        return id / MetaData.ID_RADIX ==MetaItem.PROMOTE ;
    }

}
