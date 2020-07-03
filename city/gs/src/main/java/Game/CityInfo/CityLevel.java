package Game.CityInfo;

import Game.BrandManager;
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

    @Transient
    private PeriodicTimer timer = new PeriodicTimer((int) TimeUnit.HOURS.toMillis(2));
    public void update(long diffNano) {
        if (timer.update(diffNano)) {
            sumValue = EvaManager.getInstance().getAllSumValue();
        }
    }
    public Gs.CityLevel toProto() {
        return Gs.CityLevel.newBuilder().setLv(lv).setExp(cexp).setSalary(salary).setInventCount(inventCount).build();}

    /*Modify and update city level or experience value*/
    public void updateCityLevel(long addPoint){
        long cexp = addPoint + this.cexp;//Current new points + city experience
        Map<Integer, MetaCityLevel> cityLevel = MetaData.getCityLevel();
        if(this.lv>=1){//Calculation level
            long exp=0l;
            do{
                MetaCityLevel obj=cityLevel.get(this.lv);
                exp=obj.exp;//Get the required experience
                if(cexp>=exp){
                    cexp=cexp-exp; //Minus the experience needed to upgrade
                    this.lv++;
                    /*Upgrade completed, invent new products*/
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

    /*Invent new products*/
    public void inventNewGood(MetaCityLevel cityLevelInfo){
        for (int i = 0; i <cityLevelInfo.getInventCount();i++) {
            /*Get a list of inventable products*/
            Set<Integer> cityInventGoodByType = CityGoodInfo.getCityInventGoodByType(cityLevelInfo.inventItem);
            Integer itemId = CityGoodInfo.randomGood(cityInventGoodByType);
            //Synchronize goods across the city
            CityManager.instance().cityGood.add(itemId);
            if(itemId!=null){
                //Obtain new raw materials produced by inventing new products
                Set<Integer> newMaterialId = CityGoodInfo.getNewMaterialId(itemId);
                Gs.CityGoodInfo.Builder newGoodInfo = Gs.CityGoodInfo.newBuilder();
                newGoodInfo.setGoods(Gs.Nums.newBuilder().addNum(itemId));
                newGoodInfo.setMaterial(Gs.Nums.newBuilder().addAllNum(newMaterialId));
                //Synchronize raw materials across the city
                CityManager.instance().cityMaterial.addAll(newMaterialId);
                //TODO Send an email to all players in the city to inform xx that new products have been produced and xxx materials have been produced
                List<Player> allPlayer = GameDb.getAllPlayer();
                newMaterialId.add(itemId);
                /*Add new products and new materials to all players separately*/
                CityGoodInfo.updatePlayerGoodList(allPlayer,newMaterialId);
                /*Push new product information to all online players at the same time*/
                GameServer.sendToAll(Package.create(GsCode.OpCode.newGoodMessage_VALUE,newGoodInfo.build()));
            }
        }
    }
}
