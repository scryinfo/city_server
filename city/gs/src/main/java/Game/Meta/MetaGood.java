package Game.Meta;

import org.bson.Document;

import java.util.Map;
import java.util.TreeMap;

public final class MetaGood extends MetaItem
{
    private static Map<Integer, Type> mapping = new TreeMap<>();
    static {
        int base = 51;
        for (int i = 0; i < Type.ALL.ordinal(); i++) {
            mapping.put(base+i, Type.values()[i]);
        }
    }
    public static final int LUX_SIZE = 4;
    public static Type goodType(int id) {
        return mapping.get(category(id));
    }

    public static int category(int id) {
        return (MetaItem.baseId(id))%100;
    }

    public enum Type {
        MAIN_FOOD,
        SUB_FOOD,
        CLOTHING,
        ACCESSORY,
        SPORT,
        DIGITAL,
        ALL
    }
    MetaGood(Document d) {
        super(d);
        this.lux = d.getInteger("lux");
        this.brand = d.getInteger("brand");
        this.quality = d.getInteger("quality");
    }
    public int lux;
    public int brand;
    public int quality;
    public int type;
    public static boolean legalCategory(int category) {
        return mapping.keySet().contains(category);
    }
}
