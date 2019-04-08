package Game;

import Game.Exceptions.GroundAlreadySoldException;
import Game.Meta.MetaData;
import Game.Meta.MetaGroundAuction;
import Game.Timers.DateTimeTracker;
import Shared.LogDb;
import Shared.Package;
import Shared.Util;
import common.Common;
import gs.Gs;
import gscode.GsCode;
import io.netty.channel.ChannelId;
import org.apache.log4j.Logger;
import org.hibernate.annotations.Cascade;

import javax.persistence.*;
import java.util.*;

@Entity
@Table(name = "ground_auction")
public class GroundAuction {
    public static final int ID = 0;
    private static final Logger logger = Logger.getLogger(GroundAuction.class);
    private static GroundAuction instance;
    public static GroundAuction instance() {
        return instance;
    }
    public static void init() {
        GameDb.initGroundAuction();
        instance = GameDb.getGroundAction();
        instance.loadAuction();
    }
    protected GroundAuction() {}
    @Id
    public final int id = ID;

    @Entity
    @Table(name = "ground_auction_entry")
    public final static class Entry {
        protected Entry(){}
        public Entry(MetaGroundAuction meta) {
            this.metaId = meta.id;
            this.meta = meta;
            this.transactionId = UUID.randomUUID();
        }

        public void bid(UUID id, int price, long ts) {
            this.history.add(new BidRecord(id, price, ts));
            if(this.history.size() > BID_RECORD_MAX)
                this.history.iterator().remove();
            this.tweakTicking(ts);
        }
        private static final int BID_RECORD_MAX = 10;

        @Embeddable
        public static final class BidRecord {
            public BidRecord(UUID biderId, int price, long ts) {
                this.biderId = biderId;
                this.price = price;
                this.ts = ts;
            }
            protected BidRecord(){}
            @Column(name = "biderId", nullable = false)
            UUID biderId;
            @Column(name = "price", nullable = false)
            int price = 0;
            @Column(name = "ts", nullable = false)
            long ts = 0;
        }
        @ElementCollection(fetch = FetchType.EAGER)
        @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
        @OrderBy("price ASC")
        private List<BidRecord> history = new ArrayList<>();

        public Gs.GroundAuction.Target toProto() {
            Gs.GroundAuction.Target.Builder b =  Gs.GroundAuction.Target.newBuilder();
            b.setId(meta.id);
            history.forEach(r->b.addBidHistoryBuilder().setBiderId(Util.toByteString(r.biderId)).setPrice(r.price).setTs(r.ts));
            return b.build();
        }
        @Transient //@Convert can not apply on @Id
        MetaGroundAuction meta;
        @Id
        @Column(name = "id", nullable = false)
        int metaId;

        UUID transactionId;
        @Transient
        private DateTimeTracker timer;

        public boolean update(long diffNano) {
            if(timer == null)
                return false;
            timer.update(diffNano);
            return timer.passed();
        }
        @PostLoad
        private void init() {
            this.meta = MetaData.getGroundAuction(metaId);
            if(biderId() != null)
                tweakTicking(System.currentTimeMillis());
        }

        public void tweakTicking(long now) {
            if(this.timer == null)
                this.timer = new DateTimeTracker(meta.beginTime, now+MetaData.getSysPara().auctionDelay);
            else
                this.timer.resetEnd(now+MetaData.getSysPara().auctionDelay);
        }

        public UUID biderId() {
            return history.isEmpty()?null:history.get(history.size()-1).biderId;
        }
        public int price() {
            return history.isEmpty()?0:history.get(history.size()-1).price;
        }
    }

    @OneToMany(fetch = FetchType.EAGER, orphanRemoval = true)
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    @MapKey(name = "metaId")
    @JoinColumn(name = "ground_auction_id")
    private Map<Integer, Entry> auctions = new HashMap<>();

    private int nextAuctionId = 1;

