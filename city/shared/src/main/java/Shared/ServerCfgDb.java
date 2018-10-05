package Shared;

import com.mongodb.Block;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

public final class ServerCfgDb {
		private static AccountServerInfo accInfo;
		private static final String dbName = "city";
		private static final String asInfoColName = "asInfo";
		private static final String gsInfoColName = "gsInfo";

		private static MongoClientURI connectionUri;

		private static MongoClient mongoClient;
		private static MongoDatabase database;

		public static void init(String uri){
			connectionUri = new MongoClientURI(uri);
			mongoClient = new MongoClient(connectionUri);
			database = mongoClient.getDatabase(dbName);
		}
		
		public static void startUp(){
			Document doc = database.getCollection(asInfoColName).find().first();
			accInfo = new AccountServerInfo(doc);
		}
		
		public static void shutDown(){
		}
		
		public static List<GameServerInfo> getGameServerInfoList(){
			List<GameServerInfo> res = new ArrayList<GameServerInfo>();
			database.getCollection(gsInfoColName).find().forEach((Block<Document>) doc -> {
				res.add(new GameServerInfo(doc));
			});
			return res;
		}
		
		public static GameServerInfo getGameServerInfo(int id){
			MongoCollection<Document> collection = database.getCollection(gsInfoColName);
			Document doc = collection.find(Filters.eq("_id", id)).first();
			if(doc != null)
				return new GameServerInfo(doc);
			return null;
		}
		
		public static AccountServerInfo getAccountserverInfo(){
			return accInfo;
		}
}
