package Game.Gambling;

import Game.GameDb;
import Game.Player;
import Shared.LogDb;
import Shared.Package;
import com.google.protobuf.Message;
import gs.Gs;
import gscode.GsCode;
import org.hibernate.annotations.Cascade;

import javax.persistence.*;
import java.text.ParseException;
import java.util.*;

@Entity
public class FlightManager {
    public static final int ID = 0;



    private void newFightAction(Flight flight) {
        // broadcast to client?

    }

    public static void init(){
        GameDb.initThirdPartyDataSource();
        instance = GameDb.getThirdPartyDataSource();
    }
    public void update() {
        List<Flight> fs = ThirdPartyDataSource.instance().getAllFlight();
        for (Flight f : fs) {
            if(this.undeparturedFlights.containsKey(f.id))
                continue;
            this.undeparturedFlights.put(f.id, f);
        }

        Map<Integer, String> d = ThirdPartyDataSource.instance().getDepartured();
        Iterator<Map.Entry<Integer, Flight>> iterator = undeparturedFlights.entrySet().iterator();
        while(iterator.hasNext()) {
            Map.Entry<Integer, Flight> en = iterator.next();
            int id = en.getKey();
            Flight f = en.getValue();
            if(d.containsKey(id)) {
                f.filed_departuretime = d.get(id);
                iterator.remove();
                ThirdPartyDataSource.instance().clear(id);
                fightDepartureAction(f);
            }
        }
        GameDb.saveOrUpdate(this);
    }

    private void fightDepartureAction(Flight f) {
        final Gambling g = this.allGambling.get(f.id);
        if(g == null)
            return;
        int d = 0;
        try {
            d = f.getDelay();
        } catch (ParseException e) {
            e.printStackTrace();
        }
        final int l = d - 3;
        final int h = d + 3;

        Collection<Player> updates = new HashSet<>();
        for (Gambling.Info e : g.infos.values()) {
            Player p = GameDb.getPlayer(e.id); // player is not thread safe
            int s = -e.amount;
            if(e.delay >= l && e.delay <= h)
                s = e.amount;
            p.offsetScore(s);
            p.send(Package.create(GsCode.OpCode.flightBetInform_VALUE, Gs.FlightBetInform.newBuilder().setFlightId(f.id).setFiledDepartureTime(f.filed_departuretime).build()));
            updates.add(p);
            LogDb.flightBet(e.id, e.delay, e.amount, s<0?false:true, f.toProto());
        }
        GameDb.saveOrUpdate(updates);
    }

    @OneToMany(fetch = FetchType.EAGER)
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    @JoinColumn(name = "m_id")
    @MapKey(name = "id")
    private Map<Integer, Flight> undeparturedFlights = new HashMap<>();

    public boolean betFlight(UUID id, int flightId, int delay, int score) {
        if(!undeparturedFlights.containsKey(flightId))
            return false;
        Gambling g = this.allGambling.computeIfAbsent(flightId, k->new Gambling());
        if(g.infos.containsKey(id))
            return false;
        g.infos.put(id, new Gambling.Info(id, delay, score));
        return true;
    }

    public Gs.Flights toProto(UUID playerId) {
        Gs.Flights.Builder builder = Gs.Flights.newBuilder();
        for (Flight flight : this.undeparturedFlights.values()) {
            Gs.Flights.Flight.Builder b = builder.addFlightBuilder();
            b.setData(flight.toProto());
            b.setId(flight.id);
            Gambling g = this.allGambling.get(flight.id);
            if(g == null) {
                b.setSumBetAmount(0);
                b.setSumBetCount(0);
            }
            else {
                b.setSumBetAmount(g.amount());
                b.setSumBetCount(g.number());
                Gambling.Info i = g.infos.get(playerId);
                if(i != null) {
                    b.addMyBetBuilder().setAmount(i.amount).setDelay(i.delay);
                }
            }
        }
        return builder.build();
    }

    @Entity
    public static final class Gambling {
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
    private Map<Integer, Gambling> allGambling = new HashMap<>();
    private static FlightManager instance;
    public static FlightManager instance() {
        return instance;
    }

    protected FlightManager() {}


}
