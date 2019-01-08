package Game;

import Game.Action.*;
import Shared.Util;
import com.mongodb.Block;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.MongoException;
import gs.Gs;
import org.apache.log4j.Logger;
import org.bson.Document;

import javax.persistence.AttributeConverter;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

class MetaNpc {
	MetaNpc(Document d) {
		id = d.getInteger("id");
	}
	public int id;
}
class MetaCity {
	public MetaCity(Document d) throws Exception {
		this.x = d.getInteger("x");
		this.y = d.getInteger("y");
		this.gridX = d.getInteger("gridX");
		this.gridY = d.getInteger("gridY");
		this.name = d.getString("name");
		this.timeZone = d.getInteger("timeZone");
        this.timeSection = ((List<Integer>)d.get("timeSection")).stream().mapToInt(Integer::valueOf).toArray();
        Arrays.sort(timeSection);
        if(timeSection.length == 0 || timeSection[0] != 0 || timeSection[timeSection.length-1] > 23)
            throw new Exception("city time section config is incorrect!");
        this.minHour = minTimeSectionHour();
	}
	public int x;
	public int y;
	public int gridX;
	public int gridY;
	public String name;
	public int timeZone;
	public int[] timeSection;
    public int minHour;
    public int indexOfHour(int nowHour) {
        int idx = Arrays.binarySearch(this.timeSection, nowHour);
        if(idx < 0)
            idx = -(idx+2); // fuck java
        return idx;
    }

    private int minTimeSectionHour() {
        int mini = Integer.MAX_VALUE;
        for(int i = 1; i < this.timeSection.length; ++i) {
            int d = this.timeSection[i] - this.timeSection[i-1];
            if(d < mini)
                mini = d;
        }
        return mini;
    }

    public int timeSectionDuration(int index) {
        if(index != this.timeSection.length-1)
            return this.timeSection[index+1] - this.timeSection[index];
        else
            return 24-this.timeSection[index];
    }

    public int nextTimeSectionDuration(int index) {
       return timeSectionDuration(nextIndex(index));
    }
    public int nextIndex(int index) {
        if(index >= this.timeSection.length)
            throw new IllegalArgumentException();
        if(index == this.timeSection.length-1)
            return 0;
        return index+1;
    }
    public int nextTimeSectionHour(int index) {
        return this.timeSection[nextIndex(index)];
    }
}
class MetaTalent {

    public static final class Key {
        public Key(int buildingType, int lv) {
            this.buildingType = buildingType;
            this.lv = lv;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Key key = (Key) o;
            return buildingType == key.buildingType &&
                    lv == key.lv;
        }

        @Override
        public int hashCode() {
            return Objects.hash(buildingType, lv);
        }

