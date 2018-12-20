package Game;


import Game.FriendManager.FriendRequest;
import Game.FriendManager.OfflineMessage;
import Shared.RoleBriefInfo;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import gs.Gs;
import org.apache.log4j.Logger;
import org.hibernate.*;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.hibernate.query.NativeQuery;
import org.hibernate.query.Query;
import org.hibernate.transform.Transformers;
import org.hibernate.type.IntegerType;
import org.hibernate.type.StandardBasicTypes;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import java.io.File;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;


// there are 2 ways:
// 1. use only one session, so all object state will not be detached. It will reduce re-query db times when object re-connect with session
// however, the problem is, we can not control the behavior of session level cache like weak reference. And, if get player who is other one
// coder can not forget to evict it from session, this is tough issue. But the good news is this way can enable lazy load
// 2. use session as normal way. but the problem is: there is no such standard procedure for this game. there is no conversation so called in
// http manner. so the session will closed right after we done the operation of database rather than the business logic. that will cause object
// be state of detached in most of time. when it needs update, it will have a transient duration of persist state. this will making hibernate
// do additional select

// if server reach the max npc amount, its about 400 update/sec, if each npc trigger one db update, due to SQL have not non-acknowledge mode
// the db operation is time consuming(5-10 ms, common pc, LAN), the all 400 update will consume 2-4 seconds to complete, it over limit the update frequency
// even you could do those db update in dedicated thread, that will not blocking the game logic update, but the queue in that thread will
// bigger and bigger. and there is no way to solve this except buy an high performance SQL machine. If we get this machine, then sync db
// update will not be the problem anymore
public class GameDb {
    private static final Logger logger = Logger.getLogger(GameDb.class);
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

    private static LoadingCache<UUID, Player> playerCache = CacheBuilder.newBuilder()
            .concurrencyLevel(1)
            .weakValues()
            .build(
                    new CacheLoader<UUID, Player>() {
                        public Player load(UUID id) {
                            Transaction transaction = session.beginTransaction();
                            Player res = session.get(Player.class, id);
                            transaction.commit();
                            return res;
                        }
                    });
    private static LoadingCache<UUID, Player> tmpPlayerCache = CacheBuilder.newBuilder()
            .concurrencyLevel(1)
            .expireAfterWrite(Duration.ofMinutes(2))
            .maximumSize(2560)
            .build(
                    new CacheLoader<UUID, Player>() {
                        public Player load(UUID id) {
                            StatelessSession s = sessionFactory.openStatelessSession();
                            Transaction transaction = s.beginTransaction();
                            Player res = (Player) s.get(Player.class, id);
                            transaction.commit();
                            s.close();
                            res.markTemp();
                            return res;
                        }
                    });
    private static LoadingCache<UUID, Player.Info> playerInfoCache = CacheBuilder.newBuilder()
            .concurrencyLevel(1)
            .maximumSize(10240)
            .build(
                    new CacheLoader<UUID, Player.Info>() {
                        @Override
                        public Player.Info load(UUID key) {
                            StatelessSession session = sessionFactory.openStatelessSession();
                            Transaction transaction = session.beginTransaction();
                            org.hibernate.Query q = session.createQuery("SELECT new Game.Player$Info(id ,name) FROM Player where id = :x");
                            q.setParameter("x", key);
                            List<Player.Info> l = q.list();
                            transaction.commit();
                            session.close();
                            return l.isEmpty() ? null : l.get(0);
                        }

                        @Override
                        public Map<UUID, Player.Info> loadAll(Iterable<? extends UUID> keys) {
                            StatelessSession session = sessionFactory.openStatelessSession();
                            Transaction transaction = session.beginTransaction();
                            org.hibernate.Query q = session.createQuery("SELECT new Game.Player$Info(id ,name) FROM Player where id in :x");
                            q.setParameter("x", keys);
                            List<Player.Info> list = q.list();
                            Map<UUID, Player.Info> res = new HashMap<>(list.size());
                            list.forEach(i -> res.put(i.id, i));
                            transaction.commit();
                            session.close();
                            return res;
                        }
                    });

    //wxj============================================
    public static List<OfflineMessage> getOfflineMsg(UUID to_id) {
        StatelessSession session = sessionFactory.openStatelessSession();
        Criteria criteria = session.createCriteria(OfflineMessage.class);
        List<OfflineMessage> list = criteria.add(Restrictions.eq("to_id", to_id))
                .addOrder(Order.asc("time"))
                .list();
        session.close();
        return list;
    }

    public static void deleteFriendRequest(UUID from, UUID to) {
        Transaction transaction = null;
        try {
            transaction = session.beginTransaction();
            session.createQuery("DELETE friend_request WHERE from_id=:x AND to_id=:y")
                    .setParameter("x", from)
                    .setParameter("y", to)
                    .executeUpdate();
            transaction.commit();
        } catch (RuntimeException e) {
            logger.fatal("delete friend request failed");
            e.printStackTrace();
        }
    }

