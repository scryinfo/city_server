package Game.Meta;

import org.bson.Document;

import javax.persistence.AttributeConverter;

public abstract class MetaItem {
    public static final int MATERIAL = 21;
    public static final int GOOD = 22;
    public static final int SCIENCE = 15;//科技点数类型
    public static final int PROMOTE = 16;//市场数据类型
    public static int level(int id) {
        return id % 1000;
    }
    public static int baseId(int id) {
        return id / 1000;
    }
    public MetaItem(Document d) {
        this.id = d.getInteger("_id");
        this.n = d.getDouble("numOneSec") /100000;
        this.useDirectly = d.getBoolean("default");
        this.size = d.getInteger("size");
    }
    public int id;
    public double n;
    public int size;
    public boolean useDirectly;

    public static int type(int targetId) {
        return targetId / MetaData.ID_RADIX;
    }

    public static final class Converter implements AttributeConverter<MetaItem, Integer>
    {
        @Override
        public Integer convertToDatabaseColumn(MetaItem attribute) {
            return attribute.id;
        }

        @Override
        public MetaItem convertToEntityAttribute(Integer dbData) {
            return MetaData.getItem(dbData);
        }
    }
    public static boolean isItem(int id) {
        return id / MetaData.ID_RADIX >= MATERIAL;
    }
    public static int scienceItemId(int id){/*获取研究所、推广公司类型id*/
        return id % MetaData.ID_RADIX;
    }

    /*是否是Eva点数商品*/
    public static boolean isScienceItem(int id) {
        return id / MetaData.ID_RADIX >= SCIENCE && id / MetaData.ID_RADIX<=PROMOTE;
    }
}
