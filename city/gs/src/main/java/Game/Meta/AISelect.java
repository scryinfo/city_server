package Game.Meta;

import Game.Action.IAction;
import org.bson.Document;

import java.util.Arrays;

public class AISelect extends ProbBase {
    AISelect(Document d) {
        super(12, d);
    }
    public int random() {
        int[] d = Arrays.copyOf(weight, weight.length);
        return d[super.randomIdx(d)];
    }
}