package Game.Meta;

import Shared.Util;
import org.bson.Document;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

public class ProbBase {
    ProbBase(int n, Document d) {
        this.weight = new int[n];
        id = d.getLong("_id");
        for(int i = 0; i < weight.length; ++i)
            weight[i] = d.getInteger("w"+i);
    }
    final long id;
    final int[] weight;
    public static int randomIdx(int[] weight) {
        assert weight.length > 0;
        if(weight.length == 1)
            return 0;
        int[] copy = Arrays.copyOf(weight, weight.length);
        process(copy);
        int v = Util.random(0, copy[copy.length-1]);
        int idx = Arrays.binarySearch(copy, v);
        if(idx >= 0)
            return idx;
        else
            return -(idx+1);
    }
    private static void process(int[] weight) {
        for(int i = 1; i < weight.length; ++i)
            weight[i] = weight[i] + weight[i-1];
    }
}
