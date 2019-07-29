package Shared;

import ch.qos.logback.core.util.TimeUtil;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.InvalidProtocolBufferException;
import com.googlecode.protobuf.format.JsonFormat;
import com.mongodb.Block;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.WriteConcern;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.*;
import gs.Gs;
import org.bson.Document;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Projections.excludeId;
import static com.mongodb.client.model.Projections.fields;
import static com.mongodb.client.model.Projections.include;

public class LogDb {
	private static MongoClientURI connectionUrl;
	private static MongoClient mongoClient;
	private static MongoDatabase database;
	private static final int TP_APARTMENT = 14;
	private static final int TP_TYPE_MATERIAL = 21;
	private static final int TP_TYPE_GOODS = 22;

	private static final int TYPE_BUILDING = 1;
	private static final int TYPE_GOODS = 2;
	private static final int TYPE_INVENT = 3;
	private static final int TYPE_EVAPOINT = 4;

	private static final String PAY_SALARY = "paySalary";
	private static final String PAY_TRANSFER = "payTransfer";

	private static final String BUY_INSHELF = "buyInShelf";
	private static final String NPC_BUY_INSHELF = "npcBuyInShelf";
	private static final String RENT_GROUND = "rentGround";
	private static final String BUY_GROUND = "buyGround";
	private static final String LAND_AUCTION = "landAuction"; //土地拍卖

	private static final String EXTEND_BAG = "extendBag";
	private static final String FLIGHT_BET = "flightBet";
	private static final String INCOME_VISIT = "incomeVisit";

	private static final String PLAYER_INFO = "playerInfo";
	private static final String BUILDING_INCOME = "buildingIncome";
	private static final String BUILDING_PAY = "buildingPay";
	//研究所日志记录
	private static final String LABORATORY_RECORD = "laboratoryRecord";
	// 推广公司日志记录
	private static final String PROMOTION_RECORD = "promotionRecord";

	private static final String NPC_RENT_APARTMENT = "npcRentApartment";
	private static final String CITY_BROADCAST = "cityBroadcast";
	private static final String NPC_TYPE_NUM = "npcTypeNum";

	private static final String FLOW_AND_LIFT = "flowAndLift";
	private static final String NPCBUY_INRETAILCOL = "npcBuyInRetailCol";

	//集散中心租用仓库的收入记录
	private static final String RENT_WAREHOUSE_INCOME = "rentWarehouseIncome";
	
	private static final String PLAYER_INCOME = "playerIncome";
	private static final String PLAYER_PAY = "playerPay";
	//购买租户上架的商品记录
	private static final String BUY_RENTER_INSHELF = "buyRenterInShelf";
	//租户仓库收入记录
	private static final String RENTER_SHELF_INCOME = "renterShelfIncome";
	//运输记录（租用仓库之间的运输记录）
	private static final String PAY_RENTER_TRANSFER = "payRenterTransfer";
	private static final String MINERS_COST= "minersCost";//矿工费用
	private static final String NPC_MINERS_COST = "npcMinersCost";//npc支付矿工费用

	//收入通知
	private static final String INCOME_NOTIFY = "incomeNotify";

	private static final String SELLER_BUILDING_INCOME = "sellerBuildingIncome";//建筑收入者货架收入（用于离线收入）
	//---------------------------------------------------
	private static MongoCollection<Document> flowAndLift;

	private static MongoCollection<Document> npcBuyInRetailCol; // table in the log database
	private static MongoCollection<Document> paySalary; // table in the log database

	//player buy material or goods
	private static MongoCollection<Document> buyInShelf;
	private static MongoCollection<Document> npcBuyInShelf;
	private static MongoCollection<Document> payTransfer;
	private static MongoCollection<Document> rentGround;
	private static MongoCollection<Document> buyGround;
	private static MongoCollection<Document> landAuction;
	private static MongoCollection<Document> extendBag;
	//-----------------------------------------

	//player buy material or goods  and npc buy goods
	private static MongoCollection<Document> incomeVisit;

	private static MongoCollection<Document> playerInfo;
	private static MongoCollection<Document> buildingIncome;
	private static MongoCollection<Document> buildingPay;		//建筑支出（不含详细信息，用于建筑曲线图支出统计，非经营详情）
	private static MongoCollection<Document> flightBet;
	private static MongoCollection<Document> laboratoryRecord;
	private static MongoCollection<Document> promotionRecord;

	//npc rent apartment
	private static MongoCollection<Document> npcRentApartment;
	private static MongoCollection<Document> cityBroadcast;
	private static MongoCollection<Document> npcTypeNum;
	//player rent warehouse
	private static MongoCollection<Document> rentWarehouseIncome;
	private static MongoCollection<Document> playerIncome;
	private static MongoCollection<Document> playerPay;
	//WareHouserenter
	private static MongoCollection<Document> buyRenterInShelf;	//购买了租户仓库的商品
	private static MongoCollection<Document> renterShelfIncome;//租户仓库的收入
	private static MongoCollection<Document> payRenterTransfer;//租用仓库间的运输记录
	//npc and player pay Miner cost
	private static MongoCollection<Document> minersCost;	//矿工费用
	private static MongoCollection<Document> npcMinersCost;//矿工费用

	/*用作离线通知*/
	private static MongoCollection<Document> sellerBuildingIncome;//建筑收入

	public static final String KEY_TOTAL = "total";

	private static MongoCollection<Document> incomeNotify;
	//保持时间 7 天，单位秒
	public static final long incomeNotify_expire = 7 * 24 * 3600;

	public static void init(String url, String dbName)
	{
		connectionUrl = new MongoClientURI(url);
		mongoClient = new MongoClient(connectionUrl);
		database = mongoClient.getDatabase(dbName);
		npcBuyInRetailCol = database.getCollection(NPCBUY_INRETAILCOL)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);

		paySalary = database.getCollection(PAY_SALARY)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		buyInShelf = database.getCollection(BUY_INSHELF)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		npcBuyInShelf = database.getCollection(NPC_BUY_INSHELF)
						.withWriteConcern(WriteConcern.UNACKNOWLEDGED);

