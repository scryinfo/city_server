package Game.Meta;

import org.bson.Document;

public class AIBuyRepeatedly {
    AIBuyRepeatedly(Document d) {
        id = d.getObjectId("_id").toString();
        category = d.getInteger("category");
        lux = d.getInteger("lux");
        radio = d.getInteger("radio");
    }
    final String id;
    final int category;
    final int lux;
    final int radio;
}
