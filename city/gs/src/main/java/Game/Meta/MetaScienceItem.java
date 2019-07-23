package Game.Meta;


import org.bson.Document;

import javax.persistence.AttributeConverter;

/*研究所商品元数据*/
public class MetaScienceItem extends MetaItem {

    public MetaScienceItem(Document d) {
        super(d);
    }

  /*  public static final class Converter implements AttributeConverter<MetaScienceItem, Integer>
    {
        @Override
        public Integer convertToDatabaseColumn(MetaScienceItem attribute) {
            return attribute.id;
        }

        @Override
        public MetaScienceItem convertToEntityAttribute(Integer dbData) {
            return MetaData.getScienceItem(dbData);
        }
    }*/
}
