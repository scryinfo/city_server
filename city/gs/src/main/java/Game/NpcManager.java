package Game;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import Game.Meta.MetaCity;
import Game.Timers.PeriodicTimer;
import Game.Util.DateUtil;
import Shared.LogDb;
import Shared.Package;
import gs.Gs;
import gscode.GsCode;

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
    		n.setTs(System.currentTimeMillis());
    		unEmployeeNpc.put(n.id(),n); //添加失业npc
    	});
    	GameDb.saveOrUpdate(npc);
    }
    public void addWorkNpc(Collection<Npc> npc,Building born) {
    	npc.forEach(n->{
    		unEmployeeNpc.remove(n.id()); //移除失业npc
    		n.setStatus(0);  //状态改成工作
    		n.setTs(System.currentTimeMillis());
    		n.setBorn(born);  //改为新建筑
    		allNpc.put(n.id(),n); //添加工作npc
    	});
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
    private Map<UUID, Npc> unEmployeeNpc = new HashMap<>();
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
    }
    public Map<Integer, Integer> countNpcByType(){
  	  Map<Integer, Integer> countMap= new HashMap<Integer, Integer>();
  	  //计算各类npc的数量
	  allNpc.forEach((k,v)->{
		  int type=v.type();
		  if(!countMap.containsKey(type)){ 
			  countMap.put(type, 1);
		  }else{ 
			  countMap.put(type,countMap.get(type)+1); 
		  }
	  });
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
}
