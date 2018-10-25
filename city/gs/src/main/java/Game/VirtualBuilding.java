//package Game;
//
//import com.google.protobuf.Message;
//import gs.Gs;
//
//import javax.persistence.Embedded;
//import javax.persistence.Entity;
//import javax.persistence.PostLoad;
//import javax.persistence.Transient;
//import java.util.UUID;
//import java.util.concurrent.TimeUnit;
//
//@Entity(name = "VirtualBuilding")
//public class VirtualBuilding extends Building {
//    public VirtualBuilding(MetaVirtualBuilding meta, Coord pos, UUID ownerId) {
//        super(meta.meta, pos, ownerId);
//        this.meta = meta;
//        this.store = new Storage(meta.totalCapacity());
//        this.state = Gs.BuildingState.COLLECT_MATERIAL_VALUE;
//    }
//    @Transient
//    private MetaVirtualBuilding meta;
//
//    @Embedded
//    private Storage store;
//
//    private long completeTs = 0;
//
//    @PostLoad
//    private void _1() {
//        this.meta = MetaData.getVirtualBuilding(this._d.metaId);
//        this.metaBuilding = this.meta.meta;
//        this.store.setCapacity(meta.totalCapacity());
//    }
//
//    boolean offset(MetaMaterial m, int n) {
//        if(!this.meta.valid(m))
//            return false;
//        if(n > 0) {
//             if(this.store.size(m) + n > this.meta.size(m))
//                 return false;
//        }
//        if(this.store.offset(m, n)) {
//            if (n > 0 && store.full() && state != Gs.BuildingState.WAITING_CONSTRUCT_VALUE) {
//                state = Gs.BuildingState.WAITING_CONSTRUCT_VALUE;
//                this.broadcastChange();
//            }
//            else if(n < 0 && state == Gs.BuildingState.WAITING_CONSTRUCT_VALUE) {
//                state = Gs.BuildingState.COLLECT_MATERIAL_VALUE;
//                this.broadcastChange();
//            }
//            return true;
//        }
//        return false;
//    }
//    boolean construct() {
//        if(state != Gs.BuildingState.WAITING_CONSTRUCT_VALUE)//(!store.full())
//            return false;
//        completeTs = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(this.meta.constructSec);
//        state = Gs.BuildingState.CONSTRUCTING_VALUE;
//        return true;
//    }
//    @Override
//    public Message detailProto() {
//        return null;
//    }
//
//    @Override
//    protected void _update(long diffNano) {
//        if(completeTs > 0 && System.currentTimeMillis() >= completeTs)
//            state = Gs.BuildingState.WAITING_OPEN_VALUE;
//    }
//
//    public Gs.BuildingInfo toProto() {
//        Gs.BuildingInfo i = super.toProto();
//        if(state == Gs.BuildingState.CONSTRUCTING_VALUE)
//            i = i.toBuilder().setConstructCompleteTs((int) TimeUnit.MILLISECONDS.toSeconds(completeTs)).build();
//        return i;
//    }
//
//    public boolean startBusiness() {
//        if(state != Gs.BuildingState.WAITING_OPEN_VALUE)
//            return false;
//        return true;
//    }
//    int linkBuildingId() {
//        return meta.meta.id;
//    }
//}
