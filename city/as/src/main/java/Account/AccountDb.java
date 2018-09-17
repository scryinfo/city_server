package Account;

import Shared.DatabaseName;
import Shared.RoleBriefInfo;
import Shared.RoleFieldName;
import com.mongodb.Block;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.mongodb.client.model.Projections.fields;
import static com.mongodb.client.model.Projections.include;

public class AccountDb {
	private static MongoClientURI connectionUrl;
	private static MongoClient mongoClient;
	private static MongoDatabase database;
	private static MongoCollection<Document> accCol;
	private static Map<String, MongoClient> gameDbClients = new TreeMap<>();
	public static void init(String url) {
		connectionUrl = new MongoClientURI(url);
		mongoClient = new MongoClient(connectionUrl);
		database = mongoClient.getDatabase("city");
		accCol = database.getCollection("acc");

		buildIndex();
	}
	private static void buildIndex() {
		//accCol.createIndex(Indexes.ascending("account"));
	}
	public static void startUp() {
	}

	public static void shutDown() {
	}

	public static AccountInfo create(String name) {
		AccountInfo res = new AccountInfo(name);
		try {
			accCol.insertOne(res.toDocument());
			return res;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public static AccountInfo get(String name) {
		Document doc = accCol.find(Filters.eq("_id", name)).first();
		if (doc == null)
			return null;
		return new AccountInfo(doc);
	}
	public static List<RoleBriefInfo> getRoleBriefInfos(String account, String dbUri, int gameServerId) {
		List<RoleBriefInfo> res = new ArrayList<>();
		MongoClient client = gameDbClients.get(dbUri);
		if(client == null)
		{
			client = new MongoClient(new MongoClientURI(dbUri));
			gameDbClients.put(dbUri, client);
		}
		client.getDatabase(DatabaseName.Game.name).getCollection(DatabaseName.Game.roleColName).find(
				Filters.and(
						Filters.eq(RoleFieldName.AccountNameFieldName, account),
						Filters.eq(RoleFieldName.ServerIdFieldName, gameServerId)))
				.projection(fields(include(RoleBriefInfo.queryFieldsName())))
				.forEach((Block<Document>) doc -> {
					res.add(new RoleBriefInfo(doc));
				});
		return res;
	}
}
