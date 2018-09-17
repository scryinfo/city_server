package Game;

import Shared.Util;
import com.google.protobuf.ByteString;
import gs.Gs;
import org.bson.Document;
import org.bson.types.ObjectId;

public abstract class Building {

    public int type() {
        return MetaBuilding.type(metaBuilding.id);
    }

    public static Building create(int id, Coord pos, ObjectId ownerId) {
        switch(MetaBuilding.type(id))
        {
            case MetaBuilding.TRIVIAL:
                return new TrivialBuilding(MetaData.getTrivialBuilding(id), pos, ownerId);
            case MetaBuilding.MATERIAL:
                return new MaterialFactory(MetaData.getMaterialFactory(id), pos, ownerId);
            case MetaBuilding.PRODUCTING:
                return new ProductingDepartment(MetaData.getProductingDepartment(id), pos, ownerId);
            case MetaBuilding.RETAIL:
                return new RetailShop(MetaData.getRetailShop(id), pos, ownerId);
            case MetaBuilding.APARTMENT:
                return new Apartment(MetaData.getApartment(id), pos, ownerId);
            case MetaBuilding.LAB:
                return new Laboratory(MetaData.getLaboratory(id), pos, ownerId);
            case MetaBuilding.PUBLIC:
                return new PublicFacility(MetaData.getPublicFacility(id), pos, ownerId);
        }
        return null;
    }
    public static Building create(Document d) {
        int id = d.getInteger("mid");
        switch(MetaBuilding.type(id))
        {
            case MetaBuilding.TRIVIAL:
                return new TrivialBuilding(MetaData.getTrivialBuilding(id), d);
            case MetaBuilding.MATERIAL:
                return new MaterialFactory(MetaData.getMaterialFactory(id), d);
            case MetaBuilding.PRODUCTING:
                return new ProductingDepartment(MetaData.getProductingDepartment(id), d);
            case MetaBuilding.RETAIL:
                return new RetailShop(MetaData.getRetailShop(id), d);
            case MetaBuilding.APARTMENT:
                return new Apartment(MetaData.getApartment(id), d);
            case MetaBuilding.LAB:
                return new Laboratory(MetaData.getLaboratory(id), d);
            case MetaBuilding.PUBLIC:
                return new PublicFacility(MetaData.getPublicFacility(id), d);
        }
        return null;
    }
    protected MetaBuilding metaBuilding;
    private ObjectId id;
    private ObjectId ownerId;
    private Coord coord;
    public ObjectId id() {
        return id;
    }
    public ObjectId ownerId() {
        return ownerId;
    }
    public Building(MetaBuilding meta, Coord pos, ObjectId ownerId) {
        this.id = new ObjectId();
        this.ownerId = ownerId;
        this.coord = pos;
        this.metaBuilding = meta;
    }
    public Building(MetaBuilding meta, Document d) {
        this.id = d.getObjectId("id");
        this.ownerId = d.getObjectId("owner");
        this.coord = new Coord(d);
        this.metaBuilding = meta;
    }
    public CoordPair effectRange() {
        Coord l = coord.shiftLU(this.metaBuilding.effectRange);
        Coord r = coord.offset(this.metaBuilding.x, this.metaBuilding.y).shiftRB(this.metaBuilding.effectRange);
       return new CoordPair(l, r);
    }
    public CoordPair area() {
        return new CoordPair(coord, coord.offset(metaBuilding.x, metaBuilding.y));
    }
    public Document toBson() {
        Document res = new Document();
        res.append("id", id);
        return res;
    }
    public Coord coordinate() {
        return coord;
    }
    public Gs.BuildingInfo toProto() {
        return Gs.BuildingInfo.newBuilder()
                .setId(ByteString.copyFrom(id.toByteArray()))
                .setMId(metaBuilding.id)
                .setPos(Gs.MiniIndex.newBuilder()
                        .setX(this.coord.x)
                        .setY(this.coord.y))
                .setData(Gs.BuildingInfo.MutableData.newBuilder()
                        .setOwnerId(Util.toByteString(ownerId))
                        .build())
                .build();
    }
    void update(long diffNano) {
    }
    void timeSectionTick(int newIdx, int nowHour, int hours) {

    }
}
