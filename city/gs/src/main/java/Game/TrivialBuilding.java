package Game;

import Game.Meta.MetaBuilding;
import com.google.protobuf.Message;
import gs.Gs;

import java.util.UUID;

public class TrivialBuilding extends Building {
    private MetaBuilding meta;

    @Override
    public int quality() {
        return 0;
    }

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
    protected void enterImpl(Npc npc) {

    }

    @Override
    protected void leaveImpl(Npc npc) {

    }

    @Override
    protected void _update(long diffNano) {

    }
}
