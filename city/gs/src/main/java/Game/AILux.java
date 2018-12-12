package Game;

import Game.Action.IAction;
import org.bson.Document;

import java.util.Arrays;

public class AILux extends ProbBase {
    AILux(Document d) {
        super(4, d);
    }
    public int random(double[] ratio) {
        IAction.logger.info("AILux ratio" + Arrays.toString(ratio));
        int[] d = Arrays.copyOf(weight, weight.length);
        for(int i = 0; i < d.length; ++i) {
            d[i] *= ratio[i];
        }
        return super.randomIdx(d);
    }
}