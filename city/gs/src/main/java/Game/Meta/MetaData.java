package Game.Meta;

import Game.Building;
import Game.Prob;
import com.google.common.collect.ImmutableSet;
import com.mongodb.Block;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.MongoException;
import org.apache.log4j.Logger;
import org.bson.Document;

import java.time.LocalDateTime;
import java.util.*;

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
    private static final String evaColName = "Eva";
    private static final String expColName = "Experiences";
    private static final String buildingTechName = "BuildingTech";
    private static final String warehouseColName = "WareHouse";//集散中心

    //global field
    private static SysPara sysPara;
	private static MetaCity city;
    private static final HashMap<Integer, MetaGroundAuction> groundAuction = new HashMap<>();
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
    private static final HashMap<Integer, Set<Integer>> buildingTech = new HashMap<>();
    private static final TreeMap<Integer, MetaWarehouse> warehouse = new TreeMap<>();
    private static final Map<Integer,Integer> salaryMap=new HashMap<Integer,Integer>();

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
    public static int getSalaryByBuildingType(int type){
    	return salaryMap.get(type);
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

    public static Integer randomGood(int goodCategory, Set<Integer> itemIds) {
        Set<Integer> t = new HashSet<>();
        t.addAll(good.keySet());
        t.removeAll(itemIds);

        List<Integer> candicate = new ArrayList<>();
        Iterator<Integer> iterator = t.iterator();
        while(iterator.hasNext()) {
            int id = iterator.next();
            if(MetaGood.category(id) == goodCategory)
                candicate.add(id);
        }
        if(candicate.isEmpty())
            return null;
        return candicate.get(Prob.random(0, candicate.size()));
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
            case MetaBuilding.WAREHOUSE:
                return warehouse.get(id);//集散中心（替换以前的人才中心）
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
    private static final List<MetaEva> eva = new ArrayList<MetaEva>();
    private static final Map<Integer,MetaExperiences> experiences = new HashMap<Integer,MetaExperiences>();

    public static final Formula getFormula(Formula.Key key) {
        return formula.get(key);
    }
    public static final GoodFormula getFormula(int goodId) {
        return goodFormula.get(goodId);
    }
    public static final Set<Integer> getBuildingTech(int id) {
    	return buildingTech.get(id);
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
    public static List<MetaEva> getAllEva() {
    	return eva;
    }
    public static Map<Integer,MetaExperiences> getAllExperiences() {
    	return experiences;
    }

    public static HashMap<Integer, MetaGroundAuction> getGroundAuction() {
        return groundAuction;
    }

    public static MetaGroundAuction getGroundAuction(int id) {
        return groundAuction.get(id);
    }
    public static MetaWarehouse getWarehouse(int id){//从缓存中获取集散中心
        return warehouse.get(id);
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

    public static void initBuildingTech()
    {
        mongoClient.getDatabase(dbName).getCollection(buildingTechName).find().forEach((Block<? super Document>) document -> {
            buildingTech.put(document.getInteger("_id"),
                    ImmutableSet.copyOf(((List<Integer>) document.get("ats"))));
        });

    }

    public static Set<Integer> getTechsByBuilding(Building building)
    {
        return buildingTech.get(building.type());
    }

    public static Set<Integer> getBuildingTypeByTech(int techId)
    {
        Set<Integer> bid = new HashSet<>();
        buildingTech.forEach((k,v)->{
            if (v.contains(techId)) {
                bid.add(k);
            }
        });
        return bid;
    }

    public static void initBuilding() {
        mongoClient.getDatabase(dbName).getCollection(trivialBuildingColName).find().forEach((Block<Document>) doc -> {
            MetaBuilding m = new MetaBuilding(doc);
            trivial.put(m.id, m);
            salaryMap.put(MetaBuilding.TRIVIAL, m.salary);
        });

        mongoClient.getDatabase(dbName).getCollection(apartmentColName).find().forEach((Block<Document>) doc -> {
            MetaApartment m = new MetaApartment(doc);
            apartment.put(m.id, m);
            salaryMap.put(MetaBuilding.APARTMENT, m.salary);
        });
        mongoClient.getDatabase(dbName).getCollection(materialFactoryColName).find().forEach((Block<Document>) doc -> {
            MetaMaterialFactory m = new MetaMaterialFactory(doc);
            materialFactory.put(m.id, m);
            salaryMap.put(MetaBuilding.MATERIAL, m.salary);
        });
        mongoClient.getDatabase(dbName).getCollection(produceDepartmentColName).find().forEach((Block<Document>) doc -> {
            MetaProduceDepartment m = new MetaProduceDepartment(doc);
            produceDepartment.put(m.id, m);
            salaryMap.put(MetaBuilding.PRODUCE, m.salary);
        });
        mongoClient.getDatabase(dbName).getCollection(retailShopColName).find().forEach((Block<Document>) doc -> {
            MetaRetailShop m = new MetaRetailShop(doc);
            retailShop.put(m.id, m);
            salaryMap.put(MetaBuilding.RETAIL, m.salary);
        });
        mongoClient.getDatabase(dbName).getCollection(publicFacilityColName).find().forEach((Block<Document>) doc -> {
            MetaPublicFacility m = new MetaPublicFacility(doc);
            publicFacility.put(m.id, m);
            salaryMap.put(MetaBuilding.PUBLIC, m.salary);
        });
        mongoClient.getDatabase(dbName).getCollection(laboratoryColName).find().forEach((Block<Document>) doc -> {
            MetaLaboratory m = new MetaLaboratory(doc);
            laboratory.put(m.id, m);
            salaryMap.put(MetaBuilding.LAB, m.salary);
        });
        mongoClient.getDatabase(dbName).getCollection(initialBuildingColName).find().forEach((Block<Document>) doc -> {
            InitialBuildingInfo i = new InitialBuildingInfo(doc);
            initialBuilding.add(i);
        });
        mongoClient.getDatabase(dbName).getCollection(talentCenterColName).find().forEach((Block<Document>) doc -> {
            MetaTalentCenter m = new MetaTalentCenter(doc);
            talentCenter.put(m.id, m);
        });
        //集散中心的初始化建筑
        mongoClient.getDatabase(dbName).getCollection(warehouseColName).find().forEach((Block<Document>) doc -> {
            MetaWarehouse m = new MetaWarehouse(doc);
            warehouse.put(m.id, m);
            salaryMap.put(MetaBuilding.WAREHOUSE, m.salary);
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
    public static void initEva() {
        mongoClient.getDatabase(dbName).getCollection(evaColName).find().forEach((Block<Document>) doc -> {
            MetaEva m = new MetaEva(doc);
            eva.add(m);
        });
    }
    public static void initExperiences() {
    	mongoClient.getDatabase(dbName).getCollection(expColName).find().forEach((Block<Document>) doc -> {
    		MetaExperiences m = new MetaExperiences(doc);
    		experiences.put(m.lv,m);
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

        initEva();
        initExperiences();

        initBuildingTech();
	}
}
