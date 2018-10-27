package Game;


import Shared.RoleBriefInfo;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import gs.Gs;
import org.hibernate.*;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.query.Query;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutionException;

public class GameDb {
	private static SessionFactory sessionFactory;
	private static Session session;
	private static final int BATCH_SIZE = 25;
	private static String HIBERNATE_CFG_PATH;
	private static SessionFactory buildSessionFactory() {
		try {
			StandardServiceRegistry standardRegistry = new StandardServiceRegistryBuilder().configure(new File(HIBERNATE_CFG_PATH)).build();
			Metadata metadata = new MetadataSources(standardRegistry).getMetadataBuilder().build();
			return metadata.getSessionFactoryBuilder().build();
		} catch (Throwable ex) {
			System.err.println("Initial SessionFactory creation failed." + ex);
			throw new ExceptionInInitializerError(ex);
		}
	}

//	private static LoadingCache<UUID, Player> playerCache = CacheBuilder.newBuilder()
//			.concurrencyLevel(1)
//			.weakValues()
//			.build(
//					new CacheLoader<UUID, Player>() {
//						public Player load(UUID id) {
//							Session session = sessionFactory.openSession();
//							Player res = session.get(Player.class, id);
//							session.close();
//							return res;
//						}
//					});
//
//	private static LoadingCache<UUID, Player.Info> playerInfoCache = CacheBuilder.newBuilder()
//			.concurrencyLevel(1)
//			.maximumSize(10240)
//			.build(
//					new CacheLoader<UUID, Player.Info>() {
//						@Override
//						public Player.Info load(UUID key) {
//							StatelessSession session = sessionFactory.openStatelessSession();
//							org.hibernate.Query q = session.createQuery("SELECT new Game$Player$Info(id ,name) FROM Player where id = :x");
//							q.setParameter("x", key);
//							List<Player.Info> l = q.list();
//							session.close();
//							return l.isEmpty()?null:l.get(0);
//						}
//						@Override
//						public Map<UUID, Player.Info> loadAll(Iterable<? extends UUID> keys) {
//							StatelessSession session = sessionFactory.openStatelessSession();
//							org.hibernate.Query q = session.createQuery("SELECT new Game$Player$Info(id ,name) FROM Player where id in :x");
//							q.setParameter("x", keys);
//							List<Player.Info> list = q.list();
//							Map<UUID, Player.Info> res = new HashMap<>(list.size());
//							list.forEach(i->res.put(i.id, i));
//							session.close();
//							return res;
//						}
//					});
	public static Player getPlayer(UUID id) {
		Transaction transaction = session.beginTransaction();
		Player res = session.get(Player.class, id);
		transaction.commit();
		return res;
	}
	public static List<Player.Info> getPlayerInfo(Collection<UUID> ids) {
		StatelessSession statelessSession = sessionFactory.openStatelessSession();
		Transaction transaction = statelessSession.beginTransaction();
		org.hibernate.Query q = statelessSession.createQuery("SELECT new Game$Player$Info(id ,name) FROM Player where id in :x");
		q.setParameter("x", ids);
		List<Player.Info> list = q.list();
		transaction.commit();
		statelessSession.close();
		return list;
	}
	public static boolean createPlayer(Player p) {
		boolean success = false;
		Transaction transaction = session.beginTransaction();
		try {
			session.save(p);
			transaction.commit();
			success = true;
		} catch (RuntimeException e) { // the exception is complex, may be javax.PersistenceException, or HibernateException, or jdbc exception ...
			transaction.rollback();
			e.printStackTrace();
		}
		return success;
	}
	public static void initGroundAction() {
		StatelessSession statelessSession = sessionFactory.openStatelessSession();
		Transaction transaction = statelessSession.beginTransaction();
		if(statelessSession.get(GroundAuction.class, GroundAuction.ID) == null)
			statelessSession.insert(new GroundAuction());
		transaction.commit();
		statelessSession.close();
	}
	public static void initExchange() {
		StatelessSession statelessSession = sessionFactory.openStatelessSession();
		Transaction transaction = statelessSession.beginTransaction();
		if(statelessSession.get(Exchange.class, Exchange.ID) == null)
			statelessSession.insert(new Exchange());
		transaction.commit();
		statelessSession.close();
	}
	public static GroundAuction getGroundAction() {
		Transaction transaction = session.beginTransaction();
		GroundAuction res = session.get(GroundAuction.class, GroundAuction.ID);
		transaction.commit();
		return res;
	}
	public static Exchange getExchange() {
		Transaction transaction = session.beginTransaction();
		Exchange res = session.get(Exchange.class, Exchange.ID);
		transaction.commit();
		return res;
	}
	public static List<Building> getAllBuilding() {
		Transaction transaction = session.beginTransaction();
		CriteriaBuilder builder = session.getCriteriaBuilder();
		CriteriaQuery<Building> criteria = builder.createQuery(Building.class);
		criteria.from(Building.class);
		List<Building> res = session.createQuery(criteria).list();
		transaction.commit();
		return res;
	}
	public static void saveOrUpdate(Collection objs) {
		Transaction transaction = session.beginTransaction();
		try {
			int i = 0;
			for (Object o : objs) {
				session.saveOrUpdate(o);
				++i;
				if (i % BATCH_SIZE == 0) {
					session.flush();
				}
			}
			transaction.commit();
		} catch (RuntimeException e) {
			e.printStackTrace();
			transaction.rollback();
		} finally {
		}
	}

