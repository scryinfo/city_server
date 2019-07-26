package Game;

import Game.IStorage;
import Game.ItemKey;
import gs.Gs;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.SelectBeforeUpdate;

import javax.persistence.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/*资料库（已开启的资料，等同于以前的仓库）*/
@Entity
@SelectBeforeUpdate(false)
public class ScienceStore{
    @Id
    @GeneratedValue
    private UUID id;

    public ScienceStore() {
    }
    @ElementCollection(fetch = FetchType.EAGER)
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    private Map<ItemKey, Integer> inHand = new HashMap<>();      //实际有的科技

    @ElementCollection(fetch = FetchType.EAGER)
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    private Map<ItemKey, Integer> locked = new HashMap<>();      //出售中的科技

    public Gs.ScienceStore toProto(){
        Gs.ScienceStore.Builder builder = Gs.ScienceStore.newBuilder();
        this.inHand.forEach((k, v)->{
            builder.addInHand(Gs.Item.newBuilder().setKey(k.toProto()).setN(v));
        });
        this.locked.forEach((k, v)->{
            builder.addLocked(Gs.Item.newBuilder().setKey(k.toProto()).setN(v));
        });
        return builder.build();
    }

    public int getAllNum(){/*返回仓库的库存所有数量*/
        int inhand = this.inHand.entrySet().stream().mapToInt(e -> e.getValue()).sum();
        int locked = this.locked.entrySet().stream().mapToInt(e -> e.getValue()).sum();
        return inhand + locked;
    }

    public boolean offset(ItemKey item, int n) {
        if(n == 0)
            return true;
        else if(n > 0) {
            this.inHand.put(item, this.inHand.getOrDefault(item, 0)+n);
        }
        else if(n < 0) {
            Integer c = this.inHand.get(item);
            if(c == null || c < -n)
                return false;
            this.inHand.put(item, c+n);
            if(this.inHand.get(item) == 0)
                this.inHand.remove(item);
        }
        return true;
    }

    public boolean unLock(ItemKey m, int n) {
        if(n!=0) {
            Integer i = this.locked.get(m);
            if (i == null || i < n)
                return false;
            this.locked.put(m, i - n);
            if (i == n)
                this.locked.remove(m);
            this.inHand.put(m, this.inHand.getOrDefault(m, 0) + n);
        }
        return true;
    }

    public void consumeLock(ItemKey m, int n) {
        locked.put(m, locked.get(m) - n);
        if(locked.get(m) == 0)
            locked.remove(m);
    }
    public boolean consumeInHand(ItemKey m, int n){
        if(!has(m,n)){
            return false;
        }
        this.inHand.put(m, inHand.get(m) - n);
        return true;
    }

    public int getItemCount(ItemKey key) {
       return inHand.getOrDefault(key, 0);
    }

    public boolean has(ItemKey m, int n) {
        return inHand.get(m)==null?false:inHand.get(m) >= n;
    }
    public boolean lock(ItemKey m, int n) {
        if(!has(m, n))
            return false;
        locked.put(m, locked.getOrDefault(m, 0) + n);
        this.inHand.put(m, this.inHand.getOrDefault(m, 0) - n);
        if(this.inHand.get(m) == 0)
            this.inHand.remove(m);
        return true;
    }
}
