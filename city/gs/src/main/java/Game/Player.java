package Game;

import Shared.DatabaseInfo;
import Shared.Package;
import Shared.Util;
import gs.Gs;
import gscode.GsCode;

import javax.persistence.*;
import java.util.*;

@Entity
@Table(name = DatabaseInfo.Game.Player.Table, indexes = {
        @Index(name = "NAME_IDX", columnList = DatabaseInfo.Game.Player.Name),
        @Index(name = "ACCNAME_IDX", columnList = DatabaseInfo.Game.Player.AccountName)
    }
)
public class Player implements ISessionCache {
    @ElementCollection(fetch = FetchType.EAGER)
    Set<Integer> itemIdCanProduce;
    public boolean canProduce(int id) {
        return itemIdCanProduce.contains(id);
    }
    public void learnItem(int id) {
        itemIdCanProduce.add(id);
    }


    public void setCacheType(CacheType t) {
        this.cacheType = t;
    }
    @Transient
    CacheType cacheType = CacheType.LongLiving;

    @Override
    public CacheType getCacheType() {
        return cacheType;
    }

    public static final class Info {
        public Info(UUID id, String name) {
            this.id = id;
            this.name = name;
        }

        UUID id;
        String name;
    }
    @Id
    //@GeneratedValue(generator = "uuid2")
    //@GenericGenerator(name = "id", strategy = "uuid2")
    private UUID id;

    @Column(name = DatabaseInfo.Game.Player.Name, unique = true, nullable = false)
    private String name;

    @Column(name = DatabaseInfo.Game.Player.AccountName, unique = true, nullable = false)
    private String account;

    @Column(name = DatabaseInfo.Game.Player.Money, nullable = false)
    private long money;

    @Column(name = DatabaseInfo.Game.Player.OfflineTs, nullable = false)
    private long offlineTs;

    @Column(name = DatabaseInfo.Game.Player.OnlineTs, nullable = false)
    private long onlineTs;

    @Embedded
    private Ground ground;

    @Embedded
    private GridIndex position;

    @Transient
    private City city;

    @Embedded
    private Storage bag;

    @Column(name = "bagCapacity", nullable = false)
    private int bagCapacity;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name="locked_money", joinColumns=@JoinColumn(name="player_id",referencedColumnName="id"))
    @MapKeyColumn(name = "transaction_id")
    //@Column(name="money", nullable = false)
    private Map<UUID, Long> lockedMoney = new HashMap<>();

    @Transient
    private GameSession session;

    public Player(String name, String account) {
        this.id = UUID.randomUUID();
        this.account = account;
        this.name = name;
        this.offlineTs = 0;
        this.money = 0;
        this.position = new GridIndex(0,0);
        this.ground = new Ground();
        this.bagCapacity = MetaData.getSysPara().playerBagCapcaity;
        this.bag = new Storage(bagCapacity);
        this.itemIdCanProduce = new TreeSet<>();
    }
    @PostLoad
    void _init() {
        this.bag.setCapacity(this.bagCapacity);
    }
    public Player() {
    }
    boolean extendBag() {
        int cost = 100;
        if(this.decMoney(100)) {
            this.bagCapacity += MetaData.getSysPara().bagCapacityDelta;
            return true;
        }
        return false;
    }
    IStorage getBag() {
        return bag;
    }
    public Gs.Role toProto() {
        Gs.Role.Builder builder = Gs.Role.newBuilder();
        builder.setId(Util.toByteString(id()))
            .setName(this.name)
            .setMoney(this.money)
            .setLockedMoney(this.lockedMoney())
            .setPosition(this.position.toProto())
            .setOfflineTs(this.offlineTs);
        city.forEachBuilding(id, (Building b)->{
            b.appendDetailProto(builder.getBuysBuilder());
        });
        builder.setBag(bag.toProto());
        builder.setBagCapacity(bagCapacity);
        this.itemIdCanProduce.forEach(id->builder.addItemIdCanProduce(id));
        this.exchangeFavoriteItem.forEach(id->builder.addExchangeCollectedItem(id));
        return builder.build();
    }

    private int lockedMoney() {
        return this.lockedMoney.values().stream().mapToInt(Number::intValue).sum();
    }

    public long money() {
        return money - lockedMoney();
    }
    public void setCity(City city) {
        this.city = city;
    }
    public void setSession(GameSession s) {
        this.session = s;
    }
    public void send(Package pack) {
        if(this.session != null)
            this.session.write(pack);
    }
    UUID id() {
        return id;
    }
    public void online() {
        this.onlineTs = System.currentTimeMillis();
        GameDb.saveOrUpdate(this);
    }
    public void offline(){
        this.offlineTs = System.currentTimeMillis();
        GameDb.saveOrUpdate(this);
    }

    String getAccount() {
        return account;
    }

    public boolean setPosition(GridIndex idx) {
        if(this.position.equals(idx))
            return false;
        GridIndex old = this.position;
        this.position = idx;
        city.relocation(this, old);
        return true;
    }
    public GridIndex gridIndex() {
        return this.position;
    }

    public void addGround(CoordPair area) {
        try {
            ground.add(area);
            city.mount(id, area);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void addGround(Collection<Coord> area) {
        try {
            ground.add(area);
            city.mount(id, area);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void lockMoney(UUID transactionId, long price) {
        Long p = lockedMoney.get(transactionId);
        if(p != null) {
            this.money += (p - price);
        }
        else {
            this.money -= price;
            lockedMoney.put(transactionId, price);
        }
    }
    public long unlockMoney(UUID transactionId) {
        Long p = lockedMoney.get(transactionId);
        if(p != null) {
            this.money += p;
            return p;
        }
        else {
            return 0;
        }
    }
    // should not warp db flush in function due to if an decMoney on player means there must has
    // addMoney on others units. Those dec consumeReserve should in an transaction, which means, db class
    // will has many function to cope with those different situation?
    public boolean decMoney(long cost) {
        if(cost > this.money)
            return false;
        this.money -= cost;
        return true;
    }
    public void addMoney(long cost) {
        this.money += cost;
    }
    public long spentLockMoney(UUID id) {
        return this.lockedMoney.remove(id);
    }

    public void groundBidingFail(UUID id, GroundAuction.Entry a) {
        int m = (int) this.unlockMoney(a.meta.id);
        GameDb.saveOrUpdate(this);
        this.send(Package.create(GsCode.OpCode.bidFailInform_VALUE, Gs.ByteNum.newBuilder().setId(Util.toByteString(a.meta.id)).setNum(m).build()));
    }

    public String getName() {
        return this.name;
    }

    public void collectExchangeItem(int itemId) {
        exchangeFavoriteItem.add(itemId);
    }
    public void unCollectExchangeItem(int itemId) {
        exchangeFavoriteItem.remove(itemId);
    }

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "player_exchange_favorite", joinColumns = @JoinColumn(name = "player_id"))
    private Set<Integer> exchangeFavoriteItem = new TreeSet<>();
}
