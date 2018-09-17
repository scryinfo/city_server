package Game;

import Shared.Package;
import org.bson.types.ObjectId;

import java.util.HashMap;
import java.util.HashSet;
import java.util.function.Consumer;

public class Grid {
    public static final int SYNC_RANGE_NUM = 9;
    public static final int SYNC_RANGE_DELTA = (int) (Math.sqrt(SYNC_RANGE_NUM) - 1);
    private int x;
    private int y;
    private HashMap<ObjectId, Building> buildings = new HashMap<>();
    private HashSet<ObjectId> playerIds = new HashSet<>();
    public Grid(int x, int y) {
        this.x = x;
        this.y = y;
    }
    public void add(Building b) {
        this.buildings.put(b.id(), b);
    }
    public void del(Building b) {
        this.buildings.remove(b.id());
    }
    public void playerComing(ObjectId id) {
        this.playerIds.add(id);
    }
    public void playerLeaving(ObjectId id) {
        this.playerIds.remove(id);
    }
    void send(Package pack) {
        playerIds.forEach(id->{
            GameSession s = GameServer.allGameSessions.get(id);
            if(s != null)
                s.write(pack);
        });
    }
    public boolean hasBuilding(ObjectId id) {
        return this.buildings.containsKey(id);
    }
    public void forAllBuilding(Consumer<Building> f) {
        this.buildings.values().forEach(f);
    }
}
