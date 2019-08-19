package Statistic;

import Param.ItemKey;
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
import org.bson.Document;
import ss.Ss;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static Shared.LogDb.KEY_AVG;
import static Shared.LogDb.KEY_TOTAL;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Projections.*;

public class SummaryUtil {
    public static final int MATERIAL = 11;
    public static final int PRODUCE = 12;
    public static final int RETAIL = 13;
    public static final int APARTMENT = 14;
    public static final int TECHNOLOGY=15;//新版研究所
    public static final int PROMOTE=16;//新版推广公司
    public static final int GROUND = 20; // 土地相关
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
    private static final String DAY_BUILDING_PAY = "dayBuildingPay";            //建筑每日收入统计
    private static final String DAY_PLAYER_INCOME = "dayPlayerIncome";
    private static final String DAY_PLAYER_PAY = "dayPlayerPay";
    private static final String DAY_GOODS_SOLD_DETAIL= "dayGoodsSoldDetail";
    private static final String DAY_INDUSTRY_INCOME= "dayIndustryIncome";
    private static final String DAY_BUILDING__GOOD_SOLD_DETAIL="dayBuildingGoodSoldDetail";
    private static final String DAY_BUILDING_BUSINESS= "dayBuildingBusiness";
    private static final String DAY_PLAYER_LOGINTIME= "dayPlayerLoginTime";

    //--ly
    public static final String PLAYER_EXCHANGE_AMOUNT = "playerExchangeAmount";
    public static final String AVERAGE_TRANSACTION_PRICE = "averageTransactionPrice";
    public static final String CITY_TRANSACTION_AMOUNT = "cityTransactionAmount";
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
    private static MongoCollection<Document> dayBuildingPay;        //建筑每日收入
    private static MongoCollection<Document> dayPlayerIncome;
    private static MongoCollection<Document> dayPlayerPay;
    private static MongoCollection<Document> dayGoodsSoldDetail;
    private static MongoCollection<Document> dayIndustryIncome;
    private static MongoCollection<Document> dayBuildingBusiness;
    private static MongoCollection<Document> dayBuildingGoodSoldDetail; //建筑销售明细
    private static MongoCollection<Document> averageTransactionPrice; // 平均交易价格

    //--ly
    private static MongoCollection<Document> playerExchangeAmount;
    private static MongoCollection<Document> cityTransactionAmount;

    //yty
    private static MongoCollection<Document> dayPlayerLoginTime;

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
        dayBuildingPay = database.getCollection(DAY_BUILDING_PAY)           //建筑每日收入统计
                .withWriteConcern(WriteConcern.UNACKNOWLEDGED);
        dayPlayerIncome = database.getCollection(DAY_PLAYER_INCOME)
        		.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
        dayPlayerPay = database.getCollection(DAY_PLAYER_PAY)
        		.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
        dayGoodsSoldDetail = database.getCollection(DAY_GOODS_SOLD_DETAIL)
                .withWriteConcern(WriteConcern.UNACKNOWLEDGED);
        dayIndustryIncome = database.getCollection(DAY_INDUSTRY_INCOME)
                .withWriteConcern(WriteConcern.UNACKNOWLEDGED);
        dayBuildingBusiness = database.getCollection(DAY_BUILDING_BUSINESS)
                .withWriteConcern(WriteConcern.UNACKNOWLEDGED);
        dayBuildingGoodSoldDetail = database.getCollection(DAY_BUILDING__GOOD_SOLD_DETAIL)
                .withWriteConcern(WriteConcern.UNACKNOWLEDGED);
        playerExchangeAmount = database.getCollection(PLAYER_EXCHANGE_AMOUNT)
                .withWriteConcern(WriteConcern.UNACKNOWLEDGED);
        averageTransactionPrice = database.getCollection(AVERAGE_TRANSACTION_PRICE)
                .withWriteConcern(WriteConcern.UNACKNOWLEDGED);

        dayPlayerLoginTime = database.getCollection(DAY_PLAYER_LOGINTIME)
                .withWriteConcern(WriteConcern.UNACKNOWLEDGED);
        cityTransactionAmount = database.getCollection(CITY_TRANSACTION_AMOUNT)
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
        //开始时间（前30天）
        long startTime = TimeUtil.monthStartTime();
    	//结束时间
        long endTime=TimeUtil.todayStartTime();
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
    	//document already owned : id,tp,total
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