	public static void saveOrUpdate(Object o) {
		Transaction transaction = session.beginTransaction();
		try {
			session.saveOrUpdate(o);
			transaction.commit();
		} catch (RuntimeException e) {
			e.printStackTrace();
			transaction.rollback();
		} finally {

		}
	}

	public static List<RoleBriefInfo> getPlayerInfo(String account) {
		Transaction transaction = session.beginTransaction();
		CriteriaBuilder criteriaBuilder = session.getCriteriaBuilder();
		CriteriaQuery<RoleBriefInfo> criteriaQuery = criteriaBuilder.createQuery(RoleBriefInfo.class);
		Root<Player> root = criteriaQuery.from(Player.class);
		criteriaQuery.multiselect(root.get(Player_.id), root.get(Player_.account), root.get(Player_.offlineTs));
		criteriaQuery.where(criteriaBuilder.equal(root.get(Player_.account), account));
		Query<RoleBriefInfo> query = session.createQuery(criteriaQuery);
		transaction.commit();
		return query.getResultList();
	}

	public static void delete(Object o) {
		Transaction transaction = session.beginTransaction();
		try {
			session.delete(o);
			transaction.commit();
		}
		catch(RuntimeException e) {
			transaction.rollback();
		}
		finally {

		}
	}
	public static void delete(Collection objs) {
		Transaction transaction = session.beginTransaction();
		try {
			int i = 0;
			for (Object o : objs) {
				session.delete(o);
				++i;
				if (i % BATCH_SIZE == 0) {
					session.flush();
					session.clear();
				}
			}
			transaction.commit();
		} catch (RuntimeException e) {
			transaction.rollback();
		} finally {

		}
	}
	public static void saveOrUpdateAndDelete(Collection saveOrUpdates, Collection deletes) {
		Transaction transaction = session.beginTransaction();
		try {
			int i = 0;
			for (Object o : saveOrUpdates) {
				session.saveOrUpdate(o);
				++i;
			}
			for (Object o : deletes) {
				session.delete(o);
				++i;
			}
			if (i % BATCH_SIZE == 0) {
				session.flush();
				//session.clear();
			}
			transaction.commit();
		} catch (RuntimeException e) {
			transaction.rollback();
		} finally {
			//session.close();
		}
	}

	public static List<Npc> getAllNpc() {
		Transaction transaction = session.beginTransaction();
		CriteriaBuilder builder = session.getCriteriaBuilder();
		CriteriaQuery<Npc> criteria = builder.createQuery(Npc.class);
		criteria.from(Npc.class);
		List<Npc> res = session.createQuery(criteria).list();
		transaction.commit();
		return res;
	}

	public static void startUp(String arg) {
		HIBERNATE_CFG_PATH = arg;
		sessionFactory = buildSessionFactory();
		session = sessionFactory.openSession();
	}
	public static void shutDown() {
		session.close();
		sessionFactory.close();
	}
    public static Gs.ExchangeDealLogs getExchangeDealLog(UUID id) {
		Gs.ExchangeDealLogs.Builder builder = Gs.ExchangeDealLogs.newBuilder();
		StatelessSession session = sessionFactory.openStatelessSession();
		Transaction transaction = session.beginTransaction();
		Query<Exchange.DealLog> q = session.createQuery("from Exchange$DealLog where seller= :x or buyer= :x", Exchange.DealLog.class);
		q.setParameter("x", id);
		q.list().forEach(l -> builder.addLog(l.toProto()));
		transaction.commit();
		session.close();
		return builder.build();
    }

