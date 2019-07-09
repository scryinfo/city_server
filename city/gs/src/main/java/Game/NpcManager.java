package Game;

import Game.Meta.MetaCity;
import Game.Timers.PeriodicTimer;
import Game.Util.DateUtil;
import Shared.LogDb;
import Shared.Package;
import gs.Gs;
import gscode.GsCode;

import java.sql.Time;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class NpcManager {
    static long nowTime=0;
    static{
        nowTime = System.currentTimeMillis();
    }
    private static NpcManager instance = new NpcManager();
    public static NpcManager instance() {
        return instance;
    }
    public void update(long diffNano) {
        if (updateIdx == waitToUpdate.size()) // job is done, wait next time section coming
            return;
        if(waitToUpdate.isEmpty())
            return;
        Set updates = new HashSet();
        Map<GridIndexPair, Gs.MoneyChange> packs = new HashMap<>();
        Set<UUID> ids = waitToUpdate.get(updateIdx);
        Iterator<UUID> i = ids.iterator();
        while(i.hasNext()) {
            Npc npc = allNpc.get(i.next());
            if(npc == null)     // this npc be deleted
                i.remove();
            else {
                Set u = npc.update(diffNano);
                if(u != null)
                    updates.addAll(u);
            }
        }

        packs.forEach((k,v)->{
            City.instance().send(k, Package.create(GsCode.OpCode.moneyChange_VALUE, v));
        });
        GameDb.saveOrUpdate(updates);
        if(reCalcuWaitToUpdate) {
            ids.forEach(id -> {
                int idx = Math.abs(id.hashCode())%updateTimesAtNextTimeSection;
                waitToUpdateNext.get(idx).add(id);
            });
            ids.clear();
        }
        ++updateIdx;
    }
    void timeSectionTick(int newIdx, int nowHour, int hours) {
         while(updateIdx < waitToUpdate.size()) {
             this.update(City.UpdateIntervalNano);
         }
         updateIdx = 0;

         int updateTimesAtLastTimeSection = updateTimesAtCurrentTimeSection;
         updateTimesAtCurrentTimeSection = (int) (TimeUnit.HOURS.toNanos(hours) / City.UpdateIntervalNano);
         if(reCalcuWaitToUpdate) {
             List<Set<UUID>> tmp = waitToUpdateNext;
             waitToUpdateNext = waitToUpdate;
             waitToUpdate = tmp;
         }
         int hoursNext = metaCity.nextTimeSectionDuration(newIdx);
         if(hoursNext == hours)
             reCalcuWaitToUpdate = false;
         else
         {
             reCalcuWaitToUpdate = true;
             updateTimesAtNextTimeSection = (int) (TimeUnit.HOURS.toNanos(hoursNext) / City.UpdateIntervalNano);
             if(updateTimesAtNextTimeSection < updateTimesAtLastTimeSection)
                 waitToUpdateNext = waitToUpdateNext.subList(0, updateTimesAtNextTimeSection);
             else if(updateTimesAtNextTimeSection > updateTimesAtLastTimeSection) {
                 int n = updateTimesAtNextTimeSection - updateTimesAtLastTimeSection;
                 while(n > 0) {
                     waitToUpdateNext.add(new HashSet<>());
                     --n;
                 }
             }
         }
    }
    public void delete(Collection<Npc> npc) {
        npc.forEach(n->{
            n.readyForDestroy();
            allNpc.remove(n.id());
        });
        GameDb.delete(npc);
        //waitToUpdate.forEach(s->s.removeAll(ids)); // can save this by check npc is null in saveOrUpdate
    }
    public void addUnEmployeeNpc(Collection<Npc> npc) {
        npc.forEach(n->{
            allNpc.remove(n.id()); //移除失业npc
            n.setStatus(1);  //状态改成失业
            n.setUnEmpts(System.currentTimeMillis());
            unEmployeeNpc.put(n.id(),n); //添加失业npc
        });
        //添加真实失业npc
        shutdownBusiness(npc.size());
        GameDb.saveOrUpdate(npc);
    }

    public void addWorkNpc(Collection<Npc> npc,Building born) {
    	npc.forEach(n->{
    		unEmployeeNpc.remove(n.id()); //移除失业npc
    		n.setStatus(0);  //状态改成工作
    		n.setUnEmpts(System.currentTimeMillis());
    		n.setBorn(born);  //改为新建筑
    		allNpc.put(n.id(),n); //添加工作npc
    	});
    	//添加真实就业npc
        startBusiness(npc.size());
    	GameDb.saveOrUpdate(npc);
    }
    public List<Npc> create(int type, int n, Building building, int salary) {
        List<Npc> npcs = new ArrayList<>();
        for(int i = 0; i < n; ++i) {
            Npc npc = new Npc(building, salary, type);
            npcs.add(npc);
        }
        GameDb.saveOrUpdate(npcs); // generate the id
        for (Npc npc : npcs) {
            addImpl(npc);
        }
        return npcs;
    }

    public Npc create(UUID id, Building building, long salary) {
       Npc npc = new Npc(building, salary, id);
       addImpl(npc);
       //市民人数突破,市民人数达到500,发送广播给前端,包括市民数量，时间  
       if(allNpc!=null&&allNpc.size()>=500){
       	GameServer.sendToAll(Package.create(GsCode.OpCode.cityBroadcast_VALUE,Gs.CityBroadcast.newBuilder()
       			.setType(2)
       			.setNum(allNpc.size())
                   .setTs(System.currentTimeMillis())
                   .build()));
           LogDb.cityBroadcast(null,null,0l,allNpc.size(),2);
       }
       return npc;
    }
    private void addImpl(Npc npc) {
        allNpc.put(npc.id(), npc);
        int idx = Math.abs(npc.id().hashCode())% updateTimesAtCurrentTimeSection;
        if(reCalcuWaitToUpdate) {
            int nextIdx = Math.abs(npc.id().hashCode())%updateTimesAtNextTimeSection;
            waitToUpdateNext.get(nextIdx).add(npc.id());
        }
        if(idx > updateIdx)
            waitToUpdate.get(idx).add(npc.id());
        else // don't have chance to act in this round
            ;
    }
    private Map<UUID, Npc> allNpc = new HashMap<>();  //工作npc
    private Map<UUID, Npc> unEmployeeNpc = new HashMap<>();//未工作npc

    private Map<Integer,Integer> realNpc = new HashMap<>();  //所有的实际需求npc，1 工作 2失业

    private List<Set<UUID>> waitToUpdate = new ArrayList<>();
    private List<Set<UUID>> waitToUpdateNext = new ArrayList<>();
    private int updateTimesAtCurrentTimeSection;
    private int updateTimesAtNextTimeSection;
    private int updateIdx;
    private boolean reCalcuWaitToUpdate;
    private City city = City.instance();
    private MetaCity metaCity = city.getMeta();
    private NpcManager() {
        int currentSectionHours = city.currentTimeSectionDuration();
        updateTimesAtCurrentTimeSection = (int) (TimeUnit.HOURS.toNanos(currentSectionHours) / City.UpdateIntervalNano);
        updateIdx = (int) ((double)City.instance().leftMsToNextTimeSection() / TimeUnit.HOURS.toMillis(currentSectionHours) * updateTimesAtCurrentTimeSection);
        for (int i = 0; i < updateTimesAtCurrentTimeSection; i++) {
            waitToUpdate.add(new HashSet<>());
        }
        int nextSectionHours = city.nextTimeSectionDuration();
        this.reCalcuWaitToUpdate = (nextSectionHours != currentSectionHours);
        this.updateTimesAtNextTimeSection = (int) (TimeUnit.HOURS.toNanos(nextSectionHours) / City.UpdateIntervalNano);
        for (int i = 0; i < updateTimesAtNextTimeSection; i++) {
            waitToUpdateNext.add(new HashSet<>());
        }
        //waitToUpdate = new ArrayList<>(Collections.nCopies(updateTimesAtCurrentTimeSection, new HashSet<>()));  won't works, n copies are refer to same object
        //final int numInOneUpdate = (int) Math.ceil((double)allNpc.size() / updateTimesAtCurrentTimeSection);
      //GameDb.getAllNpc().forEach(npc->this.addImpl(npc));

        //工作npc
        GameDb.getAllNpcByStatus(0).forEach(npc->this.addImpl(npc));
        //失业npc
        GameDb.getAllNpcByStatus(1).forEach(npc->{
    		unEmployeeNpc.put(npc.id(),npc);
    	});
    }

    public void hourTickAction(int nowHour) {
    }
    PeriodicTimer timer= new PeriodicTimer((int)TimeUnit.HOURS.toMillis(1),(int)TimeUnit.SECONDS.toMillis((DateUtil.getCurrentHour55()-nowTime)/1000));
    //PeriodicTimer realNpcTimer= new PeriodicTimer((int)TimeUnit.DAYS.toMillis(1),(int)TimeUnit.SECONDS.toMillis((DateUtil.getTodayEnd()-nowTime)/1000));//每天0点开始更新数据
    PeriodicTimer realNpcTimer= new PeriodicTimer((int)TimeUnit.DAYS.toMillis(1),(int)TimeUnit.SECONDS.toMillis(5));//每天0点开始更新数据

    public void countNpcNum(long diffNano) {
        if (this.timer.update(diffNano)) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(new Date());
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            Date startDate = calendar.getTime();
            long countTime=startDate.getTime()+1000 * 60  * 60;
            countNpcByType().forEach((k,v)->{
            	LogDb.npcTypeNum(countTime,k,v);//统计并入库
            });
        }
        if(realNpcTimer.update(diffNano)){//每天凌晨初始化npc实际需求数据
            realNpc.put(1, allNpc.size());
            realNpc.put(2, unEmployeeNpc.size());
        }
    }
    public Map<Integer, Integer> countNpcByType(){
  	  Map<Integer, Integer> countMap= new HashMap<Integer, Integer>();
  	  countMap.put(1, allNpc.size());
  	  countMap.put(2, unEmployeeNpc.size());
	  return countMap;
   }
    public Map<Integer, Long> countNpcByBuildingType(){
    	Map<Integer, Long> countMap= new HashMap<Integer, Long>();
    	//计算各种建筑npc的数量，包括工作npc和失业npc
    	allNpc.forEach((k,v)->{
    		int type=v.building().type();
    		if(!countMap.containsKey(type)){
    			countMap.put(type, 1l);
    		}else{
    			countMap.put(type,countMap.get(type)+1);
    		}
    	});
    	unEmployeeNpc.forEach((k,v)->{
    		int type=v.building().type();
    		if(!countMap.containsKey(type)){
    			countMap.put(type, 1l);
    		}else{
    			countMap.put(type,countMap.get(type)+1);
    		}
    	});
    	return countMap;
    }

    public long getNpcCount()
    {
        return allNpc.size();
    }

    public long getUnEmployeeNpcCount(){
    	return unEmployeeNpc.size();
    }

    public Map<UUID, Npc> getUnEmployeeNpc(){
    	return unEmployeeNpc;
    }

    public Map<Integer, List<Npc>> getUnEmployeeNpcByType(){
    	Map<Integer, List<Npc>> map=new HashMap<Integer, List<Npc>>();
    	getUnEmployeeNpc().forEach((k,v)->{
    		map.computeIfAbsent(v.type(),n -> new ArrayList<Npc>()).add(v);
    	});
    	return map;
    }

    public Map<UUID, Npc> getAllNpc() {
        return allNpc;
    }

    //添加实际需求工作npc
    public void startBusiness(int npcNum){//添加实际工作npc
        long nowSecond = DateUtil.getNowSecond();
        //增加工作npc人口
        int workNum = (int) (realNpc.getOrDefault(1,0) + npcNum * ((86400 - (int)nowSecond) / 86400.0));

        //失业npc减少
        int unEmployeeNpc = (int) (realNpc.getOrDefault(2,0) - npcNum * ((86400 - (int)nowSecond) / 86400.0));
        if (unEmployeeNpc<0){
            unEmployeeNpc=0;
        }
        realNpc.put(1, workNum);
        realNpc.put(2, unEmployeeNpc);

    }

    //添加实际需求失业人口
    public void shutdownBusiness(int npcNum){//添加实际工作npc
        long nowSecond = DateUtil.getNowSecond();
        //失业npc增加
        int unEmployeeNpc = (int) (realNpc.getOrDefault(2,0) + npcNum * ((86400 - (int)nowSecond) / 86400.0));

       //就业npc减少
        int workNum = (int) (realNpc.getOrDefault(1,0) - npcNum * ((86400 - (int)nowSecond) / 86400.0));
        if (workNum<0){
            workNum=0;
        }
        realNpc.put(2, unEmployeeNpc);
        realNpc.put(1, workNum);
    }

    public int getRealNpcNumByType(int type){
        return realNpc.get(type);
    }
}
