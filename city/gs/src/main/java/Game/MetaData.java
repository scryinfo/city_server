package Game;

import Shared.Util;
import com.mongodb.Block;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.MongoException;
import gs.Gs;
import org.apache.log4j.Logger;
import org.bson.Document;

import javax.persistence.AttributeConverter;
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
        return this.timeSection[idx];
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
        if(index != this.timeSection[this.timeSection.length-1])
            return this.timeSection[index+1] - this.timeSection[index];
        else
            return 24-this.timeSection[index];
    }

    public int nextTimeSectionDuration(int index) {
        if(index+1 > this.timeSection.length)
            return timeSectionDuration(0);
        else
            return timeSectionDuration(index+1);
    }
}
abstract class MetaItem {
    MetaItem(Document d) {
        this.id = d.getInteger("_id");
        this.n = d.getDouble("numOneSec");
        this.useDirectly = d.getBoolean("default");
    }
    int id;
    double n;
    int size;
    boolean useDirectly;
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
}
final class MetaMaterial extends MetaItem {
    MetaMaterial(Document d) {
        super(d);
    }

}
final class MetaGood extends MetaItem {
    MetaGood(Document d) {
        super(d);
        this.lux = d.getInteger("lux");
    }
    int lux;
}
class MetaBuilding {
    public static final int TRIVIAL = 10;
    public static final int MATERIAL = 11;
    public static final int PRODUCE = 12;
    public static final int RETAIL = 13;
    public static final int APARTMENT = 14;
    public static final int LAB = 15;
    public static final int PUBLIC = 16;
    public static final int VIRTUAL = 17;

    public static int type(int id) {
        return id/100000;
    }
    MetaBuilding(Document d) {
        this.id = d.getInteger("_id");
        this.x = d.getInteger("x");
        this.y = d.getInteger("y");
        this.workerNum = d.containsKey("workerNum")?d.getInteger("workerNum"):0;
        this.effectRange = d.containsKey("effectRange")?d.getInteger("effectRange"):0;
    }
	public int id;
	public int x;
	public int y;
	public int workerNum;
	public int effectRange;

    public static final class Converter implements AttributeConverter<MetaBuilding, Integer> {
        @Override
        public Integer convertToDatabaseColumn(MetaBuilding attribute) {
            return attribute.id;
        }

        @Override
        public MetaBuilding convertToEntityAttribute(Integer dbData) {
            return MetaData.getBuilding(dbData);
        }
    }
    public CoordPair area(Coordinate pos) {
        return new CoordPair(pos, pos.offset(this.x-1, this.y-1));
    }
}
class MetaVirtualBuilding {
    MetaVirtualBuilding(Document d) throws Exception {
        this.id = d.getInteger("_id");
        this.constructSec = d.getInteger("constructSec");
        this.deconstructSec = d.getInteger("deconstructSec");
        this.meta = MetaData.getBuilding(d.getInteger("buildingId"));
        List<Integer> l = (List<Integer>)d.get("material");
        assert l.size() % 2 == 0;
        for(int i = 0; i < l.size(); i+=2) {
            MetaMaterial m = MetaData.getMaterial(l.get(i));
            if(m == null)
                throw new Exception("can not find Material id: "+l.get(i));
            int n = l.get(i+1);
            material.put(m, n);
        }
    }
    boolean valid(MetaMaterial m) {
        return material.containsKey(m);
    }
    int id;
    Map<MetaMaterial, Integer> material;
    int constructSec;
    int deconstructSec;
    MetaBuilding meta;

    public int totalCapacity() {
        return material.entrySet().stream().mapToInt(e -> e.getKey().size*e.getValue()).reduce(Integer::sum).getAsInt();
    }

    public int size(MetaMaterial m) {
        Integer n = material.get(m);
        return n==null?0:n;
    }
}
class MetaApartment extends MetaBuilding {
    MetaApartment(Document d) {
        super(d);
        this.npc = d.getInteger("npc");
    }
	public int npc;
}

abstract class MetaFactoryBase extends MetaBuilding {
    public int lineNum;
    public int lineMaxWorkerNum;
    public int storeCapacity;
    public int shelfCapacity;

    MetaFactoryBase(Document d) {
        super(d);
        this.lineNum = d.getInteger("lineNum");
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
class MetaRetailShop extends MetaBuilding {
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
    public int techNum;

    MetaLaboratory(Document d) {
        super(d);
       // this.techNum = d.getInteger("techNum");
    }
}
class MetaPublicFacility extends MetaBuilding {
    public int adNum;

    MetaPublicFacility(Document d) {
        super(d);
        this.adNum = d.getInteger("adNum");
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
    }
    int playerBagCapcaity;
    int bagCapacityDelta;
}
public class MetaData {
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
    private static final String groundAuctionColName = "GroundAuction";
    private static final String initialBuildingColName = "InitialBuilding";
    private static final String trivialBuildingColName = "TrivialBuilding";
    private static final String sysParaColName = "SysPara";
    private static final String materialColName = "Material";
    private static final String goodColName = "Good";
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
    private static final TreeMap<Integer, MetaPublicFacility> publicFacility = new TreeMap<>();
    private static final TreeMap<Integer, MetaMaterialFactory> materialFactory = new TreeMap<>();
    private static final TreeMap<Integer, MetaVirtualBuilding> virtualBuilding = new TreeMap<>();

    private static final HashMap<Integer, MetaMaterial> material = new HashMap<>();
    private static final HashMap<Integer, MetaGood> good = new HashMap<>();

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
    public static MetaProduceDepartment getProductingDepartment(int id) {
        return produceDepartment.get(id);
    }

    public static MetaVirtualBuilding getVirtualBuilding(int id) {
        return virtualBuilding.get(id);
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
//	public static void initNpc() {
//		mongoClient.getDatabase(dbName).getCollection(npcColName).find().forEach((Block<Document>) doc -> {
//			MetaNpc m = new MetaNpc(doc);
//			npc.put(m.id, m);
//		});
//	}
    public static void initInitialBuilding() {
        mongoClient.getDatabase(dbName).getCollection(npcColName).find().forEach((Block<Document>) doc -> {
            MetaNpc m = new MetaNpc(doc);
            npc.put(m.id, m);
        });
    }
    public static void reloadGroundAuction() {
        groundAuction.clear();
        mongoClient.getDatabase(dbName).getCollection(groundAuctionColName).find().forEach((Block<Document>) doc -> {
            MetaGroundAuction m = new MetaGroundAuction(doc);
            groundAuction.put(m.id, m);
        });
    }

    public static void initMaterial() {
        mongoClient.getDatabase(dbName).getCollection(materialColName).find().forEach((Block<Document>) doc -> {
            MetaMaterial m = new MetaMaterial(doc);
            material.put(m.id, m);
        });
    }
    public static void initGood() {
        mongoClient.getDatabase(dbName).getCollection(goodColName).find().forEach((Block<Document>) doc -> {
            MetaGood m = new MetaGood(doc);
            good.put(m.id, m);
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
	}
}
