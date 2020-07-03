package Game.Meta;


import org.bson.Document;

import javax.persistence.AttributeConverter;

/*Institute Product Options*/
public class MetaScienceItem extends MetaItem {
    public MetaScienceItem(Document d) {
        super(d);
    }

    public static boolean isItem(int id) {
        return id / MetaData.ID_RADIX ==MetaItem.SCIENCE ;
    }
}
