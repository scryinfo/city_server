package Game.CityInfo;

import Game.*;
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
import java.util.stream.Collectors;

import static Shared.LogDb.KEY_TOTAL;

public class IndustryMgr {
    private static final int PROMOTION = 1;
    private static final int TECHNOLOGY = 2;
    private static final int INDUSTRY = 1;
    private static final int ITEM = 2;
    private static final String SUPPLY = "supply";
    private static final String DEMAND = "demand";
    private static final String TIME = "time";
    private static final String TYPE = "type";
    private static final String BUILDINGTYPE = "bt";
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
            List<Document> retail = retailSource(startTime, endTime);
            all.addAll(material);
            all.addAll(produce);
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
            list.add(new Document().append(BUILDINGTYPE, bt).append(SUPPLY, (sum + demand)).append(DEMAND, demand).append(TIME, endTime).append(TYPE, INDUSTRY));
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
        list.add(new Document().append(BUILDINGTYPE, MetaBuilding.APARTMENT).append(SUPPLY, (sumA + demandA)).append(DEMAND, demandA).append(TIME, endTime).append(TYPE, INDUSTRY));
        // retailShop
        int sumR = City.instance().typeBuilding.getOrDefault(MetaBuilding.RETAIL, new HashSet<>()).stream().mapToInt(Building::getTotalSaleCount).sum();
        long demandR = LogDb.queryIndestrySum(startTime, endTime,LogDb.getNpcBuyInShelf());
        list.add(new Document().append(BUILDINGTYPE, MetaBuilding.RETAIL).append(SUPPLY, (sumR + demandR)).append(DEMAND, demandR).append(TIME, endTime).append(TYPE, INDUSTRY));
        // ground
        int sellingCount = GroundManager.instance().getAllSellingCount();
        long demandG = LogDb.queryIndestrySum(startTime, endTime,LogDb.getBuyGround());
        list.add(new Document().append(BUILDINGTYPE, Gs.SupplyAndDemand.IndustryType.GROUND_VALUE).append(SUPPLY, (sellingCount + demandG)).append(DEMAND, demandG).append(TIME, endTime).append(TYPE, INDUSTRY));
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
            list.add(new Document().append(BUILDINGTYPE, MetaBuilding.MATERIAL).append(SUPPLY, (saleNum.get() + deman)).append(DEMAND, deman).append(TIME, endTime).append(TYPE, ITEM).append("tpi", itemId));
        });
        return list;
    }

    public List<Document> produceSource(long startTime, long endTime) {
        List<Document> list = new ArrayList<>();
        MetaData.getAllGoodId().stream().forEach(itemId -> {
            AtomicInteger saleNum = new AtomicInteger();
            City.instance().typeBuilding.getOrDefault(MetaBuilding.PRODUCE, new HashSet<>()).stream().filter(o -> !o.outOfBusiness()).forEach(b -> {
                saleNum.addAndGet(((FactoryBase) b).getShelf().getSaleNum(itemId));
            });
            long deman = LogDb.queryIndestrySum(MetaBuilding.PRODUCE, startTime, endTime, itemId);
            list.add(new Document().append(BUILDINGTYPE, MetaBuilding.PRODUCE).append(SUPPLY, (saleNum.get() + deman)).append(DEMAND, deman).append(TIME, endTime).append(TYPE, ITEM).append("tpi", itemId));
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
            list.add(new Document().append(BUILDINGTYPE, MetaBuilding.RETAIL).append(SUPPLY, (saleNum.get() + deman)).append(DEMAND, deman).append(TIME, endTime).append(TYPE, ITEM).append("tpi", itemId));
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
                    saleNum.addAndGet(((FactoryBase) b).getShelf().getSaleNum(itemId));
                    return;
                });
                return saleNum.get();
            case MetaBuilding.PRODUCE:
                City.instance().typeBuilding.getOrDefault(MetaBuilding.PRODUCE, new HashSet<>()).stream().filter(o -> !o.outOfBusiness()).forEach(b -> {
                    saleNum.addAndGet(((FactoryBase) b).getShelf().getSaleNum(itemId));
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

    // 成交数量
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

    // 获取玩家行业总人工数 不包含未开业建筑
    public int getPlayerIndustryStaffNum(UUID pid,int buildingType) {
        return City.instance().getPlayerBListByBtype(pid, buildingType).stream().filter(b->!b.outOfBusiness()).mapToInt(Building::getWorkerNum).sum();
    }

    // 获取行业总工人数  不包含未开业建筑
    public long getIndustryStaffNum(int buildingType) {
        return City.instance().typeBuilding.getOrDefault(buildingType, new HashSet<>()).stream().filter(b->!b.outOfBusiness()).mapToInt(Building::getWorkerNum).sum();
    }
    // 获取行业总营收
    public long getIndustrySumIncome(int buildingType) {
        return LogDb.queryIndustrySumIncome(buildingType);
    }
    // 土地排行信息
    public List<TopInfo> queryTop() {
        long endTime = getEndTime(System.currentTimeMillis());
        long startTime = endTime - DAY_MILLISECOND;
        List<Document> list = LogDb.dayYesterdayPlayerByGroundIncome(startTime, endTime, LogDb.getBuyGround());
        return list.stream().map(o -> {
            try {
                UUID pid = o.get("id", UUID.class);
                Player player = GameDb.getPlayer(pid);
                // 玩家昨日收入
                long total = o.getLong(KEY_TOTAL);
                // 成交量
                int count = LogDb.groundSum(startTime, endTime, pid); // 查询土地成交量

                return new TopInfo(pid, player.getFaceId(), player.getName(), total, count);
            } catch (Exception e) {
                return null;
            }
        }).filter(o -> o != null).collect(Collectors.toList());
    }

}
