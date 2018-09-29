package Account;

import Shared.DatabaseInfo;
import Shared.RoleBriefInfo;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import org.bson.Document;

import java.sql.*;
import java.util.*;

public class AccountDb {
	private static MongoClientURI connectionUrl;
	private static MongoClient mongoClient;
	private static MongoDatabase database;
	private static MongoCollection<Document> accCol;
	//private static Map<String, MongoClient> gameDbClients = new TreeMap<>();
	private static Map<String, Connection> gameDbConns = new HashMap<>();
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
	public static List<RoleBriefInfo> getRoleBriefInfos(String account, String url) throws SQLException {
		List<RoleBriefInfo> res = new ArrayList<>();
		Connection conn = gameDbConns.get(url);
		if(conn == null) {
			conn = DriverManager.getConnection(url, DatabaseInfo.Game.USERNAME, DatabaseInfo.Game.PASSWORD);
			gameDbConns.put(url, conn);
		}
		String sql = String.format("SELECT %s, %s, %s FROM %s WHERE %s = ?",
				DatabaseInfo.Game.Player.Id,
				DatabaseInfo.Game.Player.Name,
				DatabaseInfo.Game.Player.OfflineTs,
				DatabaseInfo.Game.Player.Table,
				DatabaseInfo.Game.Player.AccountName);

		PreparedStatement stmt = conn.prepareStatement(sql);
		stmt.setString(1, account);
		ResultSet rs = stmt.executeQuery();
		while(rs.next()){
			UUID id = UUID.fromString(rs.getString(DatabaseInfo.Game.Player.Id));
			String name = rs.getString(DatabaseInfo.Game.Player.Name);
			long ts = rs.getLong(DatabaseInfo.Game.Player.OfflineTs);
			res.add(new RoleBriefInfo(id, name, ts));
		}
		rs.close();
		stmt.close();
//		MongoClient client = gameDbClients.get(dbUri);
//		if(client == null)
//		{
//			client = new MongoClient(new MongoClientURI(dbUri));
//			gameDbClients.put(dbUri, client);
//		}
//		client.getDatabase(DatabaseName.Game.name).getCollection(DatabaseName.Game.roleColName).find(
//				Filters.and(
//						Filters.eq(DatabaseInfo.AccountName, account),
//						Filters.eq(DatabaseInfo.ServerIdFieldName, gameServerId)))
//				.projection(fields(include(RoleBriefInfo.queryFieldsName())))
//				.forEach((Block<Document>) doc -> {
//					res.add(new RoleBriefInfo(doc));
//				});
		return res;
	}
}
