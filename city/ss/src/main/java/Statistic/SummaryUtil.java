package Statistic;

import Shared.LogDb;
import Shared.Util;
import com.mongodb.BasicDBObject;
import com.mongodb.Block;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import static com.mongodb.client.model.Filters.*;

import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import org.bson.Document;
import ss.Ss;

import java.util.*;

public class SummaryUtil
{
    public static final long DAY_MILLISECOND = 1000 * 3600 * 24;
    private static final String ID = "id";
    private static final String TYPE = "type";
    private static final String TIME = "time";
    private static final String DAY_SELLGROUND = "daySellGround";
    private static final String DAY_RENTGROUND = "dayRentGround";
    private static final String DAY_TRANSFER = "dayTransfer";
    private static final String DAY_SALARY = "daySalary";
    private static final String DAY_MATERIAL = "dayMaterial";
    private static final String DAY_GOODS = "dayGoods";
    private static final String DAY_RENTROOM = "dayRentRoom";
    private static MongoCollection<Document> daySellGround;
    private static MongoCollection<Document> dayRentGround;
    private static MongoCollection<Document> dayTransfer;
    private static MongoCollection<Document> daySalary;
    private static MongoCollection<Document> dayMaterial;
    private static MongoCollection<Document> dayGoods;
    private static MongoCollection<Document> dayRentRoom;
    public static void init()
    {
        MongoDatabase database = LogDb.getDatabase();
        daySellGround = database.getCollection(DAY_SELLGROUND)
                .withWriteConcern(WriteConcern.UNACKNOWLEDGED);
        dayRentGround = database.getCollection(DAY_RENTGROUND)
                .withWriteConcern(WriteConcern.UNACKNOWLEDGED);
        dayTransfer = database.getCollection(DAY_TRANSFER)
                .withWriteConcern(WriteConcern.UNACKNOWLEDGED);
        daySalary = database.getCollection(DAY_SALARY)
                .withWriteConcern(WriteConcern.UNACKNOWLEDGED);
        dayMaterial = database.getCollection(DAY_MATERIAL)
                .withWriteConcern(WriteConcern.UNACKNOWLEDGED);
        dayGoods = database.getCollection(DAY_GOODS)
                .withWriteConcern(WriteConcern.UNACKNOWLEDGED);
        dayRentRoom = database.getCollection(DAY_RENTROOM)
                .withWriteConcern(WriteConcern.UNACKNOWLEDGED);
    }

    /**
     * only save : id,type,time,total
     * @param isIncome
     * @param documentList
     * @param time
     * @param collection
     */
    public static void insertDaySummary1(Type isIncome, List<Document> documentList,
                                         long time,MongoCollection<Document> collection)
    {
        //document has key "total" == LogDb.KEY_TOTAL
        //document already owned : id,total
        documentList.forEach(document ->
                document.append(TIME, time)
                        .append(TYPE, isIncome.getValue()));
        if (!documentList.isEmpty()) {
            collection.insertMany(documentList);
        }
    }

    public static void insertDaySummaryWithTypeId(Type isIncome, List<Document> documentList,
                                         long time,MongoCollection<Document> collection)
    {
        //document already owned :total,id,tpi
        documentList.forEach(document ->
                document.append(TIME, time)
                        .append(TYPE, isIncome.getValue()));
        if (!documentList.isEmpty()) {
            collection.insertMany(documentList);
        }
    }

    public static Ss.EconomyInfos getPlayerEconomy(UUID playerId)
    {
        Ss.EconomyInfos.Builder builder = Ss.EconomyInfos.newBuilder();
        builder.setPlayerId(Util.toByteString(playerId));
        builder.addInfos(getSummaryInfo1(playerId, Ss.EconomyInfo.Type.SELL_GROUND, daySellGround));
        builder.addInfos(getSummaryInfo1(playerId, Ss.EconomyInfo.Type.RENT_GROUND, dayRentGround));
        builder.addInfos(getSummaryInfo1(playerId, Ss.EconomyInfo.Type.TRANSFER, dayTransfer));
        builder.addInfos(getSummaryInfo1(playerId, Ss.EconomyInfo.Type.SALARY, daySalary));
        builder.addInfos(getSummaryInfo1(playerId, Ss.EconomyInfo.Type.RENT_ROOM, dayRentRoom));

        //goods has type id
        builder.addAllInfos(getSummaryInfoWithTpi(playerId, Ss.EconomyInfo.Type.GOODS, dayGoods));
        //material has type id
        builder.addAllInfos(getSummaryInfoWithTpi(playerId, Ss.EconomyInfo.Type.MATERIAL, dayMaterial));

        return builder.build();
    }

