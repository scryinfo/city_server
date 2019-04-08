package Game.Action;

import Game.Npc;

import java.util.Set;

public class GoHome implements IAction  {
    @Override
    public Set<Object> act(Npc npc) {
        logger.info("npc " + npc.id().toString() + " go home");
        npc.goHome();
        return null;
    }
}
