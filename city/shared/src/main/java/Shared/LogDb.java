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
	private static final String BUY_INSHELF = "buyInShelf";
	private static final String BUY_AD_SLOT = "buyAdSlot";
	private static final String PAY_TRANSFER = "payTransfer";
	private static final String RENT_GROUND = "rentGround";
	private static final String BUY_TECH = "buyTech";
	private static final String BUY_GROUND = "buyGround";
	private static final String EXTEND_BAG = "extendBag";
	//-------------------------------------------------
	private static final String INCOME_EXCHANGE = "incomeExchange";
	private static final String INCOME_INSHELF = "incomeInShelf";
	private static final String INCOME_AD_SLOT = "incomeAdSlot";
	private static final String INCOME_TECH = "incomeTech";
	private static final String INCOME_RENT_GROUND = "incomeRentGround";
	private static final String INCOME_BUY_GROUND = "incomeBuyGround";
	private static final String INCOME_VISIT = "incomeVisit";

	private static final String PLAYER_ID = "playerId";
	//---------------------------------------------------
	private static MongoCollection<Document> buyAdSlot;
	private static MongoCollection<Document> buyTech;

	private static MongoCollection<Document> npcBuyInRetailCol; // table in the log database
	private static MongoCollection<Document> paySalary; // table in the log database

	//player buy material or goods
	private static MongoCollection<Document> buyInShelf;
	private static MongoCollection<Document> payTransfer;
	private static MongoCollection<Document> rentGround;
	private static MongoCollection<Document> buyGround;
	private static MongoCollection<Document> extendBag;
	//-----------------------------------------
	private static MongoCollection<Document> incomeExchange;
	private static MongoCollection<Document> incomeAdSlot;
	private static MongoCollection<Document> incomeTech;

	//player buy material or goods  and npc buy goods
	private static MongoCollection<Document> incomeInShelf;

	private static MongoCollection<Document> incomeRentGround;
	private static MongoCollection<Document> incomeBuyGround;
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
		buyAdSlot = database.getCollection(BUY_AD_SLOT)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		payTransfer = database.getCollection(PAY_TRANSFER)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		rentGround = database.getCollection(RENT_GROUND)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		buyTech = database.getCollection(BUY_TECH)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		buyGround = database.getCollection(BUY_GROUND)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		extendBag = database.getCollection(EXTEND_BAG)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);

		incomeExchange = database.getCollection(INCOME_EXCHANGE)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		incomeInShelf = database.getCollection(INCOME_INSHELF)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		incomeAdSlot = database.getCollection(INCOME_AD_SLOT)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		incomeTech = database.getCollection(INCOME_TECH)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		incomeRentGround = database.getCollection(INCOME_RENT_GROUND)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		incomeBuyGround = database.getCollection(INCOME_BUY_GROUND)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		incomeVisit = database.getCollection(INCOME_VISIT)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		playerId = database.getCollection(PLAYER_ID)
				.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
		//test();
	}

	public static MongoDatabase getDatabase()
	{
		return database;
	}

	private static void test()
	{
		UUID uuid = UUID.randomUUID();
		UUID uuid1 = UUID.randomUUID();
		npcBuy(1, 1, UUID.randomUUID(), 1, UUID.randomUUID(), 1, 1.0f);
		paySalary(UUID.randomUUID(), UUID.randomUUID(), 10L, 1, 1);
		payTransfer(UUID.randomUUID(), 10L, 1, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1);
		buyTech(UUID.randomUUID(), UUID.randomUUID(), 10L, 1, 1);
		rentGround(UUID.randomUUID(), 10L, UUID.randomUUID(), 1, new ArrayList<>());
		buyGround(UUID.randomUUID(), UUID.randomUUID(), 10L, 1, new ArrayList<>());
		extendBag(UUID.randomUUID(), 10L, 1, 1);
		System.err.println("uuid1 = " + uuid1);
	}

	public static void startUp(){}

	/**
	 *
	 * @param yestodayStartTime
	 * @param todayStartTime
	 * @param collection
	 * @return Aggregation document only has id,total
	 */
	public static List<Document> daySummary1(long yestodayStartTime, long todayStartTime,
											MongoCollection<Document> collection)
	{
		List<Document> documentList = new ArrayList<>();
		collection.aggregate(
				Arrays.asList(
						Aggregates.match(and(
								gte("t", yestodayStartTime),
								lt("t", todayStartTime))),
						Aggregates.group("$r", Accumulators.sum(KEY_TOTAL, "$a"))
				)
		).forEach((Block<? super Document>) documentList::add);
		return documentList;
	}

	public static List<Document> daySummaryRoomRent(long yestodayStartTime, long todayStartTime)
	{
		List<Document> documentList = new ArrayList<>();
		incomeVisit.aggregate(
				Arrays.asList(
						Aggregates.match(and(
								eq("tp",TP_APARTMENT),
								gte("t", yestodayStartTime),
								lt("t", todayStartTime))),
						Aggregates.group("$r", Accumulators.sum(KEY_TOTAL, "$a"))
				)
		).forEach((Block<? super Document>) documentList::add);
		return documentList;
	}

	public static List<Document> daySummaryShelf(long yestodayStartTime, long todayStartTime,
												 MongoCollection<Document> collection,boolean isGoods)
	{
		List<Document> documentList = new ArrayList<>();
		List<Document> documentTmp = new ArrayList<>();
		BasicDBObject dbObject = new BasicDBObject("_id",
				new BasicDBObject("r", "$r")
						.append("tpi", "$tpi"));
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
						Aggregates.group(dbObject, Accumulators.sum(KEY_TOTAL, "$a"))
				)
		).forEach((Block<? super Document>) documentTmp::add);

		documentTmp.forEach(document -> {
			Document newDoc = new Document();
			Document tmp = (Document) ((Document) document.get("_id")).get("_id");
			newDoc.append("id", tmp.get("r"))
					.append("tpi", tmp.get("tpi"))
					.append(KEY_TOTAL,document.get(KEY_TOTAL));
			documentList.add(newDoc);
		});

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


	public static void buyInShelf(UUID roleId, UUID dstId, long money, long n, long price,
								  UUID producerId, UUID bid, int type, int typeId)
	{
		Document document = new Document("t", System.currentTimeMillis());
		document.append("r", roleId)
				.append("d", dstId)
				.append("b", bid)
				.append("m", money)
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

	public static void paySalary(UUID roleId, UUID buildingId, long money, long salary, long workers) {
		Document document = new Document("t", System.currentTimeMillis());
		document.append("r", roleId)
				.append("b", buildingId)
				.append("m", money)
				.append("s", salary)
				.append("a", salary * workers)
				.append("w", workers);
		paySalary.insertOne(document);
	}

	public static void buyAdSlot(UUID roleId, long money,UUID dstId, UUID bid, UUID slotId, int rentPreDay)
	{
		Document document = new Document("t", System.currentTimeMillis());
		document.append("r", roleId)
				.append("d", dstId)
				.append("b", bid)
				.append("s", slotId)
				.append("m", money)
				.append("a", rentPreDay);
		buyAdSlot.insertOne(document);
	}

	public static void payTransfer(UUID roleId, long money, long charge, UUID srcId, UUID dstId, UUID producerId, int n)
	{
		Document document = new Document("t", System.currentTimeMillis());
		document.append("r", roleId)
				.append("s", srcId)
				.append("d", dstId)
				.append("m", money)
				.append("a", charge)
				.append("i", producerId)
				.append("c", n);
		payTransfer.insertOne(document);
	}

	public static void buyTech(UUID roleId, UUID ownerId, long money, int price, int metaId)
	{
		Document document = new Document("t", System.currentTimeMillis());
		document.append("r", roleId)
				.append("d", ownerId)
				.append("m", money)
				.append("a", price)
				.append("i", metaId);
		buyTech.insertOne(document);
	}

	public static void rentGround(UUID roleId, long money, UUID ownerId, long cost, List<Positon> positons)
	{
		Document document = new Document("t", System.currentTimeMillis());
		document.append("r", roleId)
				.append("d", ownerId)
				.append("m", money)
				.append("a", cost)
				.append("p", positionToDoc(positons));
		rentGround.insertOne(document);
	}

	public static void buyGround(UUID roleId, UUID ownerId, long money, long price, List<Positon> plist1)
	{
		Document document = new Document("t", System.currentTimeMillis());
		document.append("r", roleId)
				.append("d", ownerId)
				.append("m", money)
				.append("s", price)
				.append("a", price * plist1.size())
				.append("p", positionToDoc(plist1));
		buyGround.insertOne(document);
	}

	public static void extendBag(UUID id, long money, int cost, int bagCapacity)
	{
		Document document = new Document("t", System.currentTimeMillis());
		document.append("r", id)
				.append("m", money)
				.append("a", cost)
				.append("c", bagCapacity);
		extendBag.insertOne(document);
	}

	public static void incomeExchange(UUID roleId, long money, UUID dstId, int n, int price, int producerId)
	{
		Document document = new Document("t", System.currentTimeMillis());
		document.append("r",roleId)
				.append("d",dstId)
				.append("m",money)
				.append("s",price)
				.append("a", n*price)
				.append("i",producerId);
		incomeExchange.insertOne(document);
	}

	public static void incomeInShelf(UUID roleId, UUID dstId, long money, long n, long price,
									 UUID producerId,UUID bid, int type, int typeId)
	{
		Document document = new Document("t", System.currentTimeMillis());
		document.append("r", roleId)
				.append("d", dstId)
				.append("b", bid)
				.append("m", money)
				.append("p", price)
				.append("a", n * price)
				.append("i", producerId)
				.append("tp", type)
				.append("tpi", typeId);
		incomeInShelf.insertOne(document);
	}

	public static void incomeAdSlot(UUID roleId, long money,UUID desId, UUID bid, UUID slotId, int rentPreDay)
	{
		Document document = new Document("t", System.currentTimeMillis());
		document.append("r", roleId)
				.append("d", desId)
				.append("b", bid)
				.append("s", slotId)
				.append("m", money)
				.append("a", rentPreDay);
		incomeAdSlot.insertOne(document);
	}

	public static void incomeTech(UUID roleId, UUID payId, long money, int price, int metaId)
	{
		Document document = new Document("t", System.currentTimeMillis());
		document.append("r", roleId)
				.append("d", payId)
				.append("m", money)
				.append("a", price)
				.append("i", metaId);
		incomeTech.insertOne(document);
	}

	public static void incomeRentGround(UUID roleId, long money, UUID payId, long cost, List<Positon> plist1)
	{
		Document document = new Document("t", System.currentTimeMillis());
		document.append("r", roleId)
				.append("d", payId)
				.append("m", money)
				.append("a", cost)
				.append("p", positionToDoc(plist1));
		incomeRentGround.insertOne(document);
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

	public static void incomeBuyGround(UUID roleId, UUID payId, long money, long price, List<Positon> plist1)
	{
		Document document = new Document("t", System.currentTimeMillis());
		document.append("r", roleId)
				.append("d", payId)
				.append("m", money)
				.append("s", price)
				.append("a", price * plist1.size())
				.append("p", positionToDoc(plist1));
		incomeBuyGround.insertOne(document);
	}

	public static void incomeVisit(UUID roldId, long money, int type,long cost, UUID bId, UUID payId)
	{
		Document document = new Document("t", System.currentTimeMillis());
		document.append("r", roldId)
				.append("d", payId)
				.append("b", bId)
				.append("m", money)
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

	public static MongoCollection<Document> getBuyAdSlot()
	{
		return buyAdSlot;
	}

	public static MongoCollection<Document> getPayTransfer()
	{
		return payTransfer;
	}

	public static MongoCollection<Document> getRentGround()
	{
		return rentGround;
	}

	public static MongoCollection<Document> getBuyTech()
	{
		return buyTech;
	}

	public static MongoCollection<Document> getBuyGround()
	{
		return buyGround;
	}

	public static MongoCollection<Document> getExtendBag()
	{
		return extendBag;
	}

	public static MongoCollection<Document> getIncomeExchange()
	{
		return incomeExchange;
	}

	public static MongoCollection<Document> getIncomeInShelf()
	{
		return incomeInShelf;
	}

	public static MongoCollection<Document> getIncomeAdSlot()
	{
		return incomeAdSlot;
	}

	public static MongoCollection<Document> getIncomeTech()
	{
		return incomeTech;
	}

	public static MongoCollection<Document> getIncomeRentGround()
	{
		return incomeRentGround;
	}

	public static MongoCollection<Document> getIncomeBuyGround()
	{
		return incomeBuyGround;
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
