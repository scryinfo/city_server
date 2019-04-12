package Game.Meta;

import org.bson.Document;

import java.util.Map;
import java.util.TreeMap;

public final class MetaGood extends MetaItem
{
    public static final int LUX_SIZE = 4;
    private static final Map<Integer, Type> mapping = new TreeMap<>();
    static {
        mapping.put(51, Type.MAIN_FOOD);
        mapping.put(52, Type.SUB_FOOD);
        mapping.put(53, Type.CLOTHING);
        mapping.put(54, Type.ACCESSORY);
        mapping.put(55, Type.SPORT);
        mapping.put(56, Type.DIGITAL);
    }
    public static Type goodType(int id) {
        return mapping.get(id);
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
    }
    public int lux;
    public static boolean legalCategory(int category) {
        return mapping.keySet().contains(category);
    }
}
