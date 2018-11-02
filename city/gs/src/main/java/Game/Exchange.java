package Game;

import Game.Timers.PeriodicTimer;
import Shared.Package;
import Shared.Util;
import gs.Gs;
import gscode.GsCode;
import io.netty.channel.ChannelId;
import org.hibernate.annotations.Cascade;

import javax.persistence.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Entity(name = "exchange")
public class Exchange implements ISessionCache {
    private static Exchange instance;
    public static Exchange instance() {
        return instance;
    }
    public static final int ID = 0;

    protected Exchange() {
        //buildOrderCache();
    }
    public static void init() {
        GameDb.initExchange();
        instance = GameDb.getExchange();
        //instance.buildOrderCache();
    }

    @Id
    public final int id = ID;
    public Gs.ExchangeItemList getItemList() {
        Gs.ExchangeItemList.Builder builder = Gs.ExchangeItemList.newBuilder();
        itemStat.forEach((itemId, info)->{
            builder.addSummary(Gs.ExchangeItemSummary.newBuilder()
                    .setItemId(itemId)
                    .setNowPrice(info.nowPrice())
                    .setLowPrice(info.lowPrice)
                    .setHighPrice(info.highPrice)
                    .setSumDealedPrice(info.sumDealedPrice)
                    .setPriceChange(info.priceChange())
            );
        });
        return builder.build();
    }

    public Gs.ExchangeOrders getOrder(UUID id) {
        Gs.ExchangeOrders.Builder builder = Gs.ExchangeOrders.newBuilder();
        Map<UUID, Order> o = orders.get(id);
        if(o != null) {
            o.forEach((k,v)->
                builder.addOrder(Gs.ExchangeOrders.Order.newBuilder()
                        .setId(Util.toByteString(v.id))
                        .setItemId(v.itemId)
                        .setDealedAmount(v.total-v.n)
                        .setTotalAmount(v.total)
                        .setPrice(v.price)
                        .setTs(v.ts)
                        .setSell(v.sell)
                        .setDealedPrice(v.dealedPrice)
                        .setBuildingId(Util.toByteString(v.buildingId))
                )
            );
        }
        return builder.build();
    }

    public Gs.ItemDealHistory getItemDealHistory(int itemId) {
        Gs.ItemDealHistory.Builder builder = Gs.ItemDealHistory.newBuilder();
        Stat stat = this.itemStat.get(itemId);
        if(stat != null) {
            stat.histories.forEach(log->
                builder.addLog(Gs.ItemDealHistoryElement.newBuilder()
                        .setAmount(log.n)
                        .setPrice(log.price)
                        .setTs(log.ts)
                )
            );
        }
        return builder.build();
    }

    @Override
    public CacheType getCacheType() {
        return CacheType.LongLiving;
    }

    @Entity
    @Table(name = "exchange_order")
    final static class Order {
        @Id
        @Column(name = "id", nullable = false)
        UUID id = UUID.randomUUID();
        @Column(name = "playerId", nullable = false)
        UUID playerId;
        @Column(name = "buildingId", nullable = false)
        UUID buildingId;
        @Column(nullable = false)
        int price;
        @Column(nullable = false)
        long ts;
        @Column(nullable = false)
        int itemId;
        @Column(nullable = false)
        int n;
        @Column(nullable = false)
        boolean sell;
        @Column(nullable = false)
        int total;
        @Column(nullable = false)
        int dealedPrice;
        public Order(UUID playerId, int price, int itemId, int n, UUID buildingId, boolean sell) {
            this.playerId = playerId;
            this.buildingId = buildingId;
            this.price = price;
            this.itemId = itemId;
            this.n = n;
            this.total = n;
            this.sell = sell;
            this.ts = System.currentTimeMillis();
            this.dealedPrice = 0;
        }