    private static List<Ss.EconomyInfo> getSummaryInfoWithTpi(UUID playerId, Ss.EconomyInfo.Type bType,
                                                              MongoCollection<Document> collection)
    {

        Map<Integer, Ss.EconomyInfo.Builder> map = new HashMap<>();
        Document groupObject = new Document("_id",
                new BasicDBObject(TYPE, "$"+TYPE)
                        .append("tpi", "$tpi"));

        Document projectObject = new Document()
                .append(TYPE, "$_id._id." + TYPE)
                .append("tpi", "$_id._id.tpi")
                .append(LogDb.KEY_TOTAL, "$" + LogDb.KEY_TOTAL)
                .append("_id", 0);

        collection.aggregate(
                Arrays.asList(
                        Aggregates.match(eq(ID, playerId)),
                        Aggregates.group(groupObject, Accumulators.sum(LogDb.KEY_TOTAL, "$" + LogDb.KEY_TOTAL)),
                        Aggregates.project(projectObject)
                )
        ).forEach((Block<? super Document>) document ->
        {
            int incomeType = document.getInteger(TYPE);
            int goodsId = document.getInteger("tpi");
            long total = (long) document.get(LogDb.KEY_TOTAL);
            map.computeIfAbsent(goodsId,
                    k -> Ss.EconomyInfo.newBuilder().setType(bType).setId(goodsId));
            if (incomeType == Type.INCOME.getValue())
            {
                map.get(goodsId).setIncome(total);
            }
            else
            {
                map.get(goodsId).setPay(total);
            }

        });
        List<Ss.EconomyInfo> infoList = new ArrayList<>();
        map.values().forEach(builder -> infoList.add(builder.build()));
        return infoList;
    }


    /**
     * @param playerId
     * @param bType
     * @return
     */
    private static Ss.EconomyInfo getSummaryInfo1(UUID playerId, Ss.EconomyInfo.Type bType,
                                                  MongoCollection<Document> collection)
    {
        Ss.EconomyInfo.Builder builder = Ss.EconomyInfo.newBuilder();
        builder.setType(bType);
        collection.aggregate(
                Arrays.asList(
                        Aggregates.match(eq(ID, playerId)),
                        Aggregates.group("$" + TYPE, Accumulators.sum(LogDb.KEY_TOTAL, "$" + LogDb.KEY_TOTAL))
                )
        ).forEach((Block<? super Document>) document ->
        {
            if ((int) document.get("_id") == Type.INCOME.getValue())
            {
                builder.setIncome((long) document.get(LogDb.KEY_TOTAL));
            }
            else
            {
                builder.setPay((long) document.get(LogDb.KEY_TOTAL));
            }
        });
        return builder.build();
    }

    public static long todayStartTime()
    {
        long nowTime = System.currentTimeMillis();
        return nowTime - (nowTime + TimeZone.getDefault().getRawOffset())% DAY_MILLISECOND;
    }

    public static MongoCollection<Document> getDaySellGround()
    {
        return daySellGround;
    }

    public static MongoCollection<Document> getDayRentGround()
    {
        return dayRentGround;
    }

    public static MongoCollection<Document> getDayTransfer()
    {
        return dayTransfer;
    }

    public static MongoCollection<Document> getDaySalary()
    {
        return daySalary;
    }

    public static MongoCollection<Document> getDayMaterial()
    {
        return dayMaterial;
    }

    public static MongoCollection<Document> getDayGoods()
    {
        return dayGoods;
    }

    public static MongoCollection<Document> getDayRentRoom()
    {
        return dayRentRoom;
    }

    enum Type
    {
        INCOME(1),PAY(-1);
        private int value;
        Type(int i)
        {
            this.value = i;
        }

        public int getValue()
        {
            return value;
        }
    }
}
