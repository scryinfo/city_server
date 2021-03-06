package Shared;

import com.google.common.util.concurrent.AtomicDouble;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.InvalidProtocolBufferException;
import com.googlecode.protobuf.format.JsonFormat;
import com.mongodb.Block;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.WriteConcern;
import com.mongodb.client.FindIterable;
import com.mongodb.client.ListIndexesIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.*;
import gs.Gs;
import org.bson.Document;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Projections.*;
import static com.mongodb.client.model.Projections.fields;
import static com.mongodb.client.model.Projections.include;

public class LogDb {
	public static final int TECHNOLOGY=15;//New Institute
	public static final int PROMOTE=16;//New promotion company
	private static MongoClientURI connectionUrl;
	private static MongoClient mongoClient;
	private static MongoDatabase database;
	private static final int TP_APARTMENT = 14;
	private static final int TP_TYPE_MATERIAL = 21;
	private static final int TP_TYPE_GOODS = 22;

	private static final int TYPE_BUILDING = 1;
	private static final int TYPE_GOODS = 2;
	private static final String PAY_SALARY = "paySalary";
	private static final String PAY_TRANSFER = "payTransfer";

	private static final String BUY_INSHELF = "buyInShelf";
	private static final String NPC_BUY_INSHELF = "npcBuyInShelf";
	private static final String RENT_GROUND = "rentGround";
	private static final String BUY_GROUND = "buyGround";
	private static final String LAND_AUCTION = "landAuction"; //Land auction

	private static final String EXTEND_BAG = "extendBag";
	private static final String FLIGHT_BET = "flightBet";
	private static final String INCOME_VISIT = "incomeVisit";

	private static final String PLAYER_INFO = "playerInfo";
	private static final String BUILDING_INCOME = "buildingIncome";
	private static final String BUILDING_PAY = "buildingPay";
	//Institute Log
	private static final String LABORATORY_RECORD = "laboratoryRecord";
	// Promote company logging
	private static final String PROMOTION_RECORD = "promotionRecord";

	private static final String NPC_RENT_APARTMENT = "npcRentApartment";
	private static final String CITY_BROADCAST = "cityBroadcast";
	private static final String NPC_TYPE_NUM = "npcTypeNum";

	private static final String FLOW_AND_LIFT = "flowAndLift";
	private static final String NPCBUY_INRETAILCOL = "npcBuyInRetailCol";

	//Revenue records of the warehouse rented by the distribution center
	private static final String RENT_WAREHOUSE_INCOME = "rentWarehouseIncome";

	private static final String PLAYER_INCOME = "playerIncome";
	private static final String PLAYER_PAY = "playerPay";
	//Purchase of tenant's commodity record
	private static final String BUY_RENTER_INSHELF = "buyRenterInShelf";
	//Tenant warehouse revenue record
	private static final String RENTER_SHELF_INCOME = "renterShelfIncome";
	//Transport records (transport records between rented warehouses)
	private static final String PAY_RENTER_TRANSFER = "payRenterTransfer";
	private static final String MINERS_COST= "minersCost";//Miner's fee
	private static final String NPC_MINERS_COST = "npcMinersCost";//npc pays the miner's fee

	//Income notice
	private static final String INCOME_NOTIFY = "incomeNotify";

	private static final String SELLER_BUILDING_INCOME = "sellerBuildingIncome";//(Offline revenue)
	private static final String DAY_PLAYER_INCOME = "dayPlayerIncome";
	private static final String PLAYER_BUILDING_BUSINESS = "playerBuildingBusiness";

	private static final String INDUSTRY_SUPPLYANDDEMAND = "industrySupplyAndDemand"; // Industry supply and demand
	private static final String DAY_INDUSTRY_INCOME= "dayIndustryIncome";     // Industry income statement
	private static final String CITY_MONEY_POOL= "cityMoneyPool";
	/*Player landing time statistics (player landing time statistics)yty*/
	private static final String PLAYER_LOGINTIME = "playerLoginTime";
	//AI shopping basic data
	private static final String DAY_AI_BASE_AVG = "dayAiBaseAvg";

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
	private static MongoCollection<Document> buildingPay;		//Construction expenditure (excluding detailed information, used for construction curve expenditure statistics, non-operating details)
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
	private static MongoCollection<Document> buyRenterInShelf;	//Purchased goods from tenant warehouse
	private static MongoCollection<Document> renterShelfIncome;//Tenant warehouse revenue
	private static MongoCollection<Document> payRenterTransfer;//Transportation records between rented warehouses
	//npc and player pay Miner cost
	private static MongoCollection<Document> minersCost;	//Miner's fee
	private static MongoCollection<Document> npcMinersCost;//Miner's fee

	/*Use as offline notification*/
	private static MongoCollection<Document> sellerBuildingIncome;//Construction revenue
	private static MongoCollection<Document> dayPlayerIncome;
	private static MongoCollection<Document> playerBuildingBusiness;
	// Industry supply and demand
	private static MongoCollection<Document> industrySupplyAndDemand;
	// Industry revenue--
	private static MongoCollection<Document> dayIndustryIncome;
	private static MongoCollection<Document> cityMoneyPool;


	private static MongoCollection<Document> playerLoginTime; //Player login time statistics Yty

	public static final String KEY_TOTAL = "total";
	public static final String KEY_AVG = "avg";

	private static MongoCollection<Document> incomeNotify;
	private static MongoCollection<Document> dayAiBaseAvg;

