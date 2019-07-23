package Game.Technology;

import Game.Item;
import Game.ItemKey;
import gs.Gs;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.SelectBeforeUpdate;

import javax.persistence.*;
import java.util.*;

/*存放未开启的资料*/
@Entity
@SelectBeforeUpdate(false)
public class SciencevBox {
    @Id
    @GeneratedValue
    private UUID id;

    public SciencevBox() {
    }

    @ElementCollection(fetch = FetchType.EAGER)
    @Cascade(value={org.hibernate.annotations.CascadeType.ALL})
    private Map<ItemKey, Integer> allBox = new HashMap<>();      //实际有的科技

    /*未开启宝箱的proto*/
    public List<Gs.Item> toProto(){
        List<Gs.Item> boxInfo = new ArrayList<>();
        allBox.forEach((k,v)->{
            Gs.Item.Builder builder = Gs.Item.newBuilder();
            builder.setKey(k.toProto()).setN(v);
            boxInfo.add(builder.build());
        });
        return boxInfo;
    }

    public UUID getId() {
        return id;
    }

    public Map<ItemKey, Integer> getAllBox() {
        return allBox;
    }
    public Integer getTypeBoxNum(ItemKey key){
        return allBox.getOrDefault(key, 0);
    }

    public void offSet(ItemKey key,int num){
        this.allBox.put(key, num);
    }
}