        int buildingType;
        int lv;
    }
    public static int buildingType(int id) {
        return MetaItem.baseId(id)%100;
    }
    MetaTalent(Document d) {
        this.id = d.getInteger("_id");
        this.workDaysMin = d.getInteger("workDaysMin");
        this.workDaysMax = d.getInteger("workDaysMax");

        this.itemWeights = ((List<Integer>)d.get("itemWeights")).stream().mapToInt(Integer::intValue).toArray();
        assert itemWeights.length == 4;
        this.itemsV1 = (List<Integer>)d.get("itemsV1");
        this.itemsV2 = (List<Integer>)d.get("itemsV2");
        this.itemsV3 = (List<Integer>)d.get("itemsV3");
        this.itemWeights = ((List<Integer>)d.get("funcWeights")).stream().mapToInt(Integer::intValue).toArray();
        assert funcWeights.length == lv;
        this.speedRatioMin = d.getInteger("speedRatioMin");
        this.speedRatioMax = d.getInteger("speedRatioMax");
        this.qtyAddMin = d.getInteger("qtyAddMin");
        this.qtyAddMax = d.getInteger("qtyAddMax");
        this.advRatioMin = d.getInteger("advRatioMin");
        this.advRatioMax = d.getInteger("advRatioMax");
        this.keepDaysMin = d.getInteger("keepDaysMin");
        this.keepDaysMax = d.getInteger("keepDaysMax");
        this.salaryRatioMin = d.getInteger("salaryRatioMin");
        this.salaryRatioMax = d.getInteger("salaryRatioMax");
        this.nameIdxMin = d.getInteger("nameIdxMin");
        this.nameIdxMax = d.getInteger("nameIdxMax");
    }
    public int id;
    public int createSec;
    public int lv;
    public int workDaysMin;
    public int workDaysMax;
    public int nameIdxMin;
    public int nameIdxMax;
    public int[] itemWeights;
    public List<Integer> itemsV1;
    public List<Integer> itemsV2;
    public List<Integer> itemsV3;
    public int[] funcWeights;
    public int speedRatioMin;
    public int speedRatioMax;
    public int qtyAddMin;
    public int qtyAddMax;
    public int advRatioMin;
    public int advRatioMax;
    public int keepDaysMin;
    public int keepDaysMax;
    public int salaryRatioMin;
    public int salaryRatioMax;
}
abstract class MetaItem {
    public static final int MATERIAL = 21;
    public static final int GOOD = 22;
    public static int level(int id) {
        return id % 1000;
    }
    public static int baseId(int id) {
        return id / 1000;
    }
    MetaItem(Document d) {
        this.id = d.getInteger("_id");
        this.n = d.getDouble("numOneSec");
        this.useDirectly = d.getBoolean("default");
        this.size = d.getInteger("size");
    }
    public int id;
    double n;
    int size;
    boolean useDirectly;

    public static int type(int targetId) {
        return targetId / MetaData.ID_RADIX;
    }

    public static final class Converter implements AttributeConverter<MetaItem, Integer> {
        @Override
        public Integer convertToDatabaseColumn(MetaItem attribute) {
            return attribute.id;
        }

        @Override
        public MetaItem convertToEntityAttribute(Integer dbData) {
            return MetaData.getItem(dbData);
        }
    }
    public static boolean isItem(int id) {
        return id / MetaData.ID_RADIX >= MATERIAL;
    }
}
final class MetaMaterial extends MetaItem {
    MetaMaterial(Document d) {
        super(d);
    }
}

class AIBuilding extends ProbBase {
    AIBuilding(Document d) {
        super(Type.ALL.ordinal(), d);
    }
    enum Type {
        IDLE,
        GOTO_HOME,
        GOTO_WORK,
        GOTO_APARTMENT,
        GOTO_PUBLIC_FACILITY,
        GOTO_RETAIL_SHOP,
        ALL
    }
    IAction random(double idleRatio, BrandManager.BuildingRatio ratio, int aiId) {
        IAction.logger.info("AIBuilding id " + this.id + " building ratio " + ratio.toString() + " aiId " + aiId);
        int[] d = Arrays.copyOf(weight, weight.length);
        d[Type.IDLE.ordinal()] *= idleRatio;
        d[Type.GOTO_HOME.ordinal()] *= idleRatio;
        d[Type.GOTO_WORK.ordinal()] *= idleRatio;
        d[Type.GOTO_APARTMENT.ordinal()] *= ratio.apartment;
        d[Type.GOTO_PUBLIC_FACILITY.ordinal()] *= ratio.publicFacility;
        d[Type.GOTO_RETAIL_SHOP.ordinal()] *= ratio.retail;
        IAction.logger.info("AIBuilding id " + this.id + " weights " + Arrays.toString(d));
        switch (Type.values()[super.randomIdx(d)]) {
            case IDLE:
                return new Idle();
            case GOTO_HOME:
                return new GoHome();
            case GOTO_WORK:
                return new GoWork();
            case GOTO_APARTMENT:
                return new JustVisit(MetaBuilding.APARTMENT);
            case GOTO_PUBLIC_FACILITY:
                return new JustVisit(MetaBuilding.PUBLIC);
            case GOTO_RETAIL_SHOP:
                return new Shopping(aiId);
        }
        return null;
    }
}
class MetaApartment extends MetaBuilding {
    MetaApartment(Document d) {
        super(d);
        this.npc = d.getInteger("npc");
        this.qty = d.getInteger("qty");
    }
	public int npc;
    public int qty;
}

