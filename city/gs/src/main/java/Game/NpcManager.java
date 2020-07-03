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
            allNpc.remove(n.id()); //Remove unemployed npc
            n.setStatus(1);  //Status changed to unemployed
            n.setUnEmpts(System.currentTimeMillis());
            unEmployeeNpc.put(n.id(),n); //Add unemployed npc
        });
        //Add unemployed npc
        shutdownBusiness(npc.size());
        GameDb.saveOrUpdate(npc);
    }

    public void addWorkNpc(Collection<Npc> npc,Building born) {
    	npc.forEach(n->{
    		unEmployeeNpc.remove(n.id()); //Remove unemployed npc
    		n.setStatus(0);  //Change status to work
    		n.setUnEmpts(System.currentTimeMillis());
    		n.setBorn(born);  //Change to a new building
    		allNpc.put(n.id(),n); //Add work npc
    	});
    	//Add real employment npc
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
       //The number of citizens broke through, the number of citizens reached 500, and broadcasts were sent to the front end, including the number of citizens, time
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
    private Map<UUID, Npc> allNpc = new HashMap<>();  //Work npc
    private Map<UUID, Npc> unEmployeeNpc = new HashMap<>();//Not working npc

    private Map<Integer,Integer> realNpc = new HashMap<>();  //All actual demand npc, 1 job 2 unemployment

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

        //Work npc
        GameDb.getAllNpcByStatus(0).forEach(npc->this.addImpl(npc));
        //Unemployed npc
        GameDb.getAllNpcByStatus(1).forEach(npc->{
    		unEmployeeNpc.put(npc.id(),npc);
    	});
    }

    public void hourTickAction(int nowHour) {
    }
    PeriodicTimer timer= new PeriodicTimer((int)TimeUnit.HOURS.toMillis(1),(int)TimeUnit.SECONDS.toMillis((DateUtil.getCurrentHour55()-nowTime)/1000));
    PeriodicTimer realNpcTimer= new PeriodicTimer((int)TimeUnit.DAYS.toMillis(1),(int)TimeUnit.SECONDS.toMillis((DateUtil.getTodayEnd()-nowTime)/1000));//Update data at 0:00 every day
    //PeriodicTimer realNpcTimer= new PeriodicTimer((int)TimeUnit.DAYS.toMillis(1),(int)TimeUnit.SECONDS.toMillis(5));//Update data at 0:00 every day

    public void countNpcNum(long diffNano) {
        if (this.timer.update(diffNano)) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(new Date());
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            Date startDate = calendar.getTime();
            long countTime=startDate.getTime()+1000 * 60  * 60;
            countRealNpcByType().forEach((k,v)->{
            	LogDb.npcTypeNum(countTime,k,v);//Statistics and storage
            });
        }
        if(realNpcTimer.update(diffNano)){//Initialize the actual demand data of npc every morning
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

    public Map<Integer, Integer> countRealNpcByType(){
        Map<Integer, Integer> countMap= new HashMap<Integer, Integer>();
        countMap.put(1, realNpc.getOrDefault(1,0));
        countMap.put(2, realNpc.getOrDefault(2,0));
        return countMap;
    }
    public Map<Integer, Long> countNpcByBuildingType(){
    	Map<Integer, Long> countMap= new HashMap<Integer, Long>();
    	//Calculate the number of various construction npc, including working npc and unemployed npc
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

    //Add actual demand work npc
    public void startBusiness(int npcNum){//Add actual work npc
        long nowSecond = DateUtil.getNowSecond();
        int v = (int)Math.ceil(npcNum * ((86400 - (int) nowSecond) / 86400.0));//The number of npc to increase or decrease
        //Increase working npc population
        int workNum = realNpc.getOrDefault(1,0) + v;

        //Unemployment reduced npc
        int unEmployeeNpc =realNpc.getOrDefault(2,0) - v;
        if (unEmployeeNpc<0){
            unEmployeeNpc=0;
        }
        realNpc.put(1, workNum);
        realNpc.put(2, unEmployeeNpc);
    }

    //Add actual demand unemployed
    public void shutdownBusiness(int npcNum){//Add actual work npc
        long nowSecond = DateUtil.getNowSecond();
        int v = (int)Math.ceil(npcNum * ((86400 - (int) nowSecond) / 86400.0));
        //Unemployment increased npc
        int unEmployeeNpc = realNpc.getOrDefault(2,0) + v;

       //Decrease in employment npc
        int workNum = realNpc.getOrDefault(1,0) - v;
        if (workNum<0){
            workNum=0;
        }
        realNpc.put(2, unEmployeeNpc);
        realNpc.put(1, workNum);
    }

    public int getRealNpcNumByType(int type){
        return realNpc.getOrDefault(type,0);
    }
}
