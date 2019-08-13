package Game.Eva;

import Game.GameDb;
import Game.Meta.MetaData;
import Game.Meta.MetaExperiences;
import Game.Timers.PeriodicTimer;
import Shared.Util;
import gs.Gs;
import org.bson.Document;

import java.util.*;
import java.util.concurrent.TimeUnit;

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
    private Map<UUID, Eva> evas = new HashMap<>();  /*key是Eva的Id  */
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
}