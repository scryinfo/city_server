package Game.Action;

import Game.Npc;

import java.util.Set;

public class GoWork implements IAction {
    @Override
    public Set<Object> act(Npc npc) {
        logger.info("npc " + npc.id().toString() + " go work");
        npc.goWork();
        return null;
    }
}
