package Game.Listener;

import Game.Exchange;
import Game.GameDb;
import Game.Player;

import javax.persistence.PostPersist;
import javax.persistence.PostUpdate;

public class EvictListener {
    @PostUpdate
    @PostPersist
    public void callback(Object obj) {
        if(obj instanceof Player) {
            Player p = (Player)obj;
            if(p.isTemp())
                GameDb.evict(p);
        }
        else if(obj instanceof Exchange.DealLog)
            GameDb.evict(obj); // can not let it be persist by stateless session, it need to saved by other entities. so evict it here
    }
}
