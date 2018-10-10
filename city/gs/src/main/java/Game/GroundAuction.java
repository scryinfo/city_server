package Game;

import Game.Timers.DateTimeTracker;
import Shared.Package;
import Shared.Util;
import common.Common;
import gs.Gs;
import gscode.GsCode;
import io.netty.channel.Channel;
import io.netty.channel.ChannelId;
import org.apache.log4j.Logger;
import org.bson.types.ObjectId;
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
        GameDb.initGroundAction();
        instance = GameDb.getGroundAction();
        instance.loadMore();
    }
    @Id
    public final int id = ID;

    @Entity
    @Table(name = "ground_auction_entry")
    public static class Entry {
        public Entry(){}
        public Entry(MetaGroundAuction meta) {
            this.metaId = meta.id;
            this.meta = meta;
            this.timer = new DateTimeTracker(meta.beginTime, meta.endTime);
        }
        public Gs.GroundAuction.Target toProto() {
            Gs.GroundAuction.Target.Builder b =  Gs.GroundAuction.Target.newBuilder();
            b.setId(Util.toByteString(meta.id));
            b.setPrice(price);
            if(biderId != null)
                b.setBiderId(Util.toByteString(biderId));
            return b.build();
        }
        @Transient
        MetaGroundAuction meta;
        @Id
        @Column(name = "metaId", nullable = false)
        UUID metaId;
        @Column(name = "biderId", nullable = false)
        UUID biderId;
        @Column(name = "price", nullable = false)
        int price = 0;
        @Transient
        DateTimeTracker timer;
        @PostLoad
        private void init() {
            this.meta = MetaData.getGroundAuction(metaId);
            this.timer = new DateTimeTracker(meta.beginTime, meta.endTime);
        }
    }

    @OneToMany
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    @MapKey(name = "metaId")
    private Map<UUID, Entry> auctions = new HashMap<>();
    public void loadMore() {
        Set<MetaGroundAuction> m = MetaData.getNonFinishedGroundAuction();
        Gs.MetaGroundAuction.Builder builder = Gs.MetaGroundAuction.newBuilder();
        List<Entry> newAdds = new ArrayList<>();
        m.forEach((v)->{
            if(!this.auctions.containsKey(v.id))
            {
                Entry e = new Entry(v);
                this.auctions.put(v.id, e);
                newAdds.add(e);
            }
            builder.addAuction(v.toProto());
        });
        GameDb.saveOrUpdate(newAdds);
        GameServer.allClientChannels.writeAndFlush(Package.create(GsCode.OpCode.metaGroundAuctionAddInform_VALUE, builder.build()));
    }

    public void update(long diffNano) {
        Iterator<Entry> iter = this.auctions.values().iterator();
        while(iter.hasNext())
        {
            Entry a = iter.next();
            a.timer.update(diffNano);
            if(a.timer.passed())
            {
                iter.remove();
                // no need through GameSession
                Player bider = GameDb.getPlayer(a.biderId);
                bider.addGround(a.meta.area);
                int p = bider.spentLockMoney(a.meta.id);
                GameDb.saveOrUpdate(Arrays.asList(bider, this));

                bider.send(Package.create(GsCode.OpCode.bidWinInform_VALUE, Gs.ByteNum.newBuilder().setId(Util.toByteString(a.meta.id)).setNum(p).build()));
                Package pack = Package.create(GsCode.OpCode.auctionEnd_VALUE, Gs.Id.newBuilder().setId(Util.toByteString(a.meta.id)).build());
                this.watcher.forEach(cId -> GameServer.allClientChannels.writeAndFlush(pack, (Channel channel)->{
                    if(channel.id().equals(cId))
                        return true;
                    return false;
                }));
            }
        }

    }
    public Gs.GroundAuction toProto() {
        Gs.GroundAuction.Builder b = Gs.GroundAuction.newBuilder();
        for(Entry a : this.auctions.values())
            b.addAuction(a.toProto());
        return b.build();
    }
//    public Document toBson() {
//        List<Document> ba = new ArrayList<>();
//        for(Entry e : auctions.values()) {
//            ba.add(e.toBson());
//        }
//        Document doc = new Document()
//                .append("_id", GameServer.gsInfo.getId())
//                .append("auction", ba);
//        return doc;
//    }
    public boolean contain(ObjectId id) {
        return this.auctions.containsKey(id);
    }
    public Optional<Common.Fail.Reason> bid(UUID id, Player bider, int price) {
        Entry a = this.auctions.get(id);
        if(a == null)
            return Optional.of(Common.Fail.Reason.auctionNotFound);
        if(a.price >= price)
            return Optional.of(Common.Fail.Reason.auctionPriceIsLow);
        if(a.biderId != null) {
            // unlock its money
            GameSession biderSession = GameServer.allGameSessions.get(a.biderId);
            if(biderSession == null) {
                GameDb.getPlayer(a.biderId).groundBidingFail(bider.id(), a);
            }
            else {
                biderSession.getPlayer().groundBidingFail(bider.id(), a);
            }
        }
        a.price = price;
        a.biderId = bider.id();
        bider.lockMoney(id, price);
        GameDb.saveOrUpdate(Arrays.asList(bider, a));
        Package pack = Package.create(GsCode.OpCode.bidChangeInform_VALUE, Gs.ByteNum.newBuilder().setId(Util.toByteString(id)).setNum(price).build());
        this.watcher.forEach(cId -> GameServer.allClientChannels.writeAndFlush(pack, (Channel channel)->{
            if(channel.id().equals(cId))
                return true;
            return false;
        }));
        return Optional.empty();
    }
    public void regist(ChannelId id) {
        this.watcher.add(id);
    }
    public void unregist(ChannelId id) {
        this.watcher.remove(id);
    }
    @Transient
    private Set<ChannelId> watcher = new HashSet<>();
}
