package Game.CityInfo;

import Game.*;
import Game.Eva.EvaManager;
import Game.Meta.MetaBuilding;
import Game.Meta.MetaData;
import Game.Timers.PeriodicTimer;
import Game.Util.DateUtil;
import Shared.LogDb;
import gs.Gs;
import org.bson.Document;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static Shared.LogDb.KEY_TOTAL;

public class IndustryMgr {
    private static final int PROMOTION = 1;
    private static final int TECHNOLOGY = 2;
    public static final long DAY_MILLISECOND = 1000 * 3600 * 24;
    private static IndustryMgr instance = new IndustryMgr();

    public static IndustryMgr instance() {
        return instance;
    }
    PeriodicTimer timer= new PeriodicTimer((int)TimeUnit.DAYS.toMillis(1),(int)TimeUnit.SECONDS.toMillis((DateUtil.getTodayEnd()-System.currentTimeMillis())/1000));//每天0点开始更新数据

    public void update(long diffNano) {
        if (timer.update(diffNano)) {
            // ss获取不到 暂时先放在gs统计
            long endTime = getEndTime(System.currentTimeMillis());
            long startTime = endTime - DAY_MILLISECOND;
            List<Document> source = industrySource(startTime, endTime);
            LogDb.insertIndustrySupplyAndDemand(source);
            long sum = MoneyPool.instance().getN();
            LogDb.insertCityMoneyPool(sum, endTime);
            List<Document> all = new ArrayList<>();
            List<Document> material = materialSource(startTime, endTime);
            List<Document> produce = produceSource(startTime, endTime);
            List<Document> promote = promoteSource(startTime, endTime);
            List<Document> technology = technologySource(startTime, endTime);
            List<Document> retail = retailSource(startTime, endTime);
            all.addAll(material);
            all.addAll(produce);
            all.addAll(promote);
            all.addAll(technology);
            all.addAll(retail);
            LogDb.insertIndustrySupplyAndDemand(all);
        }
    }

    public static long getEndTime(long nowTime) {
        return nowTime - (nowTime + TimeZone.getDefault().getRawOffset()) % DAY_MILLISECOND;
    }

/*    public static void main(String[] args) {
        long endTime = getEndTime(System.currentTimeMillis());
        long startTime = endTime - DAY_MILLISECOND;
        System.err.println(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(startTime));
        System.err.println(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(endTime));
        System.err.println(startTime);
        System.err.println(endTime);

    }*/

