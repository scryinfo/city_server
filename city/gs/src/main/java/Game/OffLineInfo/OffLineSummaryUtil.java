package Game.OffLineInfo;

import Shared.LogDb;
import com.google.protobuf.InvalidProtocolBufferException;
import com.mongodb.Block;
import gs.Gs;
import org.bson.Document;
import org.bson.types.Binary;

import java.util.*;

import static com.mongodb.client.model.Filters.*;

/*Offline notification statistics tool*/
public class OffLineSummaryUtil {

    /*1.Get statistics for offline forecasts of flight forecasts*/
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


    //2.Offline revenue information for buildings
    /*
     * arg1:Player offline time (as the start time of statistical conditions)
     * arg2:Player online time (do i as the end time of the statistical condition)
     * arg3:Player id
     */
    public static Map<Integer,List<OffLineBuildingRecord>> getOffLineBuildingIncome(long unLineTime, long onlineTime, UUID playerId){
        Map<Integer,List<OffLineBuildingRecord>> incomeMap=new HashMap<>();
        //First filter out all the data while the player is offline
        //Raw material factory's shelf revenue
        LogDb.getSellerBuildingIncome().find(and(
                eq("pid", playerId),
                gte("t", unLineTime),
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

    /*Delete offline statistics*/
    public static long delUnLineData(long unLineTime,long onlineTime,UUID playerId){
       LogDb.getSellerBuildingIncome().deleteMany(and(
                eq("pid", playerId),
                gte("t", unLineTime),
                lte("t", onlineTime)
        ));
        return 0;
    }
}
