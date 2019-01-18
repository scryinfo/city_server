package Shared;

import com.mongodb.*;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import org.bson.Document;
import java.util.*;

import static com.mongodb.client.model.Filters.*;

public class LogDb {
	private static MongoClientURI connectionUrl;
	private static MongoClient mongoClient;
	private static MongoDatabase database;
	private static final int TP_APARTMENT = 14;
	private static final int TP_TYPE_MATERIAL = 21;
	private static final int TP_TYPE_GOODS = 22;

	private static final String PAY_SALARY = "paySalary";
	private static final String PAY_TRANSFER = "payTransfer";

	private static final String BUY_INSHELF = "buyInShelf";
	private static final String RENT_GROUND = "rentGround";
	private static final String BUY_GROUND = "buyGround";

	private static final String EXTEND_BAG = "extendBag";

	private static final String INCOME_VISIT = "incomeVisit";

	private static final String PLAYER_ID = "playerId";
	//---------------------------------------------------
	private static MongoCollection<Document> npcBuyInRetailCol; // table in the log database
	private static MongoCollection<Document> paySalary; // table in the log database

	//player buy material or goods
	private static MongoCollection<Document> buyInShelf;
	private static MongoCollection<Document> payTransfer;
	private static MongoCollection<Document> rentGround;
	private static MongoCollection<Document> buyGround;
	private static MongoCollection<Document> extendBag;
	//-----------------------------------------

	//player buy material or goods  and npc buy goods
	private static MongoCollection<Document> incomeVisit;

	private static MongoCollection<Document> playerId;

	public static final String KEY_TOTAL = "total";

	public static void init(String url, String dbName)
	{
		connectionUrl = new MongoClientURI(url);
		mongoClient = new MongoClient(connectionUrl);
		database = mongoClient.getDatabase(dbName);
		npcBuyInRetailCol = database.getCollection("retail")
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);

		paySalary = database.getCollection(PAY_SALARY)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		buyInShelf = database.getCollection(BUY_INSHELF)
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
		playerId = database.getCollection(PLAYER_ID)
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


	public static Set<UUID> getAllPlayer()
	{
		Set<UUID> set = new HashSet<>();
		playerId.find().forEach((Block<? super Document>) document -> {
			set.add((UUID) document.get("r"));
		});
		return set;
	}

	public static void insertPlayerId(UUID uuid)
	{
		playerId.insertOne(new Document("r", uuid));
	}


	public static void buyInShelf(UUID roleId, UUID dstId, long n, long price,
								  UUID producerId, UUID bid, int type, int typeId)
	{
		Document document = new Document("t", System.currentTimeMillis());
		document.append("r", roleId)
				.append("d", dstId)
				.append("b", bid)
				.append("p", price)
				.append("a", n * price)
				.append("i", producerId)
				.append("tp", type)
				.append("tpi", typeId);
		buyInShelf.insertOne(document);
	}

	public static void npcBuy(int itemId, int price, UUID producerId, int itemQty, UUID buildingOwnerId, int buildingQty, float distance) {
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
		Document document = new Document("t", System.currentTimeMillis());
		document.append("r", roleId)
				.append("d", ownerId)
				.append("s", price)
				.append("a", price * plist1.size())
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

	public static void incomeVisit(UUID roldId,int type,long cost, UUID bId, UUID payId)
	{
		Document document = new Document("t", System.currentTimeMillis());
		document.append("r", roldId)
				.append("d", payId)
				.append("b", bId)
				.append("a", cost)
				.append("tp",type);
		incomeVisit.insertOne(document);
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

	public static MongoCollection<Document> getExtendBag()
	{
		return extendBag;
	}

	public static MongoCollection<Document> getIncomeVisit()
	{
		return incomeVisit;
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
}
