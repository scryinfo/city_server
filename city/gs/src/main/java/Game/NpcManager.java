package Game;

import Game.Meta.MetaCity;
import Shared.Package;
import Shared.Util;
import gs.Gs;
import gscode.GsCode;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class NpcManager {
    private static NpcManager instance = new NpcManager();
    public static NpcManager instance() {
        return instance;
    }
    public void update(long diffNano) {
        if (updateIdx == waitToUpdate.size()) // job is done, wait next time section coming
            return;
        if(waitToUpdate.isEmpty())
            return;
        Set<UUID> ids = waitToUpdate.get(updateIdx);
        Iterator<UUID> i = ids.iterator();
        while(i.hasNext()) {
            Npc npc = allNpc.get(i.next());
            if(npc == null)     // this npc be deleted
                i.remove();
            else
                npc.update(diffNano);
        }
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
    public List<Npc> create(int type, int n, Building building, int salary) {
        List<Npc> res = new ArrayList<>();
        for(int i = 0; i < n; ++i) {
            Npc npc = new Npc(building, salary, type);
            res.add(npc);
            addImpl(npc);
        }
        return res;
    }

    public Npc create(UUID id, Building building, long salary) {
       Npc npc = new Npc(building, salary, id);
       addImpl(npc);
       return npc;
    }
    private void addImpl(Npc npc) {
        allNpc.put(npc.id(), npc);
        //市民人数突破,市民人数达到500,发送广播给前端,包括市民数量，时间  
        if(allNpc!=null&&allNpc.size()>=500){
        	GameSession gs = GameServer.allGameSessions.get(npc.id());
        	gs.write(Package.create(GsCode.OpCode.cityBroadcast_VALUE,Gs.CityBroadcast.newBuilder()
        			.setType(2)
        			.setNum(allNpc.size())
                    .setTs(System.currentTimeMillis())
                    .build()));
        }
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
    private Map<UUID, Npc> allNpc = new HashMap<>();
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
        GameDb.getAllNpc().forEach(npc->this.addImpl(npc));
    }

    public void hourTickAction(int nowHour) {
    }
}
