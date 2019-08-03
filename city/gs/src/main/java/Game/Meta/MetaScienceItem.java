package Game.Meta;


import org.bson.Document;

import javax.persistence.AttributeConverter;

/*研究所商品选项*/
public class MetaScienceItem extends MetaItem {
    public MetaScienceItem(Document d) {
        super(d);
    }

    public static boolean isItem(int id) {
        return id / MetaData.ID_RADIX ==MetaItem.SCIENCE ;
    }
}
