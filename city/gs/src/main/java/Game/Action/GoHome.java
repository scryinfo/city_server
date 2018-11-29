package Game.Action;

import Game.Npc;

public class GoHome implements IAction  {
    @Override
    public void act(Npc npc) {
        npc.goHome();
    }
}
