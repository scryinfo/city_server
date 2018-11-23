package Game;

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
        process(weight);
        int v = ThreadLocalRandom.current().nextInt(0, weight[weight.length-1]);  // range is [l, r)
        int idx = Arrays.binarySearch(weight, v);
        if(idx >= 0)
            return idx+1; // due to we random [l, r), so the idx can not be the index of last element r, so +1 is ok
        else
            return -(idx+1);
    }
    private static void process(int[] weight) {
        for(int i = 1; i < weight.length; ++i)
            weight[i] = weight[i] + weight[i-1];
    }
}