    public static List<FriendRequest> getFriendRequest(UUID from, UUID to) {
        List<FriendRequest> list = new ArrayList<>();
        try {
            if (from == null) {
                list = session.createQuery("FROM friend_request WHERE to_id=:y")
                        .setParameter("y", to)
                        .list();
            } else {
                list = session.createQuery("FROM friend_request WHERE from_id=:x AND to_id=:y")
                        .setParameter("x", from)
                        .setParameter("y", to)
                        .list();
            }
        } catch (RuntimeException e) {
            logger.fatal("query friend request failed");
            e.printStackTrace();
        }
        return list;
    }

    public static void deleteFriend(UUID pid, UUID fid) {
        Transaction transaction = null;
        StatelessSession session = null;
        exchangeUUID(pid, fid);
        try {
            session = sessionFactory.openStatelessSession();
            transaction = session.beginTransaction();
            session.createSQLQuery("DELETE FROM friend WHERE pid=:x AND fid=:y")
                    .setParameter("x", pid)
                    .setParameter("y", fid)
                    .executeUpdate();
            transaction.commit();
        } catch (RuntimeException e) {
            transaction.rollback();
            logger.fatal("Delete friend failure");
            e.printStackTrace();
        } finally {
            if (session != null) session.close();
        }
    }

    public static Set<UUID> queryFriends(UUID pid) {
        StatelessSession session = sessionFactory.openStatelessSession();
        List<UUID> resultList = session.createSQLQuery("SELECT fid AS uuid FROM friend WHERE pid=:x UNION SELECT pid AS uuid FROM friend WHERE fid=:x ")
                .addScalar("uuid", StandardBasicTypes.UUID_CHAR)
                .setParameter("x", pid)
                .getResultList();
        session.close();
        return new HashSet<>(resultList);
    }

    public static List<Player.Info> queryPlayByPartialName(String name) {
        StatelessSession session = sessionFactory.openStatelessSession();
        List<Player.Info> list = session.createQuery("SELECT new Game.Player$Info(id,name) From Player WHERE name LIKE :x")
                .setParameter("x", name + "%")
                .list();
        session.close();
        return list;
    }

    private static void exchangeUUID(UUID pid, UUID fid) {
        if (pid.compareTo(fid) > 0) {
            UUID tmp = pid;
            pid = fid;
            fid = tmp;
        }
    }

    public static void addFriend(UUID pid, UUID fid) {
        Transaction transaction = null;
        exchangeUUID(pid, fid);
        try {
            transaction = session.beginTransaction();
            session.createSQLQuery("INSERT INTO friend (pid,fid) values (:x,:y)")
                    .setParameter("x", pid)
                    .setParameter("y", fid)
                    .executeUpdate();
            transaction.commit();
        } catch (RuntimeException e) {
            transaction.rollback();
            logger.fatal("Save friend failure");
            e.printStackTrace();
        }
    }

    public static boolean createFriendTable() {
        boolean success = false;
        Transaction transaction = null;
        try {
            transaction = session.beginTransaction();
            session.createSQLQuery("CREATE TABLE  IF NOT EXISTS friend (pid uuid not null,fid uuid not null,primary key(pid,fid))")
                    .executeUpdate();
            transaction.commit();
            success = true;
        } catch (RuntimeException e) {
            transaction.rollback();
            e.printStackTrace();
        }
        return success;
    }

    //===============================================
    public static void evict(Player player) {
        playerCache.invalidate(player.id());
        session.evict(player);
    }

    public static void evict(Object obj) {
        session.evict(obj);
    }

    public static Player getPlayer(UUID id) {
        tmpPlayerCache.invalidate(id);
        return playerCache.getUnchecked(id);
    }

    public static Player queryPlayer(UUID id) {
        Player res = playerCache.getIfPresent(id);
        if (res == null) {
            res = tmpPlayerCache.getUnchecked(id);
        }
        return res;
    }

    public static List<Player.Info> getPlayerInfo(Collection<UUID> ids) throws ExecutionException {
        ImmutableMap<UUID, Player.Info> map = playerInfoCache.getAll(ids);
        return map.values().asList();
    }

    public static Player.Info getPlayerInfo(UUID uuid) {
        return playerInfoCache.getUnchecked(uuid);
    }

