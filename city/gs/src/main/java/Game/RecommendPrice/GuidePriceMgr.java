package Game.RecommendPrice;

import Game.Item;
import Game.Meta.MetaData;
import Game.Meta.MetaGood;
import Game.Meta.MetaMaterial;
import Game.Timers.PeriodicTimer;
import Game.Util.GlobalUtil;
import Shared.LogDb;
import Shared.Util;
import com.google.common.util.concurrent.AtomicDouble;
import com.google.protobuf.ByteString;
import gs.Gs;
import org.checkerframework.checker.units.qual.A;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


public class GuidePriceMgr {
    private static String AVG_PRICE = "price";
    private static String AVG_SCORE = "score";
    private static GuidePriceMgr instance = new GuidePriceMgr();
    private static Calendar calendar = Calendar.getInstance();

    // 缓存全城均值
    private static LogDb.HistoryRecord historyRecord = new LogDb.HistoryRecord();
    private PeriodicTimer timer = new PeriodicTimer((int) TimeUnit.MINUTES.toMillis(5));

    private GuidePriceMgr() {
    }

    public static GuidePriceMgr instance() {
        return instance;
    }

    //params1.住宅评分,2.繁荣度
    public double getApartmentGuidePrice(double currScore, double currProsp) {
//        推荐定价 = (全城均住宅成交价 * (玩家住宅总评分 /400 * 7 + 1) * (1 + 玩家住宅繁荣度)) / ((全城均住宅总评分 /400 * 7 + 1) * (1 + 全城均住宅繁荣度))
        if (null != historyRecord&&historyRecord.score!=0) {
            double price = historyRecord.price;
            double score = historyRecord.score;
            double prosp = historyRecord.prosp;
            return ((price * (currScore / 400 * 7 + 1) * (1 + currProsp)) / (score / 400 * 7) * (1 + prosp));
        }
        return 0;
    }

    public Gs.MaterialRecommendPrices getMaterialPrice(UUID buildingId) {
//        推荐定价 = 全城均原料成交价
        Gs.MaterialRecommendPrices.Builder builder = Gs.MaterialRecommendPrices.newBuilder();
        Map<Integer, Double> materialPrice = historyRecord.material;
        MetaData.getAllMaterialId().forEach(i -> {
            Gs.MaterialRecommendPrices.MaterailMsg.Builder msg = Gs.MaterialRecommendPrices.MaterailMsg.newBuilder();
            msg.setMid(i);
            msg.setGuidePrice(0);
            if (null != materialPrice && materialPrice.size() > 0) {
                materialPrice.forEach((k, v) -> {
                    if (k.equals(i)) {
                        msg.setGuidePrice(v);
                    }
                });
            }
            builder.addMsg(msg);

        });
        return builder.setBuildingId(Util.toByteString(buildingId)).build();
    }

    // 根据itemID查询原料或id推荐定价
    public double getMaterialOrGoodsPrice(Item item) {
        if (item == null) {
            return 0.0;
        }
        try {
            final double[] guidePrice = {0.0};
            if (MetaMaterial.isItem(item.getKey().meta.id)) {
                this.historyRecord.material.forEach((k, v) -> {
                    if (k == item.getKey().meta.id) {
                        guidePrice[0] = v;
                    }
                });
                return guidePrice[0];
            } else if (MetaGood.isItem(item.getKey().meta.id)) {
                this.historyRecord.produce.forEach((k, v) -> {
                    if (item.getKey().meta.id == k && v != null && v.size() > 0) {
                        double score = (GlobalUtil.getBrandScore(item.getKey().getTotalQty(), item.getKey().meta.id) + MetaData.getGoodQuality(item.getKey().meta.id)) / 2;
                        guidePrice[0] = (v.getOrDefault(AVG_PRICE, 0.0) * (1 + (score - v.getOrDefault(AVG_SCORE, 0.0)) / 50));
                    }
                });
            }
            return guidePrice[0];
        } catch (RuntimeException e) {
            e.printStackTrace();
            return 0.0;
        }
    }

