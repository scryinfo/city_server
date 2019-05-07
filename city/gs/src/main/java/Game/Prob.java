package Game;

import java.util.concurrent.ThreadLocalRandom;

public class Prob {
    public static boolean success(int w, int radix) {
        return Math.random()*radix < w;
    }
    // [l, r)
    public static int random(int l, int r) {return ThreadLocalRandom.current().nextInt(l, r);}
}
