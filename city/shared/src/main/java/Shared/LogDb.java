package Shared;

import com.mongodb.client.MongoDatabase;
import org.apache.log4j.Logger;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.MongoException;
import org.bson.Document;

public class LogDb {
	private static final String dbName = "log";
	private static MongoClientURI connectionUri;

	private static MongoClient mongoClient;
	private static MongoDatabase database;

	public static void init(String uri){
		connectionUri = new MongoClientURI(uri);
		mongoClient = new MongoClient(connectionUri);
		database = mongoClient.getDatabase(dbName);
	}

	public static void startUp(){

	}
}