abstract class MetaFactoryBase extends MetaBuilding {
    public int lineNum;
    public int lineMaxWorkerNum;
    public int lineMinWorkerNum;
    public int storeCapacity;
    public int shelfCapacity;

    MetaFactoryBase(Document d) {
        super(d);
        this.lineNum = d.getInteger("lineNum");
        this.lineMinWorkerNum = d.getInteger("lineMinWorkerNum");
        this.lineMaxWorkerNum = d.getInteger("lineMaxWorkerNum");
        this.storeCapacity = d.getInteger("storeCapacity");
        this.shelfCapacity = d.getInteger("shelfCapacity");
    }
}
class MetaMaterialFactory extends MetaFactoryBase {
    MetaMaterialFactory(Document d) {
        super(d);
    }
}
class MetaProduceDepartment extends MetaFactoryBase {

    MetaProduceDepartment(Document d) {
        super(d);
    }
}
class MetaRetailShop extends MetaPublicFacility {
    public int saleTypeNum;
    public int storeCapacity;
    public int shelfCapacity;
    MetaRetailShop(Document d) {
        super(d);
        this.saleTypeNum = d.getInteger("saleTypeNum");
        this.storeCapacity = d.getInteger("storeCapacity");
        this.shelfCapacity = d.getInteger("shelfCapacity");
    }
}
class MetaLaboratory extends MetaBuilding {
    public int lineNum;
    public int lineMaxWorkerNum;
    public int lineMinWorkerNum;
    public int storeCapacity;

    MetaLaboratory(Document d) {
        super(d);
        this.lineNum = d.getInteger("lineNum");
        this.lineMinWorkerNum = d.getInteger("lineMinWorkerNum");
        this.lineMaxWorkerNum = d.getInteger("lineMaxWorkerNum");
        this.storeCapacity = d.getInteger("storeCapacity");
    }
}
class GoodFormula {
    public static final class Converter implements AttributeConverter<GoodFormula, Integer> {
        @Override
        public Integer convertToDatabaseColumn(GoodFormula attribute) {
            return attribute.id;
        }

        @Override
        public GoodFormula convertToEntityAttribute(Integer dbData) {
            return MetaData.getFormula(dbData);
        }
    }
    GoodFormula(Document d) {
        id = d.getInteger("good");
        for(int i = 0; i < material.length; ++i) {
            material[i] = new Info();
            material[i].item = MetaData.getItem(d.getInteger("material" + i));
            material[i].n = d.getInteger("num" + i);
        }
    }
    int id;
    public static final class Info {
        MetaItem item;
        int n;
    }
    Info[] material = new Info[3];
}
class Formula {
    public static final class Key {
        Type type;
        int targetId;
        int targetLv;

