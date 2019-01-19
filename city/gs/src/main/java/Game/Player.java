package Game;

import Game.FriendManager.FriendManager;
import Game.Listener.EvictListener;
import Game.Meta.MetaData;
import Shared.DatabaseInfo;
import Shared.LogDb;
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
        if(level != null && level >= lv)
            return;
        goodLv.put(mId, lv);
        itemChangeAction(mId, lv);
    }

    private void itemChangeAction(int mId, int lv) {
        if (city != null) { // null when role just be created
            city.forEachBuilding(id(), b -> {
                if (b instanceof FactoryBase)
                    ((FactoryBase) b).updateLineQuality(mId, lv);
            });
            this.send(Package.create(GsCode.OpCode.newItem_VALUE, Gs.IntNum.newBuilder().setId(mId).setNum(lv).build()));
        }
    }

    public OptionalInt addItemLv(int mId, int adds) {
        Integer level = goodLv.get(mId);
        if(level == null)
            return OptionalInt.empty();
        int lv = level+adds;
        goodLv.put(mId, lv);
        itemChangeAction(mId, lv);
        return OptionalInt.of(lv);
    }

    public void setDes(String str) {
        this.des = str;
    }

//    public void addTalent(Talent t) {
//        talentIds.add(t.id());
//        TalentManager.instance().add(t);
//        this.send(Package.create(GsCode.OpCode.newTalentInform_VALUE, t.toProto()));
//    }
//    public void delTalent(Talent t) {
//        talentIds.remove(t.id());
//        TalentManager.instance().delete(t);
//        GameDb.saveOrUpdateAndDelete(Arrays.asList(this), Arrays.asList(t));
//        this.send(Package.create(GsCode.OpCode.delTalentInform_VALUE, Gs.Id.newBuilder().setId(Util.toByteString(t.id())).build()));
//    }
//    public boolean hasTalent(UUID id) {
//        return talentIds.contains(id);
//    }
//    @ElementCollection
//    private Set<UUID> talentIds = new HashSet<>();

    public static final class Info {
        public Info(UUID id, String name,String companyName,boolean male,String des,int faceId)
        {
            this.id = id;
            this.name = name;
            this.companyName = companyName;
            this.male = male;
            this.des = des;
            this.faceId = faceId;
        }
        UUID id;
        String name;
        String companyName;
        String des;
        boolean male;
        int faceId;

        public String getDes()
        {
            return des;
        }

        public boolean isMale()
        {
            return male;
        }

        public int getFaceId()
        {
            return faceId;
        }

        public String getCompanyName()
        {
            return companyName;
        }

        public UUID getId()
        {
            return id;
        }

        public void setId(UUID id)
        {
            this.id = id;
        }

        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }
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

    @Column
    private String des = "";

    @Column
    private String companyName;

    @Column
    private boolean male;

    @Column
    private int faceId = 0;
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

    public Player(String name, String account, boolean male, String companyName, int faceId) {
        this.id = UUID.randomUUID();
        this.account = account;
        this.name = name;
        this.companyName = companyName;
        this.male = male;
        this.offlineTs = 0;
        this.money = 0;
        this.faceId = faceId;
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
            LogDb.extendBag(id, money, cost, bagCapacity);
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
                .setCompanyName(companyName)
                .setMale(this.male)
                .setDes(this.des)
                .setMoney(this.money)
                .setFaceId(this.faceId)
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
        builder.addAllRentGround(GroundManager.instance().getRentGroundProto(id()));
        builder.setBagId(Util.toByteString(BAG_ID));
        builder.addAllBuildingBrands(BrandManager.instance().getBuildingBrandProto(id()));
        builder.addAllGoodBrands(BrandManager.instance().getGoodBrandProto(id()));
        goodLv.forEach((k,v)->builder.addGoodLv(Gs.IntNum.newBuilder().setId(k).setNum(v)));

        builder.addAllFriends(FriendManager.getInstance().getFriends(this.id));
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
    public UUID id() {
        return id;
    }
    public void online() {
        this.onlineTs = System.currentTimeMillis();
        GameDb.saveOrUpdate(this);
        talentIds = TalentManager.instance().getTalentIdsByPlayerId(this.id());
    }
    public void offline(){
        this.offlineTs = System.currentTimeMillis();
        GameDb.saveOrUpdate(this);
        if(!this.talentIds.isEmpty()) {
            TalentManager.instance().unload(this.talentIds);
        }
    }
    @Transient
    Collection<UUID> talentIds;

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
        //更高出价通知
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
        MailBox.instance().sendMail(Mail.MailType.LAND_AUCTION_HIGHER.getMailType(),id,null,landCoordinates);
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

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "player_blacklist", joinColumns = @JoinColumn(name = "player_id"))
    private Set<UUID> blacklist = new HashSet<>();

    public Set<UUID> getBlacklist() { return blacklist; }

    public String getCompanyName()
    {
        return companyName;
    }

    public String getDes()
    {
        return des;
    }

    public void setCompanyName(String companyName)
    {
        this.companyName = companyName;
    }

    public int getFaceId()
    {
        return faceId;
    }

    public void setFaceId(int faceId)
    {
        this.faceId = faceId;
    }

    public boolean isMale()
    {
        return male;
    }

    public void setMale(boolean male)
    {
        this.male = male;
    }
}