    /*玩家每天的登陆时长*/
    public static void insertDayPlayerLoginTime(List<Document> documentList,
                                                long time,MongoCollection<Document> collection){
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

    public static MongoCollection<Document> getDayIndustryIncome()
    {
        return dayIndustryIncome;
    }
    public static MongoCollection<Document> getAverageTransactionPrice()
    {
        return averageTransactionPrice;
    }

    public static MongoCollection<Document> getDayBuildingBusiness()
    {
        return dayBuildingBusiness;
    }

    public static MongoCollection<Document> getDayBuildingGoodSoldDetail() {
        return dayBuildingGoodSoldDetail;
    }

    public static MongoCollection<Document> getDayPlayerLoginTime() {
        return dayPlayerLoginTime;
    }
    public static MongoCollection<Document> getCityTransactionAmount() {
        return cityTransactionAmount;
    }

    public static void insertBuildingDayIncome(List<Document> documentList, long time)
    {
        documentList.forEach(document -> {
            document.append(TIME, time);
        });
        if (!documentList.isEmpty()) {
            dayBuildingIncome.insertMany(documentList);
        }
    }

    public static void insertBuildingDayPay(List<Document> documentList,long time)
    {
        documentList.forEach(document -> {
            document.append(TIME, time);
        });
        if (!documentList.isEmpty()) {
            dayBuildingPay.insertMany(documentList);
        }
    }

    public static void insertDayBuildingGoodSoldDetail(Map<Integer,List<Document>> detailMap, long time) {
        detailMap.values().forEach(c->{
            c.forEach(document -> {
                document.append(TIME, time);
            });
            if (!c.isEmpty()) {
                dayBuildingGoodSoldDetail.insertMany(c);
            }
        });
    }

    /*获取建筑一个月的收入（yty）*/
    public static Map<Long,Long> getBuildDayIncomeById(UUID bid)
    {
        Map<Long, Long> income = new HashMap<>();
        long startTime = todayStartTime(System.currentTimeMillis()) - DAY_MILLISECOND * 30;
        dayBuildingIncome.find(and(eq(ID, bid),
                gte(TIME, startTime)))
                .sort(Sorts.ascending(TIME))
                .forEach((Block<? super Document>) document ->
                {
                    income.put(TimeUtil.getTimeDayStartTime(document.getLong(TIME)),document.getLong(KEY_TOTAL));
                });
        return income;
    }

    //获取建筑一个月的支出(yty)
    public static Map<Long,Long> getBuildDayPayById(UUID bid)
    {
        Map<Long, Long> pay = new HashMap<>();
        long startTime = todayStartTime(System.currentTimeMillis()) - DAY_MILLISECOND * 30;
        dayBuildingPay.find(and(eq(ID, bid),
                gte(TIME, startTime)))
                .sort(Sorts.ascending(TIME))
                .forEach((Block<? super Document>) document ->
                {
                    pay.put(TimeUtil.getTimeDayStartTime(document.getLong(TIME)),document.getLong(KEY_TOTAL));
                });
        return pay;
    }
    /*查询建筑7天内收入统计*/
    public static Map<Long, Map<ItemKey, Document>> queryBuildingGoodsSoldDetail(MongoCollection<Document> collection, UUID bid) {
        long startTime = todayStartTime(System.currentTimeMillis()) - DAY_MILLISECOND * 6;
        /*存储格式  key为时间，value存这人一天内出现过的销售商品*/
        Map<Long, Map<ItemKey, Document>> detail = new HashMap<>();
        collection.find(and(eq("bid", bid),
                gte(TIME, startTime),
                lt(TIME,TimeUtil.todayStartTime())))
                .sort(Sorts.ascending(TIME))
                .forEach((Block<? super Document>) d ->
                {
                    detail.computeIfAbsent(TimeUtil.getTimeDayStartTime(d.getLong(TIME)), k -> new HashMap<>()).put(new ItemKey(d.getInteger("itemId"),(UUID) d.get("p")),d);
                });
        return detail;
    }

    /*获取建筑中某一个商品的历史销售记录*/
    public static Map<Long,Document> queryBuildingGoodsSoldDetail(MongoCollection<Document> collection,int itemId,UUID bid,UUID producerId){
        long startTime = todayStartTime(System.currentTimeMillis()) - DAY_MILLISECOND * 6;
        Map<Long, Document> map = new HashMap<>();
        collection.find(and(eq("bid", bid),
                eq("itemId",itemId),
                eq("p",producerId),
                gte(TIME, startTime),
                lt(TIME,TimeUtil.todayStartTime())))
                .forEach((Block<? super Document>) doc ->
                {
                    map.put(TimeUtil.getTimeDayStartTime(doc.getLong(TIME)),doc);
                });
        return map;
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

    public enum IndustryType {
        MATERIAL(11),PRODUCE(12),RETAIL(13),APARTMENT(14), TECHNOLOGY(15), PROMOTE(16), GROUND(20);
        private int value;
        IndustryType(int i)
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
    public static void insertDayIndustryIncomeData(IndustryType buildingType,List<Document> documentList,
                                                long time,MongoCollection<Document> collection)
    {
        List<Document> list=new ArrayList<Document>();
        documentList.forEach(document ->{
                    Document d=new Document();
                    d.append("total",document.getLong("total"));
                    d.append(TIME, time).append(TYPE, buildingType.getValue());
                    list.add(d);
                }
             );
        if (!list.isEmpty()) {
            collection.insertMany(list);
        }
    }

    public static void insertAverageTransactionprice(IndustryType buildingType,List<Document> documentList,
                                                     long time,MongoCollection<Document> collection)
    {
        List<Document> list=new ArrayList<Document>();
        documentList.forEach(document ->{
                    Document d=new Document();
                    d.append(KEY_AVG,document.getDouble(KEY_AVG))
                    .append(TIME, time).append(TYPE, buildingType.getValue());
                    list.add(d);
                }
             );
        if (!list.isEmpty()) {
            collection.insertMany(list);
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
    public static List<Document> queryWeekIndustryDevelopment(MongoCollection<Document> collection)
    {
        List<Document> documentList = new ArrayList<>();
        collection.find(and(
                gte("time", TimeUtil.beforeSixDay()),
                lt("time", TimeUtil.todayStartTime())
        ))
        .projection(fields(include("time","type","total"), excludeId()))
        .sort(Sorts.descending("time"))
        .forEach((Block<? super Document>) document ->
        {
            documentList.add(document);
        });
        return documentList;
    }
    public static List<Document> queryWeekIndustryCompetition(MongoCollection<Document> collection,int buildType)
    {
        List<Document> documentList = new ArrayList<>();
        collection.find(and(
                eq("tp",buildType),
                gte("time", TimeUtil.beforeSixDay()),
                lt("time", TimeUtil.todayStartTime())
        ))
                .projection(fields(include("time","tp","n","total"), excludeId()))
                .sort(Sorts.descending("time"))
                .forEach((Block<? super Document>) document ->
                {
                    documentList.add(document);
                });
        return documentList;
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



    public static List<IndustryInfo> queryIndustryIncom() {
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
        List<Document> documentList = new ArrayList<>();
        dayIndustryIncome.find(and(
                gte("time", startTime),
                lt("time", endTime)
        ))
                .projection(fields(include("time", "type", "total"), excludeId()))
                .sort(Sorts.descending("time"))
                .forEach((Block<? super Document>) documentList::add);
        return documentList.stream().map(o-> {
            try {
                return new IndustryInfo(
                        o.getInteger("type"),
                        o.getLong("total"),
                        o.getLong("time"));
            } catch (Exception e) {
                return null;
            }
        }).filter(o -> o != null).collect(Collectors.toList());

    }

    public static Map<Long, Map<Integer, Long>> queryInfo(List<IndustryInfo> infos) {
        Map<Long, Map<Integer, Long>> map = infos.stream().collect(Collectors.groupingBy(IndustryInfo::getTime, Collectors.toMap(IndustryInfo::getType, IndustryInfo::getTotal)));
        return map;
    }

    public static long queryTodayIncome(long start, long endTime,int type) {
        AtomicLong total = new AtomicLong(0);
        List<Document> documentList = new ArrayList<>();
        Document projectObject = new Document()
                .append("id", "$_id")
                .append(KEY_TOTAL, "$" + KEY_TOTAL)
                .append("_id",0);
        LogDb.getBuyInShelf().aggregate(
                Arrays.asList
                        (
                                Aggregates.match(and(
                                        eq("bt", type),
                                        gte("t", start),
                                        lt("t", endTime))),
                                Aggregates.group(null, Accumulators.sum(KEY_TOTAL, "$a")),
                                Aggregates.project(projectObject)
                        )
        ).forEach((Block<? super Document>) documentList::add);
        documentList.stream().forEach(d->{
            total.set(d.getLong(KEY_TOTAL));
        });
        return total.longValue();
    }
    public static long queryTodayRetailIncome(long start, long endTime) {
        AtomicLong total = new AtomicLong(0);
        List<Document> documentList = new ArrayList<>();
        Document projectObject = new Document()
                .append("id", "$_id")
                .append(KEY_TOTAL, "$" + KEY_TOTAL)
                .append("_id",0);
        LogDb.getNpcBuyInShelf().aggregate(
                Arrays.asList
                        (
                                Aggregates.match(and(
                                        gte("t", start),
                                        lt("t", endTime))),
                                Aggregates.group(null, Accumulators.sum(KEY_TOTAL, "$a")),
                                Aggregates.project(projectObject)
                        )
        ).forEach((Block<? super Document>) documentList::add);
        documentList.stream().forEach(d->{
            total.set(d.getLong(KEY_TOTAL));
        });
        return total.longValue();
    }
    public static long queryTodayIncome(long start, long endTime,boolean isApartment) {
        MongoCollection<Document> collection = LogDb.getNpcRentApartment();
        if (!isApartment) {
            collection = LogDb.getBuyGround();
        }
        AtomicLong total = new AtomicLong(0);
        List<Document> documentList = new ArrayList<>();
        Document projectObject = new Document()
                .append("id", "$_id")
                .append(KEY_TOTAL, "$" + KEY_TOTAL)
                .append("_id",0);
        collection.aggregate(
                Arrays.asList
                        (
                                Aggregates.match(and(
                                        gte("t", start),
                                        lt("t", endTime))),
                                Aggregates.group(null, Accumulators.sum(KEY_TOTAL, "$a")),
                                Aggregates.project(projectObject)
                        )
        ).forEach((Block<? super Document>) documentList::add);
        documentList.stream().forEach(d->{
            total.set(d.getLong(KEY_TOTAL));
        });
        return total.longValue();
    }

    public static Ss.IndustryIncome.IncomeInfo queryTodayIncome() {
        // 当日0点-当前时间
        Map<Integer, Long> map = new HashMap<>();
        long startTime = SummaryUtil.todayStartTime(System.currentTimeMillis());
        long endTime = System.currentTimeMillis();
        map.put(SummaryUtil.MATERIAL, queryTodayIncome(startTime, endTime, SummaryUtil.MATERIAL));
        map.put(SummaryUtil.PRODUCE, queryTodayIncome(startTime, endTime, SummaryUtil.PRODUCE));
        map.put(SummaryUtil.RETAIL, queryTodayRetailIncome(startTime, endTime));
        map.put(SummaryUtil.TECHNOLOGY, queryTodayIncome(startTime, endTime, SummaryUtil.TECHNOLOGY));
        map.put(SummaryUtil.PROMOTE, queryTodayIncome(startTime, endTime, SummaryUtil.PROMOTE));
        map.put(SummaryUtil.APARTMENT, queryTodayIncome(startTime, endTime, true));
        map.put(Gs.SupplyAndDemand.IndustryType.GROUND_VALUE, queryTodayIncome(startTime, endTime,false));
        Ss.IndustryIncome.IncomeInfo.Builder builder = Ss.IndustryIncome.IncomeInfo.newBuilder();
        builder.setTime(todayStartTime(System.currentTimeMillis()));
        map.forEach((k,v)->{
            Ss.IndustryIncome.IncomeInfo.IncomeMsg.Builder msg = Ss.IndustryIncome.IncomeInfo.IncomeMsg.newBuilder();
            msg.setType(k).setIncome(v);
            builder.addMsg(msg);
        });
        return builder.build();
    }

    public static Map<Long, Double> queryAverageTransactionprice(boolean isApartment) {
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
        Map<Long, Double> map = new LinkedHashMap<>();
        int type = APARTMENT;
        if (!isApartment) {
            type = GROUND;
        }
        averageTransactionPrice.find(and(
                eq(TYPE, type),
                gte(TIME, startTime),
                lte(TIME, endTime)
        ))
                .projection(fields(include(TIME, KEY_AVG), excludeId()))
                .sort(Sorts.descending(TIME))
                .forEach((Block<? super Document>) document ->
                {
                    map.put(document.getLong(TIME), document.getDouble(KEY_AVG));
                });
        return map;
    }

    public static Ss.AverageTransactionprice.AvgPrice getCurrenttransactionPrice(boolean isApartment) {
        long startTime = SummaryUtil.todayStartTime(System.currentTimeMillis());
        long endTime = System.currentTimeMillis();
        Ss.AverageTransactionprice.AvgPrice.Builder builder = Ss.AverageTransactionprice.AvgPrice.newBuilder();
        if (isApartment) {
            builder.setPrice(0);
            List<Document> list = LogDb.transactionPrice(startTime, endTime, LogDb.getNpcRentApartment());
            list.stream().filter(o->o!=null).forEach(d->{
                builder.setPrice(d.getDouble(KEY_AVG));
            });
            builder.setTime(todayStartTime(System.currentTimeMillis()));
        } else {
            builder.setPrice(0);
            List<Document> list = LogDb.transactionPrice(startTime, endTime, LogDb.getBuyGround());
            list.stream().filter(o->o!=null).forEach(d->{
                builder.setPrice(d.getDouble(KEY_AVG));
            });
            builder.setTime(todayStartTime(System.currentTimeMillis()));
        }
        return builder.build();
    }

    public static void insertCityTransactionAmount(List<Document> documentList,
                                                   long time, MongoCollection<Document> collection) {
        documentList.forEach(document ->
                document.append(TIME, time));
        if (!documentList.isEmpty()) {
            collection.insertMany(documentList);
        }
    }

    public static Map<Long, Long> queryCityTransactionAmount(MongoCollection<Document> collection) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.set(Calendar.HOUR_OF_DAY,0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND,0);
        Date endDate = calendar.getTime();
        long endTime=endDate.getTime();

        calendar.add(Calendar.DATE, -7);
        Date startDate = calendar.getTime();
        long startTime=startDate.getTime();
        Map<Long, Long> map = new LinkedHashMap<>();

        collection.find(and(
                gte(TIME, startTime),
                lte(TIME, endTime)
        ))

                .projection(fields(include(TIME, KEY_TOTAL), excludeId()))
                .sort(Sorts.descending(TIME))
                .forEach((Block<? super Document>) document ->
                {
                    try {
                        map.put(document.getLong(TIME), document.getLong(KEY_TOTAL));
                    } catch (Exception e) {
                        map.put(document.getLong(TIME), 0l);
                    }
                });

        return map;
    }
    public static Map<Long, Long> queryCurrCityTransactionAmount(MongoCollection<Document> collection) {
        Map<Long, Long> map = new HashMap<>();
        long nowTime = System.currentTimeMillis();
        long startTime = SummaryUtil.todayStartTime(nowTime);
        List<Document> documentList = new ArrayList<>();
        collection.aggregate(
                Arrays.asList(
                        Aggregates.match(and(gte("t", startTime), lte("t", nowTime))),
                        Aggregates.group(null, Accumulators.sum(KEY_TOTAL, "$a"))
                )
        ).forEach((Block<? super Document>) documentList::add);
        documentList.stream().filter(o->o!=null).forEach(d->{
            map.put(startTime, d.getLong(KEY_TOTAL));
        });
        return map;
    }

    public static Map<Long, Long> queryCityMoneyPoolLog(MongoCollection<Document> collection) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.set(Calendar.HOUR_OF_DAY,0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND,0);
        Date endDate = calendar.getTime();
        long endTime=endDate.getTime();

        calendar.add(Calendar.DATE, -7);
        Date startDate = calendar.getTime();
        long startTime=startDate.getTime();
        Map<Long, Long> map = new LinkedHashMap<>();

        collection.find(and(
                gte(TIME, startTime),
                lte(TIME, endTime)
        ))

                .projection(fields(include(TIME, KEY_TOTAL), excludeId()))
                .sort(Sorts.descending(TIME))
                .forEach((Block<? super Document>) document ->
                {
                    map.put(document.getLong(TIME), document.getLong(KEY_TOTAL));
                });

        return map;
    }
}
