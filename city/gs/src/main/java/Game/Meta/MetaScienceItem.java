package Game.Meta;


import org.bson.Document;

import javax.persistence.AttributeConverter;

/*研究所商品元数据*/
public class MetaScienceItem extends MetaItem {
    public MetaScienceItem(Document d) {
        super(d);
    }
}
