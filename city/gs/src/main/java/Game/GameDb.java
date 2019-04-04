package Game;


import Game.Contract.Contract;
import Game.FriendManager.FriendRequest;
import Game.FriendManager.OfflineMessage;
import Game.FriendManager.Society;
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
import javax.persistence.criteria.Root;
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
							org.hibernate.Query q = session.createQuery("SELECT new Game.Player$Info(id,name,companyName,male,des,faceId,createTs) FROM Player where id = :x");
							q.setParameter("x", key);
							List<Player.Info> l = q.list();
							transaction.commit();
							session.close();
							return l.isEmpty()?null:l.get(0);
						}
						@Override
						public Map<UUID, Player.Info> loadAll(Iterable<? extends UUID> keys) {
							StatelessSession session = sessionFactory.openStatelessSession();
							Transaction transaction = session.beginTransaction();
							org.hibernate.Query q = session.createQuery("SELECT new Game.Player$Info(id,name,companyName,male,des,faceId,createTs) FROM Player where id in :x");
							q.setParameter("x", keys);
							List<Player.Info> list = q.list();
							Map<UUID, Player.Info> res = new HashMap<>(list.size());
							list.forEach(i->res.put(i.id, i));
							transaction.commit();
							session.close();
							return res;
						}
					});

	//wxj============================================

	public static List<Contract> getAllContract() {
		Transaction transaction = session.beginTransaction();
		CriteriaBuilder builder = session.getCriteriaBuilder();
		CriteriaQuery<Contract> criteria = builder.createQuery(Contract.class);
		criteria.from(Contract.class);
		List<Contract> res = session.createQuery(criteria).list();
		transaction.commit();
		return res;
	}

	public static List<Society> getAllSociety()
	{
		Session session = sessionFactory.openSession();
		List<Society> list = session.createQuery("from Society",Society.class).list();
		session.close();
		return list;
	}

	public static boolean saveOrUpdSociety(Society society)
	{
		Transaction transaction = null;
		boolean success = false;
		try
		{
			transaction = session.beginTransaction();
			session.saveOrUpdate(society);
			transaction.commit();
			success = true;
		}
		catch (RuntimeException e)
		{
			logger.error(e.getMessage());
			rollBack(transaction);
		}
		return success;
	}
	public static Society getSocietyById(UUID id)
	{
		return session.get(Society.class, id);
	}

	public static void invalidatePlayerInfoCache(UUID id)
	{
		playerInfoCache.invalidate(id);
	}

	public static void statelessInsert(Object o) {
		Transaction transaction = null;
		StatelessSession statelessSession = null;
		try {
			statelessSession = sessionFactory.openStatelessSession();
			transaction = statelessSession.beginTransaction();
			statelessSession.insert(o);
			transaction.commit();
		} catch (RuntimeException e) {
			e.printStackTrace();
			rollBack(transaction);
		} finally {
			closeSession(statelessSession);
		}
	}
	public static List<OfflineMessage> getOfflineMsgAndDel(UUID to_id)
	{
		Transaction transaction = null;
		StatelessSession statelessSession = null;
		List<OfflineMessage> list = new ArrayList<>();
		try
		{
			statelessSession = sessionFactory.openStatelessSession();
			transaction = statelessSession.beginTransaction();
			Criteria criteria = statelessSession.createCriteria(OfflineMessage.class);
			list = criteria.add(Restrictions.eq("to_id", to_id))
					.addOrder(Order.asc("time"))
					.list();
			for (OfflineMessage m : list)
			{
				statelessSession.delete(m);
			}
			transaction.commit();
		}
		catch (RuntimeException e)
		{
			e.printStackTrace();
			rollBack(transaction);
		}
		finally
		{
			if (statelessSession != null)
			{
				statelessSession.close();
			}
		}
		return list;
	}

	public static void deleteFriendRequest(UUID from, UUID to)
	{
		Transaction transaction = null;
        StatelessSession session = sessionFactory.openStatelessSession();
		try
		{
			transaction = session.beginTransaction();
			session.createQuery("DELETE friend_request WHERE from_id=:x AND to_id=:y")
					.setParameter("x", from)
					.setParameter("y", to)
					.executeUpdate();
			transaction.commit();
		}
		catch (RuntimeException e)
		{
			logger.fatal("delete friend request failed");
			e.printStackTrace();
			rollBack(transaction);
		}
		finally
        {
            closeSession(session);
        }
	}

	public static void newSessionSaveOrUpdateAndDelete(Collection saveUpdate, Collection deletes) {
		Transaction transaction = null;
		Session session1 = sessionFactory.openSession();
		try
        {
		    transaction = session1.beginTransaction();
			int i = 0;
			for (Object o : saveUpdate)
			{
				session1.saveOrUpdate(o);
				++i;
				if (i % BATCH_SIZE == 0)
					session1.flush();
			}
			for (Object o : deletes)
			{
				session1.delete(o);
				++i;
				if (i % BATCH_SIZE == 0)
					session1.flush();
			}
			transaction.commit();
		} catch (RuntimeException e) {
			if(transaction != null)
				transaction.rollback();
		} finally {
			if (session1 != null) {
				session1.close();
			}
		}
	}
	public static List<FriendRequest> getFriendRequest(UUID from, UUID to)
	{
		List<FriendRequest> list = new ArrayList<>();
		Session session1 = sessionFactory.openSession();
		try
		{
			if (from == null)
			{
				list = session1.createQuery("FROM friend_request WHERE to_id=:y")
						.setParameter("y", to)
						.list();
			}
			else
			{
				list = session1.createQuery("FROM friend_request WHERE from_id=:x AND to_id=:y")
						.setParameter("x", from)
						.setParameter("y", to)
						.list();
			}
		}
		catch (RuntimeException e)
		{
			logger.fatal("query friend request failed");
			e.printStackTrace();
		}
		finally
		{
			if (session1 != null) {
				session1.close();
			}
		}
		return list;
	}

	public static void deleteFriendWithBlacklist(Player player, UUID fid)
	{
		Transaction transaction = null;
		StatelessSession statelessSession = null;
		UUID pid = player.id();
		if (player.id().compareTo(fid) > 0)
		{
			pid = fid;
			fid = player.id();
		}
		try
		{
			statelessSession = sessionFactory.openStatelessSession();
			transaction = statelessSession.beginTransaction();
			statelessSession.createSQLQuery("DELETE FROM friend WHERE pid=:x AND fid=:y")
					.setParameter("x", pid)
					.setParameter("y", fid)
					.executeUpdate();
			transaction.commit();
			player.getBlacklist().add((player.id().equals(pid) ? fid : pid));
			session.saveOrUpdate(player);
		}
		catch (RuntimeException e)
		{
			rollBack(transaction);
			logger.fatal("deleteFriendWithBlacklist request failed");
			e.printStackTrace();
		}
		finally
		{
			closeSession(statelessSession);
		}

	}

	private static void rollBack(Transaction transaction)
	{
		if (transaction != null) {
			transaction.rollback();
		}
	}
	private static void closeSession(StatelessSession statelessSession)
	{
		if (statelessSession != null) {
			statelessSession.close();
		}
	}
	public static void deleteFriend(UUID pid, UUID fid)
	{
		Transaction transaction = null;
		StatelessSession statelessSession = null;
		if (pid.compareTo(fid) > 0)
		{
			UUID tmp = pid;
			pid = fid;
			fid = tmp;
		}
		try
		{
			statelessSession = sessionFactory.openStatelessSession();
			transaction = statelessSession.beginTransaction();
			statelessSession.createSQLQuery("DELETE FROM friend WHERE pid=:x AND fid=:y")
					.setParameter("x", pid)
					.setParameter("y", fid)
					.executeUpdate();
			transaction.commit();
		}
		catch (RuntimeException e)
		{
			rollBack(transaction);
			logger.fatal("Delete friend failure");
			e.printStackTrace();
		}
		finally
		{
			closeSession(statelessSession);
		}
	}

	public static Set<UUID> queryFriends(UUID pid)
	{
		StatelessSession session = sessionFactory.openStatelessSession();
		List<UUID> resultList= session.createSQLQuery("SELECT fid AS uuid FROM friend WHERE pid=:x UNION SELECT pid AS uuid FROM friend WHERE fid=:x ")
				.addScalar("uuid",StandardBasicTypes.UUID_CHAR)
				.setParameter("x",pid)
		        .getResultList();
		session.close();
		return new HashSet<>(resultList);
	}

	public static List<Player.Info> queryPlayByPartialName(String name)
	{
		StatelessSession session = sessionFactory.openStatelessSession();
		List<Player.Info> list  = session.createQuery("SELECT new Game.Player$Info(id,name,companyName,male,des,faceId,createTs) From Player WHERE name LIKE :x")
				.setParameter("x", name + "%")
				.list();
		session.close();
		return list;
	}
	public static void addFriend(UUID pid,UUID fid)
	{
		Transaction transaction = null;
		StatelessSession statelessSession = null;
		if (pid.compareTo(fid) > 0)
		{
			UUID tmp = pid;
			pid = fid;
			fid = tmp;
		}
		try
		{
			statelessSession = sessionFactory.openStatelessSession();
			transaction = statelessSession.beginTransaction();
			statelessSession.createSQLQuery("INSERT INTO friend (pid,fid) values (:x,:y)")
					.setParameter("x", pid)
					.setParameter("y", fid)
					.executeUpdate();
			transaction.commit();
		}
		catch (RuntimeException e)
		{
			rollBack(transaction);
			logger.fatal("Save friend failure");
			e.printStackTrace();
		}
		finally
		{
			closeSession(statelessSession);
		}
	}

	public static boolean createFriendTable()
	{
		boolean success = false;
		Transaction transaction = null;
		StatelessSession statelessSession = null;
		try
		{
			statelessSession = sessionFactory.openStatelessSession();
			transaction = statelessSession.beginTransaction();
			statelessSession.createSQLQuery("CREATE TABLE  IF NOT EXISTS friend (pid uuid not null,fid uuid not null,primary key(pid,fid))")
					.executeUpdate();
			transaction.commit();
			success = true;
		}catch (RuntimeException e) {
			rollBack(transaction);
			e.printStackTrace();
		}
		finally
		{
			closeSession(statelessSession);
		}
		return success;
	}
	//===============================================

	public static List<Talent> getTalent(Collection<UUID> talentIds) {
		List<Talent> res = new ArrayList<>();
		Transaction transaction = null;
		try {
			transaction = session.beginTransaction();
			CriteriaBuilder builder = session.getCriteriaBuilder();
			CriteriaQuery<Talent> criteria = builder.createQuery(Talent.class);
			Root<Talent> root = criteria.from(Talent.class);
			root.in(talentIds);			// this is no fucking difference with call session.get in loop, still generate multiple SQL
			res = session.createQuery(criteria).list();
			transaction.commit();
		} catch (RuntimeException e) {
			e.printStackTrace();
			if(transaction != null)
				transaction.rollback();
		} finally {
		}
		return res;
	}
	public static Talent getTalent(UUID id) {
		Talent res = null;
		Transaction transaction = null;
		try {
			transaction = session.beginTransaction();
			res = session.get(Talent.class, id);
			transaction.commit();
		} catch (RuntimeException e) {
			e.printStackTrace();
			if(transaction != null)
				transaction.rollback();
		} finally {
		}
		return res;
	}
	public static Collection<Talent> getTalentByBuildingId(UUID id) {
		Collection<Talent> res = null;
		Transaction transaction = null;
		try {
			transaction = session.beginTransaction();
			CriteriaBuilder builder = session.getCriteriaBuilder();
			CriteriaQuery<Talent> criteria = builder.createQuery(Talent.class);
			Root<Talent> root = criteria.from(Talent.class);
			criteria.select(root).where(builder.equal(root.get("buildingId"), id)); // hibernate will not return different object if those object already queried by this session
			res = session.createQuery(criteria).list();
			transaction.commit();
		} catch (RuntimeException e) {
			e.printStackTrace();
			if(transaction != null)
				transaction.rollback();
		} finally {
		}
		return res;
	}
	public static Collection<Talent> getTalentByPlayerId(UUID id) {
		Collection<Talent> res = null;
		Transaction transaction = null;
		try {
			transaction = session.beginTransaction();
			CriteriaBuilder builder = session.getCriteriaBuilder();
			CriteriaQuery<Talent> criteria = builder.createQuery(Talent.class);
			Root<Talent> root = criteria.from(Talent.class);
			criteria.select(root).where(builder.equal(root.get("ownerId"), id)); // hibernate will not return different object if those object already queried by this session
			res = session.createQuery(criteria).list();
			transaction.commit();
		} catch (RuntimeException e) {
			e.printStackTrace();
			if(transaction != null)
				transaction.rollback();
		} finally {
		}
		return res;
	}

	public static void evict(Player player) {
		playerCache.invalidate(player.id());
		session.evict(player);
	}

	public static void evict(Object obj)
	{
		if (obj != null) {
			session.evict(obj);
		}
	}
	public static Player getPlayer(UUID id) {
		tmpPlayerCache.invalidate(id);
		return playerCache.getUnchecked(id);
	}
	public static Player queryPlayer(UUID id) {
		Player res = playerCache.getIfPresent(id);
		if(res == null) {
			res = tmpPlayerCache.getUnchecked(id);
		}
		return res;
	}
	public static List<Player.Info> getPlayerInfo(Collection<UUID> ids) throws ExecutionException {
		ImmutableMap<UUID, Player.Info> map = playerInfoCache.getAll(ids);
		return map.values().asList();
	}

	public static Player.Info getPlayerInfo(UUID uuid)
	{
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
		try
		{
			transaction = session.beginTransaction();
			session.save(p);
			transaction.commit();
			success = true;
		}
		catch (RuntimeException e)
		{ // the exception is complex, may be javax.PersistenceException, or HibernateException, or jdbc exception ...
			e.printStackTrace();
			rollBack(transaction);
		}
		return success;
	}
	public static void initMoneyPool() {
		StatelessSession statelessSession = sessionFactory.openStatelessSession();
		Transaction transaction = statelessSession.beginTransaction();
		if(statelessSession.get(MoneyPool.class, MoneyPool.ID) == null)
			statelessSession.insert(new MoneyPool());
		transaction.commit();
		statelessSession.close();
	}
	public static void initGroundAuction() {
		StatelessSession statelessSession = sessionFactory.openStatelessSession();
		Transaction transaction = statelessSession.beginTransaction();
		if(statelessSession.get(GroundAuction.class, GroundAuction.ID) == null)
			statelessSession.insert(new GroundAuction());
		transaction.commit();
		statelessSession.close();
	}
	public static void initGroundManager() {
		StatelessSession statelessSession = sessionFactory.openStatelessSession();
		Transaction transaction = statelessSession.beginTransaction();
		if(statelessSession.get(GroundManager.class, GroundManager.ID) == null)
			statelessSession.insert(new GroundManager());
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
	public static void initTechTradeCenter() {
		StatelessSession statelessSession = sessionFactory.openStatelessSession();
		Transaction transaction = statelessSession.beginTransaction();
		if(statelessSession.get(TechTradeCenter.class, TechTradeCenter.ID) == null)
			statelessSession.insert(new TechTradeCenter());
		transaction.commit();
		statelessSession.close();
	}
	public static void initBrandManager() {
		StatelessSession statelessSession = sessionFactory.openStatelessSession();
		Transaction transaction = statelessSession.beginTransaction();
		if(statelessSession.get(BrandManager.class, BrandManager.ID) == null)
			statelessSession.insert(new BrandManager());
		transaction.commit();
		statelessSession.close();
	}
	public static MoneyPool getMoneyPool() {
		Transaction transaction = session.beginTransaction();
		MoneyPool res = session.get(MoneyPool.class, MoneyPool.ID);
		transaction.commit();
		return res;
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
			if(transaction != null)
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
			if(transaction != null)
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
		}
		catch(RuntimeException e) {
			if(transaction != null)
				transaction.rollback();
		}
		finally {

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
			e.printStackTrace();
			if(transaction != null)
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
			if(transaction != null)
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
		criteria.list().forEach(l -> builder.addLog(((Exchange.DealLog)l).toProto()));
		transaction.commit();
		session.close();
		return builder.build();
	}

	//llb=================================
	public static Collection<Mail> getMail(UUID playerId) {
		Collection<Mail> res = new ArrayList<>();
		Session session = sessionFactory.openSession();
		Transaction transaction = session.beginTransaction();
		org.hibernate.Query query = session.createQuery(" FROM Mail where playerId = :x ");
		query.setParameter("x", playerId);
		List<Mail> mails = query.list();
		if (null != mails && mails.size() != 0) {
			mails.forEach(mail -> res.add(mail));
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
            session.createSQLQuery("DELETE FROM Mail WHERE Id= :x and read = :y")
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

    public static void delOverdueMail() {
        Transaction transaction = null;
        StatelessSession session = null;
        try {
            session = sessionFactory.openStatelessSession();
            transaction = session.beginTransaction();

            long now = System.currentTimeMillis();
            long diffTs = now - TimeUnit.HOURS.toMillis(7 * 24);
            session.createSQLQuery("DELETE FROM Mail WHERE ts < :x")
                    .setParameter("x", diffTs)
                    .executeUpdate();
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
            session.createSQLQuery("update Mail SET read = TRUE WHERE id = :x")
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

	//llb=================================

	protected static final class TopGoodQty {
		public TopGoodQty() {}

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

		list.forEach(o->res.put(o.mid, o.goodlv));
		return res;
	}
	
	public static List<Eva> getEvaInfoByPlayId(UUID pid)
	{
		List<Eva> list = new ArrayList<Eva>();
		Session session = sessionFactory.openSession();
		try
		{
			list = session.createQuery("FROM Eva WHERE pid=:pid",Eva.class)
					.setParameter("pid", pid)
					.list();
		}
		catch (RuntimeException e)
		{
			logger.fatal("query player eva info failed");
			e.printStackTrace();
		}
		finally
		{
			if (session != null) {
				session.close();
			}
		}
		return list;
	}
}