        public Order() {
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Order order = (Order) o;
            return Objects.equals(id, order.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }


    protected static final class Trading { // private will cause JPA meta class generate fail
        TreeSet<Order> buy = new TreeSet<>(Comparator.comparingInt(o -> o.price));
        TreeSet<Order> sell = new TreeSet<>(Comparator.comparingInt(o -> o.price));
    }
    public UUID addSellOrder(UUID who, int itemId, int price, int n, UUID buildingId) {
        Order order = new Order(who, price, itemId, n, buildingId,true);
        tradings.computeIfAbsent(itemId, k->new Trading()).sell.add(order);
        orders.computeIfAbsent(who, k->new HashMap<>()).put(order.id, order);
        _orderData.add(order);
        this.watcher.sendTo(itemId);
        return order.id;
    }
    public UUID addBuyOrder(UUID who, int itemId, int price, int n, UUID buildingId) {
        Order order = new Order(who, price, itemId, n, buildingId,false);
        tradings.computeIfAbsent(itemId, k->new Trading()).buy.add(order);
        orders.computeIfAbsent(who, k->new HashMap<>()).put(order.id, order);
        _orderData.add(order);
        this.watcher.sendTo(itemId);
        return order.id;
    }
    public UUID cancelOrder(UUID who, UUID orderId) {
        Map<UUID, Order> recordingMap = orders.get(who);
        if(recordingMap == null)
            return null;
        Order o = recordingMap.get(orderId);
        if(o == null)
            return null;
        Trading trading = tradings.get(o.itemId);
        if(o.sell)
            trading.sell.remove(o);
        else
            trading.buy.remove(o);
        recordingMap.remove(orderId);
        _orderData.remove(o);
        this.watcher.sendTo(o.itemId);
        return o.buildingId;
    }
//    private void buildOrderCache() {
//        tradings.forEach((k,v)-> {
//            v.sell.forEach(o -> orders.computeIfAbsent(o.playerId, kk->new HashMap<>()).put(o.id, o));
//            v.buy.forEach(o -> orders.computeIfAbsent(o.playerId, kk->new HashMap<>()).put(o.id, o));
//        });
//    }

    private void matchmaking() {
        tradings.forEach((k,v)->{
            if(!v.sell.isEmpty() && !v.buy.isEmpty()) {
                // there is no time order because in one match we do all match which can do.
                // so orders committed in one update frame can be view as same time
                Order s = v.sell.first();
                Order b = v.buy.last();

                while(s != null && b != null && s.price <= b.price) {
                    makeDeal(b, s);
                    if(s.n == 0) {
                        v.sell.remove(s);
                        if(v.sell.isEmpty())
                            s = null;
                        else
                            s = v.sell.first();
                    }
                    if(b.n == 0) {
                        v.buy.remove(b);
                        if(v.buy.isEmpty())
                            b = null;
                        else
                            b = v.buy.first();
                    }
                }
            }
        });
    }

    private void makeDeal(Order b, Order s) {
        assert b.itemId == s.itemId;
        MetaItem mi = MetaData.getItem(s.itemId);
        if(mi == null)
            return;
        Player seller = GameDb.queryPlayer(s.playerId);
        Player buyer = GameDb.queryPlayer(b.playerId);

        int n = 0;
        if(b.n <= s.n) {
            n = b.n;
            s.n -= b.n;
        }
        else {
            n = s.n;
            b.n -= s.n;
        }
        long cost = n*s.price;
        seller.addMoney(cost);
        buyer.spentLockMoney(b.id);
        b.dealedPrice += cost;
        s.dealedPrice += cost;

        IStorage out = IStorage.get(s.buildingId, seller);
        if(out == null)
            return;
        IStorage in = IStorage.get(b.buildingId, buyer);
        if(in == null)
            return;

        out.clearOrder(s.id);
        out.consumeLock(mi, n);

        in.clearOrder(b.id);
        in.consumeReserve(mi, n);

        DealLog log = new DealLog(b.playerId, s.playerId, mi.id, n, s.price);
        this.itemStat.computeIfAbsent(mi.id, k->new Stat(mi.id)).histories.add(log);
        this._dealHistoryData.add(log);
        Collection<ISessionCache> updates = Arrays.asList(buyer, seller, (ISessionCache)in, (ISessionCache)out, log, this);
        GameDb.saveOrUpdate(updates);

        // send notify to client if they are online
        seller.send(Package.create(GsCode.OpCode.exchangeDealInform_VALUE, Gs.ExchangeDeal.newBuilder()
                .setItemId(mi.id)
                .setNum(n)
                .setPrice(s.price)
                .setBuildingId(Util.toByteString(s.buildingId))
                .build()));
        buyer.send(Package.create(GsCode.OpCode.exchangeDealInform_VALUE, Gs.ExchangeDeal.newBuilder()
                .setItemId(mi.id)
                .setNum(n)
                .setPrice(s.price)
                .setBuildingId(Util.toByteString(b.buildingId))
                .build()));
        this.watcher.sendTo(mi.id);
    }
    @Entity
    @Table(name = "exchange_deal_log", indexes = {
            @Index(name = "SELLER_IDX", columnList = "seller"),
            @Index(name = "BUYER_IDX", columnList = "buyer")
    })
    public final static class DealLog implements ISessionCache {
        public static final int ROWS_IN_ONE_PAGE = 10;
        public DealLog(UUID buyer, UUID seller, int itemId, int n, int price) {
            this.buyer = buyer;
            this.seller = seller;
            this.itemId = itemId;
            this.n = n;
            this.price = price;
            ts = System.currentTimeMillis();
        }
        @Id
        @GeneratedValue(strategy = GenerationType.AUTO)
        @Column(name = "id", updatable = false, nullable = false)
        long id;
        @Column(name = "buyer")
        UUID buyer;
        @Column(name = "seller")
        UUID seller;
        int itemId;
        int n;
        int price;
        long ts;

        public DealLog() {
        }

        Gs.ExchangeDealLog toProto() {
            return Gs.ExchangeDealLog.newBuilder()
                    .setBuyerId(Util.toByteString(this.buyer))
                    .setSellerId(Util.toByteString(this.seller))
                    .setItemId(this.itemId)
                    .setDealed(this.n)
                    .setPrice(this.price)
                    .setTs(this.ts)
                    .build();
        }

        @Override
        public CacheType getCacheType() {
            return CacheType.Temporary;
        }
    }
    @Entity
    public final static class Stat {
        @Id
        int itemId;
        int amount = 0;
        int lowPrice = Integer.MAX_VALUE;
        int highPrice = Integer.MIN_VALUE;
        float avgPrice = 0;
        long sumDealedPrice = 0;

        public Stat(int itemId) {
            this.itemId = itemId;
        }

        protected Stat() {
        }

        int nowPrice() {
            return histories.isEmpty()?-1:histories.last().price;
        }
        void calcuStat(int durationHours) {
            int sumPrice = 0;
            int deals = 0;
            long now = System.currentTimeMillis();
            for(DealLog h : histories) {
                if(now - h.ts > TimeUnit.HOURS.toMillis(durationHours)) {
                    break;
                }
                this.amount += h.n;
                sumPrice += h.price;
                if(h.price < this.lowPrice)
                    this.lowPrice = h.price;
                if(h.price > this.highPrice)
                    this.highPrice = h.price;
                deals++;
            }
            this.avgPrice = deals == 0?0:sumPrice / deals;
            this.sumDealedPrice = sumPrice;
        }
        @Transient
        // recent at last
        TreeSet<DealLog> histories = new TreeSet<>(Comparator.comparing(log->log.ts));

        public int priceChange() {
            if(histories.isEmpty())
                return 0;
            long now = System.currentTimeMillis();
            DealLog l = new DealLog();
            l.ts = now - TimeUnit.HOURS.toMillis(24);
            DealLog old = histories.ceiling(l); // least key greater than or equal to l.ts, can prove this will not return null
            return (int) ((nowPrice() - old.price)/(double)old.price*1000);
        }
    }

    public void update(long nanoDiff) {
        matchmaking();

        final long now = System.currentTimeMillis();
        if(updateTimer.update(nanoDiff)) {
            itemStat.forEach((k,v)-> {
                v.calcuStat(24);
                removeOutdatedDealLog(v.histories, now);
            });
            removeOutdatedDealLog((TreeSet<DealLog>) _dealHistoryData, now);
        }

    }

    @Transient
    private Map<Integer, Trading> tradings = new HashMap<>();
    @Transient
    private Map<UUID, Map<UUID, Order>> orders = new HashMap<>();
    @Transient
    private PeriodicTimer updateTimer = new PeriodicTimer(20000);
    //@Transient
    //private Map<Integer, Info> info = new HashMap<>();
    @OneToMany(fetch = FetchType.EAGER)
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    @JoinColumn(name = "exchange_id")
    @MapKey(name = "itemId")
    private Map<Integer, Stat> itemStat = new HashMap<>();

    //==============plain data, DO NOT use them directly======================
    @OneToMany(fetch = FetchType.EAGER)
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    @JoinColumn(name = "exchange_id")
    private Set<Order> _orderData = new HashSet<>();
    @OneToMany(fetch = FetchType.EAGER)
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    @JoinColumn(name = "exchange_id")
    @OrderBy("ts ASC")
    private SortedSet<DealLog> _dealHistoryData = new TreeSet<>(Comparator.comparing(h -> h.ts));
    //========================================================================

    // in real stock exchange, the log is always there, however, the query of myself log has limitation of duration which
    // can not longer than 30 days
    private static final long DEAL_LOG_MAX_PRESERVE_MS =  TimeUnit.DAYS.toMillis(31+10);
    @PostLoad
    void _init() {
        for(Order order : _orderData) {
            Trading trading = tradings.computeIfAbsent(order.itemId, k->new Trading());
            if(order.sell)
                trading.sell.add(order);
            else
                trading.buy.add(order);
            orders.computeIfAbsent(order.playerId, k->new HashMap<>()).put(order.id, order);
        }

        {
            // this will be too fucking slow
            // _dealHistoryData.removeIf(h->now - h.ts > DEAL_LOG_MAX_PRESERVE_MS);

            final long now = System.currentTimeMillis();
            //TreeSet<DealLog> deals = (TreeSet<DealLog>) _dealHistoryData;
            //removeOutdatedDealLog(deals, now);

            //deals.descendingSet().forEach(l->this.itemStat.computeIfAbsent(l.itemId, k-
        }
    }
    private void removeOutdatedDealLog(TreeSet<DealLog> deals, long now) {
        Iterator<DealLog> iterator = deals.descendingIterator();
        while(iterator.hasNext()) {
            DealLog h = iterator.next();
            if(now - h.ts > DEAL_LOG_MAX_PRESERVE_MS)
                iterator.remove();
            else
                break;
        }
    }

    public Gs.ExchangeItemDetail watch(ChannelId id, int itemId) {
        this.watcher.put(id, itemId);
        return genItemDetail(itemId);
    }

    private Gs.ExchangeItemDetail genItemDetail(int itemId) {
        Gs.ExchangeItemDetail.Builder builder = Gs.ExchangeItemDetail.newBuilder();
        Trading trading = this.tradings.get(itemId);
        if(trading != null) {
            trading.buy.forEach(o -> builder.addBuy(Gs.IntNum.newBuilder().setId(o.price).setNum(o.n)));
            trading.sell.forEach(o -> builder.addSell(Gs.IntNum.newBuilder().setId(o.price).setNum(o.n)));
        }
        Stat stat = this.itemStat.get(itemId);
        if(stat == null) {
            builder.setNowPrice(0);
            builder.setPriceChange(0);
        }
        else {
            builder.setNowPrice(stat.nowPrice());
            builder.setPriceChange(stat.priceChange());
        }
        return builder.build();
    }

    public void stopWatch(ChannelId id) {
        this.watcher.remove(id);
    }

    // java has no bi multi map
    private class Watcher {
        void put(ChannelId channelId, Integer itemId) {
            channelIdKey.put(channelId, itemId);
            itemIdKey.computeIfAbsent(itemId, k->new HashSet<>()).add(channelId);
        }
        void remove(ChannelId channelId) {
            Integer itemId = channelIdKey.get(channelId);
            if(itemId != null) {
                channelIdKey.remove(channelId);
                itemIdKey.get(itemId).remove(channelId);
            }
        }
        void sendTo(int itemId) {
            Set<ChannelId> channelIds = itemIdKey.get(itemId);
            if(channelIds != null)
                GameServer.sendTo(channelIds, Package.create(GsCode.OpCode.exchangeItemDetailInform_VALUE, genItemDetail(itemId)));
        }
        Map<ChannelId, Integer> channelIdKey = new HashMap();
        Map<Integer, Set<ChannelId>> itemIdKey = new HashMap();
    }
    @Transient
    private Watcher watcher = new Watcher();
}
