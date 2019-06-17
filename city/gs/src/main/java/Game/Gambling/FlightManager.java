package Game.Gambling;

import Game.GameDb;
import Game.Player;
import Game.Timers.PeriodicTimer;
import Shared.LogDb;
import Shared.Package;
import gs.Gs;
import gscode.GsCode;
import org.hibernate.annotations.Cascade;

import javax.persistence.*;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Entity
public class FlightManager {
    public static final int ID = 0;
    @Id
    private final int id = ID;
    public static void init(){
        GameDb.initFlightManager();
        instance = GameDb.getFlightManager();
    }
    @Transient
    private PeriodicTimer timer = new PeriodicTimer((int) TimeUnit.SECONDS.toMillis(10));
    public void update(long diffNano) {
        if(!timer.update(diffNano))
            return;

        Map<String, String> d = ThirdPartyDataSource.instance().getDepartured();
        Iterator<Map.Entry<String, Flight>> iterator = flightInfos.entrySet().iterator();
        boolean changed = false;
        Collection updates = new ArrayList();
        while(iterator.hasNext()) {
            Map.Entry<String, Flight> en = iterator.next();
            String id = en.getKey();
            Flight f = en.getValue();
            if(d.containsKey(id)) {
                f.FlightDeptimeDate = d.get(id);
                iterator.remove();
                changed = true;
                ThirdPartyDataSource.instance().clear(id);
                updates.addAll(fightDepartureAction(f));
            }
            this.allGambling.remove(f.id);
        }
        if(changed)
            updates.add(this);
        GameDb.saveOrUpdate(updates);
    }

    private static final int DELAY_MINIUTE_TOLERANCE = 3;
    private Collection fightDepartureAction(Flight f) {
        final BetInfos g = this.allGambling.get(f.id);
        if(g == null)
            return null;
        int d = 0;
        try {
            d = f.getDelay();
        } catch (ParseException e) {
            e.printStackTrace();
        }
        final int l = d - DELAY_MINIUTE_TOLERANCE;
        final int h = d + DELAY_MINIUTE_TOLERANCE;

        Collection<Player> updates = new HashSet<>();
        for (BetInfos.Info e : g.infos.values()) {
            Player p = GameDb.getPlayer(e.id); // player is not thread safe
            int s = -e.amount;
            if(e.delay >= l && e.delay <= h)
                s = e.amount;
            p.offsetScore(s);
            p.send(Package.create(GsCode.OpCode.flightBetInform_VALUE, Gs.FlightBetInform.newBuilder().setFlightId(f.id).setFlightDeptimeDate(f.FlightDeptimeDate).build()));
            updates.add(p);
            LogDb.flightBet(e.id, e.delay, e.amount, s<0?false:true, f.toProto());
        }
        return updates;
    }

    @OneToMany(fetch = FetchType.EAGER)
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    @JoinColumn(name = "m_id")
    @MapKey(name = "id")
    private Map<String, Flight> flightInfos = new HashMap<>();

    public boolean betFlight(UUID playerId, Flight flight, int delay, int score) {
        flightInfos.putIfAbsent(flight.id, flight);
        BetInfos g = this.allGambling.computeIfAbsent(flight.id, k->new BetInfos());
        if(g.infos.containsKey(playerId))
            return false;
        g.infos.put(playerId, new BetInfos.Info(playerId, delay, score));
        ThirdPartyDataSource.instance().trackDeparture(flight.id, flight.getDate());
        return true;
    }

    public Gs.Flights toProto(UUID playerId) {
        Gs.Flights.Builder builder = Gs.Flights.newBuilder();
        for (Flight flight : this.flightInfos.values()) {
            Gs.Flights.Flight.Builder b = builder.addFlightBuilder();
            b.setData(flight.toProto());
            b.setId(flight.id);
            BetInfos g = this.allGambling.get(flight.id);
            if(g == null) {
                b.setSumBetAmount(0);
                b.setSumBetCount(0);
            }
            else {
                b.setSumBetAmount(g.amount());
                b.setSumBetCount(g.number());
                BetInfos.Info i = g.infos.get(playerId);
                if(i != null) {
                    b.getMyBetBuilder().setAmount(i.amount).setDelay(i.delay);
                }
            }
        }
        return builder.build();
    }

    @Entity
    public static final class BetInfos {
        @Id
        int fightId;
        @Embeddable
        public static final class Info {
            UUID id;
            int delay;
            int amount;

            public Info(UUID id, int delay, int amount) {
                this.id = id;
                this.delay = delay;
                this.amount = amount;
            }
        }
        @ElementCollection(fetch = FetchType.EAGER)
        @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
        Map<UUID, Info> infos = new HashMap<>();
        int amount() {
            return infos.values().stream().mapToInt(i->i.amount).sum();
        }
        int number() {
            return infos.size();
        }
    }
    @OneToMany(fetch = FetchType.EAGER)
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    @JoinColumn(name = "m_id")
    @MapKey(name = "fightId")
    private Map<String, BetInfos> allGambling = new HashMap<>();
    private static FlightManager instance;
    public static FlightManager instance() {
        return instance;
    }

    public FlightManager() {}
}
