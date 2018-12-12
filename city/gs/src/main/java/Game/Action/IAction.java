package Game.Action;

import Game.Npc;
import org.apache.log4j.Logger;

public interface IAction {
    Logger logger = Logger.getLogger("AI");
    void act(Npc npc);
}
