package Game.Meta;

import Game.Coordinate;
import Shared.Util;
import gs.Gs;
import org.bson.Document;

import javax.persistence.AttributeConverter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class MetaGroundAuction {
    public static final class Converter implements AttributeConverter<MetaGroundAuction, UUID> {
        @Override
        public UUID convertToDatabaseColumn(MetaGroundAuction attribute) {
            return attribute.id;
        }

        @Override
        public MetaGroundAuction convertToEntityAttribute(UUID dbData) {
            return MetaData.getGroundAuction(dbData);
        }
    }
    MetaGroundAuction(Document d) {
        this.id = Util.toUuid(d.getObjectId("_id"));
        List<Integer> index = (List<Integer>) d.get("area");
        if(index.size() % 2 != 0)  {
            System.out.println("Game.Meta.MetaGroundAuction area size is not odd!");
            System.exit(-1);
        }
        for(int i = 0; i < index.size(); i+=2)
            this.area.add(new Coordinate(index.get(i), index.get(i+1)));
        this.beginTime = TimeUnit.SECONDS.toMillis(d.getLong("time"));
        this.endTime = this.beginTime + TimeUnit.SECONDS.toMillis(d.getInteger("duration"));
        this.price = d.getInteger("price");
    }

    public Gs.MetaGroundAuction.Target toProto() {
        Gs.MetaGroundAuction.Target.Builder b = Gs.MetaGroundAuction.Target.newBuilder()
                .setId(Util.toByteString(id))
                .setBeginTime(beginTime)
                .setDurationSec((int) (endTime - beginTime))
                .setBasePrice(price);
        for(Coordinate c : area) {
            b.addArea(c.toProto());
        }
        return b.build();
    }
    public UUID id;    // use to check this data has been changed or not, compare it with player db(if anyone bid it)
    public List<Coordinate> area = new ArrayList<>();
    public long beginTime;
    public long endTime;
    public int price;
}
