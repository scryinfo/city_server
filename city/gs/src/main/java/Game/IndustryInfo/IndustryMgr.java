package Game.IndustryInfo;

import Game.*;
import Game.Meta.MetaBuilding;
import Game.Timers.PeriodicTimer;
import Shared.LogDb;
import gs.Gs;
import org.bson.Document;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class IndustryMgr {
    public static final long DAY_MILLISECOND = 1000 * 3600 * 24;
    private static IndustryMgr instance = new IndustryMgr();

    public static IndustryMgr instance() {
        return instance;
    }

    static long nowTime = 0;

    static {
        nowTime = System.currentTimeMillis();
    }

    private PeriodicTimer timer = new PeriodicTimer((int) TimeUnit.DAYS.toMillis(1));


    public void update(long diffNano) {
        if (timer.update(diffNano)) {
            long endTime = getEndTime(System.currentTimeMillis());
            long startTime = endTime - DAY_MILLISECOND;
            List<Document> source = source(startTime, endTime);
            LogDb.insertIndustrySupplyAndDemand(source);
        }
    }

    public static long getEndTime(long nowTime) {
        return nowTime - (nowTime + TimeZone.getDefault().getRawOffset()) % DAY_MILLISECOND;
    }

    public static void main(String[] args) {
        long endTime = getEndTime(System.currentTimeMillis());
        long startTime = endTime - DAY_MILLISECOND-DAY_MILLISECOND;
        System.err.println(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(1565747991504l));
        System.err.println(endTime);
        System.err.println(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(startTime));
        System.err.println(startTime);

    }

    public List<Document> source(long startTime, long endTime) {
        List<Document> list = new ArrayList<>();
        // material
        int sumM = City.instance().typeBuilding.getOrDefault(MetaBuilding.MATERIAL, new HashSet<>()).stream().mapToInt(Building::getTotalSaleCount).sum();
        long demandM = LogDb.queryIndestrySum(MetaBuilding.MATERIAL, startTime, endTime);
        list.add(new Document().append("type", MetaBuilding.MATERIAL).append("supply", (sumM + demandM)).append("demand", demandM).append("time", endTime));
        // produce
        int sumP = City.instance().typeBuilding.getOrDefault(MetaBuilding.PRODUCE, new HashSet<>()).stream().mapToInt(Building::getTotalSaleCount).sum();
        long demandP = LogDb.queryIndestrySum(MetaBuilding.PRODUCE, startTime, endTime);
        list.add(new Document().append("type", MetaBuilding.PRODUCE).append("supply", (sumP + demandP)).append("demand", demandP).append("time", endTime));
        // technology
        int sumT = City.instance().typeBuilding.getOrDefault(MetaBuilding.TECHNOLOGY, new HashSet<>()).stream().mapToInt(Building::getTotalSaleCount).sum();
        long demandT = LogDb.queryIndestrySum(MetaBuilding.TECHNOLOGY, startTime, endTime);
        list.add(new Document().append("type", MetaBuilding.TECHNOLOGY).append("supply", (sumT + demandT)).append("demand", demandT).append("time", endTime));
        // promote
        int sumPro = City.instance().typeBuilding.getOrDefault(MetaBuilding.PROMOTE, new HashSet<>()).stream().mapToInt(Building::getTotalSaleCount).sum();
        long demandPro = LogDb.queryIndestrySum(MetaBuilding.PROMOTE, startTime, endTime);
        list.add(new Document().append("type", MetaBuilding.PROMOTE).append("supply", (sumPro + demandPro)).append("demand", demandPro).append("time", endTime));
        // apartment
        int sumA = City.instance().typeBuilding.getOrDefault(MetaBuilding.APARTMENT, new HashSet<>()).stream().mapToInt(Building::getTotalSaleCount).sum();
        long demandA = LogDb.queryApartmentIndestrySum(startTime, endTime,LogDb.getNpcRentApartment());
        list.add(new Document().append("type", MetaBuilding.APARTMENT).append("supply", (sumA + demandA)).append("demand", demandA).append("time", endTime));
        // retailShop
        int sumR = City.instance().typeBuilding.getOrDefault(MetaBuilding.RETAIL, new HashSet<>()).stream().mapToInt(Building::getTotalSaleCount).sum();
        long demadnR = LogDb.queryIndestrySum(startTime, endTime,LogDb.getNpcBuyInShelf());
        list.add(new Document().append("type", MetaBuilding.RETAIL).append("supply", (sumR + demadnR)).append("demand", demadnR).append("time", endTime));
        // ground
        int sellingCount = GroundManager.instance().getAllSellingCount();
        long demadnG = LogDb.queryIndestrySum(startTime, endTime,LogDb.getBuyGround());
        list.add(new Document().append("type", Gs.SupplyAndDemand.IndustryType.GROUND_VALUE).append("supply", (sellingCount + demadnG)).append("demand", demadnG).append("time", endTime));
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

    public List<TopInfo> queryTop(int buildingType) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);

        Date endDate = calendar.getTime();
        long endTime=endDate.getTime();

        calendar.add(Calendar.DATE, -1);
        Date startDate = calendar.getTime();
        long startTime=startDate.getTime();
        List<Document> list = LogDb.dayYesterdayPlayerIncome(startTime, endTime, buildingType, LogDb.getDayPlayerIncome());
        ArrayList<TopInfo> tops = new ArrayList<>();
        list.stream().filter(o->o.getInteger("id")!=null).forEach(d->{
            UUID pid = d.get("id", UUID.class);
            // 玩家行业总工人数
            int staffNum = getPlayerIndustryStaffNum(pid, buildingType);
            // 玩家名称
            String playerName = GameDb.getPlayer(pid) == null ? "" : GameDb.getPlayer(pid).getName();
            // 玩家昨日收入
            long total = d.getLong("total");
            // 玩家科技点数投入
            long science = 0;
            //
            long promotion = 0;
            tops.add(new TopInfo(pid,playerName, total, staffNum, science, promotion));
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
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);

        Date endDate = calendar.getTime();
        long endTime=endDate.getTime();

        calendar.add(Calendar.DATE, -1);
        Date startDate = calendar.getTime();
        long startTime=startDate.getTime();
        List<Document> list = LogDb.dayYesterdayPlayerIncome(startTime, endTime, Gs.SupplyAndDemand.IndustryType.GROUND_VALUE, LogDb.getDayPlayerIncome());
        ArrayList<TopInfo> tops = new ArrayList<>();
        list.stream().filter(o->o.getInteger("id")!=null).forEach(d->{
            UUID pid = d.get("id", UUID.class);
            // 玩家名称
            String playerName = GameDb.getPlayer(pid).getName();
            // 玩家昨日收入
            long total = d.getLong("total");
            // 成交量
            int count = LogDb.groundSum(startTime,endTime,pid); // 查询土地成交量
            tops.add(new TopInfo(pid, playerName, total, count));
        });
        return tops;
    }

    public TopInfo queryMyself(UUID owner,int type) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);

        Date endDate = calendar.getTime();
        long endTime=endDate.getTime();

        calendar.add(Calendar.DATE, -1);
        Date startDate = calendar.getTime();
        long startTime=startDate.getTime();
        if (type == Gs.SupplyAndDemand.IndustryType.GROUND_VALUE) {
            long ground = LogDb.queryMyself(startTime, endTime, owner, type, LogDb.getDayPlayerIncome());
            String name = GameDb.getPlayer(owner)== null ? "" : GameDb.getPlayer(owner).getName();
            int count = LogDb.groundSum(startTime,endTime,owner); // 查询土地成交量
            return new TopInfo(owner, name, ground, count);
        } else {
            long myself = LogDb.queryMyself(startTime, endTime, owner, type, LogDb.getDayPlayerIncome());
            String name = GameDb.getPlayer(owner)== null ? "" : GameDb.getPlayer(owner).getName();
            int staffNum = getPlayerIndustryStaffNum(owner, type);
            // 玩家科技点数投入
            long science = 0;
            //
            long promotion = 0;
            return new TopInfo(owner, name, myself, staffNum, science, promotion);
        }
    }


}
