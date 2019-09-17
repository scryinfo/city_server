package Game.CityInfo;

import Game.Eva.EvaManager;
import Game.GameDb;
import Game.GameServer;
import Game.Meta.MetaCityLevel;
import Game.Meta.MetaData;
import Game.Player;
import Game.Timers.PeriodicTimer;
import Shared.Package;
import gs.Gs;
import gscode.GsCode;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Transient;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Entity
public class CityLevel {
    public static final int ID = 0;
    private static CityLevel instance;
    public static CityLevel instance() {
        return instance;
    }
    public CityLevel() {
        lv=1;
        cexp=0;
        salary = MetaData.getCityLevel().get(1).baseSalary;
    }
    @Id
    public final int id = ID;
    public int lv;
    public int cexp;
    public int salary;
    public int inventCount;
    private long sumValue;
    public static void init() {
        GameDb.initCityLevel();
        instance = GameDb.getCityLevel();
    }

    public int getId() {
        return id;
    }

    public int getLv() {
        return lv;
    }

    public int getCexp() {
        return cexp;
    }

    public Gs.CityLevel toProto() {
        return Gs.CityLevel.newBuilder().setLv(lv).setExp(cexp).setSalary(salary).setInventCount(inventCount).build();}

    /*修改更新城市等级或经验值*/
    public void updateCityLevel(long addPoint){
        long cexp = addPoint + this.cexp;//当前的新增点数+城市的经验值
        Map<Integer, MetaCityLevel> cityLevel = MetaData.getCityLevel();
        if(this.lv>=1){//计算等级
            long exp=0l;
            do{
                MetaCityLevel obj=cityLevel.get(this.lv);
                exp=obj.exp;//获取所需的经验值
                if(cexp>=exp){
                    cexp=cexp-exp; //减去升级需要的经验
                    this.lv++;
                    /*升级完成，发明新的商品*/
                    MetaCityLevel cityLevelInfo = cityLevel.get(this.lv);
                    inventNewGood(cityLevelInfo);
                }
                this.salary = obj.getBaseSalary();
                this.inventCount = obj.getInventCount();
                this.cexp = (int) cexp;
            }while(cexp>=exp);
        }
        this.sumValue += addPoint;
        GameDb.saveOrUpdate(this);
    }

    /*发明新商品*/
    public void inventNewGood(MetaCityLevel cityLevelInfo){
        for (int i = 0; i <cityLevelInfo.getInventCount();i++) {
            /*获取可发明的商品列表*/
            Set<Integer> cityInventGoodByType = CityGoodInfo.getCityInventGoodByType(cityLevelInfo.inventItem);
            Integer itemId = CityGoodInfo.randomGood(cityInventGoodByType);
            //同步全城商品
            CityManager.instance().cityGood.add(itemId);
            if(itemId!=null){
                //获取发明新商品产生的新原料
                Set<Integer> newMaterialId = CityGoodInfo.getNewMaterialId(itemId);
                Gs.CityGoodInfo.Builder newGoodInfo = Gs.CityGoodInfo.newBuilder();
                newGoodInfo.setGoods(Gs.Nums.newBuilder().addNum(itemId));
                newGoodInfo.setMaterial(Gs.Nums.newBuilder().addAllNum(newMaterialId));
                //同步全城原料
                CityManager.instance().cityMaterial.addAll(newMaterialId);
                //TODO 给全城所有的玩家们发邮件通知xx新商品产生了，xxx原料产生了
                List<Player> allPlayer = GameDb.getAllPlayer();
                newMaterialId.add(itemId);
                /*分别把新商品和新原料添加给所有玩家*/
                CityGoodInfo.updatePlayerGoodList(allPlayer,newMaterialId);
                /*同时推送给所有的在线玩家新商品信息*/
                GameServer.sendToAll(Package.create(GsCode.OpCode.newGoodMessage_VALUE,newGoodInfo.build()));
            }
        }
    }
}