        public Key(Type type, int targetId, int targetLv) {
            this.type = type;
            this.targetId = targetId;
            this.targetLv = targetLv;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Key key = (Key) o;
            return targetId == key.targetId &&
                    targetLv == key.targetLv &&
                    type == key.type;
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, targetId, targetLv);
        }
    }
    enum Type{
        RESEARCH,
        INVENT
    }
    Key key;
    int phaseSec;
    int phase;
    int critiChance;
    int critiV;
    int[] successChance;
    public static final class Consume {
        MetaMaterial m;
        int n;
    }
    Consume[] consumes = new Consume[3];

    Formula(Document d) {
        key = new Key(Type.values()[d.getInteger("type")], d.getInteger("good"), d.getInteger("lv"));
        phaseSec = d.getInteger("phaseSec");
        phase = d.getInteger("phase");
        critiChance = d.getInteger("critiChance");
        critiV = d.getInteger("critiV");
        List<Integer> l = (List<Integer>) d.get("successChance");
        if(l.size() != phase)
            throw new IllegalArgumentException();
        successChance = new int[l.size()];
        for(int i = 0; i < l.size(); ++i) {
            successChance[i] = l.get(i);
        }
        for(int i = 0; i < consumes.length; ++i) {
            consumes[i] = new Consume();
            consumes[i].m = MetaData.getMaterial(d.getInteger("material" + i));
            consumes[i].n = d.getInteger("num" + i);
        }
    }
}
class MetaPublicFacility extends MetaBuilding {
    public int adNum;
    public int qty;
    public int maxNpcFlow;
    public int minDayToRent;
    public int maxDayToRent;
    public int maxRentPreDay;
    public int depositRatio;
    MetaPublicFacility(Document d) {
        super(d);
        this.adNum = d.getInteger("adNum");
        this.qty = d.getInteger("qty");
        this.maxNpcFlow = d.getInteger("maxNpcFlow");
        this.minDayToRent = d.getInteger("minDayToRent");
        this.maxDayToRent = d.getInteger("maxDayToRent");
        this.maxRentPreDay = d.getInteger("maxRentPreDay");
        this.depositRatio = d.getInteger("depositRatio");
    }
}

class MetaTalentCenter extends MetaBuilding {
    public int qty;
    public int createSecPreWorker;
    MetaTalentCenter(Document d) {
        super(d);
        this.qty = d.getInteger("qty");
        this.createSecPreWorker = d.getInteger("createSecPreWorker");
    }
}
class MetaGroundAuction {
    public static final class Converter implements AttributeConverter<MetaGroundAuction, UUID> {
        @Override
        public UUID convertToDatabaseColumn(MetaGroundAuction attribute) {
            return attribute.id;
        }

        @Override
        public MetaGroundAuction convertToEntityAttribute(UUID dbData) {
            return MetaData.getGroundAuction(dbData);
        }
    }
    MetaGroundAuction(Document d) {
        this.id = Util.toUuid(d.getObjectId("_id"));
        List<Integer> index = (List<Integer>) d.get("area");
        if(index.size() % 2 != 0)  {
            System.out.println("Game.MetaGroundAuction area size is not odd!");
            System.exit(-1);
        }
        for(int i = 0; i < index.size(); i+=2)
            this.area.add(new Coordinate(index.get(i), index.get(i+1)));
        this.beginTime = TimeUnit.SECONDS.toMillis(d.getLong("time"));
        this.endTime = this.beginTime + TimeUnit.SECONDS.toMillis(d.getInteger("duration"));
        this.price = d.getInteger("price");
    }

    Gs.MetaGroundAuction.Target toProto() {
        Gs.MetaGroundAuction.Target.Builder b = Gs.MetaGroundAuction.Target.newBuilder()
                .setId(Util.toByteString(id))
                .setBeginTime(beginTime)
                .setDurationSec((int) (endTime - beginTime))
                .setBasePrice(price);
        for(Coordinate c : area) {
            b.addArea(c.toProto());
        }
        return b.build();
    }
    UUID id;    // use to check this data has been changed or not, compare it with player db(if anyone bid it)
    Set<Coordinate> area = new HashSet<>();
    long beginTime;
    long endTime;
    int price;
}

class SysPara {
    public SysPara(Document d) {
        this.playerBagCapcaity = d.getInteger("playerBagCapacity");
        this.bagCapacityDelta = d.getInteger("bagCapacityDelta");
        this.transferChargeRatio = d.getInteger("transferChargeRatio");
        this.centerStorePos.x = d.getInteger("centerStoreX");
        this.centerStorePos.y = d.getInteger("centerStoreY");
    }
    int playerBagCapcaity;
    int bagCapacityDelta;
    int transferChargeRatio;
    Coordinate centerStorePos = new Coordinate();
}

