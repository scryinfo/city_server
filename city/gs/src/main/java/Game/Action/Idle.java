package Game.Action;

import Game.Npc;

public class Idle implements IAction {
    @Override
    public void act(Npc npc) {
        logger.info("npc " + npc.id().toString() + " choose to idle");
        npc.idle();
    }
}