    public List<Document> industrySource(long startTime, long endTime) {
        List<Document> list = new ArrayList<>();
        List<Document> documents = LogDb.queryIndestrySum(startTime, endTime);
        documents.stream().filter(o->o!=null).forEach(d->{
            int bt = d.getInteger("_id");
            int sum = City.instance().typeBuilding.getOrDefault(bt, new HashSet<>()).stream().mapToInt(Building::getTotalSaleCount).sum();
            long demand = d.getLong(KEY_TOTAL);
            list.add(new Document().append("bt", bt).append("supply", (sum + demand)).append("demand", demand).append("time", endTime).append("type", 1));
        });

       /* // material
        int sumM = City.instance().typeBuilding.getOrDefault(MetaBuilding.MATERIAL, new HashSet<>()).stream().mapToInt(Building::getTotalSaleCount).sum();
        long demandM = LogDb.queryIndestrySum(MetaBuilding.MATERIAL, startTime, endTime);
        list.add(new Document().append("bt", MetaBuilding.MATERIAL).append("supply", (sumM + demandM)).append("demand", demandM).append("time", endTime).append("type", 1));
        // produce
        int sumP = City.instance().typeBuilding.getOrDefault(MetaBuilding.PRODUCE, new HashSet<>()).stream().mapToInt(Building::getTotalSaleCount).sum();
        long demandP = LogDb.queryIndestrySum(MetaBuilding.PRODUCE, startTime, endTime);
        list.add(new Document().append("bt", MetaBuilding.PRODUCE).append("supply", (sumP + demandP)).append("demand", demandP).append("time", endTime).append("type", 1));
        // technology
        int sumT = City.instance().typeBuilding.getOrDefault(MetaBuilding.TECHNOLOGY, new HashSet<>()).stream().mapToInt(Building::getTotalSaleCount).sum();
        long demandT = LogDb.queryIndestrySum(MetaBuilding.TECHNOLOGY, startTime, endTime);
        list.add(new Document().append("bt", MetaBuilding.TECHNOLOGY).append("supply", (sumT + demandT)).append("demand", demandT).append("time", endTime).append("type", 1));
        // promote
        int sumPro = City.instance().typeBuilding.getOrDefault(MetaBuilding.PROMOTE, new HashSet<>()).stream().mapToInt(Building::getTotalSaleCount).sum();
        long demandPro = LogDb.queryIndestrySum(MetaBuilding.PROMOTE, startTime, endTime);
        list.add(new Document().append("bt", MetaBuilding.PROMOTE).append("supply", (sumPro + demandPro)).append("demand", demandPro).append("time", endTime).append("type", 1));*/
        // apartment
        int sumA = City.instance().typeBuilding.getOrDefault(MetaBuilding.APARTMENT, new HashSet<>()).stream().mapToInt(Building::getTotalSaleCount).sum();
        long demandA = LogDb.queryApartmentIndestrySum(startTime, endTime,LogDb.getNpcRentApartment());
        list.add(new Document().append("bt", MetaBuilding.APARTMENT).append("supply", (sumA + demandA)).append("demand", demandA).append("time", endTime).append("type", 1));
        // retailShop
        int sumR = City.instance().typeBuilding.getOrDefault(MetaBuilding.RETAIL, new HashSet<>()).stream().mapToInt(Building::getTotalSaleCount).sum();
        long demandR = LogDb.queryIndestrySum(startTime, endTime,LogDb.getNpcBuyInShelf());
        list.add(new Document().append("bt", MetaBuilding.RETAIL).append("supply", (sumR + demandR)).append("demand", demandR).append("time", endTime).append("type", 1));
        // ground
        int sellingCount = GroundManager.instance().getAllSellingCount();
        long demandG = LogDb.queryIndestrySum(startTime, endTime,LogDb.getBuyGround());
        list.add(new Document().append("bt", Gs.SupplyAndDemand.IndustryType.GROUND_VALUE).append("supply", (sellingCount + demandG)).append("demand", demandG).append("time", endTime));
        return list;
    }

    public List<Document> materialSource(long startTime, long endTime) {
        List<Document> list = new ArrayList<>();
        MetaData.getAllMaterialId().stream().forEach(itemId -> {
            AtomicInteger saleNum = new AtomicInteger();
            City.instance().typeBuilding.getOrDefault(MetaBuilding.MATERIAL, new HashSet<>()).stream().filter(o->!o.outOfBusiness()).forEach(b -> {
                saleNum.addAndGet(((FactoryBase) b).getShelf().getSaleNum(itemId));
            });
            long deman = LogDb.queryIndestrySum(MetaBuilding.MATERIAL, startTime, endTime, itemId);
            list.add(new Document().append("bt", MetaBuilding.MATERIAL).append("supply", (saleNum.get() + deman)).append("demand", deman).append("time", endTime).append("type", 2).append("tpi", itemId));
        });
        return list;
    }

    public List<Document> produceSource(long startTime, long endTime) {
        List<Document> list = new ArrayList<>();
        MetaData.getAllMaterialId().stream().forEach(itemId -> {
            AtomicInteger saleNum = new AtomicInteger();
            City.instance().typeBuilding.getOrDefault(MetaBuilding.PRODUCE, new HashSet<>()).stream().filter(o -> !o.outOfBusiness()).forEach(b -> {
                saleNum.addAndGet(((FactoryBase) b).getShelf().getSaleNum(itemId));
            });
            long deman = LogDb.queryIndestrySum(MetaBuilding.PRODUCE, startTime, endTime, itemId);
            list.add(new Document().append("bt", MetaBuilding.PRODUCE).append("supply", (saleNum.get() + deman)).append("demand", deman).append("time", endTime).append("type", 2).append("tpi", itemId));
        });
        return list;
    }

    public List<Document> promoteSource(long startTime, long endTime) {
        List<Document> list = new ArrayList<>();
        MetaData.getPromotionItemId().stream().forEach(itemId -> {
            AtomicInteger saleNum = new AtomicInteger();
            City.instance().typeBuilding.getOrDefault(MetaBuilding.PROMOTE, new HashSet<>()).stream().filter(o->!o.outOfBusiness()).forEach(b -> {
                saleNum.addAndGet(((ScienceBuildingBase) b).getShelf().getSaleNum(itemId));
            });
            long deman = LogDb.queryIndestrySum(MetaBuilding.PROMOTE, startTime, endTime, itemId);
            list.add(new Document().append("bt", MetaBuilding.PROMOTE).append("supply", (saleNum.get() + deman)).append("demand", deman).append("time", endTime).append("type", 2).append("tpi", itemId));
        });
        return list;
    }

