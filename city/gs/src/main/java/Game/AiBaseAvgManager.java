package Game;

import Game.Timers.PeriodicTimer;
import Game.Util.DateUtil;
import Shared.LogDb;
import org.bson.Document;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class AiBaseAvgManager
{
    static long nowTime=0;
    static{
        nowTime = System.currentTimeMillis();
    }
    private AiBaseAvgManager()
    {
    }
    private static AiBaseAvgManager instance = new AiBaseAvgManager();
    public static AiBaseAvgManager getInstance()
    {
        return instance;
    }

    private Map<Integer, Double> brandMap = new HashMap<>();
    private Map<Integer, Double> qualityMap = new HashMap<>();
    private double allBrandAvg;
    private double allQualityAvg;

    private PeriodicTimer aiTimer = new PeriodicTimer((int)TimeUnit.HOURS.toMillis(24),(int)TimeUnit.SECONDS.toMillis((DateUtil.getTodayEnd()-nowTime)/1000));

    public void init()
    {
        updateAiBaseAvg();
    }
    public void update(long diffNano) {
        if(aiTimer.update(diffNano))
            updateAiBaseAvg();
    }

    public void updateAiBaseAvg(){
        List<Document> documentList=LogDb.getDayAiBaseAvg(DateUtil.getTodayStart());
        documentList.forEach(document -> {
            int type=document.getInteger("type");
            brandMap.put(type,document.getDouble("brand"));
            qualityMap.put(type,document.getDouble("quality"));
        });
        this.allBrandAvg=brandMap.values().stream().reduce(0d, Double::sum)/(brandMap.size()==0?1:brandMap.size());
        this.allQualityAvg=qualityMap.values().stream().reduce(0d, Double::sum)/(qualityMap.size()==0?1:qualityMap.size());
    }
    public Double getBrandMapVal(int key){
        return brandMap.get(key)==null?1.d:brandMap.get(key);
    }
    public Double getQualityMapVal(int key){
        return qualityMap.get(key)==null?1.d:qualityMap.get(key);
    }
    public Double getAllBrandAvg(){
        return allBrandAvg;
    }
    public Double getAllQualityAvg(){
        return allQualityAvg;
    }
}