package Game;

import Game.Exceptions.GroundAlreadySoldException;
import Shared.LogDb;
import Shared.Package;
import Shared.Util;
import com.google.protobuf.Message;
import gs.Gs;
import gscode.GsCode;
import org.apache.log4j.Logger;
import org.hibernate.annotations.Cascade;

import javax.persistence.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Entity
public class GroundManager {
    public static final int ID = 0;
    private static final Logger logger = Logger.getLogger(GroundManager.class);
    private static GroundManager instance;
    public static GroundManager instance() {
        return instance;
    }
    public static void init() {
        GameDb.initGroundManager();
        instance = GameDb.getGroundManager();

        instance.buildCache();
    }
    private void buildCache() {
        for(GroundInfo i : info.values()) {
            playerGround.computeIfAbsent(i.ownerId, k->new HashSet<>()).add(i);
            SummaryInfo summaryInfo = this.summaryInfo.computeIfAbsent(i.coordinate().toGridIndex(), k->new SummaryInfo());
            if(i.inSelling())
                summaryInfo.sellingCount++;
            else if(i.inRenting()) {
                summaryInfo.rentingCount++;
                rentGround.computeIfAbsent(i.rentTransactionId, k -> new HashSet<>()).add(i);
            }
        }
    }
    protected GroundManager() {}

    @Id
    public final int id = ID;

    public Iterable<? extends Gs.GroundInfo> getGroundProto(UUID id) {
        Set<GroundInfo> gs = playerGround.get(id);
        if(gs == null)
            return new ArrayList<>();
        List<Gs.GroundInfo> res = new ArrayList<>(gs.size());
        for(GroundInfo i : gs) {
            res.add(i.toProto());
        }
        return res;
    }
    
    public Iterable<? extends Gs.GroundInfo> getRentGroundProto(UUID rentId) {
        Set<GroundInfo> gs = new HashSet<>();
        rentGround.values().forEach(set->{
            set.forEach(groundInfo -> {
                if (groundInfo.isRented() && groundInfo.renterId.equals(rentId)) {
                    gs.add(groundInfo);
                }
            });
        });
        if(gs.isEmpty())
            return new ArrayList<>();
        List<Gs.GroundInfo> res = new ArrayList<>(gs.size());
        for(GroundInfo i : gs) {
            res.add(i.toProto());
        }
        return res;
    }
    void update(long diffNano) {
        final long now = System.currentTimeMillis();
        List<UUID> del = new ArrayList<>();
        rentGround.forEach((k,v)->{
            if(v.isEmpty())
                return;
            GroundInfo head = v.iterator().next();
            if(now - head.rentBeginTs >= TimeUnit.DAYS.toMillis(head.rentDays)) {
                v.forEach(i->i.endRent());
                del.add(k);
                List updates = new ArrayList<>();
                updates.addAll(v);
                GameDb.saveOrUpdate(updates);
            }
        });
        for(UUID tid : del) {
            rentGround.remove(tid).forEach(gi->this.summaryInfo.get(gi.coordinate().toGridIndex()).rentingCount--);
        }
    }
    // role id --> all grounds belong to this role
    @Transient
    private Map<UUID, Set<GroundInfo>> playerGround = new HashMap<>();

    // transactionId --> all grounds belong to this transaction
    @Transient
    private Map<UUID, Set<GroundInfo>> rentGround = new HashMap<>();

    // memory is hundreds MB
    @OneToMany(fetch = FetchType.EAGER)
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    @MapKeyClass(value = Coordinate.class)
    @JoinColumn(name = "ground_manager_id")
    private Map<Coordinate, GroundInfo> info = new HashMap<>();

    public Gs.GroundSummary getGroundSummaryProto() {
        Gs.GroundSummary.Builder builder = Gs.GroundSummary.newBuilder();
        this.summaryInfo.forEach((k,v)->{
            builder.addInfoBuilder()
                    .setIdx(k.toProto())
                    .setRentingN(v.rentingCount)
                    .setSellingN(v.sellingCount);
        });
        return builder.build();
    }

    private static final class SummaryInfo {
        int sellingCount;
        int rentingCount;
    }

    @Transient
    private Map<GridIndex, SummaryInfo> summaryInfo = new HashMap<>();

//    public void forEach(Consumer<GroundInfo> f) {
//        this.info.values().forEach(f);
//    }
    public boolean hasGround(UUID playerId, CoordPair coordPair, int state) {
        Collection<Coordinate> coordinates = coordPair.toCoordinates();
        for(Coordinate c : coordinates) {
            GroundInfo i = info.get(c);
            if(i == null)
                return false;
            if(state == Game.GroundInfo.OWN) {
                 if(!i.ownerId.equals(playerId))
                     return false;
            }
            else if(state == GroundInfo.RENTING) {
                if(i.renterId == null || !i.renterId.equals(playerId))
                    return false;
            }
            else if(state == GroundInfo.RENTED) {
                if(i.renterId == null)
                    return false;
            }
            else if(state == GroundInfo.SELLING) {
                if(i.sellPrice == 0)
                    return false;
            }
            else if(state == GroundInfo.CAN_BUILD) {
                if(!i.ownerId.equals(playerId)) {
                    if(i.renterId == null || !i.renterId.equals(playerId))
                        return false;
                }
            }
        }
        return true;
    }
    public boolean canBuild(UUID playerId, CoordPair coordPair) {
        return hasGround(playerId, coordPair, GroundInfo.CAN_BUILD);
    }

