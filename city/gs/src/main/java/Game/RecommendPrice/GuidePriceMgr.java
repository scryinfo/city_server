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

    // Cache city average
    private static LogDb.HistoryRecord historyRecord = new LogDb.HistoryRecord();
    private PeriodicTimer timer = new PeriodicTimer((int) TimeUnit.MINUTES.toMillis(5));

    private GuidePriceMgr() {
    }

    public static GuidePriceMgr instance() {
        return instance;
    }

    //params 1. Residential score, 2. Prosperity
    public double getApartmentGuidePrice(double currScore, double currProsp) {
//       Recommended Pricing = (Average Residential Transaction Price in the City* (Total Player Rating/400 * 7 + 1) * (1 + Player Residence Prosperity)) / ((Average Residential Rating in the City/400 * 7 + 1) * (1 + housing prosperity across the city))
        if (null != historyRecord&&historyRecord.score!=0) {
            double price = historyRecord.price;
            double score = historyRecord.score;
            double prosp = historyRecord.prosp;
            //(score / 400 * 7)  Nan will appear now(score / 400 * 7+0.1)
            return ((price * (currScore / 400 * 7 + 1) * (1 + currProsp)) / (score / 400 * 7+0.1) * (1 + prosp));
        }
        return 0;
    }

    public Gs.MaterialRecommendPrices getMaterialPrice(UUID buildingId) {
//        Recommended pricing = average transaction price of raw materials across the city
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

    // Query raw material or id recommended pricing based on itemID
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

    // Query Institute or Promotion Company
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
//       Recommended Pricing = The average transaction price of the whole city * (1 + (total player product rating-total city average product rating) / 50)
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
        //Recommended Pricing = Dealing Price of Merchandise Retail Store Shelves in the City * (1 + (Total Rating of Player Merchandise Retail Store Shelves-Total Score of Merchandise Retail Store Shelves in the City) / 50)
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
    // Land transaction
    public double getGroundPrice() {
//   Recommended pricing = average land transaction price in the city
        double price = historyRecord.groundPrice;
        if (0.0 == price) {
            price = LogDb.queryLandAuctionAvg();
        }
        return price;
    }

    public void _update() {
        calendar.setTime(new Date());
        calendar.set(Calendar.HOUR_OF_DAY, calendar.get(Calendar.HOUR_OF_DAY));// Modify the instant view, including the current day.
        calendar.set(Calendar.MINUTE, calendar.get(Calendar.MINUTE));
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date endDate = calendar.getTime();
        long endTime = endDate.getTime();

        calendar.add(Calendar.DATE, -1);
        Date startDate = calendar.getTime();
        long startTime = startDate.getTime();
        //Residential
        historyRecord = LogDb.getApartmentRecord(startTime, endTime);
        //raw material
        historyRecord.material = LogDb.getMaterialsRecord(startTime, endTime);
        // Processing plant
        historyRecord.produce = LogDb.getGoodsRecord(startTime, endTime);
        //Retail store
        historyRecord.retail = LogDb.getRetailRecord(startTime, endTime);
        //graduate School
        historyRecord.laboratory = LogDb.getLabOrProRecord(startTime, endTime, true);
        //Data company
        historyRecord.promotion = LogDb.getLabOrProRecord(startTime, endTime, false);
        //Land transaction
        historyRecord.groundPrice = LogDb.getGroundRecord(startTime, endTime);
    }

    public void update(long diffNano) {
        if (timer.update(diffNano)) {
            _update();
        }
    }
}
