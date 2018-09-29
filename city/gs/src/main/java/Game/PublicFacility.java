package Game;

import com.google.protobuf.Message;
import org.bson.Document;
import org.bson.types.ObjectId;

import javax.persistence.Entity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@Entity(name = "PublicFacility")
public class PublicFacility extends Building {
    private MetaPublicFacility meta;
    private int rent;
    private HashMap<ObjectId, List<Contract>> contract = new HashMap<ObjectId, List<Contract>>();

    @Override
    public Message detailProto() {
        return null;
    }

    class Contract {
        Contract(int days){
            this.days = days;
        }
        int days;
    }
    class GoodContract extends Contract {
        int goodId;

        public GoodContract(int days, int goodId) {
            super(days);
            this.goodId = goodId;
        }
    }
    class BuildingContract extends Contract {
        ObjectId buildingId;

        public BuildingContract(int days, ObjectId buildingId) {
            super(days);
            this.buildingId = buildingId;
        }
    }
    public PublicFacility(MetaPublicFacility meta, Coord pos, UUID ownerId) {
        super(meta, pos, ownerId);
        this.meta = meta;
    }
    int leftAdNum() {
        return meta.adNum - contract.size();
    }
    public boolean adBuilding(ObjectId buildingId, ObjectId roleId, int days) {
        if(this.leftAdNum() == 0)
            return false;
        List<Contract> cs = this.contract.getOrDefault(roleId, new ArrayList<>());
        cs.add(new BuildingContract(days, buildingId));
        return true;
    }
    public boolean adGood(int goodId, ObjectId roleId, int days) {
        if(this.leftAdNum() == 0)
            return false;
        List<Contract> cs = this.contract.getOrDefault(roleId, new ArrayList<>());
        cs.add(new GoodContract(days, goodId));
        return true;
    }


    public void setRent(int r) {
        this.rent = r;
    }
}
