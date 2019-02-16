package Game.Meta;

import Game.Coordinate;
import Shared.Util;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class MetaGroundAuction {
//    public static final class Converter implements AttributeConverter<MetaGroundAuction, UUID> {
//        @Override
//        public UUID convertToDatabaseColumn(MetaGroundAuction attribute) {
//            return attribute.id;
//        }
//
//        @Override
//        public MetaGroundAuction convertToEntityAttribute(UUID dbData) {
//            return MetaData.getGroundAuction(dbData);
//        }
//    }
    MetaGroundAuction(Document d) {
        this.id = d.getInteger("_id");
        List<Integer> index = (List<Integer>) d.get("area");
        if(index.size() % 2 != 0)  {
            System.out.println("Game.Meta.MetaGroundAuction area size is not odd!");
            System.exit(-1);
        }
        for(int i = 0; i < index.size(); i+=2)
            this.area.add(new Coordinate(index.get(i), index.get(i+1)));
        this.beginTime = TimeUnit.SECONDS.toMillis(d.getLong("time"));
        this.price = d.getInteger("price");
    }

    public int id;
    public List<Coordinate> area = new ArrayList<>();
    public long beginTime;
    public int price;
}