    public static List<RoleBriefInfo> getPlayerInfo(String account) {
        StatelessSession session = sessionFactory.openStatelessSession();
        Transaction transaction = session.beginTransaction();
        Criteria q = session.createCriteria(Player.class)
                .setProjection(Projections.projectionList()
                        .add(Projections.property("name"), "name")
                        .add(Projections.property("onlineTs"), "lastLoginTs")
                        .add(Projections.property("id"), "id"))
                .setResultTransformer(Transformers.aliasToBean(RoleBriefInfo.class)).add(Restrictions.eq("account", account));
        // 	q.setParameter("x", account);
        List<RoleBriefInfo> l = q.list();
        transaction.commit();
        session.close();
        return l;
    }

    public static boolean createPlayer(Player p) {
        boolean success = false;
        Transaction transaction = null;
        try {
            transaction = session.beginTransaction();
            session.save(p);
            transaction.commit();
            success = true;
        } catch (RuntimeException e) { // the exception is complex, may be javax.PersistenceException, or HibernateException, or jdbc exception ...
            transaction.rollback();
            e.printStackTrace();
        }
        return success;
    }

    public static void initGroundAuction() {
        StatelessSession statelessSession = sessionFactory.openStatelessSession();
        Transaction transaction = statelessSession.beginTransaction();
        if (statelessSession.get(GroundAuction.class, GroundAuction.ID) == null)
            statelessSession.insert(new GroundAuction());
        transaction.commit();
        statelessSession.close();
    }

    public static void initGroundManager() {
        StatelessSession statelessSession = sessionFactory.openStatelessSession();
        Transaction transaction = statelessSession.beginTransaction();
        if (statelessSession.get(GroundManager.class, GroundManager.ID) == null)
            statelessSession.insert(new GroundManager());
        transaction.commit();
        statelessSession.close();
    }

    public static void initExchange() {
        StatelessSession statelessSession = sessionFactory.openStatelessSession();
        Transaction transaction = statelessSession.beginTransaction();
        if (statelessSession.get(Exchange.class, Exchange.ID) == null)
            statelessSession.insert(new Exchange());
        transaction.commit();
        statelessSession.close();
    }

    public static void initTechTradeCenter() {
        StatelessSession statelessSession = sessionFactory.openStatelessSession();
        Transaction transaction = statelessSession.beginTransaction();
        if (statelessSession.get(TechTradeCenter.class, TechTradeCenter.ID) == null)
            statelessSession.insert(new TechTradeCenter());
        transaction.commit();
        statelessSession.close();
    }

    public static void initBrandManager() {
        StatelessSession statelessSession = sessionFactory.openStatelessSession();
        Transaction transaction = statelessSession.beginTransaction();
        if (statelessSession.get(BrandManager.class, BrandManager.ID) == null)
            statelessSession.insert(new BrandManager());
        transaction.commit();
        statelessSession.close();
    }

    public static BrandManager getBrandManager() {
        Transaction transaction = session.beginTransaction();
        BrandManager res = session.get(BrandManager.class, BrandManager.ID);
        transaction.commit();
        return res;
    }

    public static TechTradeCenter getTechTradeCenter() {
        Transaction transaction = session.beginTransaction();
        TechTradeCenter res = session.get(TechTradeCenter.class, TechTradeCenter.ID);
        transaction.commit();
        return res;
    }

    public static GroundManager getGroundManager() {
        Transaction transaction = session.beginTransaction();
        GroundManager res = session.get(GroundManager.class, GroundManager.ID);
        transaction.commit();
        return res;
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
        Transaction transaction = null;
        try {
            transaction = session.beginTransaction();
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
            if (transaction != null)
                transaction.rollback();
        } finally {
        }
    }

    public static void saveOrUpdate(Object o) {
        Transaction transaction = null;
        try {
            transaction = session.beginTransaction();
            session.saveOrUpdate(o);
            transaction.commit();
        } catch (RuntimeException e) {
            e.printStackTrace();
            if (transaction != null)
                transaction.rollback();
        } finally {

        }
    }

    public static void delete(Object o) {
        Transaction transaction = null;
        try {
            transaction = session.beginTransaction();
            session.delete(o);
            transaction.commit();
        } catch (RuntimeException e) {
            if (transaction != null)
                transaction.rollback();
        } finally {

        }
    }

    public static void delete(Collection objs) {
        Transaction transaction = null;
        try {
            transaction = session.beginTransaction();
            int i = 0;
            for (Object o : objs) {
                session.delete(o);
                ++i;
                if (i % BATCH_SIZE == 0) {
                    session.flush();
                }
            }
            transaction.commit();
        } catch (RuntimeException e) {
            if (transaction != null)
                transaction.rollback();
        } finally {

        }
    }

