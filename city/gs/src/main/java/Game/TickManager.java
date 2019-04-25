package Game;

import java.util.*;


public class TickManager {

    public static TickManager instance(){
        if(tickManager == null){
            tickManager = new TickManager();
            tickManager._tickerList = new ArrayList<>();
        }
        return  tickManager;
    }

    private static TickManager tickManager;
    List<Ticker> _tickerList;
    public void registerTick(Ticker obj){
        _tickerList.add(obj);
    }
    public void unRegisterTick(Ticker obj){
        _tickerList.remove(obj);
    }
    public void tick(long delta){
        _tickerList.forEach(ticker -> ticker.tick(delta));
    }
}
