package Shared;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import java.util.UUID;

public class LogDb {
	private static MongoClientURI connectionUrl;
	private static MongoClient mongoClient;
	private static MongoDatabase database;
	private static MongoCollection<Document> npcBuyInRetailCol; // table in the log database
	private static MongoCollection<Document> salaryCol; // table in the log database
	public static void init(String url) {
		connectionUrl = new MongoClientURI(url);
		mongoClient = new MongoClient(connectionUrl);
		database = mongoClient.getDatabase("cityLog");
		npcBuyInRetailCol = database.getCollection("retail");
		npcBuyInRetailCol.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
	}
	public static void startUp(){}

	public static void npcBuy(int itemId, int price, UUID producerId, int itemQty, UUID buildingOwnerId, int buildingQty, float distance) {
		Document document = new Document();
		document.put("imId", itemId);
		document.put("p", price);
		document.put("iId", producerId);
		document.put("iQ", itemQty);
		document.put("bId", buildingOwnerId);
		document.put("bQ", buildingQty);
		document.put("d", distance);
		document.put("t", System.currentTimeMillis());
		npcBuyInRetailCol.insertOne(document);
	}

	public static void payOff(UUID roleId, UUID buildingId, int salary, int workers) {
		Document document = new Document();
		document.put("r", roleId);
		document.put("b", buildingId);
		document.put("s", salary);
		document.put("a", salary*workers);
		document.put("w", workers);
		document.put("t", System.currentTimeMillis());
		salaryCol.insertOne(document);
	}
}