    public static void saveOrUpdateAndDelete(Collection saveOrUpdates, Collection deletes) {
        Transaction transaction = null;
        try {
            transaction = session.beginTransaction();
            int i = 0;
            for (Object o : saveOrUpdates) {
                session.saveOrUpdate(o);
                ++i;
                if (i % BATCH_SIZE == 0)
                    session.flush();
            }
            for (Object o : deletes) {
                session.delete(o);
                ++i;
                if (i % BATCH_SIZE == 0)
                    session.flush();
            }

            transaction.commit();
        } catch (RuntimeException e) {
            if (transaction != null)
                transaction.rollback();
        } finally {
        }
    }

    public static List<Exchange.DealLog> getDealLogs(int days) {
        long ts = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days);
        StatelessSession session = sessionFactory.openStatelessSession();
        Transaction transaction = session.beginTransaction();
        Criteria criteria = session.createCriteria(Exchange.DealLog.class);
        criteria.add(Restrictions.ge("ts", ts)); // jpa meta model can not generate inner class, shit
        List<Exchange.DealLog> logs = criteria.list();
        transaction.commit();
        session.close();
        return logs;
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
        criteria.list().forEach(l -> builder.addLog(((Exchange.DealLog) l).toProto()));
        transaction.commit();
        session.close();
        return builder.build();
    }

    public static Collection<Mail> getMail(UUID playerId) {
        Collection<Mail> res = new ArrayList<>();
        StatelessSession session = sessionFactory.openStatelessSession();
        Transaction transaction = session.beginTransaction();
        org.hibernate.Query query = session.createQuery("FROM Mail where playerId = :x ");
        query.setParameter("x", playerId);
        List mails = query.list();
        if (null != mails && mails.size() != 0) {
            mails.forEach(mail -> res.add((Mail) mail));
        }
        transaction.commit();
        session.close();
        return res;
    }

    public static void delMail(UUID mailId) {
        Transaction transaction = null;
        StatelessSession session = null;
        try {
            session = sessionFactory.openStatelessSession();
            transaction = session.beginTransaction();
            session.createSQLQuery("DELETE FROM mail WHERE Id= :x and read = :y")
                    .setParameter("x", mailId)
                    .setParameter("y", true)
                    .executeUpdate();
            transaction.commit();
        } catch (RuntimeException e) {
            transaction.rollback();
            logger.fatal("Delete mail failure");
            e.printStackTrace();
        } finally {
            if (session != null) {
                session.close();
            }
        }
    }

    public static void deloverdueMail() {
        Transaction transaction = null;
        StatelessSession session = null;

        try {
            session = sessionFactory.openStatelessSession();
            transaction = session.beginTransaction();

            long now = System.currentTimeMillis();
            String sqlSelect = "select ts from mail";
            List ts = session.createSQLQuery(sqlSelect).list();
            if (null != ts && ts.size() != 0) {
                Long t = Long.valueOf(ts.get(0).toString());
                if (t < (now - (7 * 24 * 60 * 60 * 1000))) {
                    session.createQuery("delete from Mail where ts = :x")
                            .setParameter("x", t)
                            .executeUpdate();
                }
            }
            transaction.commit();
        } catch (RuntimeException e) {
            transaction.rollback();
            logger.fatal("Delete overdueMail failure");
            e.printStackTrace();
        } finally {
            if (session != null) {
                session.close();
            }
        }
    }

    public static void mailChangeRead(UUID mailId) {
        Transaction transaction = null;
        StatelessSession session = null;
        try {
            session = sessionFactory.openStatelessSession();
            transaction = session.beginTransaction();
            session.createSQLQuery("update mail SET read = TRUE WHERE id = :x")
                    .setParameter("x", mailId)
                    .executeUpdate();
            transaction.commit();
        } catch (RuntimeException e) {
            transaction.rollback();
            logger.fatal("mailChangeRead failure");
            e.printStackTrace();
        } finally {
            if (session != null) {
                session.close();
            }
        }
    }


    protected static final class TopGoodQty {
        public TopGoodQty() {
        }

        int mid;
        int goodlv;
    }

    public static TreeMap<Integer, Integer> getTopGoodQuality() {
        TreeMap<Integer, Integer> res = new TreeMap<>();
        StatelessSession session = sessionFactory.openStatelessSession();
        Transaction transaction = session.beginTransaction();
        NativeQuery<TopGoodQty> criteria = session.createNativeQuery("SELECT tt.* FROM player_good_lv tt INNER JOIN (SELECT mid, MAX(goodlv) AS TopLv FROM player_good_lv GROUP BY mid) groupedtt ON tt.mid = groupedtt.mid AND tt.goodlv = groupedtt.TopLv");
        criteria.addScalar("mid", new IntegerType());
        criteria.addScalar("goodlv", new IntegerType());
        criteria.setResultTransformer(Transformers.aliasToBean(TopGoodQty.class));
        List<TopGoodQty> list = criteria.list();
        transaction.commit();
        session.close();

        list.forEach(o -> res.put(o.mid, o.goodlv));
        return res;
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
