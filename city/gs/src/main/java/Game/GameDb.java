package Game;

import java.util.*;
import java.util.stream.Collectors;

import Shared.DatabaseName;
import Shared.RoleBriefInfo;
import Shared.RoleFieldName;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.mongodb.*;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Updates;
import org.apache.log4j.Logger;
import org.bson.Document;
import org.bson.types.ObjectId;

import static com.mongodb.client.model.Projections.fields;
import static com.mongodb.client.model.Projections.include;

public class GameDb {
	private static final Logger logger = Logger.getLogger(GameDb.class);
	public static void init(String url) { //"mongodb://localhost:27017"
		connectionString = new MongoClientURI(url);
	}
	public static void startUp() {
		mongoClient = new MongoClient(connectionString);
		database = mongoClient.getDatabase(DatabaseName.Game.name);
		playerCol = database.getCollection(DatabaseName.Game.roleColName);
		playerCol.withWriteConcern(WriteConcern.UNACKNOWLEDGED);

		groundAuctionCol = database.getCollection("groundAuction");

//		apartmentCol = database.getCollection("apartment");
//		materialFactoryCol = database.getCollection("materialFactory");
//		productingDepartmentCol = database.getCollection("productingDepartment");
//		retailShopCol = database.getCollection("retailShop");
//		laboratoryCol = database.getCollection("laboratory");
//		publicFacilityCol = database.getCollection("publicFacility");

		buildingCol = database.getCollection("building");
		npcCol = database.getCollection("npc");
		buildIndex();
	}
	private static void buildIndex() {
		playerCol.createIndex(Indexes.ascending(RoleFieldName.AccountNameFieldName));
	}
	private static MongoClientURI connectionString;
	private static MongoClient mongoClient;
	private static MongoDatabase database;
	private static MongoCollection<Document> playerCol;
	private static MongoCollection<Document> groundAuctionCol;
	private static MongoCollection<Document> npcCol;
	// buildings
//	private static MongoCollection<Document> apartmentCol;
//	private static MongoCollection<Document> materialFactoryCol;
//	private static MongoCollection<Document> productingDepartmentCol;
//	private static MongoCollection<Document> retailShopCol;
//	private static MongoCollection<Document> laboratoryCol;
//	private static MongoCollection<Document> publicFacilityCol;

	private static MongoCollection<Document> buildingCol;
	private static LoadingCache<ObjectId, Player> graphs = CacheBuilder.newBuilder()
			.weakValues()
			.build(
					new CacheLoader<ObjectId, Player>() {
						public Player load(ObjectId id) {
							Document d = playerCol.find(Filters.eq(id)).first();
							if(d == null)
								return null;
							return new Player(d);
						}
					});
	public static Player getPlayer(ObjectId id) {
		return graphs.getUnchecked(id);
	}

	public static boolean createPlayer(Player p)
	{
		Document bson = p.toBson();
		bson.append("_id", p.id());
		bson.append(RoleFieldName.NameFieldName, p.getName());
		bson.append(RoleFieldName.AccountNameFieldName, p.getAccount());
		bson.append(RoleFieldName.OfflineTsFieldName, 0L);
		bson.append(RoleFieldName.OnlineTsFieldName, 0L);
		try {
			playerCol.insertOne(bson);
			return true;
		}
		catch(MongoException e) {
			// how to check the error code is duplicated key?
			return false;
		}
	}
	public static List<RoleBriefInfo> getPlayerInfo(String account) {
		List<RoleBriefInfo> res = new ArrayList<>();
		playerCol.find(Filters.eq(RoleFieldName.AccountNameFieldName, account)).projection(fields(include(RoleBriefInfo.queryFieldsName()))).forEach((Block<Document>) doc -> {
			res.add(new RoleBriefInfo(doc));
		});
		return res;
	}
	public static void update(Player p) {
		playerCol.updateOne(Filters.eq(p.id()), new Document("$set", p.toBson()));
	}
	public static void updatePlayerOfflineTs(ObjectId id, long ts) {
		playerCol.updateOne(Filters.eq(id), new Document("$set", new Document(RoleFieldName.OfflineTsFieldName, ts)));
	}
	public static void updatePlayerOnlineTs(ObjectId id, long ts) {
		playerCol.updateOne(Filters.eq(id), new Document("$set", new Document(RoleFieldName.OnlineTsFieldName, ts)));
	}
    public static Document getGroundAction(int id) {
		return groundAuctionCol.find(Filters.eq("_id", id)).first();
    }

	public static void updateGroundAuction(Document d, int serverId) {
		groundAuctionCol.updateOne(Filters.eq(serverId), d);
	}