    public List<Document> technologySource(long startTime, long endTime) {
        List<Document> list = new ArrayList<>();
        MetaData.getAllScienCeId().stream().forEach(itemId -> {
            AtomicInteger saleNum = new AtomicInteger();
            City.instance().typeBuilding.getOrDefault(MetaBuilding.TECHNOLOGY, new HashSet<>()).stream().filter(o -> !o.outOfBusiness()).forEach(b -> {
                saleNum.addAndGet(((ScienceBuildingBase) b).getShelf().getSaleNum(itemId));
            });
            long deman = LogDb.queryIndestrySum(MetaBuilding.TECHNOLOGY, startTime, endTime, itemId);
            list.add(new Document().append("bt", MetaBuilding.TECHNOLOGY).append("supply", (saleNum.get() + deman)).append("demand", deman).append("time", endTime).append("type", 2).append("tpi", itemId));
        });
        return list;
    }

    public List<Document> retailSource(long startTime, long endTime) {
        List<Document> list = new ArrayList<>();
        MetaData.getAllMaterialId().stream().forEach(itemId -> {
            AtomicInteger saleNum = new AtomicInteger();
            City.instance().typeBuilding.getOrDefault(MetaBuilding.RETAIL, new HashSet<>()).stream().filter(o -> !o.outOfBusiness()).forEach(b -> {
                saleNum.addAndGet(((RetailShop) b).getShelf().getSaleNum(itemId));
            });
            long deman = LogDb.queryRetailSum(startTime, endTime, itemId);
            list.add(new Document().append("bt", MetaBuilding.RETAIL).append("supply", (saleNum.get() + deman)).append("demand", deman).append("time", endTime).append("type", 2).append("tpi", itemId));
        });
        return list;
    }


    public  int getTodaySupply(int industryType) {
        switch (industryType) {
            case MetaBuilding.APARTMENT:
                return City.instance().typeBuilding.getOrDefault(MetaBuilding.APARTMENT, new HashSet<>()).stream().mapToInt(Building::getTotalSaleCount).sum();
            case MetaBuilding.MATERIAL:
                return City.instance().typeBuilding.getOrDefault(MetaBuilding.MATERIAL, new HashSet<>()).stream().mapToInt(Building::getTotalSaleCount).sum();
            case MetaBuilding.PRODUCE:
                return City.instance().typeBuilding.getOrDefault(MetaBuilding.PRODUCE, new HashSet<>()).stream().mapToInt(Building::getTotalSaleCount).sum();
            case MetaBuilding.TECHNOLOGY:
                return City.instance().typeBuilding.getOrDefault(MetaBuilding.TECHNOLOGY, new HashSet<>()).stream().mapToInt(Building::getTotalSaleCount).sum();
            case MetaBuilding.PROMOTE:
                return City.instance().typeBuilding.getOrDefault(MetaBuilding.PROMOTE, new HashSet<>()).stream().mapToInt(Building::getTotalSaleCount).sum();
            case MetaBuilding.RETAIL:
                return City.instance().typeBuilding.getOrDefault(MetaBuilding.RETAIL, new HashSet<>()).stream().mapToInt(Building::getTotalSaleCount).sum();
            case Gs.SupplyAndDemand.IndustryType.GROUND_VALUE:
                return GroundManager.instance().getAllSellingCount();
            default:
                return 0;
        }
    }

