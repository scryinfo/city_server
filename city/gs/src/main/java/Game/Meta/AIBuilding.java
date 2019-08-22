package Game.Meta;

import Game.Action.*;
import org.bson.Document;

import java.util.Arrays;

public class AIBuilding extends ProbBase {
    AIBuilding(Document d) {
        super(Type.ALL.ordinal(), d);
    }
    enum Type {
        GOTO_RETAIL_SHOP,
        GOTO_APARTMENT,
        GOTO_WORK,
        GOTO_HOME,
        IDLE,
        ALL
    }
    public IAction random(AIBuilding aiBuilding, int aiId) {
        IAction.logger.info("AIBuilding id " + id + " AIBuilding weight " +Arrays.toString(weight)+ " aiId " + aiId);
        int[] d = Arrays.copyOf(weight, weight.length);
        d[Type.GOTO_RETAIL_SHOP.ordinal()] = weight[0];
        d[Type.GOTO_APARTMENT.ordinal()] = weight[1];
        d[Type.GOTO_WORK.ordinal()] = weight[2];
        d[Type.GOTO_HOME.ordinal()] = weight[3];
        d[Type.IDLE.ordinal()] = weight[4];
        IAction.logger.info("AIBuilding id " + id + " weights " + Arrays.toString(d));
        switch (Type.values()[super.randomIdx(d)]) {
            case IDLE:
                return new Idle();
            case GOTO_HOME:
                return new GoHome();
            case GOTO_WORK:
                return new GoWork();
            case GOTO_APARTMENT:
                return new GoApartment(MetaBuilding.APARTMENT);
            case GOTO_RETAIL_SHOP:
                return new Shopping(aiId);
        }
        return null;
    }
    public IAction randomAgain(AIBuilding aiBuilding, int aiId) {
        int[] d=new int[3];
        d[0]=weight[2];
        d[1]=weight[3];
        d[2]=weight[4];
        switch (super.randomIdx(d)) {
            case 2:
                return new Idle();
            case 1:
                return new GoHome();
            case 0:
                return new GoWork();
        }
        return null;
    }
}
