package Game;

import Game.IStorage;
import Game.ItemKey;
import gs.Gs;
import org.checkerframework.checker.units.qual.A;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.SelectBeforeUpdate;

import javax.persistence.*;
import java.util.*;

/*Database (the opened data is equivalent to the previous warehouse)*/
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
    private Map<ItemKey, Integer> inHand = new HashMap<>();      //The actual technology

    @ElementCollection(fetch = FetchType.EAGER)
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    private Map<ItemKey, Integer> locked = new HashMap<>();      //Technology for sale



    public List<Gs.ScienceStoreItem> toProto(){
        /*Combine 2 tables*/
        List<Gs.ScienceStoreItem> list = new ArrayList<>();
        List<ItemKey> totalKey = new ArrayList<>();
        this.inHand.forEach((k,v)->{
            Gs.ScienceStoreItem.Builder builder = Gs.ScienceStoreItem.newBuilder();
            builder.setItemKey(k.toProto()).setStoreNum(v).setLockedNum(this.locked.getOrDefault(k, 0));
            totalKey.add(k);
            list.add(builder.build());
        });
        for (Map.Entry<ItemKey, Integer> lock : this.locked.entrySet()) {
            if(totalKey.contains(lock.getKey()))
                continue;
            Gs.ScienceStoreItem.Builder builder = Gs.ScienceStoreItem.newBuilder();
            builder.setItemKey(lock.getKey().toProto()).setStoreNum(0).setLockedNum(lock.getValue());
            list.add(builder.build());
        }
        return list;
    }

    public int getAllNum(){/*Return all the stock quantity in warehouse*/
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
        if(inHand.get(m)==0)
            inHand.remove(m);
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

    public void cleanData(){
        this.inHand.clear();
        this.locked.clear();
    }

    public long getLockedNum(ItemKey key){
        return this.locked.getOrDefault(key, 0);
    }
}
