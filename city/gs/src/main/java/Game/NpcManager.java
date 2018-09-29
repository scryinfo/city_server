package Game;

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
                int idx = id.hashCode()%updateTimesAtNextTimeSection;
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
         MetaCity metaCity = City.instance().getMeta();
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
                     waitToUpdateNext.add(new TreeSet<>());
                     --n;
                 }
             }
         }
    }
    public void delete(Set<Npc> npc) {
        npc.forEach(n->{
            n.readyForDestory();
            allNpc.remove(n.id());
        });
        GameDb.delete(npc);
        //waitToUpdate.forEach(s->s.removeAll(ids)); // can save this by check npc is null in saveOrUpdate
    }
    public List<Npc> create(int n, Building building, int salary) {
        List<Npc> res = new ArrayList<>();
        for(int i = 0; i < n; ++i) {
            Npc npc = new Npc(building, salary);
            res.add(npc);
            addImpl(npc);
        }
        GameDb.saveOrUpdate(res);
        return res;
    }
    private void addImpl(Npc npc) {
        allNpc.put(npc.id(), npc);
       int idx = npc.id().hashCode()% updateTimesAtCurrentTimeSection;
        waitToUpdate.get(idx).add(npc.id());
    }
    private Map<UUID, Npc> allNpc = new HashMap<>();
    private List<Set<UUID>> waitToUpdate = new ArrayList<>();
    private List<Set<UUID>> waitToUpdateNext = new ArrayList<>();
    private int updateTimesAtCurrentTimeSection;
    private int updateTimesAtNextTimeSection;
    private int updateIdx;
    private boolean reCalcuWaitToUpdate;
    private NpcManager() {
        GameDb.getAllNpc().forEach(npc->this.addImpl(npc));
        // set the updateIdx according to the left time to next time section
        long leftMs = City.instance().leftMsToNextTimeSection();
        updateIdx = (int) ((double)leftMs / TimeUnit.HOURS.toMillis(City.instance().nextTimeSectionDuration()) * updateTimesAtCurrentTimeSection);
        //final int numInOneUpdate = (int) Math.ceil((double)allNpc.size() / updateTimesAtCurrentTimeSection);
    }

    public void hourTickAction(int nowHour) {
    }
}