    private void loadAuction() {
        MetaGroundAuction mga = MetaData.getGroundAuction(nextAuctionId);
        while(mga != null && mga.beginTime <= System.currentTimeMillis()) {
            Entry e = new Entry(mga);
            this.auctions.put(mga.id, e);
            mga = MetaData.getGroundAuction(++nextAuctionId);
        }
        GameDb.saveOrUpdate(this); // let hibernate do the dirty check
    }
    public void update(long diffNano) {
        Iterator<Entry> iter = this.auctions.values().iterator();
        while(iter.hasNext())
        {
            Entry a = iter.next();
            if(a.update(diffNano))
            {
                iter.remove();
                assert (a.biderId() != null);


                Player bider = GameDb.getPlayer(a.biderId());
                long p = bider.spentLockMoney(a.transactionId);
                try {
                    GroundManager.instance().addGround(bider.id(), a.meta.area, (int)p);
                } catch (GroundAlreadySoldException e) {
                    e.printStackTrace();
                    continue;
                }
                MoneyPool.instance().add(p);
                List<LogDb.Positon> plist1 = new ArrayList<>();
                for(Coordinate c : a.meta.area)
                {
                    plist1.add(new LogDb.Positon(c.x, c.y));
                }
                LogDb.buyGround(bider.id(), null,   p, plist1);
                GameDb.saveOrUpdate(Arrays.asList(bider, this, GroundManager.instance(), MoneyPool.instance()));

                bider.send(Package.create(GsCode.OpCode.bidWinInform_VALUE, Gs.IntNum.newBuilder().setId(a.meta.id).setNum((int) p).build()));
                //土地拍卖通知
                List<Coordinate> areas = a.meta.area;
                List<Integer> list = new ArrayList<>();
                for (Coordinate c : areas) {
                    list.add(c.x);
                    list.add(c.y);
                }
                int[] landCoordinates = new int[list.size()];
                for (int i = 0; i < list.size(); i++) {
                    landCoordinates[i] = list.get(i);
                }
                MailBox.instance().sendMail(Mail.MailType.LAND_AUCTION.getMailType(),bider.id(),null,landCoordinates);

                Package pack = Package.create(GsCode.OpCode.auctionEnd_VALUE, Gs.Num.newBuilder().setNum(a.metaId).build());
                //GameServer.sendTo(this.watcher, pack);
                GameServer.sendToAll(pack);
            }
        }

    }
    public Gs.GroundAuction toProto() {
        Gs.GroundAuction.Builder b = Gs.GroundAuction.newBuilder();
        for(Entry a : this.auctions.values())
            b.addAuction(a.toProto());
        return b.build();
    }

    public boolean contain(int id) {
        return this.auctions.containsKey(id);
    }
    public Optional<Common.Fail.Reason> bid(int id, Player bider, int price) {
        Entry a = this.auctions.get(id);
        if(a == null)
            return Optional.of(Common.Fail.Reason.auctionNotFound);
        if(a.price() >= price)
            return Optional.of(Common.Fail.Reason.auctionPriceIsLow);
        long now = System.currentTimeMillis();
        if(a.biderId() != null) {
            // unlock its money
            GameSession biderSession = GameServer.allGameSessions.get(a.biderId());
            if(biderSession == null) {
                GameDb.getPlayer(a.biderId()).groundBidingFail(bider.id(), a);
            }
            else {
                biderSession.getPlayer().groundBidingFail(bider.id(), a);
            }
        }
        a.bid(bider.id(), price, now);
        bider.lockMoney(a.transactionId, price);
        GameDb.saveOrUpdate(Arrays.asList(bider, this));
        Package pack = Package.create(GsCode.OpCode.bidChangeInform_VALUE, Gs.BidChange.newBuilder().setTargetId(id).setNowPrice(price).setTs(now).setBiderId(Util.toByteString(bider.id())).build());
        //GameServer.sendTo(this.watcher, pack);
        GameServer.sendToAll(pack);
        return Optional.empty();
    }
    //public void regist(ChannelId id) { this.watcher.add(id); }
    //public void unregist(ChannelId id) { this.watcher.remove(id); }
    @Transient
    private Set<ChannelId> watcher = new HashSet<>();
}
