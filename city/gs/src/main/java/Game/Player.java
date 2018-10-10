package Game;

import Shared.DatabaseInfo;
import Shared.Package;
import Shared.Util;
import com.google.protobuf.ByteString;
import gs.Gs;
import gscode.GsCode;
import org.hibernate.annotations.MapKeyType;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = DatabaseInfo.Game.Player.Table, indexes = {
        @Index(name = "NAME_IDX", columnList = DatabaseInfo.Game.Player.Name),
        @Index(name = "ACCNAME_IDX", columnList = DatabaseInfo.Game.Player.AccountName)
    }
)
public class Player {
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

    @ElementCollection
    @CollectionTable(name="locked_money", joinColumns=@JoinColumn(name="player_id",referencedColumnName="id"))
    @MapKeyType(value=@Type(type="org.hibernate.type.PostgresUUIDType"))
    @MapKeyColumn(name = "transaction_id")
    @Column(name="money", nullable = false)
    private Map<UUID, Integer> lockedMoney = new HashMap<>();

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
    }

    public Player() {
    }

    public Gs.Role toProto() {
        return Gs.Role.newBuilder()
                .setId(Util.toByteString(id()))
                .setName(this.name)
                .setMoney(this.money)
                .setLockedMoney(this.lockedMoney())
                .setPosition(this.position.toProto())
                .setOfflineTs(this.offlineTs)
            .build();
    }

    private int lockedMoney() {
        return this.lockedMoney.values().stream().mapToInt(Number::intValue).sum();
    }

    public double money() {
        return money;
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
    public void lockMoney(UUID transactionId, int price) {
        Integer p = lockedMoney.get(transactionId);
        if(p != null) {
            this.money += (p - price);
        }
        else {
            this.money -= price;
            lockedMoney.put(transactionId, price);
        }
    }
    public int unlockMoney(UUID transactionId) {
        Integer p = lockedMoney.get(transactionId);
        if(p != null) {
            this.money += p;
            return p;
        }
        else {
            return 0;
        }
    }
    // should not warp db flush in function due to if an decMoney on player means there must has
    // addMoney on others units. Those dec add should in an transaction, which means, db class
    // will has many function to cope with those different situation?
    public boolean decMoney(int cost) {
        if(cost > this.money)
            return false;
        this.money -= cost;
        return true;
    }

    public int spentLockMoney(UUID id) {
        return this.lockedMoney.remove(id);
    }

    public void groundBidingFail(UUID id, GroundAuction.Entry a) {
        int m = this.unlockMoney(a.meta.id);
        GameDb.saveOrUpdate(this);
        this.send(Package.create(GsCode.OpCode.bidFailInform_VALUE, Gs.ByteNum.newBuilder().setId(Util.toByteString(a.meta.id)).setNum(m).build()));
    }

    public String getName() {
        return this.name;
    }
}
