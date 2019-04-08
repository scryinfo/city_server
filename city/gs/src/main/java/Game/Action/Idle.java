package Game.Action;

import Game.Npc;

import java.util.Set;

public class Idle implements IAction {
    @Override
    public Set<Object> act(Npc npc) {
        logger.info("npc " + npc.id().toString() + " choose to idle");
        npc.idle();
        return null;
    }
}
