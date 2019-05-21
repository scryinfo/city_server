package Shared;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.gte;
import static com.mongodb.client.model.Filters.lt;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.*;

import com.mongodb.client.model.Sorts;
import org.bson.Document;

import com.mongodb.Block;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.BsonField;

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
	private static final int TP_TYPE_NPC = 8;
	private static final int TP_TYPE_PLAYER = 9;

	private static final String PAY_SALARY = "paySalary";
	private static final String PAY_TRANSFER = "payTransfer";

	private static final String BUY_INSHELF = "buyInShelf";
	private static final String NPC_BUY_INSHELF = "npcBuyInShelf";
	private static final String RENT_GROUND = "rentGround";
	private static final String BUY_GROUND = "buyGround";

	private static final String EXTEND_BAG = "extendBag";

	private static final String INCOME_VISIT = "incomeVisit";

	private static final String PLAYER_INFO = "playerInfo";
	private static final String BUILDING_INCOME = "buildingIncome";
	
	private static final String NPC_RENT_APARTMENT = "npcRentApartment";
	private static final String CITY_BROADCAST = "cityBroadcast";
	private static final String NPC_TYPE_NUM = "npcTypeNum";

	private static final String FLOW_AND_LIFT = "flowAndLift";
	private static final String PROMOTION_RECORD = "promotionRecord";
	private static final String LABORATORY_RECORD = "laboratoryRecord";
	private static final String NPCBUY_INRETAILCOL = "npcBuyInRetailCol";


	//ly
	public static final String HOUR_BRAND_AMOUNT = "hourBrandAmount";
	public static final String HOUR_MATERIAL = "hourMaterial";
	public static final String HOUR_GOODS = "hourGoods";

	//集散中心租用仓库的收入记录
	private static final String RENT_WAREHOUSE_INCOME = "rentWarehouseIncome";

	private static final String PLAYER_INCOME = "playerIncome";
	private static final String PLAYER_PAY = "playerPay";
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
	private static MongoCollection<Document> extendBag;
	//-----------------------------------------

	//player buy material or goods  and npc buy goods
	private static MongoCollection<Document> incomeVisit;

	private static MongoCollection<Document> playerInfo;
	private static MongoCollection<Document> buildingIncome;

	//npc rent apartment
	private static MongoCollection<Document> npcRentApartment;
	private static MongoCollection<Document> cityBroadcast;
	private static MongoCollection<Document> npcTypeNum;
	//player rent warehouse
	private static MongoCollection<Document> rentWarehouseIncome;
	private static MongoCollection<Document> playerIncome;
	private static MongoCollection<Document> playerPay;
	//promotion
	private static MongoCollection<Document> promotionRecord;
	//laboratory
	private static MongoCollection<Document> laboratoryRecord;
	//住宅
	private static MongoCollection<Document> hourBrandAmount;
	//零售店
	private static MongoCollection<Document> hourRetailShop;
	//原料
	private static MongoCollection<Document> hourMaterial;
	private static MongoCollection<Document> hourGoods;

	public static final String KEY_TOTAL = "total";

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
		extendBag = database.getCollection(EXTEND_BAG)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);


		incomeVisit = database.getCollection(INCOME_VISIT)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		playerInfo = database.getCollection(PLAYER_INFO)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		buildingIncome = database.getCollection(BUILDING_INCOME)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		
		npcRentApartment = database.getCollection(NPC_RENT_APARTMENT)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		cityBroadcast = database.getCollection(CITY_BROADCAST)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		npcTypeNum = database.getCollection(NPC_TYPE_NUM)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		flowAndLift = database.getCollection(FLOW_AND_LIFT)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		hourBrandAmount = database.getCollection(HOUR_BRAND_AMOUNT)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		hourMaterial = database.getCollection(HOUR_MATERIAL)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		hourGoods = database.getCollection(HOUR_GOODS)
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
	}

	public static MongoDatabase getDatabase()
	{
		return database;
	}

	public static void startUp(){}

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

	public static void insertPlayerInfo(UUID uuid,boolean isMale)
	{
		playerInfo.insertOne(new Document("r", uuid).append("male",isMale));
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
								  UUID producerId, UUID bid, int type, int typeId,double brand,double quality)
	{
		Document document = new Document("t", System.currentTimeMillis());
		document.append("r", buyId)
				.append("d", sellId)
				.append("b", bid)
				.append("p", price)
				.append("a", n * price)
				.append("i", producerId)
				.append("tp", type)
				.append("tpi", typeId)
				.append("tb", brand)
				.append("tq", quality);
		buyInShelf.insertOne(document);
	}

	public static void  npcBuyInShelf(UUID npcId, UUID sellId, long n, long price,
								  UUID producerId, UUID bid, int type, int typeId)
	{
		Document document = new Document("t", System.currentTimeMillis());
		document.append("r", npcId)
				.append("d", sellId)
				.append("b", bid)
				.append("p", price)
				.append("a", n * price)
				.append("i", producerId)
				.append("tp", type)
				.append("tpi", typeId);
		npcBuyInShelf.insertOne(document);
	}

	public static void npcBuyInRetailCol(int itemId, int price, UUID producerId, int itemQty,int itemBrand,UUID buildingOwnerId, int buildingQty,int buildingBrand,float distance) {
		Document document = new Document("t", System.currentTimeMillis());
		document.put("imId", itemId);
		document.put("p", price);
		document.put("iId", producerId);
		document.put("iQ", itemQty);
		document.put("iB", itemBrand);
		document.put("bId", buildingOwnerId);
		document.put("bQ", buildingQty);
		document.put("bB", buildingBrand);
		document.put("d", distance);
		npcBuyInRetailCol.insertOne(document);
	}

	public static void paySalary(UUID roleId, UUID buildingId, long salary, long workers) {
		Document document = new Document("t", System.currentTimeMillis());
		document.append("r", roleId)
				.append("b", buildingId)
				.append("s", salary)
				.append("a", salary * workers)
				.append("w", workers);
		paySalary.insertOne(document);
	}

	public static void payTransfer(UUID roleId, long charge, UUID srcId, UUID dstId, UUID producerId, int n)
	{
		Document document = new Document("t", System.currentTimeMillis());
		document.append("r", roleId)
				.append("s", srcId)
				.append("d", dstId)
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
			UUID ownerId, UUID bid, int type, int mId,double brand,double quality)
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
                .append("brand", brand)
                .append("quality", quality);
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
	public static void promotionRecord(UUID sellerId, UUID buyerId,UUID bid,int price,long cost, int typeId,int categoryType,float ability,boolean isBuilding) {
		int type = TYPE_BUILDING;
		if (!isBuilding) {
			type = TYPE_GOODS;
		}
		Document document = new Document("t", System.currentTimeMillis());
		document.append("s", sellerId);
		document.append("b", buyerId);
		document.append("p", price); //每毫秒价格
		document.append("c", cost);
		document.append("bid", bid);
		document.append("tp", type);
		document.append("ct", categoryType);
		document.append("ab", ability);
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
		document.append("c", cost);
		document.append("p", price);
		document.append("bid", bid);
		document.append("tpi", typeId);
		document.append("tp", type);
		laboratoryRecord.insertOne(document);
	}


	//租用仓库记录：租用开始时间、结束时间、租用时长、租金、租用者、集散中心建筑id、订单编号、租用大小等数据
	public static void rentWarehouseIncome(Long orderId,UUID bid,UUID renterId,Long startTime,Long endTime,int hourToRent,int rent,int rentCapacity){
		Document document = new Document("t", System.currentTimeMillis());
		document.append("o", orderId)
				.append("b", bid)
				.append("r", renterId)
				.append("s", startTime)
				.append("e", endTime)
				.append("h", hourToRent)
				.append("rent", rent)
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

	public static MongoCollection<Document> getBuildingIncome()
	{
		return buildingIncome;
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
	public static List<Document> dayHourMaterial(long startTime, long endTime, MongoCollection<Document> collection)
	{
		List<Document> documentList = new ArrayList<>();
		Document projectObject = new Document()
				.append("id", "$_id")
				.append(KEY_TOTAL, "$" + KEY_TOTAL)
				.append("p", "$p" )
				.append("size", "$size" )
				.append("_id",0);

		collection.aggregate(
				Arrays.asList(
						Aggregates.match(and(
								eq("tp", TP_TYPE_MATERIAL),
								gte("t", startTime),
								lt("t", endTime))),
						Aggregates.group("$tpi", Accumulators.sum(KEY_TOTAL, "$a"),Accumulators.sum("p", "$p"), Accumulators.sum("size", 1l)),
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
						Aggregates.group("$ct",Accumulators.sum(KEY_TOTAL, "$c"),Accumulators.sum("size", 1l)),
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
						Aggregates.group("$tp",Accumulators.sum(KEY_TOTAL, "$c"),Accumulators.sum("size", 1l)),
						Aggregates.project(projectObject)
				)
		).forEach((Block<? super Document>) documentList::add);
		return documentList;
	}

	//查询一小时住宅成交的知名度品牌定价总和
    public static List<Document> queryApartmentBrandAndQuality(long startTime, long endTime, MongoCollection<Document> collection) {
        List<Document> documentList = new ArrayList<>();
        Document projectObject = new Document()
                .append("id", "$_id")
                .append("total", "$" + "total")
                .append("p","$p")
                .append("brand","$brand")
                .append("quality","$quality")
                .append("size","$size")
                .append("_id",0);
        collection.aggregate(
                Arrays.asList(
                        Aggregates.match(and(
                                gte("t", startTime),
                                lt("t", endTime)
                        )),
                        Aggregates.group("$tp", Accumulators.sum("total", "$a"),
                                Accumulators.sum("p", "$p"),
                                Accumulators.sum("brand", "$brand"),
                                Accumulators.sum("quality", "$quality"),
                                Accumulators.sum("size", 1l)
                        )
                        , Aggregates.project(projectObject)
                )
        ).forEach((Block<? super Document>) documentList::add);

        return documentList;
    }

	//查询推广信息
	public static List<Document> queryPromotionAbility(long startTime, long endTime, MongoCollection<Document> collection) {
		List<Document> documentList = new ArrayList<>();
		Document projectObject = new Document()
				.append("id", "$_id")
				.append("ab", "$ab")
				.append("total", "$total")
				.append("_id", 0);
		collection.aggregate(
				Arrays.asList(
						Aggregates.match(and(
								gte("t", startTime),
								lt("t", endTime)
						)),
						Aggregates.group("$tpi", Accumulators.sum("total", "$p"),
								Accumulators.sum("ab", "$ab"),
								Accumulators.sum("size", 1l)

						)
						, Aggregates.project(projectObject)
				)
		).forEach((Block<? super Document>) documentList::add);
		return documentList;
	}

	//查询一小时玩家交易商品的知名度品牌定价总和
	public static List<Document> queryGoodsBrandAndQuality(long startTime, long endTime, MongoCollection<Document> collection) {
		List<Document> documentList = new ArrayList<>();
		Document projectObject = new Document()
				.append("id", "$_id")
				.append("total", "$" + "total")
				.append("p","$p")
				.append("tb","$tb")
				.append("tq","$tq")
				.append("size","$size")
				.append("_id",0);
		collection.aggregate(
				Arrays.asList(
						Aggregates.match(and(
								eq("tp",TP_TYPE_GOODS),
								gte("t", startTime),
								lt("t", endTime)
						)),
						Aggregates.group("$tpi", Accumulators.sum("total", "$a"),
								Accumulators.sum("p", "$p"),
								Accumulators.sum("tb", "$tb"),
								Accumulators.sum("tq", "$tq"),
								Accumulators.sum("size", 1l)
						)
						, Aggregates.project(projectObject)
				)
		).forEach((Block<? super Document>) documentList::add);

		return documentList;
	}


	/*	document.put("imId", itemId);
		document.put("p", price);
		document.put("iId", producerId);
		document.put("iQ", itemQty);
		document.put("iB", itemBrand);
		document.put("bId", buildingOwnerId);
		document.put("bQ", buildingQty);
		document.put("bB", buildingBrand);
		document.put("d", distance);*/


	//查询一小时npc交易商品的品质知名度，零售店的品质知名度
	public static List<Document> queryNpcGoodsBrandAndQuality(long startTime, long endTime, MongoCollection<Document> collection) {
		List<Document> documentList = new ArrayList<>();
		Document projectObject = new Document()
				.append("id", "$_id")
				.append("total", "$" + "total")
				.append("iQ","$iQ")
				.append("iB","$iB")
				.append("bQ","$bQ")
				.append("bB","$bB")
				.append("size","$size")
				.append("_id",0);
		collection.aggregate(
				Arrays.asList(
						Aggregates.match(and(
								gte("t", startTime),
								lt("t", endTime)
						)),
						Aggregates.group("$imId", Accumulators.sum("total", "$p"),
								Accumulators.sum("iQ", "$iQ"),
								Accumulators.sum("iB", "$iB"),
								Accumulators.sum("bQ", "$bQ"),
								Accumulators.sum("bB", "$bB"),
								Accumulators.sum("size", 1l)
						)
						, Aggregates.project(projectObject)
				)
		).forEach((Block<? super Document>) documentList::add);

		return documentList;
	}

	public enum Categorytype {
		APARTMENT(1),MATERIAL(2),RETAILSHOP(3),PROMOTION(4), PRODUCEDEP(5), STORAGE(6);
		private int value;
		Categorytype(int i)
		{
			this.value = i;
		}

		public int getValue()
		{
			return value;
		}
	}
	public static List<Double> queryAvg(long id,Categorytype cty) {
		List<Double> list = new ArrayList<>();
		hourBrandAmount.find(and(
				eq("ct", cty.getValue()),
				eq("id", id)
		))
//                .projection(fields(include(TIME, KEY_TOTAL), excludeId()))
				.sort(Sorts.descending("time"))
				.limit(1)
				.forEach((Block<? super Document>) document ->
				{
					double price = new BigDecimal((float) document.getLong("p") / document.getLong("size")).setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
					double quality = document.getDouble("quality") / document.getLong("size");
					double brand = document.getDouble("brand") / document.getLong("size");
					list.add(quality);
					list.add(brand);
					list.add(price);
				});
		return list;
	}
	public static double queryMaterialAvg(long mid) {
		final double[] avg = {0.0};
		hourMaterial.find(and(
				eq("id", mid)
		))
				.sort(Sorts.descending("time"))
				.limit(1)
				.forEach((Block<? super Document>) document ->
				{
					avg[0] = new BigDecimal((float) document.getLong("p") / document.getLong("size")).setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
				});
		return avg[0];
	}

	public static List<Double> queryGoodsBrandAndQuality(long id,boolean isNpcBuy) {
		List<Double> list = new ArrayList<>();
		int type = TP_TYPE_NPC;
		if (!isNpcBuy) {
			type = TP_TYPE_PLAYER;
		}
		hourGoods.find(and(
				eq("id", id),
				eq("tp", type)
		))
				.sort(Sorts.descending("time"))
				.limit(1)
				.forEach((Block<? super Document>) document ->
				{
					double quality = document.getDouble("tq") / document.getLong("size");
					double brand = document.getDouble("tb") / document.getLong("size");
					double price = document.getLong("p") / document.getLong("size");
					list.add(quality);
					list.add(brand);
					list.add(price);
				});
		return list;
	}
	public static List<Double> queryPromotionAvg(long typeId,Categorytype cty) {
		List<Double> list = new ArrayList<>();
		hourBrandAmount.find(and(
				eq("id", typeId),
				eq("ct", cty.getValue())
		))
				.sort(Sorts.descending("time"))
				.limit(1)
				.forEach((Block<? super Document>) document ->
				{
					double price = new BigDecimal((float) document.getLong("total") / document.getLong("size")).setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
					double ability = document.getDouble("ab") / document.getLong("size");
					list.add(price);
					list.add(ability);
				});
		return list;
	}
	public static List<Double> queryRetailShopGoodsAvg(long itemId) {
		List<Double> list = new ArrayList<>();
		hourRetailShop.find(and(
				eq("id", itemId)
		))
				.sort(Sorts.descending("time"))
				.limit(1)
				.forEach((Block<? super Document>) document ->
				{
					double quality = new BigDecimal((float) document.getLong("iQ") / document.getLong("size")).setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
					double brand = new BigDecimal((float) document.getLong("iB") / document.getLong("size")).setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
					double price = new BigDecimal((float) document.getLong("total") / document.getLong("size")).setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
					list.add(quality);
					list.add(brand);
					list.add(price);
				});
		return list;
	}

	public static List<Double> queryRetailShopAvg(long itemId) {
		List<Double> list = new ArrayList<>();
		hourRetailShop.find(and(
				eq("id", itemId)
		))
				.sort(Sorts.descending("time"))
				.limit(1)
				.forEach((Block<? super Document>) document ->
				{
					double quality = new BigDecimal((float) document.getLong("bQ") / document.getLong("size")).setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
					double brand = new BigDecimal((float) document.getLong("bB") / document.getLong("size")).setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
					list.add(quality);
					list.add(brand);
				});
		return list;
	}

}