    public boolean rentOutGround(UUID id, List<Coordinate> coordinates, RentPara rentPara) {
        List<GroundInfo> gis = new ArrayList<>(coordinates.size());
        for(Coordinate c : coordinates) {
            GroundInfo i = info.get(c);
            if(i == null || !i.canRenting(id))
                return false;
            gis.add(i);
        }
        for(GroundInfo i : gis) {
            i.renting(rentPara);
        }
        GameDb.saveOrUpdate(gis); // faster than GameDb.saveOrUpdate(this);
        this.broadcast(gis);
        return true;
    }

    private void broadcast(List<GroundInfo> gis) {
        Map<GridIndexPair, Gs.GroundChange.Builder> notifyMap = new HashMap<>();
        for(GroundInfo gi : gis)
        {
            GridIndexPair pair = gi.coordinate().toGridIndex().toSyncRange();
            Gs.GroundChange.Builder builder = notifyMap.computeIfAbsent(pair, k -> Gs.GroundChange.newBuilder());
            builder.addInfo(gi.toProto());
        }
        for (Map.Entry<GridIndexPair, Gs.GroundChange.Builder> entry : notifyMap.entrySet())
        {
            Package pack = Package.create(GsCode.OpCode.groundChange_VALUE,entry.getValue().build());
            City.instance().send(entry.getKey(), pack);
        }
    }
    public Gs.GroundChange getGroundProto(List<GridIndex> g) {
        Gs.GroundChange.Builder builder = Gs.GroundChange.newBuilder();
        for (GridIndex gridIndex : g) {
            gridIndex.toCoordinates().forEach(c->{
                GroundInfo i = info.get(c);
                if(i != null) {
                    builder.addInfo(i.toProto());
                }
            });
        }
        return builder.build();
    }
    public boolean rentGround(Player renter, List<Coordinate> coordinates, RentPara rentPara) {
        UUID ownerId = null;
        for(Coordinate c : coordinates) {
            GroundInfo i = info.get(c);
            if (i == null)
                return false;
            if (!i.sameAs(rentPara) || !i.inRenting())
                return false;
            if (ownerId == null)
                ownerId = i.ownerId;
            else {
                if (!ownerId.equals(i.ownerId))
                    return false;
            }
        }
        int cost = rentPara.requiredPay() * coordinates.size();
        if(!renter.decMoney(cost))
            return false;
        List<LogDb.Positon> plist1 = new ArrayList<>();
        List<Gs.MiniIndex> miniIndexList = new ArrayList<>();
        for(Coordinate c : coordinates)
        {
            plist1.add(new LogDb.Positon(c.x, c.y));
            miniIndexList.add(c.toProto());
        }

        Player owner = GameDb.queryPlayer(ownerId);
        owner.addMoney(cost);

        LogDb.rentGround(renter.id(), ownerId, cost, plist1);
        UUID tid = UUID.randomUUID();
        long now = System.currentTimeMillis();
        List<GroundInfo> gis = new ArrayList<>(coordinates.size());
        for(Coordinate c : coordinates) {
            GroundInfo i = info.get(c);
            i.rented(rentPara, tid, renter.id(), now);
            gis.add(i);
            this.summaryInfo.get(i.coordinate().toGridIndex()).rentingCount--;
        }
        GameDb.saveOrUpdate(Arrays.asList(owner, renter, this));
        this.rentGround.put(tid, new HashSet<>(gis));
        this.broadcast(gis);

        GameServer.sendIncomeNotity(ownerId,Gs.IncomeNotify.newBuilder()
                .setBuyer(Gs.IncomeNotify.Buyer.PLAYER)
                .setBuyerId(Util.toByteString(renter.id()))
                .setFaceId(renter.getFaceId())
                .setCost(cost)
                .setType(Gs.IncomeNotify.Type.RENT_GROUND)
                .addAllCoord(miniIndexList)
                .build());
        //土地出租通知
        List<Integer> list = new ArrayList<>();
        for (Coordinate c : coordinates) {
            list.add(c.x);
            list.add(c.y);
        }
        int[] landCoordinates = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            landCoordinates[i] = list.get(i);
        }
        MailBox.instance().sendMail(Mail.MailType.LAND_RENT.getMailType(),owner.id(),null,new UUID[]{renter.id()},landCoordinates);
        return true;
    }

    public boolean sellGround(UUID id, Set<Coordinate> coordinates, int price) {
        List<GroundInfo> gis = new ArrayList<>();
        for(Coordinate c : coordinates) {
            GroundInfo i = info.get(c);
            if (i == null || !i.canSell(id))
                return false;
            gis.add(i);
            if(!i.inSelling())
                summaryInfo.get(i.coordinate().toGridIndex()).sellingCount++;
        }
        gis.forEach(i->{
            i.sell(price);
        });
        GameDb.saveOrUpdate(gis);
        this.broadcast(gis);
        return true;
    }
    public boolean buyGround(Player buyer, Set<Coordinate> coordinates, int price) {
        List<GroundInfo> gis = new ArrayList<>();
        UUID sellerId = null;
        for(Coordinate c : coordinates) {
            GroundInfo i = info.get(c);
            if (i == null || !i.inSelling() || i.sellPrice != price)
                return false;
            if(sellerId == null)
                sellerId = i.ownerId;
            else {
                if(!i.ownerId.equals(sellerId))
                    return false;
            }
            gis.add(i);
        }
        int cost = gis.size() * price;
        if(buyer.money() < cost)
            return false;
        Player seller = GameDb.queryPlayer(sellerId);
        seller.addMoney(cost);
        buyer.decMoney(cost);
        List<LogDb.Positon> plist1 = new ArrayList<>();
        List<Gs.MiniIndex> miniIndexList = new ArrayList<>();
        for(Coordinate c : coordinates)
        {
            plist1.add(new LogDb.Positon(c.x, c.y));
            miniIndexList.add(c.toProto());
        }
        LogDb.buyGround(buyer.id(),sellerId,price,plist1);
        for(GroundInfo i : gis) {
            swapOwner(buyer.id(), seller.id(), i);
        }
        GameDb.saveOrUpdate(Arrays.asList(buyer,seller,this));
        this.broadcast(gis);

        GameServer.sendIncomeNotity(sellerId,Gs.IncomeNotify.newBuilder()
                .setBuyer(Gs.IncomeNotify.Buyer.PLAYER)
                .setBuyerId(Util.toByteString(buyer.id()))
                .setFaceId(buyer.getFaceId())
                .setCost(cost)
                .setType(Gs.IncomeNotify.Type.BUY_GROUND)
                .addAllCoord(miniIndexList)
                .build());
        //土地出售通知
        List<Integer> list = new ArrayList<>();
        for (Coordinate c : coordinates) {
            list.add(c.x);
            list.add(c.y);
        }
        int[] landCoordinates = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            landCoordinates[i] = list.get(i);
        }
        MailBox.instance().sendMail(Mail.MailType.LAND_SALE.getMailType(),sellerId,null,new UUID[]{buyer.id()},landCoordinates);
        return true;
    }

    private void swapOwner(UUID buyer, UUID seller, GroundInfo info) {
        info.buyGround(buyer);
        playerGround.computeIfPresent(buyer,(k,v)->{
            v.remove(info);
            return v;
        });
        playerGround.computeIfAbsent(seller, k->new HashSet<>()).add(info);
        summaryInfo.get(info.coordinate().toGridIndex()).sellingCount--;
    }

    public void addGround(UUID id, Collection<Coordinate> area) throws GroundAlreadySoldException {
        for(Coordinate c : area) {
            if(info.containsKey(c))
                throw new GroundAlreadySoldException();
        }
        List<GroundInfo> gis = new ArrayList<>();
        for(Coordinate c : area) {
            GroundInfo i = new GroundInfo(c.x, c.y);
            i.ownerId = id;
            info.put(c, i);
            gis.add(i);
            playerGround.computeIfAbsent(id, k->new HashSet<>()).add(i);
            this.summaryInfo.computeIfAbsent(i.coordinate().toGridIndex(), k->new SummaryInfo());
        }
        this.broadcast(gis);
    }

    public boolean cancelSell(UUID id, Set<Coordinate> coordinates) {
        List<GroundInfo> gis = new ArrayList<>();
        for(Coordinate c : coordinates) {
            GroundInfo i = info.get(c);
            if (i == null || !i.ownerId.equals(id) || !i.inSelling())
                return false;
            gis.add(i);
        }
        gis.forEach(i->{
            i.cancelSell();
            summaryInfo.get(i.coordinate().toGridIndex()).sellingCount--;
        });
        GameDb.saveOrUpdate(gis);
        this.broadcast(gis);
        return true;
    }

    public boolean cancelRent(UUID id, Set<Coordinate> coordinates) {
        List<GroundInfo> gis = new ArrayList<>();
        for(Coordinate c : coordinates) {
            GroundInfo i = info.get(c);
            if (i == null || !i.ownerId.equals(id) || !i.inRenting())
                return false;
            gis.add(i);
        }
        gis.forEach(i->{
            i.endRent();
            summaryInfo.get(i.coordinate().toGridIndex()).rentingCount--;
        });
        GameDb.saveOrUpdate(gis);
        this.broadcast(gis);
        return true;
    }
}
