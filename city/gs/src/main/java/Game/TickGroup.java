package Game;

import org.hibernate.annotations.Cascade;

import javax.persistence.*;
import java.util.*;

@Entity(name = "TickGroup")
public class TickGroup {
    public UUID getId() {
        return id;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    TickGroup(){}
    public TickGroup(TickManager mgr,long inTickInterval){
        tickInterval = inTickInterval;
        tickManager = mgr;
        _tickerList = new ArrayList<>();
    }

    public boolean add(Building bd){
        if(_tickerList.indexOf(bd) < 0){
            _tickerList.add(bd);
            return true;
        }
        return false;
    }
    public boolean del(Building bd){
        return  _tickerList.remove(bd);
    }
    public boolean isEmpty(){
        return _tickerList.size() == 0;
    }
    public void tick(long deltaTime){
        elapsTime += deltaTime;
        if(elapsTime < tickInterval){
            return;
        }
        elapsTime = 0;
        for (Building tickableBuilding : _tickerList) {
            tickableBuilding.tick(deltaTime);
        }
    }

    @ManyToOne
    private TickManager tickManager;
    private long tickInterval = 0;
    @OneToMany(fetch = FetchType.EAGER)
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    List<Building> _tickerList;
    @Transient
    private long elapsTime = 0;
}
