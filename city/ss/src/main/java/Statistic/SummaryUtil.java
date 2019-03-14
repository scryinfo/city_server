package Statistic;

import Shared.LogDb;
import Shared.Util;
import com.mongodb.BasicDBObject;
import com.mongodb.Block;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Projections.*;

import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import ss.Ss;

import java.text.SimpleDateFormat;
import java.util.*;

public class SummaryUtil
{
    public static final long DAY_MILLISECOND = 1000 * 3600 * 24;
    public static final long HOUR_MILLISECOND = 1000 * 3600;
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
    private static final String DAY_GOODS_NPC_NUM = "dayGoodsNpcNum";
    private static final String DAY_NPC_BUY_IN_SHELF = "dayNpcBuyInShelf";
    private static final String DAY_NPC_RENT_APARTMENT = "dayNpcRentApartment";
    private static final String DAY_PLAYER_BUY_GROUND = "dayPlayerBuyGround";
    private static final String DAY_PLAYER_BUY_IN_SHELF = "dayPlayerBuyInShelf";
    private static final String DAY_PLAYER_RENT_GROUND = "dayPlayerRentGround";
    private static MongoCollection<Document> daySellGround;
    private static MongoCollection<Document> dayRentGround;
    private static MongoCollection<Document> dayTransfer;
    private static MongoCollection<Document> daySalary;
    private static MongoCollection<Document> dayMaterial;
    private static MongoCollection<Document> dayGoods;
    private static MongoCollection<Document> dayRentRoom;
    private static MongoCollection<Document> dayGoodsNpcNum;
    private static MongoCollection<Document> dayNpcBuyInShelf;
    private static MongoCollection<Document> dayNpcRentApartment;
    private static MongoCollection<Document> dayPlayerBuyGround;
    private static MongoCollection<Document> dayPlayerBuyInShelf;
    private static MongoCollection<Document> dayPlayerRentGround;
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
        dayGoodsNpcNum = database.getCollection(DAY_GOODS_NPC_NUM)
        		.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
        dayNpcBuyInShelf = database.getCollection(DAY_NPC_BUY_IN_SHELF)
        		.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
        dayNpcRentApartment = database.getCollection(DAY_NPC_RENT_APARTMENT)
        		.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
        dayPlayerBuyGround = database.getCollection(DAY_PLAYER_BUY_GROUND)
        		.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
        dayPlayerBuyInShelf = database.getCollection(DAY_PLAYER_BUY_IN_SHELF)
        		.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
        dayPlayerRentGround = database.getCollection(DAY_PLAYER_RENT_GROUND)
        		.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
    }

    public static long getSexInfo(boolean isMale)
    {
        return LogDb.getPlayerInfo().countDocuments(eq("male", isMale));
    }

    public static Map<Long, Long> getBuildIncomeById(UUID bid)
    {
        long nowTime = System.currentTimeMillis();
        long lastFullTime = getLastFullTime(nowTime);
        long beforeDayTime = getBeforeDayStartTime(7, nowTime);
        Map<Long, Long> map = new LinkedHashMap<>();
        LogDb.getBuildingIncome().find(and(
                eq("b", bid),
                gte("t", beforeDayTime),
                lte("t", lastFullTime)))
                .projection(fields(include("t", "a"), excludeId()))
                .sort(Sorts.ascending("t"))
                .forEach((Block<? super Document>) document ->
                {
                    long node = getLastFullTime(document.getLong("t")) + HOUR_MILLISECOND;
                    map.computeIfAbsent(node, k -> 0L);
                    map.put(node, map.get(node) + document.getLong("a"));
                });
        return map;
    }
    
    public static List<Document> getGoodsNpcHistoryData(MongoCollection<Document> collection,CountType countType,long time)
    {
    	List<Document> documentList = new ArrayList<>();
        collection.find(and(
    					eq("time",time),
    					eq("type",countType.getValue())
    					))
        			.projection(fields(include("time", "total", "id"), excludeId()))
    		        .forEach((Block<? super Document>) document ->
                    {   
                    	documentList.add(document);
                    });
    	return documentList;
    }
    
    public static List<Document> getNpcTypeNumHistoryData(MongoCollection<Document> collection)
    {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        Date endDate = calendar.getTime();
        long endTime=endDate.getTime();
        
        calendar.add(Calendar.DATE, -7);
        Date startDate = calendar.getTime();
        long startTime=startDate.getTime();
    	List<Document> documentList = new ArrayList<>();
    	collection.find(and(
                gte("t", startTime),
                lt("t", endTime)
    			))
    	.projection(fields(include("t", "tp", "n"), excludeId()))
    	.sort(Sorts.ascending("t"))
    	.forEach((Block<? super Document>) document ->
    	{   
    		documentList.add(document);
    	});
    	return documentList;
    }
    
    public static long getHistoryData(MongoCollection<Document> collection,CountType countType)
    {
    	long a=0;
    	Map<Long, Long> map = new LinkedHashMap<>();
    	collection.find(and(
    			eq("type",countType.getValue())
    			))
    	.projection(fields(include("time", "total"), excludeId()))
    	.sort(Sorts.descending("time"))
    	.limit(1)
    	.forEach((Block<? super Document>) document ->
    	{   
    		map.put(document.getLong("time"), document.getLong("total"));
    	});
    	for (Map.Entry<Long, Long> entry : map.entrySet()) {
    		a=entry.getValue();
    	}
    	return a;
    }
    
    public static Map<Long, Long> queryGoodsNpcNumCurve(MongoCollection<Document> collection,int id,CountType countType)
    {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        Date endDate = calendar.getTime();
        long endTime=endDate.getTime();
        
        calendar.add(Calendar.DATE, -7);
        Date startDate = calendar.getTime();
        long startTime=startDate.getTime();
    	Map<Long, Long> map = new LinkedHashMap<>();
    	collection.find(and(
    			eq("type",countType.getValue()),
    			eq("id",id),
                gte("time", startTime),
                lt("time", endTime)
    			))
    	.projection(fields(include("time", "total"), excludeId()))
    	.sort(Sorts.descending("time"))
    	.forEach((Block<? super Document>) document ->
    	{   
    		map.put(document.getLong("time"), document.getLong("total"));
    	});
    	return map;
    }
    public static List<Document> queryCityBroadcast(MongoCollection<Document> collection)
    {
    	List<Document> documentList = new ArrayList<>();
    	collection.find()
    	.projection(fields(include("t", "s", "b", "c", "n", "tp"), excludeId()))
    	.sort(Sorts.ascending("t"))
    	.forEach((Block<? super Document>) document ->
        {
        	documentList.add(document);
        });
    	return documentList;
    }
    
    public static long getTodayData(MongoCollection<Document> collection)
    {
    	long a=0;
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        
        Date endDate = calendar.getTime();
        long startTime=endDate.getTime();
        long endTime = System.currentTimeMillis();
        List<Document> documentList=LogDb.dayTodayNpcExchangeAmount(startTime,endTime,collection);
        for(Document document : documentList) {
        	a=document.getLong("total");
		}
    	return a;
    }
    
    public static long getLastFullTime(long currentTime)
    {
        return currentTime - (currentTime % HOUR_MILLISECOND);
    }

    public static long getBeforeDayStartTime(int day, long currentTime)
    {
        long todayStartTime = todayStartTime(currentTime);
        return todayStartTime - (day - 1) * DAY_MILLISECOND;
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

    public static void insertHistoryData(CountType countType, List<Document> documentList,
            long time,MongoCollection<Document> collection)
	{
		//document already owned : id,total
		documentList.forEach(document ->
				document.append(TIME, time)
				.append(TYPE, countType.getValue()));
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

    public static long todayStartTime(long nowTime)
    {
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
    
    public static MongoCollection<Document> getDayGoodsNpcNum()
    {
    	return dayGoodsNpcNum;
    }
    
    public static MongoCollection<Document> getDayNpcBuyInShelf()
    {
    	return dayNpcBuyInShelf;
    }
    
    public static MongoCollection<Document> getDayNpcRentApartment()
    {
    	return dayNpcRentApartment;
    }
    
    public static MongoCollection<Document> getDayPlayerBuyGround()
    {
    	return dayPlayerBuyGround;
    }
    
    public static MongoCollection<Document> getDayPlayerBuyInShelf()
    {
    	return dayPlayerBuyInShelf;
    }
    
    public static MongoCollection<Document> getDayPlayerRentGround()
    {
    	return dayPlayerRentGround;
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
    enum CountType
    {
    	BYDAY(1),BYHOUR(2);
    	private int value;
    	CountType(int i)
    	{
    		this.value = i;
    	}
    	
    	public int getValue()
    	{
    		return value;
    	}
    }
}
