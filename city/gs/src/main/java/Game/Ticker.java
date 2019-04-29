package Game;

import javax.persistence.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public interface Ticker {
    public abstract void tick(long deltaTime);
    public default void postAddToWorld(){};
}