	//Hold time 7 days, in seconds
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
		//Rent warehouse
		rentWarehouseIncome = database.getCollection(RENT_WAREHOUSE_INCOME)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		playerIncome = database.getCollection(PLAYER_INCOME)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		playerPay = database.getCollection(PLAYER_PAY)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		//Purchase tenant shelves
		buyRenterInShelf = database.getCollection(BUY_RENTER_INSHELF)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		//Tenant warehouse revenue
		renterShelfIncome=database.getCollection(RENTER_SHELF_INCOME)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		//Transportation records between rented warehouses
		payRenterTransfer = database.getCollection(PAY_RENTER_TRANSFER)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		minersCost=database.getCollection(MINERS_COST)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		npcMinersCost=database.getCollection(NPC_MINERS_COST)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);

		incomeNotify = database.getCollection(INCOME_NOTIFY)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		/*Offline notification statistics*/
		sellerBuildingIncome=database.getCollection(SELLER_BUILDING_INCOME)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		dayPlayerIncome = database.getCollection(DAY_PLAYER_INCOME)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		playerBuildingBusiness = database.getCollection(PLAYER_BUILDING_BUSINESS)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		/*Player login time statistics*/
		playerLoginTime=database.getCollection(PLAYER_LOGINTIME)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		dayAiBaseAvg=database.getCollection(DAY_AI_BASE_AVG)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		industrySupplyAndDemand = database.getCollection(INDUSTRY_SUPPLYANDDEMAND)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		dayIndustryIncome = database.getCollection(DAY_INDUSTRY_INCOME)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		cityMoneyPool = database.getCollection(CITY_MONEY_POOL)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		/*Create an index of some tables*/
		createIndex();
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
	public static List<Document> daySummaryHistoryIncome(long yestodayStartTime, long todayStartTime,boolean isApartment) {
		MongoCollection<Document> collection = npcRentApartment;
		if (!isApartment) {
			collection = npcBuyInShelf;
		}
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
										Aggregates.group(null, Accumulators.sum(KEY_TOTAL, "$a")),
								Aggregates.project(projectObject)
						)
		).forEach((Block<? super Document>) documentList::add);
		return documentList;
	}
	public static List<Document> daySummaryHistoryIncome(long yestodayStartTime, long todayStartTime,int buildingType) {
		List<Document> documentList = new ArrayList<>();
		Document projectObject = new Document()
				.append("id", "$_id")
				.append(KEY_TOTAL, "$" + KEY_TOTAL)
				.append("_id",0);
		buyInShelf.aggregate(
				Arrays.asList
						(
								Aggregates.match(and(
										eq("bt", buildingType),
										gte("t", yestodayStartTime),
										lt("t", todayStartTime))),
								Aggregates.group(null, Accumulators.sum(KEY_TOTAL, "$a")),
								Aggregates.project(projectObject)
						)
		).forEach((Block<? super Document>) documentList::add);
		return documentList;
	}

	public static List<Document> daySummaryGroundHistoryIncome(long yestodayStartTime, long todayStartTime, MongoCollection<Document> collection) {
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
										lte("t", todayStartTime))),
								Aggregates.group(null, Accumulators.sum(KEY_TOTAL, "$a")),
								Aggregates.project(projectObject)
						)
		).forEach((Block<? super Document>) documentList::add);
		return documentList;
	}
	public static List<Document> transactionPrice(long yestodayStartTime, long todayStartTime, MongoCollection<Document> collection) {
		List<Document> documentList = new ArrayList<>();
		Document projectObject = new Document()
				.append("id", "$_id")
				.append(KEY_AVG, "$" + KEY_AVG)
				.append("_id",0);
		collection.aggregate(
				Arrays.asList
						(
								Aggregates.match(and(
										gte("t", yestodayStartTime),
										lte("t", todayStartTime))),
								Aggregates.group(null, Accumulators.avg(KEY_AVG, "$a")),
								Aggregates.project(projectObject)
						)
		).forEach((Block<? super Document>) documentList::add);
		return documentList;
	}
	public static List<Document> todayTransactionPrice(long yestodayStartTime, long todayStartTime, MongoCollection<Document> collection,int industryType,int itemId) {
		List<Document> documentList = new ArrayList<>();
		Document projectObject = new Document()
				.append("id", "$_id")
				.append(KEY_AVG, "$" + KEY_AVG)
				.append("_id",0);
		collection.aggregate(
				Arrays.asList
						(
								Aggregates.match(and(
										eq("bt",industryType),
										eq("tpi",itemId),
										gte("t", yestodayStartTime),
										lte("t", todayStartTime))),
								Aggregates.group(null, Accumulators.avg(KEY_AVG, "$p")),
								Aggregates.project(projectObject)
						)
		).forEach((Block<? super Document>) documentList::add);
		return documentList;
	}
	public static List<Document> todayItemSales(long yestodayStartTime, long todayStartTime, MongoCollection<Document> collection,int industryType,int itemId) {
		List<Document> documentList = new ArrayList<>();
		Document projectObject = new Document()
				.append("id", "$_id")
				.append(KEY_TOTAL, "$" + KEY_TOTAL)
				.append("_id",0);
		collection.aggregate(
				Arrays.asList
						(
								Aggregates.match(and(
										eq("bt",industryType),
										eq("tpi",itemId),
										gte("t", yestodayStartTime),
										lte("t", todayStartTime))),
								Aggregates.group(null, Accumulators.sum(KEY_TOTAL, "$a")),
								Aggregates.project(projectObject)
						)
		).forEach((Block<? super Document>) documentList::add);
		return documentList;
	}
	public static List<Document> todayItemSales(long yestodayStartTime, long todayStartTime, MongoCollection<Document> collection,int itemId) {
		List<Document> documentList = new ArrayList<>();
		Document projectObject = new Document()
				.append("id", "$_id")
				.append(KEY_TOTAL, "$" + KEY_TOTAL)
				.append("_id",0);
		collection.aggregate(
				Arrays.asList
						(
								Aggregates.match(and(
										eq("tpi",itemId),
										gte("t", yestodayStartTime),
										lte("t", todayStartTime))),
								Aggregates.group(null, Accumulators.sum(KEY_TOTAL, "$a")),
								Aggregates.project(projectObject)
						)
		).forEach((Block<? super Document>) documentList::add);
		return documentList;
	}

	public static List<Document> todayTransactionPrice(long yestodayStartTime, long todayStartTime, MongoCollection<Document> collection, int itemId) {
		List<Document> documentList = new ArrayList<>();
		Document projectObject = new Document()
				.append("id", "$_id")
				.append(KEY_AVG, "$" + KEY_AVG)
				.append("_id", 0);
		collection.aggregate(
				Arrays.asList
						(
								Aggregates.match(and(
										eq("tpi", itemId),
										gte("t", yestodayStartTime),
										lte("t", todayStartTime))),
								Aggregates.group(null, Accumulators.avg(KEY_AVG, "$p")),
								Aggregates.project(projectObject)
						)
		).forEach((Block<? super Document>) documentList::add);
		return documentList;
	}
	public static List<Document> transactionPrice(long yestodayStartTime, long todayStartTime, MongoCollection<Document> collection,int type) {
		List<Document> documentList = new ArrayList<>();
		Document projectObject = new Document()
				.append("id", "$_id")
				.append(KEY_AVG, "$" + KEY_AVG)
				.append("_id",0);
		collection.aggregate(
				Arrays.asList
						(
								Aggregates.match(and(
										eq("bt", type),
										gte("t", yestodayStartTime),
										lte("t", todayStartTime))),
								Aggregates.group("$tpi", Accumulators.avg(KEY_AVG, "$p")),  // 单价
								Aggregates.project(projectObject)
						)
		).forEach((Block<? super Document>) documentList::add);
		return documentList;
	}	public static List<Document> retailshopTransactionPrice(long yestodayStartTime, long todayStartTime, MongoCollection<Document> collection) {
		List<Document> documentList = new ArrayList<>();
		Document projectObject = new Document()
				.append("id", "$_id")
				.append(KEY_AVG, "$" + KEY_AVG)
				.append("_id",0);
		collection.aggregate(
				Arrays.asList
						(
								Aggregates.match(and(
										gte("t", yestodayStartTime),
										lte("t", todayStartTime))),
								Aggregates.group("$tpi", Accumulators.avg(KEY_AVG, "$p")),  // 单价
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
						Aggregates.project(fields(include("tpi","p","a","t","r","w","d","b","miner"), excludeId()))
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
								eq("tp", buildType),		//yty
								eq("r", playerId),
								gte("t", yestodayStartTime),
								lt("t", todayStartTime))),
						Aggregates.project(fields(include("tpi","p","a","t","r","w","d","b","miner"), excludeId()))
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
						Aggregates.project(fields(include("tpi","p","a","t","d","b","miner"), excludeId()))
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
/*Statistics player income and expenditure by day（yty）*/
	public static List<Document> dayPlayerIncomeOrPay(long startTime, long endTime, MongoCollection<Document> collection)
	{
		List<Document> documentList = new ArrayList<>();
		Document groupObject = new Document("_id",
				new Document("p", "$p")
						.append("tp", "$tp"));
		Document projectObject = new Document()
				.append("id", "$_id._id.p")
				.append("tp", "$_id._id.tp")
				.append(KEY_TOTAL, "$" + KEY_TOTAL)
				.append("_id",0);
		collection.aggregate(
				Arrays.asList(
						Aggregates.match(and(
								gte("t", startTime),
								lt("t", endTime))),
						Aggregates.group(groupObject,  Accumulators.sum(KEY_TOTAL, "$a")),
						Aggregates.project(projectObject)
				)
		).forEach((Block<? super Document>) documentList::add);
		return documentList;
	}
//Get today's player income and expenses（yty）
	public static List<Long> getTodayPlayerIncomeOrPay(long startTime, long endTime, MongoCollection<Document> collection,UUID pid){
        List<Long> incomeOrPay = new ArrayList<>();
        collection.find(
                and(
                    eq("p",pid),
                    gte("t", startTime),
                    lt("t", endTime))
        ).forEach((Block<? super Document>) d ->
        {

            incomeOrPay.add(d.getLong("a"));
        });
        return incomeOrPay;
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
	//Calculate 1 day of construction expenditure（yty）
	public static List<Document> buildingDayPaySummary(long yestodayStartTime, long todayStartTime)		//Count all expenditures of the player's construction day (without detailed information)
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

	/*Statistics of the operation details of all shelf buildings (including retail stores)(yty)*/
	public static Map<Integer,List<Document>> buildingDaySaleDetailIncomeSummary(long yestodayStartTime, long todayStartTime){
		Map<Integer, List<Document>> map = new HashMap<>();
		List<Document> factoryInshelf = new ArrayList<>();//Factory category (or research institute, retail store) shelf revenue
		List<Document> retailInshelf = new ArrayList<>();//Retail store
		Document projectObject = new Document()
				.append("bid","$_id._id.b")
				.append("itemId","$_id._id.tpi")
				.append("brand","$brand")
				.append("p","$_id.i")
				.append("num","$n")
				.append(KEY_TOTAL, "$" + KEY_TOTAL)
				.append("_id", 0)
				.append("miner","$miner");
		//Grouping id (grouped by producer id, building id, commodity id)
		Document groupObject = new Document("_id",
				new Document("b", "$b")
						.append("tpi", "$tpi"))
						.append("i","$i");
		buyInShelf.aggregate(
				Arrays.asList(
					Aggregates.match(and(
							gte("t", yestodayStartTime),
							lt("t", todayStartTime))),
					Aggregates.sort(Sorts.descending("t")),
					Aggregates.group(groupObject,Accumulators.first("brand","$brand"),Accumulators.sum("n","$n"),Accumulators.sum(KEY_TOTAL, "$a"),Accumulators.sum("miner", "$miner")),
					Aggregates.project(projectObject)
				)
		).forEach((Block<? super Document>) factoryInshelf::add);

		//Recalculate the daily operating details of retail store buildings
		npcBuyInShelf.aggregate(
				Arrays.asList(
						Aggregates.match(and(
								gte("t", yestodayStartTime),
								lt("t", todayStartTime))),
						Aggregates.sort(Sorts.descending("t")),
						Aggregates.group(groupObject,Accumulators.first("brand","$brand"), Accumulators.sum(KEY_TOTAL, "$a"),Accumulators.sum("n", "$n"),Accumulators.sum("miner", "$miner")),
						Aggregates.project(projectObject)
				)
		).forEach((Block<? super Document>) retailInshelf::add);
		map.put(1,factoryInshelf);
		map.put(2,retailInshelf);
		return map;
	}

	/*Statistics on the operation details of the building today)(yty)*/
	public static List<Document> buildingDaySaleDetailByBuilding(long startTime,long endTime,UUID bid,MongoCollection<Document> collection){
		List<Document> record = new ArrayList<>();
		Document projectObject = new Document()
				.append("bid","$_id._id.b")
				.append("itemId","$_id._id.tpi")
				.append("brand","$brand")
				.append("p","$_id.i")
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
								eq("b",bid),
								gte("t", startTime),
								lt("t", endTime))),
						Aggregates.sort(Sorts.descending("t")),
						Aggregates.group(groupObject,Accumulators.first("brand","$brand"),Accumulators.sum(KEY_TOTAL, "$a"),Accumulators.sum("n", "$n")),
						Aggregates.project(projectObject)
				)
		).forEach((Block<? super Document>)record::add);
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
								  UUID producerId, UUID bid, UUID wid,int type,
								  int typeId,String brand,double score,int buildingType,long minerCost)//ytyJoin absenteeism fee
	{
		Document document = new Document("t", System.currentTimeMillis());
		document.append("r", buyId)
				.append("d", sellId)
				.append("b", bid)
				.append("w", wid)
				.append("p", price)
				.append("n",n)				//yty  Quantity
				.append("brand",brand)      //yty Brand name
				.append("a", n * price-minerCost)
				.append("i", producerId)
				.append("tp", type)
				.append("tpi", typeId)
				.append("score", score)
				.append("bt",buildingType)
				.append("miner",minerCost);
		buyInShelf.insertOne(document);
	}

	public static void  npcBuyInShelf(UUID npcId, UUID sellId, long n, long price,
								  UUID producerId, UUID bid,int type, int typeId,
							      String brand,double score,double gbrd,double gqty,double rbrd,double rqty,long minerCost)//ytyJoin absenteeism fee
	{
		Document document = new Document("t", System.currentTimeMillis());
		document.append("r", npcId)
				.append("d", sellId)
				.append("b", bid)
				.append("p", price)
				.append("n",n)          //yty  Quantity
				.append("brand",brand)  //yty Brand name
				.append("a", n * price)
				.append("i", producerId)
				.append("tp", type)
				.append("tpi", typeId)
				.append("score", score)
				.append("gbrd", gbrd) //Product awareness
				.append("gqty", gqty) //Product quality
				.append("rbrd", rbrd) //Retail store visibility
				.append("rqty", rqty)//Retail store quality
				.append("miner",minerCost);//Miner's fee
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

	public static void buyGround(UUID roleId, UUID ownerId, long price, List<Positon> plist1,Long minerCost)
	{
		long all = price;
		if (ownerId != null) {
			all = price * plist1.size();
		}
		Document document = new Document("t", System.currentTimeMillis());
		document.append("r", roleId)
				.append("d", ownerId)
				.append("s", price)
				.append("n", plist1.size())  // Number of plots
				.append("a", all)
				.append("p", positionToDoc(plist1))
				.append("miner",minerCost);		//yty Miner's fee
		buyGround.insertOne(document);
	}
	public static void landAuction(UUID roleId, UUID ownerId, long price, List<Positon> plist1)
	{
		Document document = new Document("t", System.currentTimeMillis());
		document.append("r", roleId)
				.append("d", ownerId)
				.append("n", plist1.size())  // ly
				.append("s", price/plist1.size()) // ly unit price
				.append("a", price)
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
			UUID ownerId, UUID bid, int type, int mId,int score,int prosp,double brd,double qty)
	{
		Document document = new Document("t", System.currentTimeMillis());
		document.append("r", npcId)
				.append("d", sellId)
				.append("p", price)
				.append("a", n * price)
				.append("o", ownerId)
				.append("b", bid)
				.append("tp", type)
				.append("mid", mId)
				.append("score", score)
				.append("prosp", prosp)
				.append("abrd", brd)
				.append("aqty", qty);
		npcRentApartment.insertOne(document);
	}

	public static void  cityBroadcast(UUID sellerId, UUID buyerId, long cost, int num, int type)
	{
		//Do not delete the previous reminder for major transactions
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
	//Record promotion transaction records
	public static void promotionRecord(UUID sellerId, UUID buyerId,UUID bid,int price,long cost, int typeId,int categoryType,boolean isBuilding) {
		int type = TYPE_BUILDING;
		if (!isBuilding) {
			type = TYPE_GOODS;
		}
		Document document = new Document("t", System.currentTimeMillis());
		document.append("s", sellerId);
		document.append("b", buyerId);
		document.append("p", price); //Price per millisecond
		document.append("a", cost);
		document.append("bid", bid);
		document.append("tp", type);
		document.append("ct", categoryType);
		document.append("tpi", typeId);
		promotionRecord.insertOne(document);
	}

	//Record Institute Transaction Records
	public static void laboratoryRecord(UUID sellerId, UUID buyerId,UUID bid,int price,long cost, int typeId,boolean isInvent) {
/*		int type = TYPE_INVENT;
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
		laboratoryRecord.insertOne(document);*/
	}


	//Lease warehouse records: lease time, end time, rent, renter id, order number, lease capacity and other data
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
	public static void playerIncome(UUID playerId,long cost,int buildType){
		Document document = new Document("t", System.currentTimeMillis());
		document.append("p", playerId)
				.append("a", cost)
		        .append("tp",buildType);
		playerIncome.insertOne(document);
	}
	public static void playerPay(UUID playerId,long cost,int buildType){
		Document document = new Document("t", System.currentTimeMillis());
		document.append("p", playerId)
				.append("a", cost)
				.append("tp",buildType);
		playerPay.insertOne(document);
	}
	//Purchase tenant shelf product record
	public static void buyRenterInShelf(UUID buyId, UUID sellId, long n, long price,
										UUID producerId, Long orderId, int type, int typeId){
		Document document = new Document("t", System.currentTimeMillis());
		document.append("r", buyId)
				.append("d", sellId)
				.append("or", orderId)//Order number
				.append("p", price)
				.append("a", n * price)
				.append("i", producerId)
				.append("tp", type)
				.append("tpi", typeId);
		buyRenterInShelf.insertOne(document);
	}
	//Record tenant warehouse income
	public static void renterShelfIncome(Long orderId,UUID payId,long cost,int type,int typeId){ //Revenue from renting warehouse shelves
		Document document = new Document("t", System.currentTimeMillis());
		document.append("or", orderId)
				.append("p", payId)
				.append("a", cost)
				.append("tp", type)
				.append("tpi", typeId);
		renterShelfIncome.insertOne(document);
	}

	//Transportation between rented warehouses
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
				.append("ratio", ratio);//Proportion of miner fees charged
		minersCost.insertOne(document);
	}

	public static void npcMinersCost(UUID npcId,double money,double  ratio){
		Document document = new Document("t", System.currentTimeMillis());
		document.append("pid", npcId)//npcId
				.append("a", money)  //Miner's fee
				.append("ratio", ratio);//Proportion of miner fees charged
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
	public static void  playerBuildingBusiness(UUID playerId,long n,long staffNum,int type)
	{
		Document document = new Document("t", System.currentTimeMillis());
		document.append("p", playerId)
				.append("n", n)
				.append("sn", staffNum)
				.append("tp", type);
		playerBuildingBusiness.insertOne(document);
	}

	/*Player login duration record (arg1: player id agr2: login duration arg3: recorded login time)*/
	public static void playerLoginTime(UUID playerId,Long loginTime,Long recordTime){
		Document document = new Document("t",System.currentTimeMillis());
		document.append("p", playerId)
				.append("lgt", loginTime)
				.append("rt", recordTime)
				.append("date",new Date());
		playerLoginTime.insertOne(document);
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
		//Get the amount of construction expenditure today
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

	/*Used for offline notifications, shelf revenue*/
	public static MongoCollection<Document> getSellerBuildingIncome() {
		return sellerBuildingIncome;
	}

	public static MongoCollection<Document> getPlayerBuildingBusiness() {
		return playerBuildingBusiness;
	}

	public static MongoCollection<Document> getDayPlayerIncome()
	{
		return dayPlayerIncome;
	}

	public static MongoCollection<Document> getFlightBet() {
		return flightBet;
	}

	public static MongoCollection<Document> getPlayerLoginTime() {
		return playerLoginTime;
	}
	public static MongoCollection<Document> getCityMoneyPool() {
		return cityMoneyPool;
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
								eq("bt",PROMOTE),
								gte("t", startTime),
								lt("t", endTime)
						)),
						Aggregates.group("$tpi", Accumulators.sum(KEY_TOTAL, "$a"), Accumulators.sum("size", 1l)),
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
								eq("bt", TECHNOLOGY),
								gte("t", startTime),
								lt("t", endTime)
						)),
						Aggregates.group("$tpi", Accumulators.sum(KEY_TOTAL, "$a"), Accumulators.sum("size", 1l)),
						Aggregates.project(projectObject)
				)
		).forEach((Block<? super Document>) documentList::add);
        return documentList;
    }
	public static List<Document> dayPlayerIncome(long todayStartTime,int buildType,MongoCollection<Document> collection)
	{
		List<Document> documentList = new ArrayList<>();
		Document projectObject = new Document()
				.append("id", "$_id")
				.append(KEY_TOTAL, "$" + KEY_TOTAL)
				.append("_id", 0);
		collection.aggregate(
				Arrays.asList(
						Aggregates.match(and(
								eq("bt",buildType),
								lt("t", todayStartTime))),
						Aggregates.group("$id", Accumulators.sum(KEY_TOTAL, "$total")),
						Aggregates.sort(Sorts.descending(KEY_TOTAL)),
						Aggregates.limit(10),
						Aggregates.project(projectObject)
				)
		).forEach((Block<? super Document>) documentList::add);
		return documentList;
	}
	public static List<Document>  dayYesterdayPlayerIncome(long strartTime,long endTime,int buildType,MongoCollection<Document> collection)
	{
		List<Document> documentList = new ArrayList<>();
		Document projectObject = new Document()
				.append("id", "$_id")
				.append(KEY_TOTAL, "$" + KEY_TOTAL)
				.append("_id", 0);
		collection.aggregate(
				Arrays.asList(
						Aggregates.match(and(
								eq("tp", buildType),
								gte("time", strartTime),
								lte("time", endTime)
						)),
						Aggregates.group("$id", Accumulators.sum(KEY_TOTAL, "$total")),
						Aggregates.sort(Sorts.descending(KEY_TOTAL)),
						Aggregates.limit(10),
						Aggregates.project(projectObject)
				)
		).forEach((Block<? super Document>) documentList::add);
		return documentList;
	}
	public static List<Document>  dayYesterdayPlayerByGroundIncome(long strartTime,long endTime,MongoCollection<Document> collection)
	{
		List<Document> documentList = new ArrayList<>();
		Document projectObject = new Document()
				.append("id", "$_id")
				.append(KEY_TOTAL, "$" + KEY_TOTAL)
				.append("_id", 0);
		collection.aggregate(
				Arrays.asList(
						Aggregates.match(and(
								gte("time", strartTime),
								lte("time", endTime)
						)),
						Aggregates.group("$d", Accumulators.sum(KEY_TOTAL, "$a")),
						Aggregates.sort(Sorts.descending(KEY_TOTAL)),
						Aggregates.limit(10),
						Aggregates.project(projectObject)
				)
		).forEach((Block<? super Document>) documentList::add);
		return documentList;
	}
	public static List<Document>  dayYesterdayProductIncome(long strartTime,long endTime,int itemId,int buildType,MongoCollection<Document> collection)
	{
		List<Document> documentList = new ArrayList<>();
		Document projectObject = new Document()
				.append("id", "$_id")
				.append(KEY_TOTAL, "$" + KEY_TOTAL)
				.append("_id", 0);
		collection.aggregate(
				Arrays.asList(
						Aggregates.match(and(
								eq("bt", buildType),
								eq("tpi", itemId),
								gte("t", strartTime),
								lte("t", endTime)
						)),
						Aggregates.group("$id", Accumulators.sum(KEY_TOTAL, "$total")),
						Aggregates.sort(Sorts.descending(KEY_TOTAL)),
						Aggregates.limit(10),
						Aggregates.project(projectObject)
				)
		).forEach((Block<? super Document>) documentList::add);
		return documentList;
	}
	public static List<Document>  dayYesterdayRetailProductIncome(long strartTime,long endTime,int itemId,MongoCollection<Document> collection)
	{
		List<Document> documentList = new ArrayList<>();
		Document projectObject = new Document()
				.append("id", "$_id")
				.append(KEY_TOTAL, "$" + KEY_TOTAL)
				.append("_id", 0);
		collection.aggregate(
				Arrays.asList(
						Aggregates.match(and(
								eq("tpi", itemId),
								gte("t", strartTime),
								lte("t", endTime)
						)),
						Aggregates.group("$id", Accumulators.sum(KEY_TOTAL, "$total")),
						Aggregates.sort(Sorts.descending(KEY_TOTAL)),
						Aggregates.limit(10),
						Aggregates.project(projectObject)
				)
		).forEach((Block<? super Document>) documentList::add);
		return documentList;
	}
	public static List<Document>  dayYesterdayPlayerIncome(long strartTime,long endTime,MongoCollection<Document> collection)
	{
		List<Document> documentList = new ArrayList<>();
		Document projectObject = new Document()
				.append("id", "$_id")
				.append(KEY_TOTAL, "$" + KEY_TOTAL)
				.append("_id", 0);
		collection.aggregate(
				Arrays.asList(
						Aggregates.match(and(
								ne("id",null),
								gte("time", strartTime),
								lte("time", endTime)
						)),
						Aggregates.group("$id", Accumulators.sum(KEY_TOTAL, "$total")),
						Aggregates.sort(Sorts.descending("total")),
						Aggregates.limit(10),
						Aggregates.project(projectObject)
				)
		).forEach((Block<? super Document>) documentList::add);
		return documentList;
	}

	public static long queryMyself(long strartTime, long endTime, UUID pid, int buildType, MongoCollection<Document> collection) {
		List<Document> documentList = new ArrayList<>();
		collection.find(and(eq("id", pid), eq("tp", buildType), gte("time", strartTime), lte("time", endTime))).forEach((Block<? super Document>) documentList::add);
		final long[] income = {0};
		documentList.stream().filter(o -> o != null).forEach(d -> {
			income[0] += d.getLong(KEY_TOTAL);
		});
		return income[0];
	}
	public static long queryMyself(long strartTime, long endTime, UUID pid, int buildType,int itemId, MongoCollection<Document> collection) {
		List<Document> documentList = new ArrayList<>();
		collection.find(and(eq("d", pid), eq("bt", buildType),eq("tpi",itemId), gte("t", strartTime), lte("t", endTime))).forEach((Block<? super Document>) documentList::add);
		final long[] income = {0};
		documentList.stream().filter(o -> o != null).forEach(d -> {
			income[0] += d.getLong(KEY_TOTAL);
		});
		return income[0];
	}
	public static long queryMyselfRetail(long strartTime, long endTime, UUID pid,int itemId, MongoCollection<Document> collection) {
		List<Document> documentList = new ArrayList<>();
		collection.find(and(eq("d", pid),eq("tpi",itemId), gte("t", strartTime), lte("t", endTime))).forEach((Block<? super Document>) documentList::add);
		final long[] income = {0};
		documentList.stream().filter(o -> o != null).forEach(d -> {
			income[0] += d.getLong(KEY_TOTAL);
		});
		return income[0];
	}
	public static long queryMyself(long strartTime, long endTime, UUID pid, MongoCollection<Document> collection) {
		List<Document> documentList = new ArrayList<>();
		collection.find(and(eq("id", pid), gte("t", strartTime), lte("t", endTime))).forEach((Block<? super Document>) documentList::add);
		final long[] income = {0};
		documentList.stream().filter(o -> o != null).forEach(d -> {
			income[0] += d.getLong(KEY_TOTAL);
		});
		return income[0];
	}

	public static int groundSum(long strartTime,long endTime,UUID pid)
	{
		final int[] count = {0};
		buyGround.aggregate(
				Arrays.asList(
						Aggregates.match(and(eq("d", pid),gte("t", strartTime), lte("t", endTime))),
						Aggregates.count()
				)
		).forEach((Block<? super Document>) d->{
			count[0] =d.getInteger("count");
		});
		return count[0];
	}
	public static Map<UUID,Long> todayPlayerIncome(long startTime, long endTime, MongoCollection<Document> collection,int buildType)
	{
		Map<UUID,Long> map=new HashMap<UUID,Long>();
		List<Document> documentList = new ArrayList<>();
		Document projectObject = new Document()
				.append("id", "$_id")
				.append(KEY_TOTAL, "$" + KEY_TOTAL)
				.append("_id",0);
		collection.aggregate(
				Arrays.asList(
						Aggregates.match(and(
								eq("tp",buildType),
								gte("t", startTime),
								lt("t", endTime))),
						Aggregates.group("$p",  Accumulators.sum(KEY_TOTAL, "$a")),
						Aggregates.project(projectObject)
				)
		).forEach((Block<? super Document>) document ->
		{
			map.put(document.get("p",UUID.class), document.getLong(KEY_TOTAL));
		});
		return map;
	}
    public static List<Document> playerBuildingBusiness(long startTime, long endTime, MongoCollection<Document> collection,int buildType)
    {
        List<Document> documentList = new ArrayList<>();
        Document groupObject = new Document("_id",
                new Document("p", "$p")
                        .append("tp", "$tp"));
        Document projectObject = new Document()
                .append("id", "$_id._id.p")
                .append("tp", "$_id._id.tp")
                .append("n", "$n")
                .append(KEY_TOTAL, "$" + KEY_TOTAL)
                .append("_id",0);
        if(buildType>0){
            collection.aggregate(
                    Arrays.asList(
                            Aggregates.match(and(
                                    eq("tp",buildType),
                                    gte("t", startTime),
                                    lt("t", endTime))),
                            Aggregates.group(groupObject,  Accumulators.sum(KEY_TOTAL, "$sn"),Accumulators.sum("n", 1l)),
                            Aggregates.project(projectObject)
                    )
            ).forEach((Block<? super Document>) documentList::add);
        }else{
            collection.aggregate(
                    Arrays.asList(
                            Aggregates.match(and(
                                    gte("t", startTime),
                                    lt("t", endTime))),
                            Aggregates.group(groupObject,  Accumulators.sum(KEY_TOTAL, "$sn"),Accumulators.sum("n", 1l)),
                            Aggregates.project(projectObject)
                    )
            ).forEach((Block<? super Document>) documentList::add);
        }
        return documentList;
    }



	public static class HistoryRecord {
		public double price;  // Average price of houses in the city
		public double score;  // All residential properties in the city
		public double prosp;  // Prosperity of the whole city
		public Map<Integer, Double> material;//raw material
		public Map<Integer, Map<String, Double>> produce; //Processing plant
		public Map<Integer, Map<String, Double>> retail;  //Retail store
		public Map<Integer, Double> laboratory;//graduate School
		public Map<Integer, Double> promotion; //Data company
		public double groundPrice; // Average land transaction price

		public HistoryRecord() {
		}
	}

	public static LogDb.HistoryRecord getApartmentRecord(long startTime, long endTime) {
		List<Document> documentList = new ArrayList<>();
		LogDb.HistoryRecord history = new LogDb.HistoryRecord();
		npcRentApartment.aggregate(
				Arrays.asList(
						Aggregates.match(and(gte("t", startTime), lte("t", endTime))),
						Aggregates.group(null,Accumulators.avg("avg","$a"),Accumulators.avg("score","$score"),Accumulators.avg("prosp","$prosp"))
				)
		).forEach((Block<? super Document>) documentList::add);
		documentList.stream().filter(o->o!=null).forEach(d->{
			history.price = d.getDouble("avg");
			history.score = d.getDouble("score")==null?1:d.getDouble("score");
			history.prosp = d.getDouble("prosp")==null?1:d.getDouble("prosp");
		});
		return history;
	}


	public static Map<Integer, Double> getMaterialsRecord(long startTime, long endTime) {
		List<Document> documentList = new ArrayList<>();
		Map<Integer, Double> map = new HashMap<>();
		buyInShelf.aggregate(
				Arrays.asList(
						Aggregates.match(and(eq("tp",TP_TYPE_MATERIAL),gte("t", startTime), lte("t", endTime))),
						Aggregates.group("$tpi", Accumulators.avg("avg", "$a"))
				)
		).forEach((Block<? super Document>) documentList::add);
		documentList.stream().filter(o -> o != null).forEach(d -> {
			map.put(d.getInteger("_id"), d.getDouble("avg"));
		});
		return map;
	}
	public static double queryLandAuctionAvg() {
		List<Document> documentList = new ArrayList<>();
		AtomicDouble price = new AtomicDouble(0);
		landAuction.aggregate(
				Arrays.asList(
						Aggregates.group(null, Accumulators.avg(KEY_AVG, "$s"))
				)
		).forEach((Block<? super Document>) documentList::add);
		documentList.stream().filter(o -> o != null).forEach(d -> {
			price.set(d.getDouble(KEY_AVG));
		});
		return price.doubleValue();
	}

	public static Map<Integer, Map<String, Double>> getGoodsRecord(long startTime, long endTime) {
		List<Document> documentList = new ArrayList<>();
		Map<Integer, Map<String, Double>> map = new HashMap<>();
		npcBuyInShelf.aggregate(
				Arrays.asList(
						Aggregates.match(and(eq("tp",TP_TYPE_GOODS))),
						Aggregates.group("$tpi",Accumulators.avg("avg","$a"),Accumulators.avg("score","$score"))
				)
		).forEach((Block<? super Document>) documentList::add);
		documentList.stream().filter(o -> o != null).forEach(d -> {
			HashMap<String, Double> hashMap = new HashMap<>();
			hashMap.put("price", d.getDouble("avg"));
			hashMap.put("score", d.getDouble("score"));
			map.put(d.getInteger("_id"), hashMap);
		});
		return map;
	}

	public static Map<Integer, Map<String, Double>> getRetailRecord(long startTime, long endTime) {
		List<Document> documentList = new ArrayList<>();
		Map<Integer, Map<String, Double>> map = new HashMap<>();
		npcBuyInShelf.aggregate(
				Arrays.asList(
						Aggregates.match(and(gte("t", startTime), lte("t", endTime))),
						Aggregates.group("$tpi",Accumulators.avg("avg","$a"),Accumulators.avg("score","$score"))
				)
		).forEach((Block<? super Document>) documentList::add);
		documentList.stream().filter(o -> o != null).forEach(d -> {
			HashMap<String, Double> hashMap = new HashMap<>();
			hashMap.put("price", d.getDouble("avg"));
			hashMap.put("score", d.getDouble("score"));
			map.put(d.getInteger("_id"), hashMap);
		});
		return map;
	}

	public static Map<Integer, Double> getLabOrProRecord(long startTime, long endTime, boolean islab) {
		int bt = LogDb.TECHNOLOGY;
		Map<Integer, Double> map = new HashMap<>();
		if (!islab) {
			bt = LogDb.PROMOTE;
		}
		List<Document> documentList = new ArrayList<>();
		buyInShelf.aggregate(
				Arrays.asList(
						Aggregates.match(and(eq("bt", bt), gte("t", startTime), lte("t", endTime))),
						Aggregates.group("$tpi", Accumulators.avg("avg", "$a"))
				)
		).forEach((Block<? super Document>) documentList::add);
		documentList.stream().filter(o -> o != null).forEach(d -> {
			map.put(d.getInteger("_id"), d.getDouble("avg"));
		});
		return map;
	}

	public static double getGroundRecord(long startTime, long endTime) {
		final double[] avg = {0};
		List<Document> documentList = new ArrayList<>();
		buyGround.aggregate(
				Arrays.asList(
						Aggregates.match(and(gte("t", startTime), lte("t", endTime))),
						Aggregates.group(null, Accumulators.avg("avg", "$a"))
				)
		).forEach((Block<? super Document>) documentList::add);
		documentList.stream().filter(o -> o != null).forEach(d -> {
			avg[0] = d.getDouble("avg");
		});
		return avg[0];
	}