    // 查询研究所或推广公司
    public double getTechOrPromGuidePrice(int itemId, boolean isTechnology) {
        AtomicDouble guidePrice = new AtomicDouble(0);
        try {
            if (isTechnology) {
                Map<Integer, Double> map = historyRecord.laboratory;
                map.forEach((k, v) -> {
                    if (k == itemId) {
                        guidePrice.set(v);
                    }
                });
            } else {
                Map<Integer, Double> map = historyRecord.promotion;
                map.forEach((k, v) -> {
                    if (k == itemId) {
                        guidePrice.set(v);
                    }
                });
            }
        } catch (Exception e) {
            return 0.0;
        }

        return guidePrice.doubleValue();
    }
    public Gs.ProduceDepRecommendPrice getProducePrice(Map<Integer, Double> playerGoodsScore, UUID buildingId) {
//        推荐定价 = 全城均商品成交价 * (1 + (玩家商品总评分 - 全城均商品总评分) / 50)
        Gs.ProduceDepRecommendPrice.Builder builder = Gs.ProduceDepRecommendPrice.newBuilder();
        Map<Integer, Map<String, Double>> produce = historyRecord.produce;
        MetaData.getAllGoodId().forEach(i -> {
            Gs.ProduceDepRecommendPrice.ProduceMsg.Builder msg = Gs.ProduceDepRecommendPrice.ProduceMsg.newBuilder();
            msg.setMid(i);
            msg.setGuidePrice(0);
            try {
                playerGoodsScore.forEach((a, b) -> {
                    if (null != produce && produce.size() > 0) {
                        produce.forEach((k, v) -> {
                            if (k.equals(a) && !v.isEmpty()) {
                                double guidePrice = v.getOrDefault(AVG_PRICE, 0.0) * (1 + (b - v.getOrDefault(AVG_SCORE, 0.0)) / 50);
                                msg.setGuidePrice(guidePrice);
                            }
                        });
                    }
                });
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
            builder.addMsg(msg);
        });
        return builder.setBuildingId(Util.toByteString(buildingId)).build();
    }

    public Gs.RetailShopRecommendPrice getRetailPrice(Map<Integer, Double> map, UUID buildingId) {
        //推荐定价 = 全城均商品零售店货架成交价 * (1 + (玩家商品零售店货架总评分 - 全城均商品零售店货架总评分) / 50)
        Gs.RetailShopRecommendPrice.Builder builder = Gs.RetailShopRecommendPrice.newBuilder();
        Map<Integer, Map<String, Double>> retail = historyRecord.retail;
        MetaData.getAllGoodId().forEach(i -> {
            Gs.RetailShopRecommendPrice.RetailMsg.Builder msg = Gs.RetailShopRecommendPrice.RetailMsg.newBuilder();
            msg.setMid(i);
            msg.setGuidePrice(0);
            try {
                map.forEach((a, b) -> {
                    if (retail != null && retail.size() > 0) {
                        retail.forEach((k, v) -> {
                            if (k.equals(a) && !v.isEmpty() && v.size() > 0) {
                                double guidePrice = v.getOrDefault(AVG_PRICE, 0.0) * (1 + (b - v.getOrDefault(AVG_SCORE, 0.0)) / 50);
                                msg.setGuidePrice(guidePrice);
                            }
                        });
                    }
                });
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
            builder.addMsg(msg);
        });
        return builder.setBuildingId(Util.toByteString(buildingId)).build();
    }

    public Gs.GMRecommendPrice getLabOrProPrice(UUID buildingId, boolean islab) {
        Gs.GMRecommendPrice.Builder builder = Gs.GMRecommendPrice.newBuilder();
        Gs.GMRecommendPrice.GMInfo.Builder msg = Gs.GMRecommendPrice.GMInfo.newBuilder();
        if (islab) {
            List<Integer> ids = MetaData.getAllScienCeId();
            Map<Integer, Double> lab = historyRecord.laboratory;
            ids.stream().filter(i -> i != null).forEach(o -> {
                msg.setTypeId(o);
                msg.setGuidePrice(0);
                try {
                    lab.forEach((k, v) -> {
                        if (k.equals(o)) {
                            msg.setGuidePrice(v);
                        }
                    });
                } catch (RuntimeException e) {
                    e.printStackTrace();
                }
                builder.addMsg(msg);
            });
        } else {
            List<Integer> ids = MetaData.getPromotionItemId();
            Map<Integer, Double> promotion = historyRecord.promotion;
            ids.stream().filter(i -> i != null).forEach(o -> {
                msg.setTypeId(o);
                msg.setGuidePrice(0);
                try {
                    promotion.forEach((k, v) -> {
                        if (k.equals(o)) {
                            msg.setGuidePrice(v);
                            return;
                        }
                    });
                } catch (RuntimeException e) {
                    e.printStackTrace();
                }
                builder.addMsg(msg);
            });
        }
        return builder.setBuildingId(Util.toByteString(buildingId)).build();
    }
    // 土地交易
    public double getGroundPrice() {
//   推荐定价 = 全城均土地成交价
        return historyRecord.groundPrice;
    }

    public void _update() {
        calendar.setTime(new Date());
        calendar.set(Calendar.HOUR_OF_DAY, calendar.get(Calendar.HOUR_OF_DAY));// 修改即时查看,包括当天.
        calendar.set(Calendar.MINUTE, calendar.get(Calendar.MINUTE));
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date endDate = calendar.getTime();
        long endTime = endDate.getTime();

        calendar.add(Calendar.DATE, -1);
        Date startDate = calendar.getTime();
        long startTime = startDate.getTime();
        //住宅
        historyRecord = LogDb.getApartmentRecord(startTime, endTime);
        //原料
        historyRecord.material = LogDb.getMaterialsRecord(startTime, endTime);
        // 加工厂
        historyRecord.produce = LogDb.getGoodsRecord(startTime, endTime);
        //零售店
        historyRecord.retail = LogDb.getRetailRecord(startTime, endTime);
        //研究所
        historyRecord.laboratory = LogDb.getLabOrProRecord(startTime, endTime, true);
        //数据公司
        historyRecord.promotion = LogDb.getLabOrProRecord(startTime, endTime, false);
        //土地交易
        historyRecord.groundPrice = LogDb.getGroundRecord(startTime, endTime);
    }

    public void update(long diffNano) {
        if (timer.update(diffNano)) {
            _update();
        }
    }
}