public class MetaData {
    public static final int ID_RADIX = 100000;
	private static final Logger logger = Logger.getLogger(MetaData.class);
	private static final String dbName = "meta";
	private static MongoClient mongoClient;
	//collection name
	private static final String npcColName = "Npc";
	private static final String cityColName = "City";
    private static final String apartmentColName = "Apartment";
    private static final String materialFactoryColName = "MaterialFactory";
    private static final String produceDepartmentColName = "ProduceDepartment";
    private static final String retailShopColName = "RetailShop";
    private static final String laboratoryColName = "Laboratory";
    private static final String publicFacilityColName = "PublicFacility";
    private static final String talentCenterColName = "TalentCenter";
    private static final String groundAuctionColName = "GroundAuction";
    private static final String initialBuildingColName = "InitialBuilding";
    private static final String trivialBuildingColName = "TrivialBuilding";
    private static final String sysParaColName = "SysPara";
    private static final String materialColName = "Material";
    private static final String goodColName = "Good";
    private static final String aiBuildingColName = "AIBuilding";
    private static final String aiBuyColName = "AIBuy";
    private static final String aiLuxColName = "AILux";
    private static final String dayColName = "Holiday";
    private static final String buildingSpendColName = "BuildingSpendRatio";
    private static final String goodSpendColName = "GoodSpendRatio";
    private static final String formulaColName = "Formular";
    private static final String goodFormulaColName = "GoodFormular";
    private static final String talentColName = "Talent";
    private static final String talentLvCreateColName = "TalentLvCreate";
    //global field
    private static SysPara sysPara;
	private static MetaCity city;
    private static final HashMap<UUID, MetaGroundAuction> groundAuction = new HashMap<>();
	private static final TreeMap<Integer, MetaNpc> npc = new TreeMap<>();
    private static final TreeMap<Integer, MetaBuilding> trivial = new TreeMap<>();
    private static final TreeMap<Integer, MetaApartment> apartment = new TreeMap<>();
    private static final TreeMap<Integer, MetaProduceDepartment> produceDepartment = new TreeMap<>();
    private static final TreeMap<Integer, MetaRetailShop> retailShop = new TreeMap<>();
    private static final TreeMap<Integer, MetaLaboratory> laboratory = new TreeMap<>();
    private static final TreeMap<Integer, MetaTalentCenter> talentCenter = new TreeMap<>();
    private static final TreeMap<Integer, MetaPublicFacility> publicFacility = new TreeMap<>();
    private static final TreeMap<Integer, MetaMaterialFactory> materialFactory = new TreeMap<>();
    private static final TreeMap<Long, AIBuilding> aiBuilding = new TreeMap<>();
    private static final TreeMap<Long, AIBuy> aiBuy = new TreeMap<>();
    private static final TreeMap<Long, AILux> aiLux = new TreeMap<>();
    private static final TreeMap<Integer, Double> buildingSpendRatio = new TreeMap<>();
    private static final TreeMap<Integer, Double> goodSpendRatio = new TreeMap<>();
    private static final HashMap<Integer, MetaMaterial> material = new HashMap<>();
    private static final HashMap<Integer, MetaGood> good = new HashMap<>();
    private static final HashMap<Formula.Key, Formula> formula = new HashMap<>();
    private static final HashMap<Integer, GoodFormula> goodFormula = new HashMap<>();

