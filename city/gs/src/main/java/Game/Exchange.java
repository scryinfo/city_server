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

@Entity
public class Exchange {
    private static Exchange instance = new Exchange();
    public static Exchange instance() {
        return instance;
    }
    public static final int ID = 0;

    Exchange() {
        buildOrderCache();
    }
    public static void init() {
        GameDb.initExchange();
        instance = GameDb.getExchange();
    }

    @Id
    public final int id = ID;
    public Gs.ExchangeItemList getItemList() {
        Gs.ExchangeItemList.Builder builder = Gs.ExchangeItemList.newBuilder();
        info.forEach((itemId, info)->{
            builder.addSummary(Gs.ExchangeItemSummary.newBuilder()
                    .setItemId(itemId)
                    .setNowPrice(info.nowPrice())
                    .setLowPrice(info.stat.lowPrice)
                    .setHighPrice(info.stat.highPrice)
                    .setSumDealedPrice(info.stat.sumDealedPrice)
            );
        });
        return builder.build();
    }



    @Entity
    @Table(name = "exchange_order")
    final class Order {
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

        public Order(UUID playerId, int price, int itemId, int n, UUID buildingId, boolean sell) {
            this.playerId = playerId;
            this.buildingId = buildingId;
            this.price = price;
            this.itemId = itemId;
            this.n = n;
            this.sell = sell;
            this.ts = System.currentTimeMillis();
        }
    }

    @Entity
    @Table(name = "exchange_trading")
    protected static final class Trading { // private will cause JPA meta class generate fail
        @OneToMany
        @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
        SortedSet<Order> buy = new TreeSet<>(Comparator.comparingInt(o -> o.price));

        @OneToMany
        @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
        SortedSet<Order> sell = new TreeSet<>(Comparator.comparingInt(o -> o.price));
    }
    public UUID addSellOrder(UUID who, int itemId, int price, int n, UUID buildingId) {
        Order order = new Order(who, price, itemId, n, buildingId,true);
        tradings.getOrDefault(itemId, new Trading()).sell.add(order);
        orders.getOrDefault(who, new HashMap<>()).put(order.id, order);
        this.watcher.sendTo(itemId);
        return order.id;
    }
    public UUID addBuyOrder(UUID who, int itemId, int price, int n, UUID buildingId) {
        Order order = new Order(who, price, itemId, n, buildingId,false);
        tradings.getOrDefault(itemId, new Trading()).buy.add(order);
        orders.getOrDefault(who, new HashMap<>()).put(order.id, order);
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
            trading.sell.removeIf(order -> order.id.equals(orderId));
        else
            trading.buy.removeIf(order -> order.id.equals(orderId));
        this.watcher.sendTo(o.itemId);
        return o.buildingId;
    }
    private void buildOrderCache() {
        tradings.forEach((k,v)-> {
            v.sell.forEach(o -> orders.getOrDefault(o.playerId, new HashMap<>()).put(o.id, o));
            v.buy.forEach(o -> orders.getOrDefault(o.playerId, new HashMap<>()).put(o.id, o));
        });
    }

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
        Player seller = GameDb.getPlayer(s.playerId);
        Player buyer = GameDb.getPlayer(b.playerId);

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
        Building out = City.instance().getBuilding(s.buildingId);
        if(out == null || !(out instanceof Storagable))
            return;

        Building in = City.instance().getBuilding(b.buildingId);
        if(in == null || !(in instanceof Storagable))
            return;

        Storagable outs = (Storagable)out;
        outs.clearOrder(s.id);
        outs.consumeLock(mi, n);

        Storagable ins = (Storagable)in;
        ins.clearOrder(b.id);
        ins.consumeReserve(mi, n);

