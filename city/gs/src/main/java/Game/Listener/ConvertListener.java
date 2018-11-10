package Game.Listener;

import Game.Building;
import com.google.protobuf.InvalidProtocolBufferException;

import javax.persistence.PostLoad;
import javax.persistence.PreUpdate;

public class ConvertListener {
    @PreUpdate
    public void out(Object obj) {
        if(obj instanceof Building) {
            Building building = (Building)obj;
            building.serializeBinaryMembers();
        }
    }
    @PostLoad
    public void in(Object obj) throws InvalidProtocolBufferException {
        if(obj instanceof Building) {
            Building building = (Building)obj;
            building.deserializeBinaryMembers();
        }
    }
}
