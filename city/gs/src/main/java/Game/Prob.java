package Game;

public class Prob {
    public static boolean success(int w, int radix) {
        return w < Math.random()*radix;
    }
}