	public static Gs.ExchangeDealLogs getExchangeDealLog(int page) {
		Gs.ExchangeDealLogs.Builder builder = Gs.ExchangeDealLogs.newBuilder();
		StatelessSession session = sessionFactory.openStatelessSession();
		Transaction transaction = session.beginTransaction();
		Criteria criteria = session.createCriteria(Exchange.DealLog.class);
		criteria.setFirstResult(page);
		criteria.setMaxResults(Exchange.DealLog.ROWS_IN_ONE_PAGE);
		criteria.list().forEach(l -> builder.addLog(((Exchange.DealLog)l).toProto()));
		transaction.commit();
		session.close();
		return builder.build();
	}
}
//public class GameDb {
//	private static final Logger logger = Logger.getLogger(GameDb.class);
//	public static void startUp(String url) { //"mongodb://localhost:27017"
//		connectionString = new MongoClientURI(url);
//	}
//	public static void startUp() {
//		mongoClient = new MongoClient(connectionString);
//		database = mongoClient.getDatabase(DatabaseName.Game.name);
//		playerCol = database.getCollection(DatabaseName.Game.roleColName);
//		playerCol.withWriteConcern(WriteConcern.UNACKNOWLEDGED);
//
//		groundAuctionCol = database.getCollection("groundAuction");
//
////		apartmentCol = database.getCollection("apartment");
////		materialFactoryCol = database.getCollection("materialFactory");
////		productingDepartmentCol = database.getCollection("productingDepartment");
////		retailShopCol = database.getCollection("retailShop");
////		laboratoryCol = database.getCollection("laboratory");
////		publicFacilityCol = database.getCollection("publicFacility");
//
//		buildingCol = database.getCollection("building");
//		npcCol = database.getCollection("npc");
//		buildIndex();
//	}
//	private static void buildIndex() {
//		playerCol.createIndex(Indexes.ascending(DatabaseInfo.AccountName));
//	}
//	private static MongoClientURI connectionString;
//	private static MongoClient mongoClient;
//	private static MongoDatabase database;
//	private static MongoCollection<Document> playerCol;
//	private static MongoCollection<Document> groundAuctionCol;
//	private static MongoCollection<Document> npcCol;
//	// buildings
////	private static MongoCollection<Document> apartmentCol;
////	private static MongoCollection<Document> materialFactoryCol;
////	private static MongoCollection<Document> productingDepartmentCol;
////	private static MongoCollection<Document> retailShopCol;
////	private static MongoCollection<Document> laboratoryCol;
////	private static MongoCollection<Document> publicFacilityCol;
//
//	private static MongoCollection<Document> buildingCol;
//	private static LoadingCache<ObjectId, Player> playerCache = CacheBuilder.newBuilder()
//			.weakValues()
//			.build(
//					new CacheLoader<ObjectId, Player>() {
//						public Player load(ObjectId id) {
//							Document _d = playerCol.find(Filters.eq(id)).first();
//							if(_d == null)
//								return null;
//							return new Player(_d);
//						}
//					});
//	public static Player getPlayer(ObjectId id) {
//		return playerCache.getUnchecked(id);
//	}
//
//	public static boolean createPlayer(Player p)
//	{
//		Document bson = p.toBson();
//		bson.append("_id", p.id());
//		bson.append(DatabaseInfo.Name, p.getName());
//		bson.append(DatabaseInfo.AccountName, p.getAccount());
//		bson.append(DatabaseInfo.OfflineTs, 0L);
//		bson.append(DatabaseInfo.OnlineTs, 0L);
//		try {
//			playerCol.insertOne(bson);
//			return true;
//		}
//		catch(MongoException e) {
//			// how to check the error code is duplicated key?
//			return false;
//		}
//	}
//	public static List<RoleBriefInfo> getPlayerInfo(String account) {
//		List<RoleBriefInfo> res = new ArrayList<>();
//		playerCol.find(Filters.eq(DatabaseInfo.AccountName, account)).projection(fields(include(RoleBriefInfo.queryFieldsName()))).forEach((Block<Document>) doc -> {
//			res.consumeReserve(new RoleBriefInfo(doc));
//		});
//		return res;
//	}
//	public static void saveOrUpdate(Player p) {
//		playerCol.updateOne(Filters.eq(p.id()), new Document("$set", p.toBson()));
//	}
//	public static void updatePlayerOfflineTs(ObjectId id, long ts) {
//		playerCol.updateOne(Filters.eq(id), new Document("$set", new Document(DatabaseInfo.OfflineTs, ts)));
//	}
//	public static void updatePlayerOnlineTs(ObjectId id, long ts) {
//		playerCol.updateOne(Filters.eq(id), new Document("$set", new Document(DatabaseInfo.OnlineTs, ts)));
//	}
//    public static Document getGroundAction(int id) {
//		return groundAuctionCol.find(Filters.eq("_id", id)).first();
//    }
//
//	public static void updateGroundAuction(Document _d, int serverId) {
//		groundAuctionCol.updateOne(Filters.eq(serverId), _d);
//	}
//
//	public static Set<Building> readAllBuilding() {
//		Set<Building> res = new HashSet<>();
//		buildingCol.find().forEach((Block<Document>) doc -> {
//			Building b = Building.create(doc);
//			res.consumeReserve(b);
//		});
//		return res;
//	}
////    public static HashMap<ObjectId, Apartment> getAllApartment() {
////		HashMap<ObjectId, Apartment> res = new HashMap<>();
////		apartmentCol.find().forEach((Block<Document>) doc -> {
////			Apartment m = new Apartment(doc);
////			res.put(m.id(), m);
////		});
////		return res;
////    }
////	public static HashMap<ObjectId, PublicFacility> getAllPublicFacility() {
////		HashMap<ObjectId, PublicFacility> res = new HashMap<>();
////		publicFacilityCol.find().forEach((Block<Document>) doc -> {
////			PublicFacility m = new PublicFacility(doc);
////			res.put(m.id(), m);
////		});
////		return res;
////	}
////
////	public static HashMap<ObjectId, ProduceDepartment> getAllProductingDepartment() {
////		HashMap<ObjectId, ProduceDepartment> res = new HashMap<>();
////		productingDepartmentCol.find().forEach((Block<Document>) doc -> {
////			ProduceDepartment m = new ProduceDepartment(doc);
////			res.put(m.id(), m);
////		});
////		return res;
////	}
////
////	public static HashMap<ObjectId, Laboratory> getAllLaboratory() {
////		HashMap<ObjectId, Laboratory> res = new HashMap<>();
////		laboratoryCol.find().forEach((Block<Document>) doc -> {
////			Laboratory m = new Laboratory(doc);
////			res.put(m.id(), m);
////		});
////		return res;
////	}
////
////	public static HashMap<ObjectId, MaterialFactory> getAllMaterialFactory() {
////		HashMap<ObjectId, MaterialFactory> res = new HashMap<>();
////		materialFactoryCol.find().forEach((Block<Document>) doc -> {
////			MaterialFactory m = new MaterialFactory(doc);
////			res.put(m.id(), m);
////		});
////		return res;
////	}
////
////	public static HashMap<ObjectId, RetailShop> getAllRetailShop() {
////		HashMap<ObjectId, RetailShop> res = new HashMap<>();
////		retailShopCol.find().forEach((Block<Document>) doc -> {
////			RetailShop m = new RetailShop(doc);
////			res.put(m.id(), m);
////		});
////		return res;
////	}
//
//	public static void addBuilding(Building b) {
//		buildingCol.insertOne(b.toBson());
//	}
//
//	public static void delBuilding(Building b) {
//		buildingCol.deleteOne(Filters.eq(b.id()));
//	}
//
//
//    public static Set<Npc> readAllNpc() {
//		Set<Npc> res = new HashSet<>();
//		npcCol.find().forEach((Block<Document>) doc -> {
//			Npc npc = new Npc(doc);
//			res.consumeReserve(npc);
//		});
//		return res;
//    }
//
////	public static void paySalary(Player p, Set<Npc> allNpc, int salary) {
////		ArrayList<ObjectId> ids = new ArrayList<>(allNpc.size());
////		allNpc.forEach(npc->ids.consumeReserve(npc.id()));
////		ClientSession s = mongoClient.startSession();
////		s.startTransaction();
////		playerCol.updateOne(Filters.eq(p.id()), Updates.inc(DatabaseInfo.Money, -salary*allNpc.size()));
////		npcCol.updateMany(Filters.in("_id", ids), Updates.inc(DatabaseInfo.Money, salary));
////		s.commitTransaction();
////	}
//
//	public static void offsetNpcMoney(Collection<ObjectId> ids, int delta) {
//		npcCol.updateMany(Filters.in("_id", ids), Updates.inc(DatabaseInfo.Money, delta));
//	}
//
//
//	// must use transaction senario:
//	// 1. money which relative to any unit (this is mean any thing which invole with money include:
//	// 			buy ground, goods, building...
//	//			rent ground, building...
//
//	// only keep in memory is allowed:
//	// 1. where the building which npc located in
//	// 2. ?
//	public static ClientSession startTransaction() {
//		ClientSession s = mongoClient.startSession();
//		s.startTransaction();
//		return s;
//	}
//	public static boolean commit(ClientSession s) {
//		while (true) {
//			try {
//				s.commitTransaction();
//				return true;
//			} catch (MongoException e) {
//				// can retry commit
//				if (e.hasErrorLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL)) {
//					continue;
//				} else {
//					//throw e;
//					s.abortTransaction();
//					logger.fatal("mongodb transaction fail " + e);
//					return false;
//				}
//			}
//		}
//	}
//
//	public static void create(List<Npc> res) {
//		npcCol.insertMany(res.stream().map(Npc::toBson).collect(Collectors.toList()));
//	}
//
//	public static void delNpc(Set<Npc> npc) {
//	}
//}