/*Count the length of time a player logs in*/
	public static List<Document> dayPlayerLoginTime(long startTime, long endTime, MongoCollection<Document> collection) {
		List<Document> documentList = new ArrayList<>();
		Document groupObject = new Document("_id",
				new Document("p", "$p"));
		Document projectObject = new Document()
				.append("id", "$_id._id.p")
				.append(KEY_TOTAL, "$" + KEY_TOTAL)
				.append("_id",0);
		collection.aggregate(
				Arrays.asList(
						Aggregates.match(and(
								gte("rt", startTime),
								lt("rt", endTime))),
						Aggregates.group(groupObject,  Accumulators.sum(KEY_TOTAL, "$lgt")),
						Aggregates.project(projectObject)
				)
		).forEach((Block<? super Document>) documentList::add);
		return documentList;
	}

	public static long queryIndestrySum(int buidingType,long startTime,long endTime) {
		final long[] count = {0};
		buyInShelf.aggregate(
				Arrays.asList(
						Aggregates.match(and(eq("bt", buidingType),gte("t", startTime), lte("t", endTime))),
						Aggregates.group(null,Accumulators.sum(KEY_TOTAL,"$n"))
				)
		).forEach((Block<? super Document>) d->{
			count[0] =d.getInteger(KEY_TOTAL);
		});
		return count[0];
	}
	public static List<Document> queryIndestrySum(long startTime,long endTime) {
		List<Document> list = new ArrayList<>();
		buyInShelf.aggregate(
				Arrays.asList(
						Aggregates.match(and(gte("t", startTime), lte("t", endTime))),
						Aggregates.group("$bt", Accumulators.sum(KEY_TOTAL, "$n"))
				)
		).forEach((Block<? super Document>) list::add);
		return list;
	}
	public static long queryIndestrySum(int buidingType,long startTime,long endTime,int itemId) {
		final long[] count = {0};
		buyInShelf.aggregate(
				Arrays.asList(
						Aggregates.match(and(eq("bt", buidingType),eq("tpi",itemId),gte("t", startTime), lte("t", endTime))),
						Aggregates.group(null,Accumulators.sum(KEY_TOTAL,"$n"))
				)
		).forEach((Block<? super Document>) d->{
			count[0] =d.getInteger(KEY_TOTAL);
		});
		return count[0];
	}
	public static long queryRetailSum(long startTime,long endTime,int itemId) {
		final long[] count = {0};
		npcBuyInShelf.aggregate(
				Arrays.asList(
						Aggregates.match(and(eq("tpi",itemId),gte("t", startTime), lte("t", endTime))),
						Aggregates.group(null,Accumulators.sum(KEY_TOTAL,"$n"))
				)
		).forEach((Block<? super Document>) d->{
			count[0] =d.getInteger(KEY_TOTAL);
		});
		return count[0];
	}

	public static long queryIndestrySum(long startTime, long endTime,MongoCollection<Document> collection) {
		final long[] count = {0};
		collection.aggregate(
				Arrays.asList(
						Aggregates.match(and(gte("t", startTime), lte("t", endTime))),
						Aggregates.group(null, Accumulators.sum(KEY_TOTAL, "$n"))
				)
		).forEach((Block<? super Document>) d -> {
			count[0] = d.getInteger(KEY_TOTAL);
		});
		return count[0];

	}

	public static long queryIndestrySum(long startTime, long endTime, MongoCollection<Document> collection, int itemId) {
		final long[] count = {0};
		collection.aggregate(
				Arrays.asList(
						Aggregates.match(and(eq("tpi", itemId), gte("t", startTime), lte("t", endTime))),
						Aggregates.group(null, Accumulators.sum(KEY_TOTAL, "$n"))
				)
		).forEach((Block<? super Document>) d -> {
			count[0] = d.getInteger(KEY_TOTAL);
		});
		return count[0];

	}
	public static long queryApartmentIndestrySum(long startTime, long endTime,MongoCollection<Document> collection) {
		final long[] count = {0};
		collection.aggregate(
				Arrays.asList(
						Aggregates.match(and(gte("t", startTime), lte("t", endTime))),
						Aggregates.count()
				)
		).forEach((Block<? super Document>) d -> {
			count[0] = d.getInteger("count");
		});

		return count[0];

	}

	public static void insertIndustrySupplyAndDemand(List<Document> source) {
		if (!source.isEmpty()) {
			industrySupplyAndDemand.insertMany(source);
		}
	}
	public static void insertCityMoneyPool(long total,long time) {
			cityMoneyPool.insertOne(new Document().append(KEY_TOTAL,total).append("time",time));
	}

	public static List<Document> querySupplyAndDemand(int bt) {
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
		List<Document> documentList = new ArrayList<>();
		industrySupplyAndDemand.find(and(
				eq("bt", bt),
				eq("type", 1),
				gte("time", startTime),
				lt("time", endTime)
		))
				.projection(fields(include("time", "supply", "demand"), excludeId()))
				.sort(Sorts.descending("time"))
				.forEach((Block<? super Document>) documentList::add);
		return documentList;
	}
	public static List<Document> querySupplyAndDemand(int type,int itemId) {
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
		List<Document> documentList = new ArrayList<>();
		industrySupplyAndDemand.find(and(
				eq("bt", type),
				eq("tpi",itemId),
				eq("type", 2),
				gte("time", startTime),
				lt("time", endTime)
		))
				.projection(fields(include("time", "supply", "demand"), excludeId()))
				.sort(Sorts.descending("time"))
				.forEach((Block<? super Document>) documentList::add);
		return documentList;
	}
	public static MongoCollection<Document> getDayIndustryIncome()
	{
		return dayIndustryIncome;
	}

	public static long  queryIndustrySumIncome(int buildingType) {
		final long[] count = {0};
		dayIndustryIncome.aggregate(
				Arrays.asList(
						Aggregates.match(and(eq("type", buildingType))),
						Aggregates.group(null, Accumulators.sum(KEY_TOTAL, "$total"))
				)
		).forEach((Block<? super Document>) d -> {
			count[0] = d.getLong(KEY_TOTAL);
		});

		return count[0];
	}

	public static List<Document> queryCityAllTransactionAmount(long startTime, long endTime, MongoCollection<Document> collection) {
		List<Document> documentList = new ArrayList<>();
		collection.aggregate(
				Arrays.asList(
						Aggregates.match(and(ne("p", null), gte("t", startTime), lte("t", endTime))),
						Aggregates.group(null, Accumulators.sum(KEY_TOTAL, "$a"))
				)
		).forEach((Block<? super Document>) documentList::add);
		return documentList;
	}
	//Retail store visibility and average quality
	public static List<Document> getNpcBuyInShelfAvg1(long startTime, long endTime) {
		List<Document> documentList = new ArrayList<>();
		npcBuyInShelf.aggregate(
				Arrays.asList(
						Aggregates.match(and(gte("t", startTime), lt("t", endTime))),
						Aggregates.group(null,Accumulators.avg("brand","$rbrd"),Accumulators.avg("quality","$rqty"))
				)
		).forEach((Block<? super Document>) documentList::add);
		return documentList;
	}
	//Product awareness and average quality
	public static List<Document> getNpcBuyInShelfAvg2(long startTime, long endTime) {
		List<Document> documentList = new ArrayList<>();
		npcBuyInShelf.aggregate(
				Arrays.asList(
						Aggregates.match(and(gte("t", startTime), lt("t", endTime))),
						Aggregates.group("$tpi",Accumulators.avg("brand","$gbrd"),Accumulators.avg("quality","$gqty"))
				)
		).forEach((Block<? super Document>) documentList::add);
		return documentList;
	}
