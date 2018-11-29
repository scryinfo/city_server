package Game;

import org.bson.Document;

import java.util.Arrays;

public class AIBuy extends ProbBase {
    AIBuy(Document d) {
        super(MetaGood.Type.ALL.ordinal(), d);
    }
    public MetaGood.Type random(double[] ratio) {
        int[] d = Arrays.copyOf(weight, weight.length);
        for(int i = 0; i < d.length; ++i) {
            d[i] *= ratio[i];
        }
        return MetaGood.Type.values()[super.randomIdx(d)];
    }
}
