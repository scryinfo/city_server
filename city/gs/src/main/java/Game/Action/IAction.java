package Game.Action;

import Game.Npc;
import org.apache.log4j.Logger;

import java.util.Set;

public interface IAction {
    Logger logger = Logger.getLogger("AI");
    Set<Object> act(Npc npc);
}
