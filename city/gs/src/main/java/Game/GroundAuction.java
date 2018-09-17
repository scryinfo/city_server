package Game;

import Shared.Package;
import Shared.Util;
import Game.Timers.DateTimeTracker;
import com.google.protobuf.ByteString;

import common.Common;
import gs.Gs;
import gscode.GsCode;
import io.netty.channel.Channel;
import io.netty.channel.ChannelId;
import org.apache.log4j.Logger;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.*;

public class GroundAuction {
    private static final Logger logger = Logger.getLogger(GroundAuction.class);
    private static final GroundAuction instance = new GroundAuction();
    public static GroundAuction instance() {
        return instance;
    }
    class Auction {
        Auction(MetaGroundAuction meta) {
            this.meta = meta;
            this.timer = new DateTimeTracker(meta.beginTime, meta.endTime);
        }
        Auction(Document d) {
            this.bider = d.getObjectId("bider");
            this.price = d.getInteger("price");
            this.meta = MetaData.getGroundAuction(d.getObjectId("id"));
        }

        public Document toBson() {
            Document res = new Document()
                    .append("id", meta.id)
                    .append("bider", bider)
                    .append("price", price);
            return res;
        }
        public Gs.GroundAuction.Target toProto() {
            return Gs.GroundAuction.Target.newBuilder()
                    .setId(ByteString.copyFrom(this.meta.id.toByteArray()))
                    .setBiderId(ByteString.copyFrom(this.bider.toByteArray()))
                    .build();
        }
        MetaGroundAuction meta;
        ObjectId bider = Util.NullOid;
        int price = 0;
        DateTimeTracker timer;
    }
    private HashMap<ObjectId, Auction> auctions = new HashMap<>();
    public void loadMore() {
        Set<MetaGroundAuction> m = MetaData.getNonFinishedGroundAuction();
        Gs.MetaGroundAuction.Builder builder = Gs.MetaGroundAuction.newBuilder();
        m.forEach((v)->{
            if(!this.auctions.containsKey(v.id))
                this.auctions.put(v.id, new Auction(v));
            builder.addAuction(v.toProto());
        });
        GameServer.allClientChannels.writeAndFlush(Package.create(GsCode.OpCode.metaGroundAuctionAddInform_VALUE, builder.build()));
    }
    private GroundAuction() {
        Document doc = GameDb.getGroundAction(GameServer.gsInfo.getId());
        if(doc != null) { // means this bid action is not finished
            List<Document> al = (List<Document>) doc.get("auction");
            for(Document d : al) {
                Auction a = new Auction(d);
                if(a.meta == null) // meta already changed, omit this
                {
                    logger.fatal("unfinished ground auction can not found in meta!");
                    continue;
                }
                this.auctions.put(a.meta.id, a);
            }
        }
        loadMore();
    }

    public void update(long diffNano) {
        Iterator<Auction> iter = this.auctions.values().iterator();
        while(iter.hasNext())
        {
            Auction a = iter.next();
            a.timer.update(diffNano);
            if(a.timer.passed())
            {
                GameSession biderSession = GameServer.allGameSessions.get(a.bider);
                if(biderSession == null) {
                    GameDb.getPlayer(a.bider).bidWin(a);
                }
                else {
                    biderSession.getPlayer().bidWin(a);
                }
                iter.remove();

                Package pack = Package.create(GsCode.OpCode.auctionEnd_VALUE, Gs.Id.newBuilder().setId(ByteString.copyFrom(a.meta.id.toByteArray())).build());
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
        for(Auction a : this.auctions.values())
            b.addAuction(a.toProto());
        return b.build();
    }
    public Document toBson() {
        List<Document> ba = new ArrayList<>();
        for(Auction e : auctions.values()) {
            ba.add(e.toBson());
        }
        Document doc = new Document()
                .append("_id", GameServer.gsInfo.getId())
                .append("auction", ba);
        return doc;
    }
    public boolean contain(ObjectId id) {
        return this.auctions.containsKey(id);
    }
    public Optional<Common.Fail.Reason> bid(ObjectId id, Player bider, int price) {
        Auction a = this.auctions.get(id);
        if(a == null)
            return Optional.of(Common.Fail.Reason.auctionNotFound);
        if(a.price >= price)
            return Optional.of(Common.Fail.Reason.auctionPriceIsLow);
        if(a.bider != Util.NullOid) {
            // unlock its money
            GameSession biderSession = GameServer.allGameSessions.get(a.bider);
            if(biderSession == null) {
                GameDb.getPlayer(a.bider).groundBidingFail(bider.id(), a);
            }
            else {
                biderSession.getPlayer().groundBidingFail(bider.id(), a);
            }
        }
        a.price = price;
        a.bider = bider.id();
        bider.lockMoney(id, price);
        GameDb.update(bider);
        GameDb.updateGroundAuction(toBson(), GameServer.gsInfo.getId());
        Package pack = Package.create(GsCode.OpCode.bidChangeInform_VALUE, Gs.IdNum.newBuilder().setId(ByteString.copyFrom(id.toByteArray())).setNum(price).build());
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
    private Set<ChannelId> watcher = new HashSet<>();
}