    public static MetaBuilding getTrivialBuilding(int id) {
        return trivial.get(id);
    }
    public static MetaMaterialFactory getMaterialFactory(int id) {
        return materialFactory.get(id);
    }
    public static MetaPublicFacility getPublicFacility(int id) {
        return publicFacility.get(id);
    }
    public static MetaApartment getApartment(int id) {
        return apartment.get(id);
    }
    public static MetaLaboratory getLaboratory(int id) {
        return laboratory.get(id);
    }
    public static MetaRetailShop getRetailShop(int id) {
        return retailShop.get(id);
    }
    public static MetaProduceDepartment getProduceDepartment(int id) {
        return produceDepartment.get(id);
    }
    public static int getDayId() {
        return dayId;
    }
    private static int dayId;
    public static void updateDayId() {
        dayId = dayIds.get(new DayKey());
    }
    private static Map<DayKey, Integer> dayIds = new HashMap<>();
    private static Map<Integer, int[]> talentLvWeights = new HashMap<>();
    private static Map<MetaTalent.Key, MetaTalent> talent = new HashMap<>();
    public static int getTalentLv(int score) {
        int[] ws = talentLvWeights.get(score);
        if(ws == null) {
            logger.fatal("score " + score + " don't exit");
            return 0;
        }
        return ProbBase.randomIdx(ws);
    }
    public static MetaTalent getTalent(int buildingType, int talentLv) {
        return talent.get(new MetaTalent.Key(buildingType, talentLv));
    }
    public static double getBuildingSpendMoneyRatio(int type) {
        return buildingSpendRatio.get(type);
    }

    public static double getGoodSpendMoneyRatio(int mId) {
        return goodSpendRatio.get(mId);
    }

    public static MetaTalentCenter getTalentCenter(int id) {
        return talentCenter.get(id);
    }

    public static final class DayKey {
        int y;
        int m;
        int d;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DayKey dayKey = (DayKey) o;
            return y == dayKey.y &&
                    m == dayKey.m &&
                    d == dayKey.d;
        }

        @Override
        public int hashCode() {
            return Objects.hash(y, m, d);
        }

