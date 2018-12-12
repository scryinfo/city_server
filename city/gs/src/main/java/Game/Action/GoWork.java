package Game.Action;

import Game.Npc;

public class GoWork implements IAction {
    @Override
    public void act(Npc npc) {
        logger.info("npc " + npc.id().toString() + " go work");
        npc.goWork();
    }
}
