package Game.Meta;

import org.bson.Document;

import javax.persistence.AttributeConverter;

public class GoodFormula {
    public static final class Converter implements AttributeConverter<GoodFormula, Integer> {
        @Override
        public Integer convertToDatabaseColumn(GoodFormula attribute) {
            return attribute.id;
        }

        @Override
        public GoodFormula convertToEntityAttribute(Integer dbData) {
            return MetaData.getFormula(dbData);
        }
    }
    GoodFormula(Document d) {
        id = d.getInteger("good");
        for(int i = 0; i < material.length; ++i) {
            material[i] = new Info();
            material[i].item = MetaData.getItem(d.getInteger("material" + i));
            material[i].n = d.getInteger("num" + i);
        }
    }
    public int id;
    public static final class Info {
        public MetaItem item;
        public int n;
    }
    public Info[] material = new Info[3];
}
