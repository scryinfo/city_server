package Statistic;

import Shared.LogDb;
import Shared.Util;
import Statistic.Util.TimeUtil;
import com.mongodb.BasicDBObject;
import com.mongodb.Block;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Sorts;
import gs.Gs;
import io.opencensus.stats.Aggregation;
import org.bson.Document;
import ss.Ss;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static Shared.LogDb.KEY_TOTAL;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Projections.*;

public class SummaryUtil {
    public static final int TRIVIAL = 10;
    public static final int MATERIAL = 11;
    public static final int PRODUCE = 12;
    public static final int RETAIL = 13;
    public static final int APARTMENT = 14;
    public static final int LAB = 15;
    public static final int PUBLIC = 16;
    public static final int TALENT = 17;
    public static final long DAY_MILLISECOND = 1000 * 3600 * 24;
    public static final long HOUR_MILLISECOND = 1000 * 3600;
    public static final long SECOND_MILLISECOND = 1000 * 10;
    private static final String ID = "id";
    private static final String TYPE = "type";
    private static final String TIME = "time";
    private static final String COUNTTYPE = "countType";
    private static final String DAY_SELLGROUND = "daySellGround";
    private static final String DAY_RENTGROUND = "dayRentGround";
    private static final String DAY_TRANSFER = "dayTransfer";
    private static final String DAY_SALARY = "daySalary";
    private static final String DAY_MATERIAL = "dayMaterial";
    private static final String DAY_GOODS = "dayGoods";
    private static final String DAY_RENTROOM = "dayRentRoom";
    private static final String DAY_GOODS_NPC_NUM = "dayGoodsNpcNum";
    private static final String DAY_APARTMENT_NPC_NUM = "dayApartmentNpcNum";
    private static final String DAY_NPC_BUY_IN_SHELF = "dayNpcBuyInShelf";
    private static final String DAY_NPC_RENT_APARTMENT = "dayNpcRentApartment";
    private static final String DAY_PLAYER_BUY_GROUND = "dayPlayerBuyGround";
    private static final String DAY_PLAYER_BUY_IN_SHELF = "dayPlayerBuyInShelf";
    private static final String DAY_PLAYER_RENT_GROUND = "dayPlayerRentGround";
    private static final String DAY_PLAYER_RESEARCH = "dayPlayerResearch";
    private static final String DAY_PLAYER_PROMOTION = "dayPlayePromotion";
    private static final String DAY_BUILDING_INCOME = "dayBuildingIncome";
    private static final String DAY_PLAYER_INCOME = "dayPlayerIncome";
    private static final String DAY_PLAYER_PAY = "dayPlayerPay";
    private static final String DAY_GOODS_SOLD_DETAIL= "dayGoodsSoldDetail";

    //--ly
    public static final String PLAYER_EXCHANGE_AMOUNT = "playerExchangeAmount";
    public static final String TOP_INFO = "topInfo";
    private static MongoCollection<Document> daySellGround;
    private static MongoCollection<Document> dayRentGround;
    private static MongoCollection<Document> dayTransfer;
    private static MongoCollection<Document> daySalary;
    private static MongoCollection<Document> dayMaterial;
    private static MongoCollection<Document> dayGoods;
    private static MongoCollection<Document> dayRentRoom;
    private static MongoCollection<Document> dayGoodsNpcNum;
    private static MongoCollection<Document> dayApartmentNpcNum;
    private static MongoCollection<Document> dayNpcBuyInShelf;
    private static MongoCollection<Document> dayNpcRentApartment;
    private static MongoCollection<Document> dayPlayerBuyGround;
    private static MongoCollection<Document> dayPlayerBuyInShelf;
    private static MongoCollection<Document> dayPlayerRentGround;
    private static MongoCollection<Document> dayPlayerResearch;
    private static MongoCollection<Document> dayPlayerPromotion;
    private static MongoCollection<Document> dayBuildingIncome;
    private static MongoCollection<Document> dayPlayerIncome;
    private static MongoCollection<Document> dayPlayerPay;
    private static MongoCollection<Document> dayGoodsSoldDetail;