    public int getTodaySupply(int industryType, int itemId) {
        AtomicInteger saleNum = new AtomicInteger(0);
        switch (industryType) {
            case MetaBuilding.MATERIAL:
                City.instance().typeBuilding.getOrDefault(MetaBuilding.MATERIAL, new HashSet<>()).stream().filter(o -> !o.outOfBusiness()).forEach(b -> {
                    FactoryBase factoryBase = (FactoryBase) b;
                    saleNum.addAndGet(((FactoryBase) b).getShelf().getSaleNum(itemId));
                    return;
                });
                return saleNum.get();
            case MetaBuilding.PRODUCE:
                City.instance().typeBuilding.getOrDefault(MetaBuilding.PRODUCE, new HashSet<>()).stream().filter(o -> !o.outOfBusiness()).forEach(b -> {
                    saleNum.addAndGet(((FactoryBase) b).getShelf().getSaleNum(itemId));
                });
                return saleNum.get();
            case MetaBuilding.TECHNOLOGY:
                City.instance().typeBuilding.getOrDefault(MetaBuilding.TECHNOLOGY, new HashSet<>()).stream().filter(o -> !o.outOfBusiness()).forEach(b -> {
                    saleNum.addAndGet(((ScienceBuildingBase) b).getShelf().getSaleNum(itemId));
                });
                return saleNum.get();
            case MetaBuilding.PROMOTE:
                City.instance().typeBuilding.getOrDefault(MetaBuilding.PROMOTE, new HashSet<>()).stream().filter(o -> !o.outOfBusiness()).forEach(b -> {
                    saleNum.addAndGet(((ScienceBuildingBase) b).getShelf().getSaleNum(itemId));
                });
                return saleNum.get();
            case MetaBuilding.RETAIL:
                City.instance().typeBuilding.getOrDefault(MetaBuilding.RETAIL, new HashSet<>()).stream().filter(o -> !o.outOfBusiness()).forEach(b -> {
                    saleNum.addAndGet(((RetailShop) b).getShelf().getSaleNum(itemId));
                });
                return saleNum.get();
            default:
                return 0;
        }
    }

    // 详细商品今日货架剩余数量
    public  long getTodayDemand(int industryType) {
        long startTime = getEndTime(System.currentTimeMillis());
        long endTime = System.currentTimeMillis();
        switch (industryType) {
            case MetaBuilding.APARTMENT:
                return LogDb.queryApartmentIndestrySum(startTime, endTime, LogDb.getNpcRentApartment());
            case MetaBuilding.MATERIAL:
                return LogDb.queryIndestrySum(MetaBuilding.MATERIAL, startTime, endTime);
            case MetaBuilding.PRODUCE:
                return LogDb.queryIndestrySum(MetaBuilding.PRODUCE, startTime, endTime);
            case MetaBuilding.TECHNOLOGY:
                return LogDb.queryIndestrySum(MetaBuilding.TECHNOLOGY, startTime, endTime);
            case MetaBuilding.PROMOTE:
                return LogDb.queryIndestrySum(MetaBuilding.PROMOTE, startTime, endTime);
            case MetaBuilding.RETAIL:
                return LogDb.queryIndestrySum(startTime, endTime, LogDb.getNpcBuyInShelf());
            case Gs.SupplyAndDemand.IndustryType.GROUND_VALUE:
                return LogDb.queryIndestrySum(startTime, endTime, LogDb.getBuyGround());
            default:
                return 0;
        }
    }
    // 详细商品今日成交量
    public  long getTodayDemand(int industryType,int itemId) {
        long startTime = getEndTime(System.currentTimeMillis());
        long endTime = System.currentTimeMillis();
        switch (industryType) {
            case MetaBuilding.MATERIAL:
                return LogDb.queryIndestrySum(MetaBuilding.MATERIAL, startTime, endTime,itemId);
            case MetaBuilding.PRODUCE:
                return LogDb.queryIndestrySum(MetaBuilding.PRODUCE, startTime, endTime,itemId);
            case MetaBuilding.TECHNOLOGY:
                return LogDb.queryIndestrySum(MetaBuilding.TECHNOLOGY, startTime, endTime,itemId);
            case MetaBuilding.PROMOTE:
                return LogDb.queryIndestrySum(MetaBuilding.PROMOTE, startTime, endTime,itemId);
            case MetaBuilding.RETAIL:
                return LogDb.queryIndestrySum(startTime, endTime, LogDb.getNpcBuyInShelf(),itemId);
            default:
                return 0;
        }
    }