		payTransfer = database.getCollection(PAY_TRANSFER)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		rentGround = database.getCollection(RENT_GROUND)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		buyGround = database.getCollection(BUY_GROUND)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		landAuction = database.getCollection(LAND_AUCTION)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		extendBag = database.getCollection(EXTEND_BAG)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);

		flightBet = database.getCollection(FLIGHT_BET)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		incomeVisit = database.getCollection(INCOME_VISIT)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		playerInfo = database.getCollection(PLAYER_INFO)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		buildingIncome = database.getCollection(BUILDING_INCOME)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		buildingPay= database.getCollection(BUILDING_PAY)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		npcRentApartment = database.getCollection(NPC_RENT_APARTMENT)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		cityBroadcast = database.getCollection(CITY_BROADCAST)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		npcTypeNum = database.getCollection(NPC_TYPE_NUM)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		flowAndLift = database.getCollection(FLOW_AND_LIFT)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		promotionRecord = database.getCollection(PROMOTION_RECORD)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		laboratoryRecord = database.getCollection(LABORATORY_RECORD)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		//租用仓库
		rentWarehouseIncome = database.getCollection(RENT_WAREHOUSE_INCOME)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		playerIncome = database.getCollection(PLAYER_INCOME)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		playerPay = database.getCollection(PLAYER_PAY)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		//购买租户上架商品
		buyRenterInShelf = database.getCollection(BUY_RENTER_INSHELF)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		//租户仓库的收入
		renterShelfIncome=database.getCollection(RENTER_SHELF_INCOME)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		//租用仓库间的运输记录
		payRenterTransfer = database.getCollection(PAY_RENTER_TRANSFER)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		minersCost=database.getCollection(MINERS_COST)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		npcMinersCost=database.getCollection(NPC_MINERS_COST)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);

		incomeNotify = database.getCollection(INCOME_NOTIFY)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		/*离线通知统计使用*/
		sellerBuildingIncome=database.getCollection(SELLER_BUILDING_INCOME)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		AtomicBoolean hasIndex = new AtomicBoolean(false);
		incomeNotify.listIndexes().forEach((Consumer<? super Document>) document ->
		{
			System.out.println(document.toJson());
			if (document.get("key",Document.class).get("time") != null)
			{
				hasIndex.set(true);
			}
		});

		if (!hasIndex.get())
		{
			System.out.println("collection incomeNotify no index,create");
			incomeNotify.createIndex(new Document("time", 1),
					new IndexOptions().background(true).expireAfter(incomeNotify_expire, TimeUnit.SECONDS));
			incomeNotify.listIndexes().forEach((Consumer<? super Document>) document ->
			{
				System.out.println(document.toJson());
			});
		}
		else{ System.out.println("collection incomeNotify index exists");}

	}

	public static MongoDatabase getDatabase()
	{
		return database;
	}

	public static void startUp(){}

	private static JsonFormat jsonFormat = new JsonFormat();
	public static void insertIncomeNotify(UUID receiver, Gs.IncomeNotify notify)
	{
		Document document = new Document("time", new Date())
				.append("receiver", receiver)
				.append("notifyJson", jsonFormat.printToString(notify));
		incomeNotify.insertOne(document);
	}

	public static List<Gs.IncomeNotify> getIncomeNotify(UUID receiver, int limit)
	{
		List<Gs.IncomeNotify> notifyList = new ArrayList<>();
		incomeNotify.find(Filters.eq("receiver",receiver))
				.sort(Sorts.descending("time"))
				.limit(limit).forEach((Block<? super Document>) document ->
		{
			Gs.IncomeNotify.Builder builder = Gs.IncomeNotify.newBuilder();
			try
			{
				jsonFormat.merge(document.getString("notifyJson"), ExtensionRegistry.getEmptyRegistry(), builder);
				notifyList.add(builder.build());
			}
			catch (JsonFormat.ParseException e)
			{
				e.printStackTrace();
			}
		});
		return notifyList;
	}

	/**
	 * @param yestodayStartTime
	 * @param todayStartTime
	 * @param collection
	 * @return Aggregation document only has id,total
	 */
	public static List<Document> daySummary1(long yestodayStartTime, long todayStartTime,
											 MongoCollection<Document> collection, boolean isIncomne)
	{
		List<Document> documentList = new ArrayList<>();
		String groupStr = "$r";
		if (isIncomne) {
			groupStr = "$d";
		}
		Document projectObject = new Document()
				.append("id", "$_id")
				.append(KEY_TOTAL, "$" + KEY_TOTAL)
				.append("_id", 0);
		collection.aggregate(
				Arrays.asList(
						Aggregates.match(and(
								gte("t", yestodayStartTime),
								lt("t", todayStartTime))),
						Aggregates.group(groupStr, Accumulators.sum(KEY_TOTAL, "$a")),
						Aggregates.project(projectObject)
				)
		).forEach((Block<? super Document>) documentList::add);
		return documentList;
	}

	public static List<Document> daySummaryRoomRent(long yestodayStartTime, long todayStartTime)
	{
		List<Document> documentList = new ArrayList<>();
        Document projectObject = new Document()
                .append("id", "$_id")
                .append(KEY_TOTAL, "$" + KEY_TOTAL)
                .append("_id",0);
		incomeVisit.aggregate(
				Arrays.asList(
						Aggregates.match(and(
								eq("tp",TP_APARTMENT),
								gte("t", yestodayStartTime),
								lt("t", todayStartTime))),
						Aggregates.group("$r", Accumulators.sum(KEY_TOTAL, "$a")),
                        Aggregates.project(projectObject)
				)
		).forEach((Block<? super Document>) documentList::add);
		return documentList;
	}

	public static List<Document> daySummaryShelf(long yestodayStartTime, long todayStartTime,
												 MongoCollection<Document> collection,
												 boolean isGoods,boolean isIncomne)
	{
		List<Document> documentList = new ArrayList<>();
		String groupStr = "$r";
		if (isIncomne) {
			groupStr = "$d";
		}
        Document groupObject = new Document("_id",
                new Document("r", groupStr)
                        .append("tpi", "$tpi"));
        Document projectObject = new Document()
                .append("id", "$_id._id.r")
                .append("tpi", "$_id._id.tpi")
                .append("total","$total")
                .append("_id",0);
		int tp = TP_TYPE_GOODS;
		if (!isGoods) {
			tp = TP_TYPE_MATERIAL;
		}
		//npc buy
        collection.aggregate(
                Arrays.asList(
                        Aggregates.match(and(
                                eq("tp", tp),
                                gte("t", yestodayStartTime),
                                lt("t", todayStartTime))),
                        Aggregates.group(groupObject, Accumulators.sum(KEY_TOTAL, "$a")),
                        Aggregates.project(projectObject)
                )
        ).forEach((Block<? super Document>) documentList::add);
		return documentList;
	}
	public static List<Document> daySummaryHistoryIncome(long yestodayStartTime, long todayStartTime,MongoCollection<Document> collection) {
		List<Document> documentList = new ArrayList<>();
		Document projectObject = new Document()
				.append("id", "$_id")
				.append(KEY_TOTAL, "$" + KEY_TOTAL)
				.append("_id",0);
		collection.aggregate(
				Arrays.asList
						(
								Aggregates.match(and(
										gte("t", yestodayStartTime),
										lt("t", todayStartTime))),
										Aggregates.group("$tp", Accumulators.sum(KEY_TOTAL, "$a")),
								Aggregates.project(projectObject)
						)
		).forEach((Block<? super Document>) documentList::add);
		return documentList;
	}

	public static List<Document> daySummaryShelfIncome(long yestodayStartTime, long todayStartTime,
												 MongoCollection<Document> collection,int buildType, UUID playerId)
	{
		List<Document> documentList = new ArrayList<>();
		collection.aggregate(
				Arrays.asList(
						Aggregates.match(and(
								eq("tp", buildType),
								eq("d", playerId),
								gte("t", yestodayStartTime),
								lt("t", todayStartTime))),
						Aggregates.project(fields(include("tpi","p","a","t","r","w","d","b"), excludeId()))
				)
		).forEach((Block<? super Document>) documentList::add);
		return documentList;
	}
	public static List<Document> daySummaryShelfPay(long yestodayStartTime, long todayStartTime,
														 MongoCollection<Document> collection,int buildType, UUID playerId)
	{
		List<Document> documentList = new ArrayList<>();
		collection.aggregate(
				Arrays.asList(
						Aggregates.match(and(
								eq("r", playerId),
								gte("t", yestodayStartTime),
								lt("t", todayStartTime))),
						Aggregates.project(fields(include("tpi","p","a","t","r","w","d","b"), excludeId()))
				)
		).forEach((Block<? super Document>) documentList::add);
		return documentList;
	}
	public static List<Document> daySummaryTransferPay(long yestodayStartTime, long todayStartTime,
													MongoCollection<Document> collection,int buildType, UUID playerId)
	{
		List<Document> documentList = new ArrayList<>();
		collection.aggregate(
				Arrays.asList(
						Aggregates.match(and(
								eq("r", playerId),
								gte("t", yestodayStartTime),
								lt("t", todayStartTime))),
						Aggregates.project(fields(include("tpi","t","s","d","a","i","c"), excludeId()))
				)
		).forEach((Block<? super Document>) documentList::add);
		return documentList;
	}
	public static List<Document> daySummaryRetailShopIncome(long yestodayStartTime, long todayStartTime,
													MongoCollection<Document> collection,int buildType, UUID playerId)
	{
		List<Document> documentList = new ArrayList<>();
		collection.aggregate(
				Arrays.asList(
						Aggregates.match(and(
								eq("d", playerId),
								gte("t", yestodayStartTime),
								lt("t", todayStartTime))),
						Aggregates.project(fields(include("tpi","p","a","t","d","b"), excludeId()))
				)
		).forEach((Block<? super Document>) documentList::add);
		return documentList;
	}
	public static List<Document> daySummaryApartmentIncome(long yestodayStartTime, long todayStartTime,
													MongoCollection<Document> collection,int buildType, UUID playerId)
	{
		List<Document> documentList = new ArrayList<>();
		collection.aggregate(
				Arrays.asList(
						Aggregates.match(and(
								eq("tp", buildType),
								eq("d", playerId),
								gte("t", yestodayStartTime),
								lt("t", todayStartTime))),
						Aggregates.project(fields(include("p","a","t","d","b","mid"), excludeId()))
				)
		).forEach((Block<? super Document>) documentList::add);
		return documentList;
	}
	public static List<Document> daySummaryGroundIncome(long yestodayStartTime, long todayStartTime,
														   MongoCollection<Document> collection,int buildType, UUID playerId)
	{
		List<Document> documentList = new ArrayList<>();
		collection.aggregate(
				Arrays.asList(
						Aggregates.match(and(
								eq("d", playerId),
								gte("t", yestodayStartTime),
								lt("t", todayStartTime))),
						Aggregates.project(fields(include("s","a","p","t"), excludeId()))
				)
		).forEach((Block<? super Document>) documentList::add);
		return documentList;
	}
	public static List<Document> daySummaryGroundPay(long yestodayStartTime, long todayStartTime,
														MongoCollection<Document> collection,int buildType, UUID playerId)
	{
		List<Document> documentList = new ArrayList<>();
		collection.aggregate(
				Arrays.asList(
						Aggregates.match(and(
								eq("r", playerId),
								gte("t", yestodayStartTime),
								lt("t", todayStartTime))),
						Aggregates.project(fields(include("s","a","p","t"), excludeId()))
				)
		).forEach((Block<? super Document>) documentList::add);
		return documentList;
	}
	public static List<Document> daySummaryStaffSalaryPay(long yestodayStartTime, long todayStartTime,
													MongoCollection<Document> collection,int buildType, UUID playerId)
	{
		List<Document> documentList = new ArrayList<>();
		collection.aggregate(
				Arrays.asList(
						Aggregates.match(and(
								eq("tp", buildType),
								eq("r", playerId),
								gte("t", yestodayStartTime),
								lt("t", todayStartTime))),
						Aggregates.project(fields(include("s","a","t","r","b"), excludeId()))
				)
		).forEach((Block<? super Document>) documentList::add);
		return documentList;
	}
	public static List<Document> dayNpcGoodsNum(long startTime, long endTime, MongoCollection<Document> collection)
	{
		List<Document> documentList = new ArrayList<>();
        Document projectObject = new Document()
                .append("id", "$_id")
                .append(KEY_TOTAL, "$" + KEY_TOTAL)
                .append("_id",0);
        collection.aggregate(
				Arrays.asList(
						Aggregates.match(and(
								gte("t", startTime),
								lt("t", endTime))),
						Aggregates.group("$tpi",  Accumulators.sum(KEY_TOTAL, 1l)),
                        Aggregates.project(projectObject)
				)
		).forEach((Block<? super Document>) documentList::add);
		return documentList;
	}

	public static List<Document> dayApartmentNpcNum(long startTime, long endTime, MongoCollection<Document> collection)
	{
		List<Document> documentList = new ArrayList<>();
        Document projectObject = new Document()
                .append("id", "$_id")
                .append(KEY_TOTAL, "$" + KEY_TOTAL)
                .append("_id",0);
        collection.aggregate(
				Arrays.asList(
						Aggregates.match(and(
								gte("t", startTime),
								lt("t", endTime))),
						Aggregates.group(null,  Accumulators.sum(KEY_TOTAL, 1l)),
                        Aggregates.project(projectObject)
				)
		).forEach((Block<? super Document>) documentList::add);
		return documentList;
	}
	
	public static List<Document> dayYesterdayExchangeAmount(long endTime, MongoCollection<Document> collection)
	{
		List<Document> documentList = new ArrayList<>();
		Document projectObject = new Document()
				.append("id", "$_id")
				.append(KEY_TOTAL, "$" + KEY_TOTAL)
				.append("_id",0);
		collection.aggregate(
				Arrays.asList(
						Aggregates.match(and(
								lt("t", endTime))),
						Aggregates.group(null,  Accumulators.sum(KEY_TOTAL, "$a")),
						Aggregates.project(projectObject)
						)
				).forEach((Block<? super Document>) documentList::add);
		return documentList;
	}
	
	public static List<Document> dayTodayNpcExchangeAmount(long startTime,long endTime,MongoCollection<Document> collection)
	{
		List<Document> documentList = new ArrayList<>();
		Document projectObject = new Document()
				.append("id", "$_id")
				.append(KEY_TOTAL, "$" + KEY_TOTAL)
				.append("_id",0);
		collection.aggregate(
				Arrays.asList(
						Aggregates.match(and(
								gte("t", startTime),
								lt("t", endTime))),
						Aggregates.group(null,  Accumulators.sum(KEY_TOTAL, "$a")),
						Aggregates.project(projectObject)
						)
				).forEach((Block<? super Document>) documentList::add);
		return documentList;
	}

	public static List<Document> dayPlayerIncomeOrPay(long startTime, long endTime, MongoCollection<Document> collection)
	{
		List<Document> documentList = new ArrayList<>();
		Document projectObject = new Document()
				.append("id", "$_id")
				.append(KEY_TOTAL, "$" + KEY_TOTAL)
				.append("_id",0);
		collection.aggregate(
				Arrays.asList(
						Aggregates.match(and(
								gte("t", startTime),
								lt("t", endTime))),
						Aggregates.group("$p",  Accumulators.sum(KEY_TOTAL, "$a")),
						Aggregates.project(projectObject)
				)
		).forEach((Block<? super Document>) documentList::add);
		return documentList;
	}

	public static List<Document> getDayGoodsSoldDetail(long startTime, long endTime, MongoCollection<Document> collection)
	{
		List<Document> documentList = new ArrayList<>();
		Document projectObject = new Document()
				.append("p", "$_id._id.i")
				.append("id", "$_id._id.tpi")
				.append(KEY_TOTAL, "$" + KEY_TOTAL)
				.append("n", "$n")
				.append("_id",0);
		Document groupObject = new Document("_id",
				new Document("i", "$i")
						.append("tpi", "$tpi"));
		collection.aggregate(
				Arrays.asList(
						Aggregates.match(and(
								gte("t", startTime),
								lt("t", endTime))),
						Aggregates.group(groupObject, Accumulators.sum(KEY_TOTAL, "$a"),Accumulators.sum("n", 1l)),
						Aggregates.project(projectObject)
				)
		).forEach((Block<? super Document>) documentList::add);
		return documentList;
	}

	public static void insertPlayerInfo(UUID uuid,boolean isMale)
	{
		playerInfo.insertOne(new Document("r", uuid).append("male",isMale));
	}

	public static void flightBet(UUID playerId, int delay, int amount, boolean win, Gs.FlightData d,int s)
	{
		Document document = new Document("t", System.currentTimeMillis());
		document.append("i", playerId)
				.append("d", delay)
				.append("a", amount)
				.append("w", win)
				.append("f", d.toByteArray())
				.append("s", s);
		flightBet.insertOne(document);
	}
	public static class FlightBetRecord {
		public FlightBetRecord(UUID playerId, int delay, int amount, boolean win, Gs.FlightData data) {
			this.playerId = playerId;
			this.delay = delay;
			this.amount = amount;
			this.win = win;
			this.data = data;
		}

		public UUID playerId;
		public int delay;
		public int amount;
		public boolean win;
		public Gs.FlightData data;
	}
	public static List<FlightBetRecord> getFlightBetRecord(UUID playerId)
	{
		List<Document> list = new ArrayList<>();
		flightBet.find(eq("i", playerId)).forEach((Block<? super Document>) list::add);
		return list.stream().map(o-> {
			try {
				return new FlightBetRecord(
						o.get("i", UUID.class),
						o.getInteger("d"),
						o.getInteger("a"),
						o.getBoolean("w"),
						Gs.FlightData.parseFrom(o.get("f", org.bson.types.Binary.class).getData()));
			} catch (InvalidProtocolBufferException e) {
				return null;
			}
		}).filter(o -> o != null).collect(Collectors.toList());
	}

	public static void buildingIncome(UUID bId,UUID payId,long cost,int type,int typeId)
	{
		Document document = new Document("t", System.currentTimeMillis());
		document.append("b", bId)
				.append("p", payId)
				.append("a", cost)
				.append("tp", type)
				.append("tpi", typeId);
		buildingIncome.insertOne(document);
	}

	public static void buildingIncome(UUID bId,UUID payId,long cost,int type,int typeId,long time)
	{
		Document document = new Document("t", time);
		document.append("b", bId)
				.append("p", payId)
				.append("a", cost)
				.append("tp", type)
				.append("tpi", typeId);
		buildingIncome.insertOne(document);
	}

	public static void buildingPay(UUID bId,UUID payId,long cost)
	{
		Document document = new Document("t", System.currentTimeMillis());
		document.append("b", bId)
				.append("p", payId)
				.append("a", cost);
		buildingPay.insertOne(document);
	}

	public static List<Document> buildingDayIncomeSummary(long yestodayStartTime, long todayStartTime)
	{
		List<Document> documentList = new ArrayList<>();
		Document projectObject = new Document()
				.append("id", "$_id")
				.append(KEY_TOTAL, "$" + KEY_TOTAL)
				.append("_id", 0);
		buildingIncome.aggregate(
				Arrays.asList(
						Aggregates.match(and(
								gte("t", yestodayStartTime),
								lt("t", todayStartTime))),
						Aggregates.group("$b", Accumulators.sum(KEY_TOTAL, "$a")),
						Aggregates.project(projectObject)
				)
		).forEach((Block<? super Document>) documentList::add);
		return documentList;
	}
	//统计建筑1天的支出（yty）
	public static List<Document> buildingDayPaySummary(long yestodayStartTime, long todayStartTime)		//统计玩家建筑一天的所有支出（不含详细信息）
	{
		List<Document> documentList = new ArrayList<>();
		Document projectObject = new Document()
				.append("id", "$_id")
				.append(KEY_TOTAL, "$" + KEY_TOTAL)
				.append("_id", 0);
		buildingPay.aggregate(
				Arrays.asList(
						Aggregates.match(and(
								gte("t", yestodayStartTime),
								lt("t", todayStartTime))),
						Aggregates.group("$b", Accumulators.sum(KEY_TOTAL, "$a")),
						Aggregates.project(projectObject)
				)
		).forEach((Block<? super Document>) documentList::add);
		return documentList;
	}

	/*统计所有货架建筑的经营详情(包括了零售店)的货物销售详情(yty)*/
	public static Map<Integer,List<Document>> buildingDaySaleDetailIncomeSummary(long yestodayStartTime, long todayStartTime){
		Map<Integer, List<Document>> map = new HashMap<>();
		List<Document> factoryInshelf = new ArrayList<>();
		List<Document> retailInshelf = new ArrayList<>();
		Document projectObject = new Document()
				.append("bid","$_id._id.b")
				.append("itemId","$_id._id.tpi")
				.append("brand","$_id._id.brandName")
				.append("p","$_id.i")
				.append("num","$n")
				.append(KEY_TOTAL, "$" + KEY_TOTAL)
				.append("_id", 0);
		//分组id(根据生产者id、建筑id、商品id分组)
		Document groupObject = new Document("_id",
				new Document("b", "$b")
						.append("tpi", "$tpi"))
						.append("i","$i");
		buyInShelf.aggregate(
				Arrays.asList(
					Aggregates.match(and(
							gte("t", yestodayStartTime),
							lt("t", todayStartTime))),
						Aggregates.group(groupObject, Accumulators.sum("n","$n"),Accumulators.sum(KEY_TOTAL, "$a")),
					Aggregates.project(projectObject)
				)
		).forEach((Block<? super Document>) factoryInshelf::add);

		//再统计零售店建筑的每日经营详情
		npcBuyInShelf.aggregate(
				Arrays.asList(
						Aggregates.match(and(
								gte("t", yestodayStartTime),
								lt("t", todayStartTime))),
						Aggregates.group(groupObject, Accumulators.sum(KEY_TOTAL, "$a"),Accumulators.sum("n", "$n")),
						Aggregates.project(projectObject)
				)
		).forEach((Block<? super Document>) retailInshelf::add);
		map.put(1,factoryInshelf);
		map.put(2,retailInshelf);
		return map;
	}

	/*统计建筑今日的经营详情)(yty)*/
	public static List<Document> buildingDaySaleDetailByBuilding(long startTime,long endTime,UUID bid,MongoCollection<Document> collection){
		List<Document> record = new ArrayList<>();
		Document projectObject = new Document()
				.append("bid","$_id._id.b")
				.append("itemId","$_id._id.tpi")
				.append("p","$_id.i")
				.append("brand","$_id._id.brand")
				.append("num","$n")
				.append(KEY_TOTAL, "$" + KEY_TOTAL)
				.append("_id", 0);
		Document groupObject = new Document("_id",
				new Document("b", "$b")
						.append("tpi", "$tpi"))
						.append("i","$i");
		collection.aggregate(
				Arrays.asList(
						Aggregates.match(and(
								eq("b","$b"),
								gte("t", startTime),
								lt("t", endTime))),
						Aggregates.group(groupObject, Accumulators.sum("n","$n"),Accumulators.sum(KEY_TOTAL, "$a")),
						Aggregates.project(projectObject)
				)
		).forEach((Block<? super Document>) record::add);
		return record;
	}

	public static List<Document> queryBuildingFlowAndLift(long startTime,UUID buildingId)
	{
		List<Document> list = new ArrayList<>();
		 flowAndLift.find(and(eq("b",buildingId),gte("t", startTime)))
				.sort(Sorts.ascending("t"))
				 .forEach((Block<? super Document>) list::add);
		return list;
	}

	public static void flowAndLift(UUID buildingId,int flowcount,float lift)
	{
			long now = System.currentTimeMillis();
			long time = now - now % 3600000;
			Document document = new Document().append("t", time)
					.append("b", buildingId)
					.append("f", flowcount)
					.append("l", lift);
			flowAndLift.insertOne(document);
	}

	public static void buyInShelf(UUID buyId, UUID sellId, long n, long price,
								  UUID producerId, UUID bid, UUID wid,int type, int typeId,String brand)
	{
		Document document = new Document("t", System.currentTimeMillis());
		document.append("r", buyId)
				.append("d", sellId)
				.append("b", bid)
				.append("w", wid)
				.append("p", price)
				.append("n",n)				//yty  数量
				.append("brand",brand)      //yty 品牌名
				.append("a", n * price)
				.append("i", producerId)
				.append("tp", type)
				.append("tpi", typeId);
		buyInShelf.insertOne(document);
	}

	public static void  npcBuyInShelf(UUID npcId, UUID sellId, long n, long price,
								  UUID producerId, UUID bid,int type, int typeId,String brand)
	{
		Document document = new Document("t", System.currentTimeMillis());
		document.append("r", npcId)
				.append("d", sellId)
				.append("b", bid)
				.append("p", price)
				.append("n",n)          //yty  数量
				.append("brand",brand)  //yty 品牌名
				.append("a", n * price)
				.append("i", producerId)
				.append("tp", type)
				.append("tpi", typeId);
		npcBuyInShelf.insertOne(document);
	}

	public static void npcBuyInRetailCol(int itemId, int price, UUID producerId, int itemQty,UUID buildingOwnerId, int buildingQty,float distance) {
		Document document = new Document("t", System.currentTimeMillis());
		document.put("imId", itemId);
		document.put("p", price);
		document.put("iId", producerId);
		document.put("iQ", itemQty);
		document.put("bId", buildingOwnerId);
		document.put("bQ", buildingQty);
		document.put("d", distance);
		npcBuyInRetailCol.insertOne(document);
	}

	public static void paySalary(UUID roleId,int type, UUID buildingId, long salary, long workers) {
		Document document = new Document("t", System.currentTimeMillis());
		document.append("r", roleId)
				.append("tp", type)
				.append("b", buildingId)
				.append("s", salary)
				.append("a", salary * workers)
				.append("w", workers);
		paySalary.insertOne(document);
	}

	public static void payTransfer(UUID roleId, long charge, UUID srcId, UUID dstId,int itemId, UUID producerId, int n)
	{
		Document document = new Document("t", System.currentTimeMillis());
		document.append("r", roleId)
				.append("s", srcId)
				.append("d", dstId)
				.append("tpi", itemId)
				.append("a", charge)
				.append("i", producerId)
				.append("c", n);
		payTransfer.insertOne(document);
	}

	public static void rentGround(UUID roleId, UUID ownerId, long cost, List<Positon> positons)
	{
		Document document = new Document("t", System.currentTimeMillis());
		document.append("r", roleId)
				.append("d", ownerId)
				.append("a", cost)
				.append("p", positionToDoc(positons));
		rentGround.insertOne(document);
	}

	public static void buyGround(UUID roleId, UUID ownerId, long price, List<Positon> plist1)
	{
		long all = price;
		if (ownerId != null) {
			all = price * plist1.size();
		}
		Document document = new Document("t", System.currentTimeMillis());
		document.append("r", roleId)
				.append("d", ownerId)
				.append("s", price)
				.append("a", all)
				.append("p", positionToDoc(plist1));
		buyGround.insertOne(document);
	}
	public static void landAuction(UUID roleId, UUID ownerId, long price, List<Positon> plist1)
	{
		long all = price;
		if (ownerId != null) {
			all = price * plist1.size();
		}
		Document document = new Document("t", System.currentTimeMillis());
		document.append("r", roleId)
				.append("d", ownerId)
				.append("s", price)
				.append("a", all)
				.append("p", positionToDoc(plist1));
		landAuction.insertOne(document);
	}

	public static void extendBag(UUID id, int cost, int bagCapacity)
	{
		Document document = new Document("t", System.currentTimeMillis());
		document.append("r", id)
				.append("a", cost)
				.append("c", bagCapacity);
		extendBag.insertOne(document);
	}

	private static List<Document> positionToDoc(List<Positon> plist1)
	{
		List<Document> pDList = new ArrayList<>();
		for (Positon p : plist1)
		{
			pDList.add(p.toDocument());
		}
		return pDList;
	}

	public static void incomeVisit(UUID roldId,int buildType,long cost, UUID bId, UUID payId)
	{
		Document document = new Document("t", System.currentTimeMillis());
		document.append("r", roldId)
				.append("d", payId)
				.append("b", bId)
				.append("a", cost)
				.append("tp",buildType);
		incomeVisit.insertOne(document);
	}
	
	public static void  npcRentApartment(UUID npcId, UUID sellId, long n, long price,
			UUID ownerId, UUID bid, int type, int mId)
	{
		Document document = new Document("t", System.currentTimeMillis());
		document.append("r", npcId)
				.append("d", sellId)
				.append("p", price)
				.append("a", n * price)
				.append("o", ownerId)
				.append("b", bid)
				.append("tp", type)
				.append("mid", mId);
		npcRentApartment.insertOne(document);
	}

	public static void  cityBroadcast(UUID sellerId, UUID buyerId, long cost, int num, int type)
	{
		//重大交易不删以前的提示，其他都要删除以前的提示
		if(type!=1){
			cityBroadcast.deleteMany(and(
	    			eq("tp",type)
	    			));
		}
		Document document = new Document("t", System.currentTimeMillis());
		document.append("s", sellerId)
				.append("b", buyerId)
				.append("c", cost)
				.append("n", num)
				.append("tp", type);
		cityBroadcast.insertOne(document);
	}
	
	public static void  npcTypeNum(long time, int type, long n)
	{
		Document document = new Document("t", time);
		document.append("tp", type)
				.append("n", n);
		npcTypeNum.insertOne(document);
	}
	//记录推广成交记录
	public static void promotionRecord(UUID sellerId, UUID buyerId,UUID bid,int price,long cost, int typeId,int categoryType,boolean isBuilding) {
		int type = TYPE_BUILDING;
		if (!isBuilding) {
			type = TYPE_GOODS;
		}
		Document document = new Document("t", System.currentTimeMillis());
		document.append("s", sellerId);
		document.append("b", buyerId);
		document.append("p", price); //每毫秒价格
		document.append("a", cost);
		document.append("bid", bid);
		document.append("tp", type);
		document.append("ct", categoryType);
		document.append("tpi", typeId);
		promotionRecord.insertOne(document);
	}

	//记录研究所成交记录
	public static void laboratoryRecord(UUID sellerId, UUID buyerId,UUID bid,int price,long cost, int typeId,boolean isInvent) {
		int type = TYPE_INVENT;
		if (!isInvent) {
			type = TYPE_EVAPOINT;
		}
		Document document = new Document("t", System.currentTimeMillis());
		document.append("s", sellerId);
		document.append("b", buyerId);
		document.append("a", cost);
		document.append("p", price);
		document.append("bid", bid);
		document.append("tpi", typeId);
		document.append("tp", type);
		laboratoryRecord.insertOne(document);
	}


	//租用仓库记录：租用时间、结束时间、租金、租用者id、订单编号、租用容量等数据
	public static void rentWarehouseIncome(Long orderId,UUID bid,UUID renterId,Long endTime,int hourToRent,int rent,int rentCapacity){
		Document document = new Document("t", System.currentTimeMillis());
		document.append("or", orderId)
				.append("b", bid)
				.append("r", renterId)
				.append("e", endTime)
				.append("h", hourToRent)
				.append("money", rent)
				.append("c", rentCapacity);
		rentWarehouseIncome.insertOne(document);
	}
	public static void playerIncome(UUID playerId,long cost){
		Document document = new Document("t", System.currentTimeMillis());
		document.append("p", playerId)
				.append("a", cost);
		playerIncome.insertOne(document);
	}
	public static void playerPay(UUID playerId,long cost){
		Document document = new Document("t", System.currentTimeMillis());
		document.append("p", playerId)
				.append("a", cost);
		playerPay.insertOne(document);
	}
	//购买租户上架商品记录
	public static void buyRenterInShelf(UUID buyId, UUID sellId, long n, long price,
										UUID producerId, Long orderId, int type, int typeId){
		Document document = new Document("t", System.currentTimeMillis());
		document.append("r", buyId)
				.append("d", sellId)
				.append("or", orderId)//订单编号
				.append("p", price)
				.append("a", n * price)
				.append("i", producerId)
				.append("tp", type)
				.append("tpi", typeId);
		buyRenterInShelf.insertOne(document);
	}
	//记录租户仓库收入
	public static void renterShelfIncome(Long orderId,UUID payId,long cost,int type,int typeId){ //租用仓库的货架收入
		Document document = new Document("t", System.currentTimeMillis());
		document.append("or", orderId)
				.append("p", payId)
				.append("a", cost)
				.append("tp", type)
				.append("tpi", typeId);
		renterShelfIncome.insertOne(document);
	}

	//租用仓库之间的运输
	public static void payRenterTransfer(UUID roleId, long charge, Serializable srcId, Serializable dstId, UUID producerId, int n){
		Document document = new Document("t", System.currentTimeMillis());
		document.append("r", roleId)
				.append("s", srcId)
				.append("d", dstId)
				.append("a", charge)
				.append("i", producerId)
				.append("c", n);
		payTransfer.insertOne(document);
	}

	public static void minersCost(UUID pid,double money,double  ratio){
		Document document = new Document("t", System.currentTimeMillis());
		document.append("pid", pid)
				.append("a", money)
				.append("ratio", ratio);//矿工费用收取比例
		minersCost.insertOne(document);
	}

	public static void npcMinersCost(UUID npcId,double money,double  ratio){
		Document document = new Document("t", System.currentTimeMillis());
		document.append("pid", npcId)//npcId
				.append("a", money)  //矿工费用
				.append("ratio", ratio);//矿工费用收取比例
		npcMinersCost.insertOne(document);
	}

	public static void sellerBuildingIncome(UUID bid,int buildingType,UUID playerId,int n,int price,int itemId){
		Document document = new Document("t", System.currentTimeMillis());
		document.append("bid", bid)
				.append("bt", buildingType)
				.append("pid", playerId)
				.append("n", n)
				.append("price",price)
				.append("itemId",itemId);
		sellerBuildingIncome.insertOne(document);
	}
	public static MongoCollection<Document> getNpcBuyInRetailCol()
	{
		return npcBuyInRetailCol;
	}

	public static MongoCollection<Document> getPaySalary()
	{
		return paySalary;
	}

	public static MongoCollection<Document> getBuyInShelf()
	{
		return buyInShelf;
	}

	public static MongoCollection<Document> getPayTransfer()
	{
		return payTransfer;
	}

	public static MongoCollection<Document> getRentGround()
	{
		return rentGround;
	}

	public static MongoCollection<Document> getBuyGround()
	{
		return buyGround;
	}
	public static MongoCollection<Document> getLandAuction()
	{
		return landAuction;
	}
	public static MongoCollection<Document> getBuildingIncome()
	{
		return buildingIncome;
	}

	public static MongoCollection<Document> getBuildingPay() {
		return buildingPay;
	}
	public static Long getTodayBuildingPay(UUID buildingId){
		Long sum=0L;
		//获取今日建筑的支出金额
		FindIterable<Document> documents = buildingPay.find(and(
				eq("b", buildingId),
				gte("t", Util.getTodayStartTs())
		));
		for (Document document : documents) {
			sum+= document.getLong("a");
		}
		return sum;
	}

	public static MongoCollection<Document> getNpcBuyInShelf()
	{
		return npcBuyInShelf;
	}

	public static MongoCollection<Document> getExtendBag()
	{
		return extendBag;
	}

	public static MongoCollection<Document> getIncomeVisit()
	{
		return incomeVisit;
	}

	public static MongoCollection<Document> getPlayerInfo()
	{
		return playerInfo;
	}
	
	public static MongoCollection<Document> getNpcRentApartment()
	{
		return npcRentApartment;
	}
	
	public static MongoCollection<Document> getCityBroadcast()
	{
		return cityBroadcast;
	}
	
	public static MongoCollection<Document> getNpcTypeNum()
	{
		return npcTypeNum;
	}

	public static MongoCollection<Document> getRentWarehouseIncome() {
		return rentWarehouseIncome;
	}
	public static MongoCollection<Document> getPlayerIncome() {
		return playerIncome;
	}
	public static MongoCollection<Document> getPlayerPay() {
		return playerPay;
	}

	public static MongoCollection<Document> getPromotionRecord() {
		return promotionRecord;
	}
	public static MongoCollection<Document> getLaboratoryRecord() {
		return laboratoryRecord;
	}


	public static MongoCollection<Document> getBuyRenterInShelf() {
		return buyRenterInShelf;
	}

	public static MongoCollection<Document> getRenterShelfIncome() {
		return renterShelfIncome;
	}

	public static MongoCollection<Document> getPayRenterTransfer() {
		return payRenterTransfer;
	}

	public static MongoCollection<Document> getMinersCost() {
		return minersCost;
	}

	public static MongoCollection<Document> getNpcMinersCost() {
		return npcMinersCost;
	}

	/*用于离线通知，货架收入*/
	public static MongoCollection<Document> getSellerBuildingIncome() {
		return sellerBuildingIncome;
	}

	public static MongoCollection<Document> getFlightBet() {
		return flightBet;
	}

	public static class Positon
	{
		int x;
		int y;

		public Positon(int x, int y)
		{
			this.x = x;
			this.y = y;
		}

		public Positon(Document document)
		{
			this.x = document.getInteger("x");
			this.y = document.getInteger("y");
		}

		public Document toDocument()
		{
			return new Document().append("x", x).append("y", y);
		}
	}
    public static String getPositionStr(List<Document> list){
		String str="";
		if(list.size()==1){
			Document positon=list.get(0);
			str="("+ positon.getInteger("x")+","+positon.getInteger("y")+")";
		}else{
			Document startPosition=list.get(0);
			Document endPosition=list.get(list.size()-1);
			str="("+ startPosition.getInteger("x")+","+endPosition.getInteger("y")+")";
		}
		return str;
	}
	//--ly
	public static List<Document> dayPlayerExchange1(long startTime, long endTime, MongoCollection<Document> collection,int id)
	{
		List<Document> documentList = new ArrayList<>();
		Document projectObject = new Document()
				.append("id", "$_id")
				.append(KEY_TOTAL, "$" + KEY_TOTAL)
				.append("size", "$size")
				.append("_id",0);
		collection.aggregate(
				Arrays.asList(
						Aggregates.match(and(
								gte("t",startTime),
								lt("t", endTime))),
						Aggregates.group(id,  Accumulators.sum(KEY_TOTAL, "$a"),Accumulators.sum("size", 1l)),
						Aggregates.project(projectObject)
				)
		).forEach((Block<? super Document>) documentList::add);
		return documentList;
	}


	public static List<Document> dayPlayerExchange2(long startTime, long endTime, MongoCollection<Document> collection,boolean isGoods)
	{
		List<Document> documentList = new ArrayList<>();
		Document projectObject = new Document()
				.append("id", "$_id")
				.append(KEY_TOTAL, "$" + KEY_TOTAL)
				.append("size", "$size")
				.append("_id",0);
		int tp = TP_TYPE_GOODS;
		if (!isGoods) {
			tp = TP_TYPE_MATERIAL;
		}
		collection.aggregate(
				Arrays.asList(
						Aggregates.match(and(
								eq("tp", tp),
								gte("t", startTime),
								lt("t", endTime))),
						Aggregates.group("$tpi", Accumulators.sum(KEY_TOTAL, "$a"),Accumulators.sum("size", 1l)),
						Aggregates.project(projectObject)
				)
		).forEach((Block<? super Document>) documentList::add);
		return documentList;
	}

    public static List<Document> hourPromotionRecord(long startTime, long endTime, MongoCollection<Document> collection)
    {
        List<Document> documentList = new ArrayList<>();
        Document projectObject = new Document()
                .append("id", "$_id")
                .append(KEY_TOTAL, "$"+KEY_TOTAL)
                .append("size", "$size" )
                .append("_id",0);
        collection.aggregate(
                Arrays.asList(
                        Aggregates.match(and(
                                gte("t", startTime),
                                lt("t", endTime)
                        )),
                        Aggregates.group("$ct",Accumulators.sum(KEY_TOTAL, "$a"),Accumulators.sum("size", 1l)),
                        Aggregates.project(projectObject)
                )
        ).forEach((Block<? super Document>) documentList::add);
        return documentList;
    }

    public static List<Document> hourLaboratoryRecord(long startTime, long endTime, MongoCollection<Document> collection) {
        List<Document> documentList = new ArrayList<>();
        Document projectObject = new Document()
                .append("id", "$_id")
                .append(KEY_TOTAL, "$"+KEY_TOTAL)
                .append("size", "$size" )
                .append("_id",0);
        collection.aggregate(
                Arrays.asList(
                        Aggregates.match(and(
                                gte("t", startTime),
                                lt("t", endTime)
                        )),
                        Aggregates.group("$tp",Accumulators.sum(KEY_TOTAL, "$a"),Accumulators.sum("size", 1l)),
                        Aggregates.project(projectObject)
                )
        ).forEach((Block<? super Document>) documentList::add);
        return documentList;
    }


}
