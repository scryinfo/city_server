package Game;

import Shared.Package;

import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;
import java.util.function.Consumer;

public class Grid {
    public static final int SYNC_RANGE_NUM = 9;
    public static final int SYNC_RANGE_DELTA = (int) (Math.sqrt(SYNC_RANGE_NUM) - 1);
    private int x;
    private int y;
    private HashMap<UUID, Building> buildings = new HashMap<>();
    private HashSet<UUID> playerIds = new HashSet<>();
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
    public void playerComing(UUID id) {
        this.playerIds.add(id);
    }
    public void playerLeaving(UUID id) {
        this.playerIds.remove(id);
    }
    void send(Package pack) {
        playerIds.forEach(id->{
            GameSession s = GameServer.allGameSessions.get(id);
            if(s != null)
                s.write(pack);
        });
    }
    public boolean hasBuilding(UUID id) {
        return this.buildings.containsKey(id);
    }
    public void forAllBuilding(Consumer<Building> f) {
        this.buildings.values().forEach(f);
    }
}