    public List<TopInfo> queryTop(int buildingType) {
        long endTime = getEndTime(System.currentTimeMillis());
        long startTime = endTime - DAY_MILLISECOND;
        List<Document> list = LogDb.dayYesterdayPlayerIncome(startTime, endTime, buildingType, LogDb.getDayPlayerIncome());
        ArrayList<TopInfo> tops = new ArrayList<>();
        list.stream().filter(o->o!=null).forEach(d->{
            UUID pid = d.get("id", UUID.class);
            // 玩家行业总工人数
            int staffNum = getPlayerIndustryStaffNum(pid, buildingType);
            // 玩家名称
            Player player = GameDb.getPlayer(pid);
            String playerName =  player == null ? "" : player.getName();
            //faceId
            String faceId = player == null ? "" : player.getFaceId();
            // 玩家昨日收入
            long total = d.getLong(KEY_TOTAL);
            Map<Integer, Long> map = EvaManager.getInstance().getScience(pid, buildingType);
            long promotion = map.getOrDefault(PROMOTION, 0l);
            // 玩家研究点数
            long science = map.getOrDefault(TECHNOLOGY, 0l);
            tops.add(new TopInfo(pid,faceId,playerName, total, staffNum, science, promotion));
        });
        return tops;
    }

    // 获取玩家行业总人工数
    public int getPlayerIndustryStaffNum(UUID pid,int buildingType) {
        return City.instance().getPlayerBListByBtype(pid, buildingType).stream().mapToInt(Building::getWorkerNum).sum();
    }

    // 获取行业总工人数
    public long getIndustryStaffNum(int buildingType) {
        return City.instance().typeBuilding.getOrDefault(buildingType, new HashSet<>()).stream().mapToInt(Building::getWorkerNum).sum();
    }
    // 获取行业总营收
    public long getIndustrySumIncome(int buildingType) {
        return LogDb.queryIndustrySumIncome(buildingType);
    }
    // 土地排行信息
    public List<TopInfo> queryTop() {
        long endTime = getEndTime(System.currentTimeMillis());
        long startTime = endTime - DAY_MILLISECOND;
        List<Document> list = LogDb.dayYesterdayPlayerByGroundIncome(startTime, endTime, LogDb.getDayPlayerIncome());
        ArrayList<TopInfo> tops = new ArrayList<>();
        list.stream().filter(o -> o.getInteger("id") != null).forEach(d -> {
            UUID pid = d.get("id", UUID.class);
            Player player = GameDb.getPlayer(pid);
            // 玩家名称
            String playerName = player == null ? "" : player.getName();
            String faceId = player == null ? "" : player.getFaceId();
            // 玩家昨日收入
            long total = d.getLong(KEY_TOTAL);
            // 成交量
            int count = LogDb.groundSum(startTime, endTime, pid); // 查询土地成交量
            tops.add(new TopInfo(pid, faceId, playerName, total, count));
        });
        return tops;
    }

    public TopInfo queryMyself(UUID owner, int type) {
        long endTime = getEndTime(System.currentTimeMillis());
        long startTime = endTime - DAY_MILLISECOND;
        if (type == Gs.SupplyAndDemand.IndustryType.GROUND_VALUE) {
            long ground = LogDb.queryMyself(startTime, endTime, owner, type, LogDb.getDayPlayerIncome());
            Player player = GameDb.getPlayer(owner);
            String name = player == null ? "" : player.getName();
            String faceId = player.getFaceId();
            int count = LogDb.groundSum(startTime, endTime, owner); // 查询土地成交量
            return new TopInfo(owner, faceId, name, ground, count);
        } else {
            long myself = LogDb.queryMyself(startTime, endTime, owner, type, LogDb.getDayPlayerIncome());
            Player player = GameDb.getPlayer(owner);
            String name = player == null ? "" : player.getName();
            String faceId = player.getFaceId();
            int staffNum = getPlayerIndustryStaffNum(owner, type);
            Map<Integer, Long> map = EvaManager.getInstance().getScience(owner, type);
            // 玩家推广点数投入
            long promotion = map.getOrDefault(PROMOTION, 0l);
            // 玩家研究点数
            long science = map.getOrDefault(TECHNOLOGY, 0l);
            return new TopInfo(owner, faceId, name, myself, staffNum, science, promotion);
        }
    }