        DealLog log = new DealLog(b.playerId, s.playerId, mi.id, n, s.price);
        Collection<Object> updates = Arrays.asList(buyer, seller, in, out, log);
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
    }
    @Entity
    @Table(name = "exchange_deal_log", indexes = {
            @Index(name = "SELLER_IDX", columnList = "seller"),
            @Index(name = "BUYER_IDX", columnList = "buyer")
    })
    final static public class DealLog {
        public DealLog(UUID buyer, UUID seller, int itemId, int n, int price) {
            this.buyer = buyer;
            this.seller = seller;
            this.itemId = itemId;
            this.n = n;
            this.price = price;
            ts = System.currentTimeMillis();
        }
        @Column(name = "buyer")
        UUID buyer;
        @Column(name = "seller")
        UUID seller;
        int itemId;
        int n;
        int price;
        long ts;
    }
    final static protected class Info {// private will cause JPA meta class generate fail
        final static private class Stat {
            int amount = 0;
            int lowPrice = Integer.MAX_VALUE;
            int highPrice = Integer.MIN_VALUE;
            float avgPrice = 0;
            long sumDealedPrice = 0;
        }
        int nowPrice() {
            return histories.isEmpty()?-1:histories.get(histories.size()-1).dealPrice;
        }
        void calcuStat(int durationHours) {
            int sumPrice = 0;
            int deals = 0;
            long now = System.currentTimeMillis();
            for(History h : histories) {
                if(now - h.ts > TimeUnit.HOURS.toMillis(durationHours)) {
                    break;
                }
                stat.amount += h.amount;
                sumPrice += h.dealPrice;
                if(h.dealPrice < stat.lowPrice)
                    stat.lowPrice = h.dealPrice;
                if(h.dealPrice > stat.highPrice)
                    stat.highPrice = h.dealPrice;
                deals++;
            }
            stat.avgPrice = deals == 0?0:sumPrice / deals;
            stat.sumDealedPrice = sumPrice;
        }

        final static private class History {
            int dealPrice;
            int amount;
            long ts;
        }
        Stat stat = new Stat();
        private List<History> histories = new ArrayList<>();
    }
    public void update(long nanoDiff) {
        matchmaking();
        if(updateTimer.update(nanoDiff)) {
            info.forEach((k,v)->v.calcuStat(24));
        }
    }

    @OneToMany
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    @MapKey(name = "itemId")
    private Map<Integer, Trading> tradings = new HashMap<>();

    @OneToMany
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    @MapKey(name = "itemId")
    private Map<Integer, Info> info = new HashMap<>();

    // cache
    @Transient
    private Map<UUID, Map<UUID, Order>> orders = new HashMap<>();

    @Transient
    private PeriodicTimer updateTimer = new PeriodicTimer(20000);

    public Gs.ExchangeItemDetail watch(ChannelId id, int itemId) {
        this.watcher.put(id, itemId);
        return genItemDetail(itemId);
    }

    private Gs.ExchangeItemDetail genItemDetail(int itemId) {
        Gs.ExchangeItemDetail.Builder builder = Gs.ExchangeItemDetail.newBuilder();
        Trading trading = this.tradings.get(itemId);
        trading.buy.forEach(o->builder.addBuy(Gs.IntNum.newBuilder().setId(o.price).setNum(o.n)));
        trading.sell.forEach(o->builder.addSell(Gs.IntNum.newBuilder().setId(o.price).setNum(o.n)));
        return builder.build();
    }

    public void stopWatch(ChannelId id) {
        this.watcher.remove(id);
    }

    // java has no bi multi map
    private class Watcher {
        void put(ChannelId channelId, Integer itemId) {
            channelIdKey.put(channelId, itemId);
            itemIdKey.getOrDefault(itemId, new HashSet<>()).add(channelId);
        }
        void remove(ChannelId channelId) {
            Integer itemId = channelIdKey.get(channelId);
            channelIdKey.remove(channelId);
            itemIdKey.get(itemId).remove(channelId);
        }
        void sendTo(int itemId) {
            GameServer.sendTo(itemIdKey.get(itemId), Package.create(GsCode.OpCode.exchangeItemDetailInform_VALUE, genItemDetail(itemId)));
        }
        Map<ChannelId, Integer> channelIdKey = new HashMap();
        Map<Integer, Set<ChannelId>> itemIdKey = new HashMap();
    }
    @Transient
    private Watcher watcher = new Watcher();
}
