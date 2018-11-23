package Game.Action;

import Game.Npc;

public class Idle implements IAction {
    @Override
    public void act(Npc npc) {
        npc.idle();
    }
}
