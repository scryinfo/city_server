package Game.RecommendPrice;

import Game.Meta.MetaBuilding;
import Game.Timers.PeriodicTimer;
import Shared.LogDb;
import gs.Gs;
import org.bson.Document;
import java.util.*;
import java.util.concurrent.TimeUnit;


public class GuidePriceMgr {
    private static String AVG_PRICE = "price";
    private static String AVG_SCORE = "score";
    private static String AVG_PROSPEROUS = "prosperous";
    private static GuidePriceMgr instance = new GuidePriceMgr();

    // 缓存全城均值
    private Map<Integer, Map<String, Double>> avgInfomation = new HashMap<>();
    private Map<Integer, Map<String, Double>> produceInfomation = new HashMap<>();
    private PeriodicTimer timer = new PeriodicTimer((int) TimeUnit.MINUTES.toMillis(1));

    private GuidePriceMgr() {
    }

    public static GuidePriceMgr instance() {
        return instance;
    }

    //params1.住宅评分,2.繁荣度
    public double getApartmentGuidePrice(double currScore, double currProsp) {
//        推荐定价 = (全城均住宅成交价 * (玩家住宅总评分 /400 * 7 + 1) * (1 + 玩家住宅繁荣度)) / ((全城均住宅总评分 /400 * 7 + 1) * (1 + 全城均住宅繁荣度))
        Map<String, Double> map = avgInfomation.get(MetaBuilding.APARTMENT);
        if (null!=map) {
            Double price = map.get(AVG_PRICE);
            Double score = map.get(AVG_SCORE);
            Double prosp = map.get(AVG_PROSPEROUS);
            return (price * (currScore / 400 * 7 + 1) * (1 + currProsp)) / (score / 400 * 7) * (1 + prosp);
        }
        return 0;
    }

    public Gs.MaterialRecommendPrices getMaterialPrice() {
//        推荐定价 = 全城均原料成交价
        Gs.MaterialRecommendPrices.Builder builder = Gs.MaterialRecommendPrices.newBuilder();
        Map<String, Double> map = avgInfomation.get(MetaBuilding.MATERIAL);
        if (null!=map) {
            map.forEach((k, v) -> {
                builder.addMsg(Gs.MaterialRecommendPrices.MaterailMsg.newBuilder().setMid(Integer.parseInt(k)).setGuidePrice(v));
            });
        }
        return builder.build();
    }

    public Gs.ProduceDepRecommendPrice getProducePrice(Map<Integer, Double> playerGoodsScore) {
//        推荐定价 = 全城均商品成交价 * (1 + (玩家商品总评分 - 全城均商品总评分) / 50)
        Gs.ProduceDepRecommendPrice.Builder builder = Gs.ProduceDepRecommendPrice.newBuilder();
        playerGoodsScore.forEach((a, b) -> {
            produceInfomation.forEach((k, v) -> {
                if (k.equals(a) && !v.isEmpty()) {
                    double guidePrice = v.getOrDefault(AVG_PRICE, 0.0) * (1 + (b - v.getOrDefault(AVG_SCORE, 0.0)) / 50);
                    builder.addMsg(Gs.ProduceDepRecommendPrice.ProduceMsg.newBuilder().setMid(k).setGuidePrice(guidePrice));
                }
            });
        });
        return builder.build();
    }

    public void update(long diffNano) {
        if (timer.update(diffNano)) {
            System.err.println("这就很舒服了...");
            _update();
        }
    }

    private void queryApartmentAvg(long startTime, long endTime) {
        Map<String, Double> map = new HashMap<>();
        // 统计数据
        List<Document> documents = LogDb.sumApartMent(startTime, endTime);
        if (!documents.isEmpty()) {
            documents.forEach((doc -> {
                Long size = doc.getLong("size");
                map.put(AVG_PRICE, (double) (doc.getLong("total") / size));
                map.put(AVG_SCORE, (doc.getDouble("score") / size));
                map.put(AVG_PROSPEROUS, ((doc.getDouble("prosp") / size)));
            }));
        }
        avgInfomation.put(MetaBuilding.APARTMENT, map);
    }

    private void querymaterialAvg(long startTime, long endTime) {
        Map<String, Double> map = new HashMap<>();
        List<Document> documents = LogDb.sumMaterialOrGoods(startTime, endTime, false);
        if (!documents.isEmpty()) {
            documents.forEach(document -> {
                Long size = document.getLong("size");
                map.put(String.valueOf(document.getInteger("id")), (double) document.getLong("total") / size);
            });
        }
        avgInfomation.put(MetaBuilding.MATERIAL, map);
    }

    private void queryProduceAvg(long startTime, long endTime) {
        List<Document> documents = LogDb.sumMaterialOrGoods(startTime, endTime, true);
        if (!documents.isEmpty()) {
            documents.forEach(document -> {
                Map<String, Double> map = new HashMap<>();
                Long size = document.getLong("size");
                map.put(AVG_PRICE, (double) (document.getLong("total") / size));
                map.put(AVG_SCORE, (double) (document.getInteger("score") / size));
                produceInfomation.put((Integer) document.getInteger("id"), map);
            });
        }
    }

    public void _update() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.set(Calendar.HOUR_OF_DAY, calendar.get(Calendar.HOUR_OF_DAY));// 修改即时查看,包括当天.
        calendar.set(Calendar.MINUTE, calendar.get(Calendar.MINUTE));
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date endDate = calendar.getTime();
        long endTime = endDate.getTime();

        calendar.add(Calendar.DATE, -30);
        Date startDate = calendar.getTime();
        long startTime = startDate.getTime();
        queryApartmentAvg(startTime, endTime);
        querymaterialAvg(startTime, endTime);
        queryProduceAvg(startTime, endTime);
    }
}
