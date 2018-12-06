package Statistic;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

// this class job is when timer trigger read db data, do statistical, write result to another table
public class LogDb {
    private static MongoClientURI connectionUrl;
    private static MongoClient mongoClient;
    private static MongoDatabase database;
    private static MongoCollection<Document> xxxCol; // table in the log database
    public static void init(String url) {
        connectionUrl = new MongoClientURI(url);
        mongoClient = new MongoClient(connectionUrl);
        database = mongoClient.getDatabase("cityLog");
        xxxCol = database.getCollection("???");
    }
}