	public static Set<Building> readAllBuilding() {
		Set<Building> res = new HashSet<>();
		buildingCol.find().forEach((Block<Document>) doc -> {
			Building b = Building.create(doc);
			res.add(b);
		});
		return res;
	}
//    public static HashMap<ObjectId, Apartment> getAllApartment() {
//		HashMap<ObjectId, Apartment> res = new HashMap<>();
//		apartmentCol.find().forEach((Block<Document>) doc -> {
//			Apartment m = new Apartment(doc);
//			res.put(m.id(), m);
//		});
//		return res;
//    }
//	public static HashMap<ObjectId, PublicFacility> getAllPublicFacility() {
//		HashMap<ObjectId, PublicFacility> res = new HashMap<>();
//		publicFacilityCol.find().forEach((Block<Document>) doc -> {
//			PublicFacility m = new PublicFacility(doc);
//			res.put(m.id(), m);
//		});
//		return res;
//	}
//
//	public static HashMap<ObjectId, ProductingDepartment> getAllProductingDepartment() {
//		HashMap<ObjectId, ProductingDepartment> res = new HashMap<>();
//		productingDepartmentCol.find().forEach((Block<Document>) doc -> {
//			ProductingDepartment m = new ProductingDepartment(doc);
//			res.put(m.id(), m);
//		});
//		return res;
//	}
//
//	public static HashMap<ObjectId, Laboratory> getAllLaboratory() {
//		HashMap<ObjectId, Laboratory> res = new HashMap<>();
//		laboratoryCol.find().forEach((Block<Document>) doc -> {
//			Laboratory m = new Laboratory(doc);
//			res.put(m.id(), m);
//		});
//		return res;
//	}
//
//	public static HashMap<ObjectId, MaterialFactory> getAllMaterialFactory() {
//		HashMap<ObjectId, MaterialFactory> res = new HashMap<>();
//		materialFactoryCol.find().forEach((Block<Document>) doc -> {
//			MaterialFactory m = new MaterialFactory(doc);
//			res.put(m.id(), m);
//		});
//		return res;
//	}
//
//	public static HashMap<ObjectId, RetailShop> getAllRetailShop() {
//		HashMap<ObjectId, RetailShop> res = new HashMap<>();
//		retailShopCol.find().forEach((Block<Document>) doc -> {
//			RetailShop m = new RetailShop(doc);
//			res.put(m.id(), m);
//		});
//		return res;
//	}

	public static void addBuilding(Building b) {
		buildingCol.insertOne(b.toBson());
	}

	public static void delBuilding(Building b) {
		buildingCol.deleteOne(Filters.eq(b.id()));
	}


    public static Set<Npc> readAllNpc() {
		Set<Npc> res = new HashSet<>();
		npcCol.find().forEach((Block<Document>) doc -> {
			Npc npc = new Npc(doc);
			res.add(npc);
		});
		return res;
    }

//	public static void paySalary(Player p, Set<Npc> allNpc, int salary) {
//		ArrayList<ObjectId> ids = new ArrayList<>(allNpc.size());
//		allNpc.forEach(npc->ids.add(npc.id()));
//		ClientSession s = mongoClient.startSession();
//		s.startTransaction();
//		playerCol.updateOne(Filters.eq(p.id()), Updates.inc(RoleFieldName.MoneyFieldName, -salary*allNpc.size()));
//		npcCol.updateMany(Filters.in("_id", ids), Updates.inc(RoleFieldName.MoneyFieldName, salary));
//		s.commitTransaction();
//	}

	public static void offsetNpcMoney(Collection<ObjectId> ids, int delta) {
		npcCol.updateMany(Filters.in("_id", ids), Updates.inc(RoleFieldName.MoneyFieldName, delta));
	}


	// must use transaction senario:
	// 1. money which relative to any unit (this is mean any thing which invole with money include:
	// 			buy ground, goods, building...
	//			rent ground, building...

	// only keep in memory is allowed:
	// 1. where the building which npc located in
	// 2. ?
	public static ClientSession startTransaction() {
		ClientSession s = mongoClient.startSession();
		s.startTransaction();
		return s;
	}
	public static boolean commit(ClientSession s) {
		while (true) {
			try {
				s.commitTransaction();
				return true;
			} catch (MongoException e) {
				// can retry commit
				if (e.hasErrorLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL)) {
					continue;
				} else {
					//throw e;
					s.abortTransaction();
					logger.fatal("mongodb transaction fail " + e);
					return false;
				}
			}
		}
	}

	public static void create(List<Npc> res) {
		npcCol.insertMany(res.stream().map(Npc::toBson).collect(Collectors.toList()));
	}

	public static void delNpc(Set<Npc> npc) {
	}
}
