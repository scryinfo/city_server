package Game.Meta;

import Game.Action.*;
import Game.BrandManager;
import org.bson.Document;

import java.util.Arrays;

public class AIBuilding extends ProbBase {
    AIBuilding(Document d) {
        super(Type.ALL.ordinal(), d);
    }
    enum Type {
        IDLE,
        GOTO_HOME,
        GOTO_WORK,
        GOTO_APARTMENT,
        GOTO_PUBLIC_FACILITY,
        GOTO_RETAIL_SHOP,
        ALL
    }
    public IAction random(double idleRatio, BrandManager.BuildingRatio ratio, int aiId) {
        IAction.logger.info("AIBuilding id " + this.id + " building ratio " + ratio.toString() + " aiId " + aiId);
        int[] d = Arrays.copyOf(weight, weight.length);
        d[Type.IDLE.ordinal()] *= idleRatio;
        d[Type.GOTO_HOME.ordinal()] *= idleRatio;
        d[Type.GOTO_WORK.ordinal()] *= idleRatio;
        d[Type.GOTO_APARTMENT.ordinal()] *= ratio.apartment;
        d[Type.GOTO_PUBLIC_FACILITY.ordinal()] *= ratio.publicFacility;
        d[Type.GOTO_RETAIL_SHOP.ordinal()] *= ratio.retail;
        IAction.logger.info("AIBuilding id " + this.id + " weights " + Arrays.toString(d));
        switch (Type.values()[super.randomIdx(d)]) {
            case IDLE:
                return new Idle();
            case GOTO_HOME:
                return new GoHome();
            case GOTO_WORK:
                return new GoWork();
            case GOTO_APARTMENT:
                return new JustVisit(MetaBuilding.APARTMENT);
            case GOTO_PUBLIC_FACILITY:
                return new JustVisit(MetaBuilding.PUBLIC);
            case GOTO_RETAIL_SHOP:
                return new Shopping(aiId);
        }
        return null;
    }
}
