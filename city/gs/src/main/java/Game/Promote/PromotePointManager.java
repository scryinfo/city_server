package Game.Promote;

import Game.Eva.EvaManager;
import Game.GameDb;
import Game.Technology.SciencePoint;
import Game.Technology.SciencePointManager;
import Game.Timers.PeriodicTimer;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class PromotePointManager {
    private PromotePointManager(){}
    private static PromotePointManager instance = new PromotePointManager();
    public static PromotePointManager getInstance()
    {
        return instance;
    }

    private Map<UUID, Set<PromotePoint>> promoteMap = new HashMap<UUID, Set<PromotePoint>>();//缓存玩家所有的推广点数
    public void init()
    {
        GameDb.getAllFromOneEntity(PromotePoint.class).forEach(
                promote ->{
                    promoteMap.computeIfAbsent(promote.getPid(),
                            k -> new HashSet<>()).add(promote);
                } );
    }

    /*获取玩家的所有推广点数信息*/
    public Set<PromotePoint> getPromotePointList(UUID playerId)
    {
        return promoteMap.get(playerId) == null ?
                new HashSet<PromotePoint>() : promoteMap.get(playerId);
    }

    /*获取指定的推广点数信息*/
    public PromotePoint getPromotePoint(UUID playerId,int type)
    {
        Set<PromotePoint> set=getPromotePointList(playerId);
        for (PromotePoint promote : set) {
            if(type==promote.type){
                return promote;
            }
        }
        return null;
    }

    /*更新推广点数*/
    public void updatePromotionPoint(PromotePoint promote) {
        Set<PromotePoint> s=promoteMap.get(promote.getPid());
        s.remove(getPromotePoint(promote.getPid(),promote.getType()));
        s.add(promote);
        promoteMap.put(promote.getPid(), s);
        GameDb.saveOrUpdate(promote);
    }
    /*批量添加推广点数（初始化时用）*/
    public void addPromotePointList(List<PromotePoint> promoteList){
        promoteList.forEach(p->{
            promoteMap.computeIfAbsent(p.getPid(),
                    k -> new HashSet<>()).add(p);
        });
        GameDb.saveOrUpdate(promoteList);
    }

    public Map<UUID, Set<PromotePoint>> getPromoteMap() {
        return promoteMap;
    }

    public PromotePoint updatePlayerPromotePoint(UUID pid, int type, int point){
        PromotePoint promotePoint = PromotePointManager.getInstance().getPromotePoint(pid, type);//获取玩家的推广类型点数
        if(point<0&&Math.abs(point)>promotePoint.promotePoint){/*如果是扣除，还需判断库存是否充足*/
            return null;
        }
        promotePoint.promotePoint += point;
        return promotePoint;
    }

    /*根据建筑大类型，查找玩家的的点数资料*/
    public PromotePoint getPromotePointByBuildingType(UUID pId,int buildingType){
        //拼接类型
        StringBuffer sb = new StringBuffer("16000");
        int pointType = Integer.parseInt(sb.append(buildingType).toString());
        PromotePoint promotePoint = getPromotePoint(pId, pointType);
        return promotePoint;
    }
}
