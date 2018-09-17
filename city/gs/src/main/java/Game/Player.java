package Game;

import Shared.Package;
import Shared.RoleBriefInfo;
import Shared.RoleFieldName;
import Shared.Util;
import com.google.protobuf.ByteString;
import gs.Gs;
import gscode.GsCode;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.*;

public class Player {
    private ObjectId id;
    private String name;
    private String account;
    private long money;
    private HashMap<ObjectId, Building> buildings;
    private long offlineTs;
    private long onlineTs;
    private Ground ground;
    private GridIndex position;
    private City city;
    private HashMap<ObjectId, Integer> lockedMoney = new HashMap<>();
    private GameSession session;
    public Player(String name, String account) {
        this.id = new ObjectId();
        this.account = account;
        this.name = name;
        this.offlineTs = 0;
        this.money = 0;
        this.position = new GridIndex(0,0);

    }
    public Player(Document doc){
        this.id = doc.getObjectId("_id");
        this.onlineTs = doc.getLong(RoleFieldName.OnlineTsFieldName);
        this.offlineTs = doc.getLong(RoleFieldName.OfflineTsFieldName);
        this.name = doc.getString(RoleFieldName.NameFieldName);
        this.account = doc.getString(RoleFieldName.AccountNameFieldName);
        this.money = doc.getLong("money");
        this.position = new GridIndex((Document)doc.get("coord"));
        for(Document d : (List<Document>) doc.get("lockMoney")) {
            ObjectId tid = d.getObjectId("tid");
            int m = d.getInteger("m");
            this.lockedMoney.put(tid, m);
        }
    }
    public Gs.Role toProto() {
        return Gs.Role.newBuilder()
                .setId(ByteString.copyFrom(id.toByteArray()))
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
    ObjectId id() {
        return id;
    }
    public void online() {
        this.onlineTs = System.currentTimeMillis();
        GameDb.updatePlayerOnlineTs(this.id, this.onlineTs);
    }
    public void offline(){
        this.offlineTs = System.currentTimeMillis();
        GameDb.updatePlayerOfflineTs(this.id, this.offlineTs);
    }
    Document toBson() {
        List<Document> lockMoneyArr = new ArrayList<>();
        for(Map.Entry<ObjectId, Integer> e : lockedMoney.entrySet()) {
            Document d = new Document();
            d.append("tid", e.getKey());
            d.append("m", e.getValue());
            lockMoneyArr.add(d);
        }
        Document doc = new Document()
                //.append("_id", this.id)
                //.append("acc", this.account)
                //.append("name", this.name)
                .append("coord", this.position.toBson())
                .append("money", this.money)
                .append("lockMoney", lockMoneyArr);
        return doc;
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
    public void lockMoney(ObjectId transactionId, int price) {
        Integer p = lockedMoney.get(transactionId);
        if(p != null) {
            this.money += (p - price);
        }
        else {
            this.money -= price;
            lockedMoney.put(transactionId, price);
        }
    }
    public int unlockMoney(ObjectId transactionId) {
        Integer p = lockedMoney.get(transactionId);
        if(p != null) {
            this.money += p;
            return p;
        }
        else {
            return 0;
        }
    }
    public boolean decMoney(int cost) {
        if(cost > this.money)
            return false;
        this.money -= cost;
        return true;
    }

    public void bidWin(GroundAuction.Auction a) {
        this.addGround(a.meta.area);
        int p = this.spentLockMoney(a.meta.id);
        GameDb.update(this);
        this.send(Package.create(GsCode.OpCode.bidWinInform_VALUE, Gs.IdNum.newBuilder().setId(Util.toByteString(a.meta.id)).setNum(p).build()));
    }

    private int spentLockMoney(ObjectId id) {
        return this.lockedMoney.remove(id);
    }

    public void groundBidingFail(ObjectId id, GroundAuction.Auction a) {
        int m = this.unlockMoney(a.meta.id);
        GameDb.update(this);
        this.send(Package.create(GsCode.OpCode.bidFailInform_VALUE, Gs.IdNum.newBuilder().setId(Util.toByteString(a.meta.id)).setNum(m).build()));
    }

    public RoleBriefInfo briefInfo() {
        RoleBriefInfo res = new RoleBriefInfo();
        res.id = this.id;
        res.name = this.name;
        return res;
    }

    public String getName() {
        return this.name;
    }
}
