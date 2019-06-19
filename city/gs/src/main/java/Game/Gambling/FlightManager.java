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
    @PostLoad
    void _init() {
        if(!this.allGambling.isEmpty()) {
            List<ThirdPartyDataSource.TrackInfo> i = new ArrayList<>();
            this.allGambling.forEach((k,v)->{
                i.add(new ThirdPartyDataSource.TrackInfo(k, v.date));
                ThirdPartyDataSource.instance().trackDeparture(k, v.date); // let update do the departure action, delay it
            });
            this.flightInfos.putAll(ThirdPartyDataSource.instance().getFlights(i));
        }
    }
    @Transient
    private PeriodicTimer timer = new PeriodicTimer((int) TimeUnit.SECONDS.toMillis(10));
    public void update(long diffNano) {
        if(!timer.update(diffNano))
            return;
        Collection updates = new ArrayList();
        Map<String, String> d = ThirdPartyDataSource.instance().getDepartured();
        if(!d.isEmpty()) {
            for (Map.Entry<String, String> e : d.entrySet()) {
                String id = e.getKey();
                String date = e.getValue();

                Flight flight = this.flightInfos.remove(id);
                flight.FlightDeptimeDate = date;


                ThirdPartyDataSource.instance().clear(id);
                updates.addAll(fightDepartureAction(flight));
            }
            updates.add(this);
            GameDb.saveOrUpdate(updates);
        }
    }

    private static final int DELAY_MINUTE_TOLERANCE = 3;
    private Collection fightDepartureAction(Flight f) {
        final BetInfos g = this.allGambling.remove(f.id);
        if(g == null)
            return null;
        int d = 0;
        try {
            d = f.getDelayMinute();
        } catch (ParseException e) {
            e.printStackTrace();
        }
        final int l = d - DELAY_MINUTE_TOLERANCE;
        final int h = d + DELAY_MINUTE_TOLERANCE;

        Collection<Player> updates = new HashSet<>();
        for (Map.Entry<UUID, BetInfos.Info> e : g.infos.entrySet()) {
            UUID playerId = e.getKey();
            BetInfos.Info info = e.getValue();
            Player p = GameDb.getPlayer(playerId); // player is not thread safe
            int s = -info.amount;
            if(info.delay >= l && info.delay <= h)
                s = info.amount;
            p.offsetScore(s);
            p.send(Package.create(GsCode.OpCode.flightBetInform_VALUE, Gs.FlightBetInform.newBuilder().setFlightId(f.id).setFlightDeptimeDate(f.FlightDeptimeDate).build()));
            updates.add(p);
            LogDb.flightBet(playerId, info.delay, info.amount, s<0?false:true, f.toProto());
        }
        return updates;
    }

    @Transient
    private Map<String, Flight> flightInfos = new HashMap<>();

    public boolean betFlight(UUID playerId, Flight flight, int delay, int score) {
        flightInfos.putIfAbsent(flight.id, flight);
        BetInfos g = this.allGambling.computeIfAbsent(flight.id, k->new BetInfos(flight.id, flight.getDate()));
        if(g.infos.containsKey(playerId))
            return false;
        g.infos.put(playerId, new BetInfos.Info(delay, score));
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
        public BetInfos(String fightId, String date) {
            this.fightId = fightId;
            this.date = date;
        }

        protected BetInfos() {}

        @Id
        String fightId;
        String date;

        @Embeddable
        public static final class Info {
            int delay;
            int amount;

            public Info(int delay, int amount) {
                this.delay = delay;
                this.amount = amount;
            }
            protected Info(){}
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
    @OneToMany(fetch = FetchType.EAGER, orphanRemoval = true)
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