        public DayKey() {
            LocalDateTime now = LocalDateTime.now();
            this.y = now.getYear() % 100;
            this.m = now.getMonthValue();
            this.d = now.getDayOfMonth();
        }
        public DayKey(Document document) {
            this.y = document.getInteger("year");
            this.m = document.getInteger("month");
            this.d = document.getInteger("day");
        }
    }
    public static void initDayId() {
        mongoClient.getDatabase(dbName).getCollection(dayColName).find().forEach((Block<Document>) doc -> {
            dayIds.put(new DayKey(doc), doc.getInteger("id"));
        });
    }
    public static void initSpendRatio() {
        mongoClient.getDatabase(dbName).getCollection(goodSpendColName).find().forEach((Block<Document>) doc -> {
            goodSpendRatio.put(doc.getInteger("_id"), doc.getDouble("ratio"));
        });
        mongoClient.getDatabase(dbName).getCollection(buildingSpendColName).find().forEach((Block<Document>) doc -> {
            buildingSpendRatio.put(doc.getInteger("_id"), doc.getDouble("ratio"));
        });
    }
    public static MetaBuilding getBuilding(int id) {
        switch(MetaBuilding.type(id)) {
            case MetaBuilding.TRIVIAL:
                return trivial.get(id);
            case MetaBuilding.MATERIAL:
                return materialFactory.get(id);
            case MetaBuilding.PRODUCE:
                return produceDepartment.get(id);
            case MetaBuilding.RETAIL:
                return retailShop.get(id);
            case MetaBuilding.APARTMENT:
                return apartment.get(id);
            case MetaBuilding.LAB:
                return laboratory.get(id);
            case MetaBuilding.PUBLIC:
                return publicFacility.get(id);
        }
        return null;
    }
    public static class InitialBuildingInfo {
        public InitialBuildingInfo(Document d) {
            this.x = d.getInteger("x");
            this.y = d.getInteger("y");
            this.id = d.getInteger("buildingId");
        }
        public int x;
        public int y;
        public int id;
        // Initial building ObjectId have no requirement to be identity all the time, so omit it
    }
    private static final List<InitialBuildingInfo> initialBuilding = new ArrayList<>();
    public static final Formula getFormula(Formula.Key key) {
        return formula.get(key);
    }
    public static final GoodFormula getFormula(int goodId) {
        return goodFormula.get(goodId);
    }
    public static final MetaMaterial getMaterial(int id) {
        return material.get(id);
    }
    public static final MetaGood getGood(int id) {
        return good.get(id);
    }
    public static final MetaItem getItem(int id) {
        MetaItem res = getMaterial(id);
        return res == null ? getGood(id):res;
    }
    public static AIBuilding getAIBuilding(long id) { return aiBuilding.get(id); }
    public static AIBuy getAIBuy(long id) {
        return aiBuy.get(id);
    }
    public static AILux getAILux(long id) {
        return aiLux.get(id);
    }
    public static List<InitialBuildingInfo> getAllInitialBuilding() {
        return initialBuilding;
    }
    public static Set<MetaGroundAuction> getNonFinishedGroundAuction() {
        Set<MetaGroundAuction> res = new HashSet<>();
        long now = System.currentTimeMillis();
        for(MetaGroundAuction a : groundAuction.values()) {
            if(a.endTime > now)
                res.add(a);
        }
        return res;
    }
    public static Set<MetaGroundAuction> getNonBeginGroundAuction() {
        Set<MetaGroundAuction> res = new HashSet<>();
        long now = System.currentTimeMillis();
        for(MetaGroundAuction a : groundAuction.values()) {
            if(a.beginTime > now)
                res.add(a);
        }
        return res;
    }
    public static MetaGroundAuction getGroundAuction(UUID id) {
        return groundAuction.get(id);
    }
	public static void init(String uri){
		try{
			mongoClient = new MongoClient(new MongoClientURI(uri));
		} catch(MongoException e){
			logger.fatal(e.getMessage());
		}
	}
	private static void initTalent() {
        mongoClient.getDatabase(dbName).getCollection(talentColName).find().forEach((Block<Document>) doc -> {
            MetaTalent t = new MetaTalent(doc);
            talent.put(new MetaTalent.Key(MetaTalent.buildingType(t.id), t.lv), t);
        });
        mongoClient.getDatabase(dbName).getCollection(talentLvCreateColName).find().forEach((Block<Document>) doc -> {
            int[] ws = {doc.getInteger("v0"), doc.getInteger("v1"), doc.getInteger("v2")};
            talentLvWeights.put(doc.getInteger("_id"), ws);
        });
    }
	private static void initCity() throws Exception {
		Document d = mongoClient.getDatabase(dbName).getCollection(cityColName).find().first();
		if(d == null)
			throw new Exception("city table is empty!");
		city = new MetaCity(d);
	}
    private static void initSysPara() throws Exception {
        Document d = mongoClient.getDatabase(dbName).getCollection(sysParaColName).find().first();
        if(d == null)
            throw new Exception("SysPara table is empty!");
        sysPara = new SysPara(d);
    }
    private static void initAIBuilding() {
        mongoClient.getDatabase(dbName).getCollection(aiBuildingColName).find().forEach((Block<Document>) doc -> {
            AIBuilding m = new AIBuilding(doc);
            aiBuilding.put(m.id, m);
        });
    }
    private static void initAIBuy() {
        mongoClient.getDatabase(dbName).getCollection(aiBuyColName).find().forEach((Block<Document>) doc -> {
            AIBuy m = new AIBuy(doc);
            aiBuy.put(m.id, m);
        });
    }
    private static void initAILux() {
        mongoClient.getDatabase(dbName).getCollection(aiLuxColName).find().forEach((Block<Document>) doc -> {
            AILux m = new AILux(doc);
            aiLux.put(m.id, m);
        });
    }
