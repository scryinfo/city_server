package Game;

import DB.Db;
import com.google.protobuf.InvalidProtocolBufferException;
import gs.Gs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class Storage {
    public Storage(int capacity) {
        Capacity = capacity;
    }
    public Storage(byte[] bin, int capacity) throws InvalidProtocolBufferException {
        Db.Store.parseFrom(bin).getStoreMap().forEach((k, v)-> {
            this.store.put(MetaData.getItem(k), v);
        });
        this.Capacity = capacity;
    }
    public byte[] binary() {
        Db.Store.Builder builder = Db.Store.newBuilder();
        this.store.forEach((k,v)->{
            builder.putStore(k.id, v);
        });
        return builder.build().toByteArray();
    }
    public Collection<Gs.IntNum> toProto() {
        Collection<Gs.IntNum> res = new ArrayList<>();
        this.store.forEach((k,v)->{
            res.add(Gs.IntNum.newBuilder().setId(k.id).setNum(v).build());
        });
        return res;
    }
    public boolean offset(MetaItem item, final int n) {
        if(n == 0)
            return true;
        else if(n > 0) {
            if(item.size*n > availSize())
                return false;
            this.store.put(item, this.store.getOrDefault(item, 0)+n);
        }
        else if(n < 0) {
            Integer c = this.store.get(item);
            if(c == null || c < -n)
                return false;
            this.store.put(item, c+n);
        }
        return true;
    }
    public final int Capacity;
    private Map<MetaItem, Integer> store = new HashMap<>();
    public int usedSize() {
        int res = 0;
        for(Map.Entry<MetaItem, Integer> e : store.entrySet()) {
            res += e.getKey().size * e.getValue();
        }
        return res;
    }
    public int availSize() {
        return Capacity - usedSize();
    }
    public boolean full() {
        return Capacity == usedSize();
    }
}
