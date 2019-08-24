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
    public Map<EvaKey,Set<Eva>> typeEvaMap = new HashMap<>();//封装各种类型的Eva信息
    private Map<UUID, Eva> evas = new HashMap<>();  /*key是Eva的Id*/
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


    //分类eva
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
    /*缓存Eva*/
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
        evas.put(eva.getId(), eva);/*更新Eva缓存*/
		//同步Eva类型map
        updateTypeEvaMap(eva);
     	GameDb.saveOrUpdate(eva);
    }

    public void addEvaList(List<Eva> evaList){
    	evaList.forEach(e->{
    	   	evaMap.computeIfAbsent(e.getPid(),
                    k -> new HashSet<>()).add(e);
            //同步Eva类型map
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
        if(level>=1){//计算等级
            long exp=0l;
            do{
                MetaExperiences obj=map.get(level);
                exp=obj.exp;
                if(cexp>=exp){
                    cexp=cexp-exp; //减去升级需要的经验
                    level++;
                }
            }while(cexp>=exp);
        }
        Eva e=new Eva();//修改后的Eva
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

    /*根据EvaId获取Eva*/
    public Eva getEvaById(UUID id){
        return evas.get(id);
    }

    /*根据要修改eva的摘要，获取eva信息*/
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

    // 全城所有玩家科技点数+推广点数
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
        //获取与原料厂相关的Eva信息
        aTypes.forEach(itemId->{
            List<Eva> evas = EvaManager.getInstance().getEva(pid, itemId);
            Gs.BuildingEva.EvaType.Builder typeEva = Gs.BuildingEva.EvaType.newBuilder().setAtype(itemId);
            evas.forEach(e->{
                Gs.BuildingEva.EvaType.ItemEva.Builder itemEva = Gs.BuildingEva.EvaType.ItemEva.newBuilder();
                itemEva.setBtype(e.getBt()).setEva(e.toProto());
                typeEva.addItemEva(itemEva);
            });
            buildingEva.addEvaType(typeEva);
        });
        //获取当前查询建筑的点数信息
        Gs.BuildingPoint buildingTypePoint = EvaTypeUtil.getBuildingTypePoint(pid, type);
        buildingEva.setBuildingPoint(buildingTypePoint);
        return buildingEva.build();
    }

    /*为修改的Eva归类（分类到具体的某一个建筑,修改Eva使用）*/
    public Gs.BuildingEvas classifyEvaType(List<Gs.Eva> evas,UUID playerId){
        List<Gs.BuildingEva> list = new ArrayList<>();
        Map<Integer, Set<Gs.BuildingEva.EvaType.ItemEva>> evaMap = new HashMap<>();//key 为atype value 为对应的所有eva
        evas.forEach(e->{
            Gs.BuildingEva.EvaType.ItemEva.Builder itemEva = Gs.BuildingEva.EvaType.ItemEva.newBuilder().setBtype(e.getBt().getNumber()).setEva(e);
            evaMap.computeIfAbsent(e.getAt(), k -> new HashSet()).add(itemEva.build());
        });
        Map<Integer,List<Gs.BuildingEva.EvaType>> buildingEvaInfo = new HashMap<>();//key为建筑大类型，value为建筑的Eva数组
        evaMap.forEach((k,v)->{
            Gs.BuildingEva.EvaType.Builder evaType = Gs.BuildingEva.EvaType.newBuilder().setAtype(k).addAllItemEva(v);
            if(MetaGood.isItem(k)){
                buildingEvaInfo.computeIfAbsent(MetaBuilding.PRODUCE,type-> new ArrayList<>()).add(evaType.build());
            }else if(MetaMaterial.isItem(k)){
                buildingEvaInfo.computeIfAbsent(MetaBuilding.MATERIAL,type-> new ArrayList<>()).add(evaType.build());
            }else if(MetaBuilding.APARTMENT==k){
                buildingEvaInfo.computeIfAbsent(MetaBuilding.APARTMENT,type-> new ArrayList<>()).add(evaType.build());
            }else if(MetaBuilding.RETAIL==k){
                buildingEvaInfo.computeIfAbsent(MetaBuilding.RETAIL,type-> new ArrayList<>()).add(evaType.build());
            }else if(MetaPromotionItem.isItem(k)){
                buildingEvaInfo.computeIfAbsent(MetaBuilding.PROMOTE,type-> new ArrayList<>()).add(evaType.build());
            }else if(MetaScienceItem.isItem(k)){
                buildingEvaInfo.computeIfAbsent(MetaBuilding.TECHNOLOGY,type-> new ArrayList<>()).add(evaType.build());
            }
        });
        buildingEvaInfo.forEach((k,v)->{
            Gs.BuildingPoint buildingTypePoint = EvaTypeUtil.getBuildingTypePoint(playerId, k);
            Gs.BuildingEva.Builder buildingEva = Gs.BuildingEva.newBuilder().setBuildingType(k).addAllEvaType(v).setBuildingPoint(buildingTypePoint);
            list.add(buildingEva.build());
        });
        Gs.BuildingEvas.Builder builder = Gs.BuildingEvas.newBuilder().addAllBuildingEvas(list);
        return builder.build();
    }
}