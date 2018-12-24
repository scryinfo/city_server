package Game;

import Game.Exceptions.GroundAlreadySoldException;
import Shared.Package;
import gs.Gs;
import gscode.GsCode;
import org.apache.log4j.Logger;
import org.hibernate.annotations.Cascade;

import javax.persistence.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

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
            if(i.rentTransactionId != null) {
                rentGround.computeIfAbsent(i.rentTransactionId, k->new HashSet<>()).add(i);
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
    void update(long diffNano) {
        final long now = System.currentTimeMillis();
        List<UUID> del = new ArrayList<>();
        rentGround.forEach((k,v)->{
            if(v.isEmpty())
                return;
            GroundInfo head = v.iterator().next();
            if(now - head.rentBeginTs >= TimeUnit.DAYS.toMillis(head.rentDays)) {
                Player renter = GameDb.queryPlayer(head.renterId);
                renter.unlockMoney(head.rentTransactionId);
                v.forEach(i->i.endRent());
                del.add(k);
                List updates = new ArrayList<>();
                updates.addAll(v);
                updates.add(renter);
                GameDb.saveOrUpdate(updates);
            }
            else {
                if(now - head.payTs >= TimeUnit.DAYS.toMillis(head.paymentCycleDays)) {
                    int cost = head.rentPreDay*head.paymentCycleDays;
                    Player renter = GameDb.queryPlayer(head.renterId);
                    Player owner = GameDb.queryPlayer(head.ownerId);
                    if(renter.money() < cost) {
                        renter.spentLockMoney(head.rentTransactionId);
                        owner.addMoney(head.deposit);
                        v.forEach(i->i.endRent());
                        del.add(k);
                    }
                    else {
                        renter.decMoney(cost);
                        owner.addMoney(cost);
                        v.forEach(i->i.payTs = now);
                    }
                    List updates = new ArrayList<>();
                    updates.addAll(v);
                    updates.add(renter);
                    updates.add(owner);
                    GameDb.saveOrUpdate(updates);
                }
            }
        });
        for(UUID tid : del) {
            rentGround.remove(tid);
        }
    }
    @Transient
    Map<UUID, Set<GroundInfo>> playerGround = new HashMap<>();
    @Transient
    Map<UUID, Set<GroundInfo>> rentGround = new HashMap<>();
    // memory is hundreds MB
    @OneToMany(fetch = FetchType.EAGER)
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    @MapKeyClass(value = Coordinate.class)
    @JoinColumn(name = "ground_manager_id")
    Map<Coordinate, GroundInfo> info = new HashMap<>();

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
            if(i == null || i.inSell() || !i.sameAs(rentPara))
                return false;
            gis.add(i);
        }
        for(GroundInfo i : gis) {
            if(!i.ownerId.equals(id) || i.renterId != null)
                return false;
        }
        for(GroundInfo i : gis) {
            i.setBy(rentPara);
        }
        GameDb.saveOrUpdate(gis); // faster than GameDb.saveOrUpdate(this);
        this.broadcast(gis);
        return true;
    }

    private void broadcast(List<GroundInfo> gis) {
        Set<GridIndexPair> pairSet = new HashSet<>();
        Gs.GroundChange.Builder builder = Gs.GroundChange.newBuilder();
        for(GroundInfo gi : gis) {
            pairSet.add(new Coordinate(gi.x, gi.y).toGridIndex().toSyncRange());
            builder.addInfo(gi.toProto());
        }
        Package pack = Package.create(GsCode.OpCode.groundChange_VALUE, builder.build());
        for(GridIndexPair p : pairSet) {
            City.instance().send(p, pack);
        }
    }

    public UUID rentGround(Player renter, List<Coordinate> coordinates, RentPara rentPara) {
        UUID ownerId = null;
        for(Coordinate c : coordinates) {
            GroundInfo i = info.get(c);
            if(i == null)
                return null;
            if(!i.sameAs(rentPara))
                return null;
            if(ownerId == null)
                ownerId = i.ownerId;
            else {
                if(!ownerId.equals(i.ownerId))
                    return null;
            }
        }

        if(renter.money() < rentPara.requiredCost())
            return null;
        renter.decMoney(rentPara.requiredPay());
        Player owner = GameDb.queryPlayer(ownerId);
        owner.addMoney(rentPara.requiredPay());
        UUID tid = UUID.randomUUID();
        renter.lockMoney(tid, rentPara.deposit);
        long now = System.currentTimeMillis();
        List updates = new ArrayList<>(coordinates.size());
        List<GroundInfo> gis = new ArrayList<>(coordinates.size());
        for(Coordinate c : coordinates) {
            GroundInfo i = info.get(c);
            i.rentOut(rentPara, tid, renter.id(), now);
            updates.add(i);
            gis.add(i);
        }
        updates.add(renter);
        GameDb.saveOrUpdate(updates);
        this.broadcast(gis);
        return tid;
    }

    public boolean sellGround(UUID id, Set<Coordinate> coordinates, int price) {
        List<GroundInfo> gis = new ArrayList<>();
        for(Coordinate c : coordinates) {
            GroundInfo i = info.get(c);
            if (i == null || !i.canSell(id))
                return false;
            gis.add(i);
        }
        gis.forEach(i->i.sellPrice = price);
        GameDb.saveOrUpdate(gis);
        this.broadcast(gis);
        return true;
    }
    public boolean buyGround(Player buyer, Set<Coordinate> coordinates, int price) {
        List<GroundInfo> gis = new ArrayList<>();
        UUID sellerId = null;
        for(Coordinate c : coordinates) {
            GroundInfo i = info.get(c);
            if (i == null || !i.inSell() || i.sellPrice != price)
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
        List updates = new ArrayList<>();
        for(GroundInfo i : gis) {
            i.ownerId = buyer.id();
            i.sellPrice = 0;
            updates.add(i);
        }
        updates.add(buyer);
        updates.add(seller);
        GameDb.saveOrUpdate(updates);
        this.broadcast(gis);
        return true;
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
        }
        this.broadcast(gis);
    }

    public boolean cancelSell(UUID id, Set<Coordinate> coordinates) {
        List<GroundInfo> gis = new ArrayList<>();
        for(Coordinate c : coordinates) {
            GroundInfo i = info.get(c);
            if (i == null || !i.ownerId.equals(id) || !i.inSell())
                return false;
            gis.add(i);
        }
        gis.forEach(i->i.sellPrice = 0);
        GameDb.saveOrUpdate(gis);
        this.broadcast(gis);
        return true;
    }

    public boolean cancelRent(UUID id, Set<Coordinate> coordinates) {
        List<GroundInfo> gis = new ArrayList<>();
        for(Coordinate c : coordinates) {
            GroundInfo i = info.get(c);
            if (i == null || !i.ownerId.equals(id) || i.inRent())
                return false;
            gis.add(i);
        }
        gis.forEach(i->i.cancelRent());
        GameDb.saveOrUpdate(gis);
        this.broadcast(gis);
        return true;
    }
}
