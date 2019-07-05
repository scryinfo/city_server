package Game.Meta;

import org.bson.Document;

final public class MetaMaterial extends MetaItem {
    MetaMaterial(Document d) {
        super(d);
    }
    public static boolean isItem(int id) {
        return id / MetaData.ID_RADIX >= MATERIAL&&id / MetaData.ID_RADIX<GOOD;
    }
}