//	//Merchandise category popularity and average quality
//	public static List<Document> getNpcBuyInShelfAvg3(long startTime, long endTime) {
//		List<Document> documentList = new ArrayList<>();
//		npcBuyInShelf.aggregate(
//				Arrays.asList(
//						Aggregates.match(and(gte("t", startTime), lt("t", endTime))),
//						Aggregates.group("$tpi/1000",Accumulators.avg("gbrd","$gbrd"),Accumulators.avg("gqty","$gqty"))
//				)
//		).forEach((Block<? super Document>) documentList::add);
//		return documentList;
//	}
//	//Product luxury awareness and average quality
//	public static List<Document> getNpcBuyInShelfAvg4(long startTime, long endTime) {
//		List<Document> documentList = new ArrayList<>();
//		npcBuyInShelf.aggregate(
//				Arrays.asList(
//						Aggregates.match(and(gte("t", startTime), lt("t", endTime))),
//						Aggregates.group("$tpi/100%10",Accumulators.avg("gbrd","$gbrd"),Accumulators.avg("gqty","$gqty"))
//				)
//		).forEach((Block<? super Document>) documentList::add);
//		return documentList;
//	}
	//Residential popularity and average quality
	public static List<Document> getNpcRentApartmentAvg1(long startTime, long endTime) {
		List<Document> documentList = new ArrayList<>();
		npcRentApartment.aggregate(
				Arrays.asList(
						Aggregates.match(and(gte("t", startTime), lt("t", endTime))),
						Aggregates.group(null,Accumulators.avg("brand","$abrd"),Accumulators.avg("quality","$aqty"))
				)
		).forEach((Block<? super Document>) documentList::add);
		return documentList;
	}
	public static List<Document> queryCityTransactionAmount(long startTime, long endTime, MongoCollection<Document> collection,int buildingType) {
		List<Document> documentList = new ArrayList<>();
		collection.aggregate(
				Arrays.asList(
						Aggregates.match(and(gte("t", startTime), lte("t", endTime),eq("bt",buildingType))),
						Aggregates.group("$tpi", Accumulators.sum(KEY_TOTAL, "$a"))
				)
		).forEach((Block<? super Document>) documentList::add);
		return documentList;
	}
	public static List<Document> getDayAiBaseAvg(long time) {
		List<Document> documentList = new ArrayList<>();
		dayAiBaseAvg.aggregate(
				Arrays.asList(
						Aggregates.match(and(eq("time", time))),
						Aggregates.project(fields(include("time","type","brand","quality"), excludeId()))
				)
		).forEach((Block<? super Document>) documentList::add);
		return documentList;
	}
	public static List<Document> queryCityTransactionAmount(long startTime, long endTime, MongoCollection<Document> collection) {
		List<Document> documentList = new ArrayList<>();
		collection.aggregate(
				Arrays.asList(
						Aggregates.match(and(gte("t", startTime), lte("t", endTime))),
						Aggregates.group("$tpi", Accumulators.sum(KEY_TOTAL, "$a"))
				)
		).forEach((Block<? super Document>) documentList::add);
		return documentList;
	}

	public static void createIndex(){
		/*Determine if there is an index*/
		boolean hasIndex = false;
		ListIndexesIterable<Document> documents = playerLoginTime.listIndexes();
		for (Document doc : documents) {
			String indexName = doc.getString("name");
			if (indexName.startsWith("date")) {
				System.err.println("The index already exists");
				hasIndex = true;
			}
		}
		if(!hasIndex) {
			playerLoginTime.createIndex(new Document("date", 1), new IndexOptions().expireAfter(1l, TimeUnit.DAYS));
		}
	}
}
