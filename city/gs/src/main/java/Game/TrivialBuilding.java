package Game;

import com.google.protobuf.Message;
import gs.Gs;

import java.util.UUID;

public class TrivialBuilding extends Building {
    private MetaBuilding meta;
    public TrivialBuilding(MetaBuilding meta, Coordinate pos, UUID ownerId) {
        super(meta, pos, ownerId);
        this.meta = meta;
    }

    @Override
    public Message detailProto() {
        return null;
    }

    @Override
    public void appendDetailProto(Gs.BuildingSet.Builder builder) {

    }

    @Override
    protected void _update(long diffNano) {

    }
}