    public List<TopInfo> queryRegalRanking() {
        long endTime = getEndTime(System.currentTimeMillis());
        long startTime = endTime - DAY_MILLISECOND;
        List<Document> list = LogDb.dayYesterdayPlayerIncome(startTime, endTime, LogDb.getDayPlayerIncome());
        ArrayList<TopInfo> tops = new ArrayList<>();
        list.stream().filter(o->o!=null).forEach(d->{
            UUID pid = d.get("id", UUID.class);
            // 玩家总工人数
            long staffNum = City.instance().getPlayerStaffNum(pid);
            // 玩家名称
            Player player = GameDb.getPlayer(pid);
            String playerName =  player == null ? "" : player.getName();
            //faceId
            String faceId = player == null ? "" : player.getFaceId();
            // 玩家昨日收入
            long total = d.getLong(KEY_TOTAL);
            Map<Integer, Long> map = EvaManager.getInstance().getPlayerSumValue(pid);
            // 玩家推广点数投入
            long promotion = map.getOrDefault(PROMOTION, 0l);
            // 玩家研究点数
            long science = map.getOrDefault(TECHNOLOGY, 0l);
            tops.add(new TopInfo(pid,faceId,playerName, total, staffNum, science, promotion));
        });
        return tops;
    }

    public TopInfo queryMyself(UUID owner) {
        long endTime = getEndTime(System.currentTimeMillis());
        long startTime = endTime - DAY_MILLISECOND;
        long myself = LogDb.queryMyself(startTime, endTime, owner, LogDb.getDayPlayerIncome());
        ArrayList<TopInfo> tops = new ArrayList<>();
        Player player = GameDb.getPlayer(owner);
        long staffNum = City.instance().getPlayerStaffNum(owner);
        String name = player == null ? "" : player.getName();
        String faceId =player == null ? "" :  player.getFaceId();
        Map<Integer, Long> map = EvaManager.getInstance().getPlayerSumValue(owner);
        // 玩家推广点数投入
        long promotion = map.getOrDefault(PROMOTION, 0l);
        // 玩家研究点数
        long science = map.getOrDefault(TECHNOLOGY, 0l);
        return new TopInfo(owner, faceId, name, myself, staffNum, science, promotion);
    }

    public List<TopInfo> queryProductRanking(int bt, int itemId) {
        long endTime = getEndTime(System.currentTimeMillis());
        long startTime = endTime - DAY_MILLISECOND;
        ArrayList<TopInfo> tops = new ArrayList<>();
        List<Document> list = null;
        if (bt == MetaBuilding.RETAIL) {
            list = LogDb.dayYesterdayRetailProductIncome(startTime, endTime, itemId, LogDb.getNpcBuyInShelf());
        } else {
            list = LogDb.dayYesterdayProductIncome(startTime, endTime, itemId, bt, LogDb.getBuyInShelf());
        }
        list.stream().filter(o -> o != null).forEach(d -> {
            UUID pid = d.get("id", UUID.class);
            int staffNum = getPlayerIndustryStaffNum(pid, bt);
            // 玩家名称
            Player player = GameDb.getPlayer(pid);
            String playerName = player == null ? "" : player.getName();
            //faceId
            String faceId = player == null ? "" : player.getFaceId();
            long total = d.getLong(KEY_TOTAL);
            // 具体商品投入的点数
            Map<Integer, Long> map = EvaManager.getInstance().getItemPoint(pid, itemId);
            // 玩家推广点数投入
            long promotion = map.getOrDefault(PROMOTION, 0l);
            // 玩家研究点数
            long science = map.getOrDefault(TECHNOLOGY, 0l);
            tops.add(new TopInfo(pid, faceId, playerName, total, staffNum, science, promotion));
        });
        return tops;
    }

    public TopInfo queryMyself(UUID owner, int bt, int itemId) {
        long endTime = getEndTime(System.currentTimeMillis());
        long startTime = endTime - DAY_MILLISECOND;
        long income = 0;
        if (bt == MetaBuilding.RETAIL) {
            income = LogDb.queryMyselfRetail(startTime, endTime, owner, itemId, LogDb.getNpcBuyInShelf());
        } else {
            income = LogDb.queryMyself(startTime, endTime, owner, bt, itemId, LogDb.getBuyInShelf());
        }
        Player player = GameDb.getPlayer(owner);
        String name = player == null ? "" : player.getName();
        String faceId = player == null ? "" : player.getFaceId();
        int staffNum = getPlayerIndustryStaffNum(owner, bt);
        Map<Integer, Long> map = EvaManager.getInstance().getItemPoint(owner, itemId);
        // 玩家推广点数投入
        long promotion = map.getOrDefault(PROMOTION, 0l);
        // 玩家研究点数
        long science = map.getOrDefault(TECHNOLOGY, 0l);

        return new TopInfo(owner, faceId, name, income, staffNum, science, promotion);
    }

}
