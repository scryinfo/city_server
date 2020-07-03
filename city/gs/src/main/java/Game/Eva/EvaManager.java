package Game.Eva;

import Game.CityInfo.CityManager;
import Game.GameDb;
import Game.Meta.*;
import Game.Timers.PeriodicTimer;
import Game.Util.EvaTypeUtil;
import Shared.Util;
import gs.Gs;
import org.bson.Document;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class EvaManager
{
    private EvaManager()
    {
    }
    private static EvaManager instance = new EvaManager();
    public static EvaManager getInstance()
    {
        return instance;
    }

    private Map<UUID, Set<Eva>> evaMap = new HashMap<UUID, Set<Eva>>();
    public Map<EvaKey,Set<Eva>> typeEvaMap = new HashMap<>();//Encapsulate various types of Eva information and classify them according to atype and btype
    private Map<UUID, Eva> evas = new HashMap<>();  /*key is Eva's Id*/
    private Map<UUID, EvaSalary> evaSalaryMap = new HashMap<UUID, EvaSalary>();
    private PeriodicTimer timer = new PeriodicTimer((int) TimeUnit.SECONDS.toMillis(1));

    public void init()
    {
        GameDb.getAllFromOneEntity(Eva.class).forEach(
                eva ->{
                	evaMap.computeIfAbsent(eva.getPid(),
                            k -> new HashSet<>()).add(eva);
                } );
        GameDb.getAllFromOneEntity(EvaSalary.class).forEach(
                es ->{
                    evaSalaryMap.put(es.getId(),es);
                } );
        initTypeEvaMap();
        initEvaSalaryMap();
        initEvas();
    }


    //Classification eva
    public void initTypeEvaMap(){
        EvaKey key=null;
        Set<Eva> evas = getAllEvas();
        for (Eva eva : evas) {
            key = new EvaKey(eva.getAt(),eva.getBt());
            typeEvaMap.computeIfAbsent(key, k -> new HashSet<>()).add(eva);
        }
    }
    public void initEvaSalaryMap(){
        if(evaSalaryMap==null||evaSalaryMap.size()==0){
            EvaSalary es=new EvaSalary(0);
            evaSalaryMap.put(es.getId(),es);
            GameDb.saveOrUpdate(es);
        }
    }
    /*Cache Eva*/
    public void initEvas(){
        Set<Eva> allEvas = getAllEvas();
        allEvas.forEach(eva->{
            evas.put(eva.getId(), eva);
        });
    }
    public void updateTypeEvaMap(Eva eva){
        UUID id = eva.getId();
        EvaKey key=new EvaKey(eva.getAt(),eva.getBt());
        Set<Eva> evas = typeEvaMap.get(key);
        Iterator<Eva> iterator = evas.iterator();
        while (iterator.hasNext()){
            Eva next = iterator.next();
            if(next.getId().equals(id)){
                iterator.remove();
            }
        }
        evas.add(eva);
    }

    public Set<Eva> getEvaList(UUID playerId)
    {
         return evaMap.get(playerId) == null ?
                 new HashSet<Eva>() : evaMap.get(playerId);
    }
    
    public Eva getEva(UUID playerId,int at,int bt)
    {
    	Set<Eva> set=getEvaList(playerId);
    	for (Eva eva : set) {
			if(at==eva.getAt()&&bt==eva.getBt()){
				return eva;
			}
		}
		return null;
    }

    public Map<Integer, Long> getScience(UUID playerId, int buildingTech) {
        final long[] sum = {0,0};
        Map<Integer, Long> map = new HashMap<>();
        Set<Integer> tech = MetaData.getBuildingTech(buildingTech);
        tech.stream().forEach(i -> {
            List<Eva> eva = getEva(playerId, i);
            sum[0] += eva.stream().filter(o -> o.getBt() == 2).mapToLong(Eva::getSumValue).sum();
            sum[1] += eva.stream().filter(o -> o.getBt() != 2).mapToLong(Eva::getSumValue).sum();
        });
        map.put(1, sum[0]);
        map.put(2, sum[1]);
        return map;
    }

    
    public void updateEva(Eva eva) {
    	Set<Eva> s=evaMap.get(eva.getPid());
    	s.remove(getEva(eva.getPid(),eva.getAt(),eva.getBt()));
    	s.add(eva);
		evaMap.put(eva.getPid(), s);
        evas.put(eva.getId(), eva);/*Update Eva cache*/
		//Synchronous Eva type map
        updateTypeEvaMap(eva);
     	GameDb.saveOrUpdate(eva);
    }

    public void addEvaList(List<Eva> evaList){
    	evaList.forEach(e->{
    	   	evaMap.computeIfAbsent(e.getPid(),
                    k -> new HashSet<>()).add(e);
            //Synchronous Eva type map
    	   	typeEvaMap.computeIfAbsent(new EvaKey(e.getAt(),e.getBt()),
                    k->new HashSet<>()).add(e);
            evas.put(e.getId(),e);
    	});
		GameDb.saveOrUpdate(evaList);
    }
    public double computePercent(Eva eva){
        return ((eva!=null&&eva.getLv()>0)?MetaData.getAllExperiences().get(eva.getLv()).p/ 100000d:0);
        //return ((eva!=null&&eva.getLv()>0)?(eva.getLv()-1)/100d:0);
    }


    public List<Eva> getEva(UUID playerId,int at)
    {
        Set<Eva> set=getEvaList(playerId);
        List<Eva> list = new ArrayList<>();
        for (Eva eva : set) {
            if(at==eva.getAt()){
                list.add(eva);
            }
        }
        return list;
    }

    public Set<Eva> getAllEvas(){
        ArrayList<Set<Eva>> list = new ArrayList<>(this.evaMap.values());
        Set<Eva> evas = new HashSet<>();
        for (Set<Eva> e : list) {
            evas.addAll(e);
        }
        return evas;
    }
    public List<Eva> getPlayerAllEvas(UUID pid){
        List<Eva> list = new ArrayList<>();
        getAllEvas().stream().filter(e -> e.getPid().equals(pid)).forEach(list::add);
        return list;
    }

    public  Eva updateMyEva(Gs.Eva eva){
        int level=eva.getLv();
        long cexp=eva.getCexp()+eva.getDecEva();
        Map<Integer,MetaExperiences> map=MetaData.getAllExperiences();
        if(level>=1){//Calculation level
            long exp=0l;
            do{
                MetaExperiences obj=map.get(level);
                exp=obj.exp;
                if(cexp>=exp){
                    cexp=cexp-exp; //Minus the experience needed to upgrade
                    level++;
                }
            }while(cexp>=exp);
        }
        Eva e=new Eva();//Modified Eva
        e.setId(Util.toUuid(eva.getId().toByteArray()));
        e.setPid(Util.toUuid(eva.getPid().toByteArray()));
        e.setAt(eva.getAt());
        e.setBt(eva.getBt().getNumber());
        e.setLv(level);
        e.setCexp(cexp);
        e.setB(-1);
        e.setSumValue(eva.getSumValue()+eva.getDecEva());
        return e;
    }

    public void updateEvaSalary(int point){
        evaSalaryMap.forEach((k,v)->{
            v.addPoit(point);
            evaSalaryMap.put(k,v);
            GameDb.saveOrUpdate(v);
        });
    }

    public Map<UUID, EvaSalary> getEvaSalary(){
        return evaSalaryMap;
    }

    public int getSalaryStandard(){
        List<org.bson.Document> documentList=MetaData.initSalaryStandard();
        Map<UUID, EvaSalary> map=EvaManager.getInstance().getEvaSalary();
        for (Map.Entry<UUID, EvaSalary> entry : map.entrySet()) {
            int point=entry.getValue().getPoint();
            for(Document d:documentList){
                int min=d.getInteger("minPoint");
                int max=d.getInteger("maxPoint");
                int s=d.getInteger("salary");
                if((min<=point)&&(point<=max)){
                    return s;
                }
            }
        }
        return 0;
    }

    /*Get Eva according to EvaId*/
    public Eva getEvaById(UUID id){
        return evas.get(id);
    }

    /*According to the summary of eva to be modified, get eva information*/
    public Gs.Evas getAllUpdateEvas(Gs.UpdateMyEvas updateMyEvas){
        List<Gs.Eva> evaList = new ArrayList<>();
        List<Gs.UpdateMyEvas.EvaSummary> list = updateMyEvas.getEvaSummarysList();
        for (Gs.UpdateMyEvas.EvaSummary summary : list) {
            UUID evaId = Util.toUuid(summary.getId().toByteArray());
            Eva eva = evas.get(evaId);
            if(eva==null)
                return null;
            Gs.Eva.Builder builder = eva.toProto().toBuilder().setDecEva(summary.getPointNum());
            evaList.add(builder.build());
        }
        Gs.Evas.Builder builder = Gs.Evas.newBuilder().addAllEva(evaList);
        return builder.build();
    }

    // Technology points + promotion points for all players in the city
    public long getAllSumValue() {
        return getAllEvas().stream().filter(o -> o != null).mapToLong(Eva::getSumValue).sum();
    }
    public Map<Integer, Long> getPlayerSumValue(UUID playerId) {
        final long[] sum = {0,0};
        Map<Integer, Long> map = new HashMap<>();
        List<Eva> playerAllEvas = getPlayerAllEvas(playerId);
        sum[0] = playerAllEvas.stream().filter(e -> e.getBt() == 2).mapToLong(Eva::getSumValue).sum();
        sum[1] = playerAllEvas.stream().filter(e -> e.getBt() != 2).mapToLong(Eva::getSumValue).sum();
        map.put(1, sum[0]);
        map.put(2, sum[1]);
        return map;
    }
    public Map<Integer, Long> getItemPoint(UUID playerId,int itemId) {
        final long[] sum = {0,0};
        Map<Integer, Long> map = new HashMap<>();
        List<Eva> playerAllEvas = getEva(playerId, itemId);
        sum[0] = playerAllEvas.stream().filter(e -> e.getBt() == 2).mapToLong(Eva::getSumValue).sum();
        sum[1] = playerAllEvas.stream().filter(e -> e.getBt() != 2).mapToLong(Eva::getSumValue).sum();
        map.put(1, sum[0]);
        map.put(2, sum[1]);
        return map;
    }

    public Map<Integer, Long> getGrade(int at, int bt) {
        EvaKey evaKey = new EvaKey(at, bt);
        Set<Eva> evas = typeEvaMap.get(evaKey);
        return evas.stream().filter(e -> e.getBt() == bt).collect(Collectors.groupingBy(Eva::getLv, Collectors.counting()));
    }

    public Gs.BuildingEva queryTypeBuildingEvaInfo(UUID pid,int type){
        Gs.BuildingEva.Builder buildingEva = Gs.BuildingEva.newBuilder().setBuildingType(type);
        List<Integer> aTypes = new ArrayList<>();
        if(type==MetaBuilding.MATERIAL){
            aTypes.addAll(CityManager.instance().cityMaterial);
        }else if(type==MetaBuilding.PRODUCE){
            aTypes.addAll(CityManager.instance().cityGood);
        }else{
            aTypes.addAll(MetaData.getBuildingTech(type));
        }
        //Get Eva information related to the raw material plant
        aTypes.forEach(itemId->{
           EvaManager.getInstance().getEva(pid, itemId).forEach(eva->{
               buildingEva.addEva(eva.toSimpleEvaProto());
           });
        });
        //Get the point information of the current query building
        Gs.BuildingPoint buildingTypePoint = EvaTypeUtil.getBuildingTypePoint(pid, type);
        buildingEva.setBuildingPoint(buildingTypePoint);
        return buildingEva.build();
    }

    /*Classify the modified Eva (classify to a specific building, modify Eva to use)*/
    public Gs.BuildingEvas classifyEvaType(List<Gs.Eva> evas,UUID playerId){
        Map<Integer,List<Gs.Eva>> buildingEvaInfo = new HashMap<>();//key is the major type of building, and value is all eva corresponding to the building
        evas.forEach(e->{
            int atype = e.getAt();
            if(MetaGood.isItem(atype)){
                buildingEvaInfo.computeIfAbsent(MetaBuilding.PRODUCE,type-> new ArrayList<>()).add(e);
            }else if(MetaMaterial.isItem(atype)){
                buildingEvaInfo.computeIfAbsent(MetaBuilding.MATERIAL,type-> new ArrayList<>()).add(e);
            }else if(MetaBuilding.APARTMENT==atype){
                buildingEvaInfo.computeIfAbsent(MetaBuilding.APARTMENT,type-> new ArrayList<>()).add(e);
            }else if(MetaBuilding.RETAIL==atype){
                buildingEvaInfo.computeIfAbsent(MetaBuilding.RETAIL,type-> new ArrayList<>()).add(e);
            }else if(MetaPromotionItem.isItem(atype)){
                buildingEvaInfo.computeIfAbsent(MetaBuilding.PROMOTE,type-> new ArrayList<>()).add(e);
            }else if(MetaScienceItem.isItem(atype)){
                buildingEvaInfo.computeIfAbsent(MetaBuilding.TECHNOLOGY,type-> new ArrayList<>()).add(e);
            }
        });
        Gs.BuildingEvas.Builder builder = Gs.BuildingEvas.newBuilder();
        //key is a large type of building, and value is the Eva array of the building
        buildingEvaInfo.forEach((k,v)->{
            Gs.BuildingPoint buildingTypePoint = EvaTypeUtil.getBuildingTypePoint(playerId, k);
            Gs.BuildingEva.Builder buildingEva = Gs.BuildingEva.newBuilder().addAllEva(v).setBuildingType(k).setBuildingPoint(buildingTypePoint);
            builder.addBuildingEvas(buildingEva);
        });
        return builder.build();
    }
}