    //--ly
    private static MongoCollection<Document> playerExchangeAmount;
    private static MongoCollection<Document> topInfo;

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
        dayApartmentNpcNum = database.getCollection(DAY_APARTMENT_NPC_NUM)
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
        dayPlayerResearch = database.getCollection(DAY_PLAYER_RESEARCH)
        		.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
        dayPlayerPromotion = database.getCollection(DAY_PLAYER_PROMOTION)
        		.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
        dayBuildingIncome = database.getCollection(DAY_BUILDING_INCOME)
                .withWriteConcern(WriteConcern.UNACKNOWLEDGED);
        dayPlayerIncome = database.getCollection(DAY_PLAYER_INCOME)
        		.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
        dayPlayerPay = database.getCollection(DAY_PLAYER_PAY)
        		.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
        dayGoodsSoldDetail = database.getCollection(DAY_GOODS_SOLD_DETAIL)
                .withWriteConcern(WriteConcern.UNACKNOWLEDGED);
        playerExchangeAmount = database.getCollection(PLAYER_EXCHANGE_AMOUNT)
                .withWriteConcern(WriteConcern.UNACKNOWLEDGED);
        topInfo = database.getCollection(TOP_INFO)
                .withWriteConcern(WriteConcern.UNACKNOWLEDGED);
    }

    public static long getSexInfo(boolean isMale)
    {
        return LogDb.getPlayerInfo().countDocuments(eq("male", isMale));
    }

    /*public static Map<Long, Long> getBuildIncomeById(UUID bid)
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
    }*/


    
    public static List<Document> getNpcHistoryData(MongoCollection<Document> collection,CountType countType,long time)
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
    
    public static Map<Long, Map> getNpcTypeNumHistoryData(MongoCollection<Document> collection)
    {
    	Map<Long, Map> countMap= new TreeMap<Long, Map>();
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
    	collection.find(and(
                gte("t", startTime),
                lt("t", endTime)
    			))
    	.projection(fields(include("t", "tp", "n"), excludeId()))
    	.forEach((Block<? super Document>) document ->
    	{ 
    	  long t=document.getLong("t");
		  int tp=document.getInteger("tp");
		  long n=document.getLong("n");
		  if(!countMap.containsKey(t)){
			  Map<Integer,Long> m=new HashMap<Integer,Long>();
			  m.put(tp, n);
			  countMap.put(t, m);
		  }else{ 
			  Map<Integer,Long> mm=countMap.get(t);
			  mm.put(tp,n);
			  countMap.put(t,mm); 
		  }
    	});
    	return countMap;
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
    public static Map<Long, Long> queryApartmentNpcNumCurve(MongoCollection<Document> collection,CountType countType)
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
    public static Map<Long, Long> queryPlayerIncomePayCurve(MongoCollection<Document> collection,UUID id)
    {
    	Calendar calendar =TimeUtil.monthCalendar();
        //开始时间
    	Date startDate = calendar.getTime();
    	long startTime=startDate.getTime();
    	calendar.setTime(new Date());
    	//结束时间（到现在时间点的统计）
        Date endDate = calendar.getTime();
        long endTime=endDate.getTime();
    	Map<Long, Long> map = new LinkedHashMap<>();
    	collection.find(and(
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
    public static Map<Long, Document> queryGoodsSoldDetailCurve(MongoCollection<Document> collection,int itemId,UUID produceId)
    {
    	Calendar calendar =TimeUtil.monthCalendar();
        //开始时间
    	Date startDate = calendar.getTime();
    	long startTime=startDate.getTime();
    	calendar.setTime(new Date());
    	//结束时间（到现在时间点的统计）
        Date endDate = calendar.getTime();
        long endTime=endDate.getTime();
    	Map<Long, Document> map = new LinkedHashMap<>();
    	collection.find(and(
                eq("id",itemId),
    			eq("p",produceId),
    			gte("time", startTime),
    			lt("time", endTime)
    			))
    	.projection(fields(include("time", "total"), excludeId()))
    	.sort(Sorts.descending("time"))
    	.forEach((Block<? super Document>) document ->
    	{
    		map.put(document.getLong("time"), document);
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
    public static void insertPlayerIncomeOrPay(List<Document> documentList,
    		long time,MongoCollection<Document> collection)
    {
    	//document already owned : id,total
    	documentList.forEach(document ->
    	document.append(TIME, time));
    	if (!documentList.isEmpty()) {
    		collection.insertMany(documentList);
    	}
    }

    public static void insertNpcHistoryData(List<Document> documentList,
                                               long time,MongoCollection<Document> collection)
    {
        //document already owned : id,total
        documentList.forEach(document ->
                document.append(TIME, time));
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
                .append(KEY_TOTAL, "$" + KEY_TOTAL)
                .append("_id", 0);

        collection.aggregate(
                Arrays.asList(
                        Aggregates.match(eq(ID, playerId)),
                        Aggregates.group(groupObject, Accumulators.sum(KEY_TOTAL, "$" + KEY_TOTAL)),
                        Aggregates.project(projectObject)
                )
        ).forEach((Block<? super Document>) document ->
        {
            int incomeType = document.getInteger(TYPE);
            int goodsId = document.getInteger("tpi");
            long total = (long) document.get(KEY_TOTAL);
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
                        Aggregates.group("$" + TYPE, Accumulators.sum(KEY_TOTAL, "$" + KEY_TOTAL))
                )
        ).forEach((Block<? super Document>) document ->
        {
            if ((int) document.get("_id") == Type.INCOME.getValue())
            {
                builder.setIncome((long) document.get(KEY_TOTAL));
            }
            else
            {
                builder.setPay((long) document.get(KEY_TOTAL));
            }
        });
        return builder.build();
    }

    public static long todayStartTime(long nowTime)
    {
        return nowTime - (nowTime + TimeZone.getDefault().getRawOffset())% DAY_MILLISECOND;
    }
    public static long hourStartTime(long nowTime)
    {
        return nowTime - (nowTime + TimeZone.getDefault().getRawOffset())% HOUR_MILLISECOND;
    }
    public static long secondStartTime(long nowTime)
    {
        return nowTime - (nowTime + TimeZone.getDefault().getRawOffset())% SECOND_MILLISECOND;
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
    
    public static MongoCollection<Document> getDayApartmentNpcNum()
    {
    	return dayApartmentNpcNum;
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
    public static MongoCollection<Document> getDayPlayerResearch()
    {
    	return dayPlayerResearch;
    }
    public static MongoCollection<Document> getDayPlayerPromotion()
    {
    	return dayPlayerPromotion;
    }

    public static MongoCollection<Document> getDayPlayerIncome()
    {
    	return dayPlayerIncome;
    }
    
    public static MongoCollection<Document> getDayPlayerPay()
    {
    	return dayPlayerPay;
    }

    public static MongoCollection<Document> getDayGoodsSoldDetail()
    {
        return dayGoodsSoldDetail;
    }

    public static void insertBuildingDayIncome(List<Document> documentList,long time)
    {
        documentList.forEach(document -> {
            document.append(TIME, time);
        });
        if (!documentList.isEmpty()) {
            dayBuildingIncome.insertMany(documentList);
        }
    }

    public static List<Ss.NodeIncome> getBuildDayIncomeById(UUID bid)
    {
        long startTime = todayStartTime(System.currentTimeMillis()) - DAY_MILLISECOND * 30;
        List<Ss.NodeIncome> list = new ArrayList<>();
        dayBuildingIncome.find(and(eq(ID, bid),
                gte(TIME, startTime)))
                .sort(Sorts.ascending(TIME))
                .forEach((Block<? super Document>) document ->
                {
                    list.add(Ss.NodeIncome.newBuilder()
                            .setTime(TimeUtil.getTimeDayStartTime(document.getLong(TIME)))
                            .setIncome(document.getLong(KEY_TOTAL)).build());
                });
        return list;
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
        public enum CountType
    {
    	BYDAY(1),BYHOUR(2),BYMINU(3),BYSECONDS(4);
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


     public enum ExchangeType {
        MATERIAL(1),GOODS(2),GROUND(3),PUBLICITY(4), LABORATORY(5), STORAGE(6);
        private int value;
        ExchangeType(int i)
        {
            this.value = i;
        }

        public int getValue()
        {
            return value;
        }
    }
    //--ly
    public static MongoCollection<Document> getPlayerExchangeAmount()
    {
        return playerExchangeAmount;
    }
    public static void insertPlayerExchangeData(CountType countType,ExchangeType exchangeType,List<Document> documentList,
                                         long time,MongoCollection<Document> collection)
    {
        //document already owned : id,total
        documentList.forEach(document ->
                document.append(TIME, time)
                        .append(TYPE, exchangeType.getValue())
                        .append(COUNTTYPE,countType.getValue()));
        if (!documentList.isEmpty()) {
            collection.insertMany(documentList);
        }
    }

    //玩家交易汇总表中查询开服截止当前时间玩家交易量。
    public static long getTodayData(MongoCollection<Document> collection,SummaryUtil.CountType countType)
    {
        long a=0;
        Map<Long, Long> map = new LinkedHashMap<>();
        List<Document> documentList = new ArrayList<>();
        Document projectObject = new Document()
                .append("id", "$_id")
                .append(KEY_TOTAL, "$" + KEY_TOTAL)
                .append("_id",0);
        collection.aggregate(
                Arrays.asList(
                        Aggregates.match(eq(COUNTTYPE, countType.getValue())),
                        Aggregates.group(null, Accumulators.sum(KEY_TOTAL, "$total")),
                        Aggregates.project(projectObject)
                )
        ).forEach((Block<? super Document>) documentList::add);
        documentList.forEach((document -> map.put(document.getLong("time"), document.getLong("total"))));
        for (Map.Entry<Long, Long> entry : map.entrySet()) {
            a=entry.getValue();
        }
        return a;
    }

    public static Map<Long,Long> queryPlayerExchangeCurve(MongoCollection<Document> collection, long id, int exchangeType, CountType countType,boolean isMoney) {

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.set(Calendar.HOUR_OF_DAY,calendar.get(Calendar.HOUR_OF_DAY));// 修改即时查看,包括当天.
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND,0);
        Date endDate = calendar.getTime();
        long endTime=endDate.getTime();

        calendar.add(Calendar.DATE, -7);
        Date startDate = calendar.getTime();
        long startTime=startDate.getTime();
        Map<Long, Long> map = new LinkedHashMap<>();

        if (!isMoney) {
            collection.find(and(
                    eq(COUNTTYPE, countType.getValue()),
                    eq(TYPE, exchangeType),
                    eq(ID, id),
                    gte(TIME, startTime),
                    lte(TIME, endTime)
            ))

                    .projection(fields(include(TIME, "size"), excludeId()))
                    .sort(Sorts.descending(TIME))
                    .forEach((Block<? super Document>) document ->
                    {
                        map.put(document.getLong(TIME), document.getLong("size"));
                    });
        } else {
            collection.find(and(
                    eq(COUNTTYPE, countType.getValue()),
                    eq(TYPE, exchangeType),
                    eq(ID, id),
                    gte(TIME, startTime),
                    lte(TIME, endTime)
            ))

                    .projection(fields(include(TIME, KEY_TOTAL), excludeId()))
                    .sort(Sorts.descending(TIME))
                    .forEach((Block<? super Document>) document ->
                    {
                        map.put(document.getLong(TIME), document.getLong(KEY_TOTAL));
                    });
        }

        return map;
    }

    public static MongoCollection<Document> getTopInfo() {
        return topInfo;
    }

    public static void insertTopInfo(MongoCollection<Document> collection, List<Document> documentList, int type) {
        documentList.forEach(document -> {
            document.append("tp", type);
        });
        if (!documentList.isEmpty()) {
            collection.insertMany(documentList);
        }

    }

    public static long getTodayIncome(MongoCollection<Document> collection, UUID pid,int type) {
        final long[] todayIncome = {0};
        long startTime = SummaryUtil.todayStartTime(System.currentTimeMillis());
        long currentTimeMillis = System.currentTimeMillis();
        LogDb.getNpcRentApartment().find(and(eq("d", pid), eq("tp", type),
                gte(TIME, startTime),
                lte(TIME, currentTimeMillis)
        ))
//                .sort(Sorts.ascending(TIME))
//                .projection(Aggregates.group("$tp",Accumulators.sum("total", "$a")))
                .forEach((Block<? super Document>) document ->
                {
                    if (document != null) {
                        todayIncome[0] = document.getLong("total");
                    }
                });
        return todayIncome[0];
    }

    public static Ss.TopInfo queryIndustryTop(UUID pid, int type) {
        Ss.TopInfo.Builder builder = Ss.TopInfo.newBuilder();
        Ss.TopInfo.TopMsg.Builder topMsg = Ss.TopInfo.TopMsg.newBuilder();
        Ss.TopInfo.IndustryMsg.Builder msg = Ss.TopInfo.IndustryMsg.newBuilder();
        topMsg.setOwner(0);
        AtomicInteger num = new AtomicInteger();
        topInfo.find(and(eq("tp", type)))
                .sort(and(eq("total", -1)))
                .forEach((Block<? super Document>) document ->
                {
                    num.incrementAndGet();
                    UUID id = document.get("id", UUID.class);
                    if (pid.equals(id)) {
                        topMsg.setOwner(num.get());
                    }
                    msg.setPid(Util.toByteString(id));
                    msg.setTodayIncome(getTodayIncome(topInfo, pid, type));
                    msg.setSumIncome(document.getLong("total"));
                    msg.setName(Gs.Str.newBuilder().setStr(document.getString("rn")).build());
                    msg.setCompanyName(Gs.Str.newBuilder().setStr(document.getString("cn")).build());
                    topMsg.addIndustryMsg(msg.build());
                });
        topMsg.setIndustry(type);
        return builder.setMsg(topMsg.build()).build();
    }

}
