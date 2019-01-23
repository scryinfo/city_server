package Game.Meta;

import Game.Meta.MetaItem;
import org.bson.Document;

public final class MetaGood extends MetaItem
{
    public static final int LUX_SIZE = 4;
    public static Type goodType(int id) {
        Type res;
        switch ((MetaItem.baseId(id))%100) {
            case 51:
                res = Type.MAIN_FOOD;
                break;
            case 52:
                res = Type.SUB_FOOD;
                break;
            case 53:
                res = Type.CLOTHING;
                break;
            case 54:
                res = Type.ACCESSORY;
                break;
            case 55:
                res = Type.SPORT;
                break;
            case 56:
                res = Type.DIGITAL;
                break;
            default:
                throw new IllegalArgumentException(Integer.toString(id));
        }
        return res;
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
}
