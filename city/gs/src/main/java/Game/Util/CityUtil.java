package Game.Util;

import Game.*;
import Game.Meta.MetaData;
import Shared.LogDb;
import com.mongodb.Block;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import org.bson.Document;

import java.util.*;

import static com.mongodb.client.model.Filters.*;


public class CityUtil {

    //获取玩家的性别信息
    public static Map<String,Integer> genderSex(List<Player> players){
        Map<String, Integer> sex = new HashMap<>();
        int man=0;
        int woman=0;
        for (Player p : players) {
            if(p.isMale())
                man++;
            else
                woman++;
        }
        sex.put("girl", woman);
        sex.put("boy", man);
        return sex;
    }
}
