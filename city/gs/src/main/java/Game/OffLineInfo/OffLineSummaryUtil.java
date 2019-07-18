package Game.OffLineInfo;

import Shared.LogDb;
import com.google.protobuf.InvalidProtocolBufferException;
import com.mongodb.Block;
import gs.Gs;
import org.bson.Document;
import org.bson.types.Binary;

import java.util.*;

import static com.mongodb.client.model.Filters.*;

/*离线通知统计工具*/
public class OffLineSummaryUtil {

    /*1.获取航班预测离线记录的统计*/
    public static List<OffLineFlightRecord> getPlayerFlightForecast(UUID playerId, Long startTime, Long endTime){
        List<OffLineFlightRecord> records = new ArrayList<>();
        LogDb.getFlightBet().find(and(
                eq("i", playerId),
                gte("t",startTime),
                lte("t",endTime)
        )).forEach((Block<? super Document>) d ->{
            try {
                OffLineFlightRecord offLineFlightRecord = new OffLineFlightRecord(
                        d.get("i", UUID.class),
                        d.getBoolean("w"),
                        d.getInteger("s"),
                        Gs.FlightData.parseFrom(d.get("f", Binary.class).getData()));
                records.add(offLineFlightRecord);
            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }
        });
        return records;
    }


    //2.建筑的离线收入信息
    /*
     * arg1:玩家离线时间（作为统计条件的开始时间）
     * arg2:玩家在线时间（做i为统计条件的结束时间）
     * arg3:玩家id
     */
    public static Map<Integer,List<OffLineBuildingRecord>> getOffLineBuildingIncome(long unlineTime, long onlineTime, UUID playerId){
        Map<Integer,List<OffLineBuildingRecord>> incomeMap=new HashMap<>();
        //首先筛选出玩家离线期间的所有数据
        //原料厂的货架收入
        LogDb.getSellerBuildingIncome().find(and(
                eq("pid", playerId),
                gte("t", unlineTime),
                lte("t", onlineTime)
        )).forEach((Block<? super Document>) document ->{
            UUID bid= (UUID) document.get("bid");
            UUID pid = (UUID) document.get("pid");
            Integer n = document.getInteger("n");
            Integer price = document.getInteger("price");
            OffLineBuildingRecord record = new OffLineBuildingRecord(bid, pid, n, price);
            incomeMap.computeIfAbsent(document.getInteger("bt"), k -> new ArrayList<>()).add(record);
        });
        return incomeMap;
    }
}