//	public static void initNpc() {
//		mongoClient.getDatabase(dbName).getCollection(npcColName).find().forEach((Block<Document>) doc -> {
//			MetaNpc m = new MetaNpc(doc);
//			npc.put(m.id, m);
//		});
//	}
    public static void reloadGroundAuction() {
        groundAuction.clear();
        mongoClient.getDatabase(dbName).getCollection(groundAuctionColName).find().forEach((Block<Document>) doc -> {
            MetaGroundAuction m = new MetaGroundAuction(doc);
            groundAuction.put(m.id, m);
        });
    }

    public static Set<Integer> getAllDefaultToUseItemId() {
        return Collections.unmodifiableSet(defaultToUseItemId);
    }
    private static Set<Integer> defaultToUseItemId = new HashSet<>();

    public static void initMaterial() {
        mongoClient.getDatabase(dbName).getCollection(materialColName).find().forEach((Block<Document>) doc -> {
            MetaMaterial m = new MetaMaterial(doc);
            material.put(m.id, m);
            if(m.useDirectly)
                defaultToUseItemId.add(m.id);
        });
    }
    public static void initGood() {
        mongoClient.getDatabase(dbName).getCollection(goodColName).find().forEach((Block<Document>) doc -> {
            MetaGood m = new MetaGood(doc);
            good.put(m.id, m);
            if(m.useDirectly)
                defaultToUseItemId.add(m.id);
        });
    }
    public static void initBuilding() {
        mongoClient.getDatabase(dbName).getCollection(trivialBuildingColName).find().forEach((Block<Document>) doc -> {
            MetaBuilding m = new MetaBuilding(doc);
            trivial.put(m.id, m);
        });

        mongoClient.getDatabase(dbName).getCollection(apartmentColName).find().forEach((Block<Document>) doc -> {
            MetaApartment m = new MetaApartment(doc);
            apartment.put(m.id, m);
        });
        mongoClient.getDatabase(dbName).getCollection(materialFactoryColName).find().forEach((Block<Document>) doc -> {
            MetaMaterialFactory m = new MetaMaterialFactory(doc);
            materialFactory.put(m.id, m);
        });
        mongoClient.getDatabase(dbName).getCollection(produceDepartmentColName).find().forEach((Block<Document>) doc -> {
            MetaProduceDepartment m = new MetaProduceDepartment(doc);
            produceDepartment.put(m.id, m);
        });
        mongoClient.getDatabase(dbName).getCollection(retailShopColName).find().forEach((Block<Document>) doc -> {
            MetaRetailShop m = new MetaRetailShop(doc);
            retailShop.put(m.id, m);
        });
        mongoClient.getDatabase(dbName).getCollection(publicFacilityColName).find().forEach((Block<Document>) doc -> {
            MetaPublicFacility m = new MetaPublicFacility(doc);
            publicFacility.put(m.id, m);
        });
        mongoClient.getDatabase(dbName).getCollection(laboratoryColName).find().forEach((Block<Document>) doc -> {
            MetaLaboratory m = new MetaLaboratory(doc);
            laboratory.put(m.id, m);
        });
        mongoClient.getDatabase(dbName).getCollection(initialBuildingColName).find().forEach((Block<Document>) doc -> {
            InitialBuildingInfo i = new InitialBuildingInfo(doc);
            initialBuilding.add(i);
        });
        mongoClient.getDatabase(dbName).getCollection(talentCenterColName).find().forEach((Block<Document>) doc -> {
            MetaTalentCenter m = new MetaTalentCenter(doc);
            talentCenter.put(m.id, m);
        });
    }

    public static void initGoodFormula() {
        mongoClient.getDatabase(dbName).getCollection(goodFormulaColName).find().forEach((Block<Document>) doc -> {
            GoodFormula m = new GoodFormula(doc);
            goodFormula.put(m.id, m);
        });
    }
    public static void initFormula() {
        mongoClient.getDatabase(dbName).getCollection(formulaColName).find().forEach((Block<Document>) doc -> {
            Formula m = new Formula(doc);
            formula.put(m.key, m);
        });
    }
	public static MetaNpc getNpc(int id) {
		return npc.get(id);
	}
	public static MetaCity getCity() {
		return city;
	}
	public static SysPara getSysPara() {
        return sysPara;
    }
    public static void startUp() throws Exception {
        initSysPara();
		initCity();
		//initNpc();
        initMaterial();
        initGood();
		initBuilding();
        reloadGroundAuction();

        initAIBuilding();
        initAIBuy();
        initAILux();
        initDayId();
        initSpendRatio();

        initFormula();
        initGoodFormula();
	}
}
