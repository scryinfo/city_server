package Game.Util;

import Game.Meta.*;
import Game.Promote.PromotePoint;
import Game.Promote.PromotePointManager;
import Game.Technology.SciencePoint;
import Game.Technology.SciencePointManager;
import gs.Gs;

import java.util.Set;
import java.util.UUID;

/*Eva工具*/
public class EvaTypeUtil {
    public static final int SCIENCE_TYPE=15;
    public static final int PROMOTE_TYPE=16;
    //    判断点数类型(15 表示科技点数，16表示市场数据)
    public static int judgeScienceType(Gs.Eva eva){
        return eva.getBt()==Gs.Eva.Btype.Brand ? PROMOTE_TYPE:SCIENCE_TYPE;
    }

    /*获取建筑加点类型*/
    public static int getEvaPointType(int type,int at){/*参数1：加点类型（科技点数、市场数据）   bt  Eva的a类型*/
        StringBuilder sb = new StringBuilder();
        /*确定大类型*/
        if(type==SCIENCE_TYPE){
            sb.append("15000");
        }else{
            sb.append("16000");
        }
        /*确定小类型*/
        if(MetaGood.isItem(at)){//商品（代表加工厂）
            sb.append("12");
        }else if(MetaMaterial.isItem(at)){  //代表原料厂
            sb.append("11");
        }else if(at==MetaBuilding.APARTMENT){
            sb.append(MetaBuilding.APARTMENT);
        }else if(at==MetaBuilding.RETAIL){
            sb.append(MetaBuilding.RETAIL);
        }else if(MetaPromotionItem.isItem(at)){
            sb.append(MetaBuilding.PROMOTE);
        }else if(MetaScienceItem.isItem(at)){
            sb.append(MetaBuilding.TECHNOLOGY);
        }
        int pointType = Integer.parseInt(sb.toString().intern());
        return pointType;
    }

    /*获取当前所有建筑类型的点数信息*/
    public static Gs.PlayerEvaPointType classifyBuildingTypePoint(UUID pId){/*分类玩家各种建筑类型的Eva加点*/
        Set<Integer> allBuildingType = MetaData.getAllBuildingType();/*所有的建筑类型*/
        Gs.PlayerEvaPointType.Builder builder = Gs.PlayerEvaPointType.newBuilder();
        allBuildingType.forEach(bt->{
            SciencePoint sciencePoint = SciencePointManager.getInstance().getSciencePointByBuildingType(pId, bt);
            PromotePoint promotePoint = PromotePointManager.getInstance().getPromotePointByBuildingType(pId, bt);
            Gs.PlayerEvaPointType.BuildingPoint.Builder buildingPoint = Gs.PlayerEvaPointType.BuildingPoint.newBuilder();
            buildingPoint.setBuildingType(bt);
            if(sciencePoint!=null){
                Gs.PlayerEvaPointType.PointInfo.Builder pointInfo = Gs.PlayerEvaPointType.PointInfo.newBuilder();
                pointInfo.setPointId(sciencePoint.getType()).setPointNum(sciencePoint.point);
                buildingPoint.setSciencePoint(pointInfo);
            }
            if(promotePoint!=null){
                Gs.PlayerEvaPointType.PointInfo.Builder pointInfo = Gs.PlayerEvaPointType.PointInfo.newBuilder();
                pointInfo.setPointId(promotePoint.getType()).setPointNum(promotePoint.promotePoint);
                buildingPoint.setPromotionPoint(pointInfo);
            }
            builder.addBuildingPoint(buildingPoint);
        });
        return builder.build();
    }
}