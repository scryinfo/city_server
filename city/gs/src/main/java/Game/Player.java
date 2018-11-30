package Game;

import Game.Listener.EvictListener;
import Shared.DatabaseInfo;
import Shared.Package;
import Shared.Util;
import gs.Gs;
import gscode.GsCode;
import org.hibernate.annotations.SelectBeforeUpdate;

import javax.persistence.*;
import java.util.*;

@Entity
@SelectBeforeUpdate(false)
@Table(name = DatabaseInfo.Game.Player.Table, indexes = {
        @Index(name = "NAME_IDX", columnList = DatabaseInfo.Game.Player.Name),
        @Index(name = "ACCNAME_IDX", columnList = DatabaseInfo.Game.Player.AccountName)
    }
)
@EntityListeners({
        EvictListener.class,
})
public class Player {
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Player player = (Player) o;
        return Objects.equals(id, player.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public static final UUID BAG_ID = UUID.fromString("a33eab42-cb75-4c77-bd27-710d299f5591");

    public boolean hasItem(int mId, int lv) {
        if(goodLv.keySet().contains(mId))
            return goodLv.get(mId) >= lv;
        return false;
    }
    public boolean hasItem(int mId) {
        return goodLv.keySet().contains(mId);
    }
    public void markTemp() {
        this.temp = true;
    }
    @Transient
    boolean temp = false;

    public boolean isTemp() {
        return temp;
    }

    public void addItem(int mId, int lv) {
        Integer level = goodLv.get(mId);
        if(level != null && level < lv)
            return;
        goodLv.put(mId, lv);

        // create role
        if(city != null) {
            city.forEachBuilding(id(), b -> {
                if (b instanceof FactoryBase)
                    ((FactoryBase) b).updateLineQuality(mId, lv);
            });
            this.send(Shared.Package.create(GsCode.OpCode.newItem_VALUE, Gs.IntNum.newBuilder().setId(mId).setNum(lv).build()));
        }
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

    // for player, it position is GridIndex, Coordinate is too fine-grained
    @Embedded
    private GridIndex position;

    @Transient
    private City city;

    @OneToOne(cascade=CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "bag_id")
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
        this.bagCapacity = MetaData.getSysPara().playerBagCapcaity;
        this.bag = new Storage(bagCapacity);
    }
    @PostLoad
    void _init() {
        this.bag.setCapacity(this.bagCapacity);
    }
    protected Player() {}
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
        this.exchangeFavoriteItem.forEach(id->builder.addExchangeCollectedItem(id));
        builder.addAllGround(GroundManager.instance().getGroundProto(id()));
        builder.setBagId(Util.toByteString(BAG_ID));

        builder.addAllBuildingBrands(BrandManager.instance().getBuildingBrandProto(id()));
        builder.addAllGoodBrands(BrandManager.instance().getGoodBrandProto(id()));
        goodLv.forEach((k,v)->builder.addGoodLv(Gs.IntNum.newBuilder().setId(k).setNum(v)));
        return builder.build();
    }

    private long lockedMoney() {
        return this.lockedMoney.values().stream().mapToLong(Number::longValue).sum();
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
    public GridIndex getPosition() {
        return this.position;
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
        sendMoney();
    }
    public long unlockMoney(UUID transactionId) {
        Long p = lockedMoney.get(transactionId);
        if(p != null) {
            this.money += p;
            sendMoney();
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
        sendMoney();
        return true;
    }
    public void addMoney(long cost) {
        this.money += cost;
        sendMoney();
    }
    private void sendMoney() {
        this.send(Package.create(GsCode.OpCode.moneyChange_VALUE, Gs.MoneyChange.newBuilder().setMoney(money()).setLockedMoney(this.lockedMoney()).build()));
    }
    public long spentLockMoney(UUID id) {
        long m = this.lockedMoney.remove(id);
        sendMoney();
        return m;
    }

    public void groundBidingFail(UUID id, GroundAuction.Entry a) {
        int m = (int) this.unlockMoney(a.meta.id);
        GameDb.saveOrUpdate(this);
        this.send(Package.create(GsCode.OpCode.bidFailInform_VALUE, Gs.ByteNum.newBuilder().setId(Util.toByteString(a.meta.id)).setNum(a.price).build()));
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

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "player_exchange_favorite", joinColumns = @JoinColumn(name = "player_id"))
    private Set<Integer> exchangeFavoriteItem = new TreeSet<>();

    public int getGoodLevel(int mId) {
        return goodLv.get(mId);
    }

    @ElementCollection(fetch = FetchType.EAGER)
    @MapKeyColumn(name = "mid")
    @CollectionTable(name = "player_good_lv", joinColumns = @JoinColumn(name = "player_id"), indexes = { @Index(name = "TOP_QTY", columnList = "goodlv") })
    private Map<Integer, Integer> goodLv = new HashMap<>();
}