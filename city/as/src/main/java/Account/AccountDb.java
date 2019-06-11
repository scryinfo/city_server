package Account;

import Shared.DatabaseInfo;
import Shared.RoleBriefInfo;
import as.As;
import com.mongodb.Block;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.apache.log4j.Logger;
import org.bson.Document;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

public class AccountDb {
	private static MongoClientURI connectionUrl;
	private static MongoClient mongoClient;
	private static MongoCollection<Document> accCol;
	private static MongoCollection<Document> invitationCardCollection;
	private static CopyOnWriteArraySet<String> accountInfo = new CopyOnWriteArraySet<>();
	private static final Logger LOGGER = Logger.getLogger(AccountDb.class);

	//private static Map<String, MongoClient> gameDbClients = new TreeMap<>();
	private static Map<String, Connection> gameDbConns = new HashMap<>();
	public static void init(String url) {
		connectionUrl = new MongoClientURI(url);
		mongoClient = new MongoClient(connectionUrl);
		accCol = mongoClient.getDatabase("city").getCollection("acc");
		accCol.find().forEach((Block<? super Document>) document ->
		{
			accountInfo.add(document.getString("_id"));
		});
		buildIndex();
		invitationCardCollection = mongoClient.getDatabase("meta").getCollection("InvitationCard");
	}
	private static void buildIndex() {
		//accCol.createIndex(Indexes.ascending("account"));
	}
	public static void startUp() {
	}

	public static void shutDown() {
	}

	public static As.CodeStatus invitationCardUseful(String card)
	{
		String pattern = String.format("^%s$", card);
		Document document = invitationCardCollection.find(Filters.regex("_id",pattern,"i")).first();
		if (document == null)
		{
			return As.CodeStatus.ERROR;
		}
		else if (document.getBoolean("used",false))
		{
			return As.CodeStatus.USED;
		}
		else return As.CodeStatus.USEFUL;
	}

	public static boolean accountExist(String account)
	{
		return accountInfo.contains(account);
	}

	public static void modifyPwd(String account,String md5Pwd)
	{
		accCol.updateOne(Filters.eq("_id", account), Updates.set("pwd", md5Pwd));
	}

	public static synchronized As.CreateResult.Status createAccount(String account, String md5Pwd, String invataitonCode)
	{
		String pattern = String.format("^%s$", invataitonCode);
		Document document = invitationCardCollection.find(Filters.regex("_id",pattern,"i")).first();
		if (document == null || document.getBoolean("used"))
		{
			return As.CreateResult.Status.FAIL_INVCODE_USED;
		}
		try
		{
			accCol.insertOne(new AccountInfo(account, md5Pwd).toDocument());
			accountInfo.add(account);
		}
		catch (Exception e)
		{
			e.printStackTrace();
			LOGGER.fatal("create account failed! : " + e.toString());
			return As.CreateResult.Status.FAIL;
		}
		try
		{
			invitationCardCollection.updateOne(Filters.eq("_id", invataitonCode), Updates.set("used", true));
		}
		catch (Exception e)
		{
			e.printStackTrace();
			LOGGER.fatal("update invitationCode status failed! : " + e.toString());
		}
		return As.CreateResult.Status.SUCCESS;
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
		// can not use jpa meta class here, the problem is you can not change the package which the
		// meta class belong to. Even you put the *_ classes in Shared, the package still is Game. So refer them is trouble.
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
