package Statistic;

import Shared.DispatcherBase;
import Shared.GlobalConfig;
import Shared.Package;
import gs.Gs;
import org.apache.log4j.Logger;
import ss.Ss;
import sscode.SsCode;

public class StatisticEventDispatcher extends DispatcherBase {
    private static StatisticEventDispatcher instance = new StatisticEventDispatcher();
    private static final Logger logger = Logger.getLogger(StatisticEventDispatcher.class);

    private StatisticEventDispatcher() {
        try {
            table.put((short) SsCode.OpCode.queryPlayerEconomy_VALUE, Wrapper.newWithMessage(Ss.Id.PARSER, StatisticSession.class, "queryPlayerEconomy"));
            table.put((short) SsCode.OpCode.queryBuildingIncomeMap_VALUE, Wrapper.newWithMessage(Ss.Id.PARSER, StatisticSession.class, "queryBuildingIncome"));
            table.put((short) SsCode.OpCode.querySexInfo_VALUE, Wrapper.newOnlyOpcode(StatisticSession.class, "queryAllPlayerSex"));
            table.put((short) SsCode.OpCode.queryNpcNum_VALUE,Wrapper.newWithMessage(Ss.QueryNpcNum.PARSER, StatisticSession.class, "queryNpcNum"));
            table.put((short) SsCode.OpCode.queryNpcExchangeAmount_VALUE, Wrapper.newOnlyOpcode(StatisticSession.class, "queryNpcExchangeAmount"));
            table.put((short) SsCode.OpCode.queryExchangeAmount_VALUE, Wrapper.newOnlyOpcode(StatisticSession.class, "queryExchangeAmount"));
            table.put((short) SsCode.OpCode.queryGoodsNpcNumCurve_VALUE, Wrapper.newWithMessage(Ss.GoodsNpcNumCurve.PARSER,StatisticSession.class, "queryGoodsNpcNumCurve"));
            table.put((short) SsCode.OpCode.queryApartmentNpcNumCurve_VALUE, Wrapper.newOnlyOpcode(StatisticSession.class, "queryApartmentNpcNumCurve"));
            table.put((short) SsCode.OpCode.queryCityBroadcast_VALUE, Wrapper.newOnlyOpcode(StatisticSession.class, "queryCityBroadcast"));
            table.put((short) SsCode.OpCode.queryNpcTypeNum_VALUE, Wrapper.newOnlyOpcode(StatisticSession.class, "queryNpcTypeNum"));
            table.put((short) SsCode.OpCode.queryBuildingFlow_VALUE, Wrapper.newWithMessage(Ss.Id.PARSER,StatisticSession.class, "queryBuildingFlow"));
            table.put((short) SsCode.OpCode.queryBuildingLift_VALUE, Wrapper.newWithMessage(Ss.Id.PARSER, StatisticSession.class, "queryBuildingLift"));
            table.put((short) SsCode.OpCode.queryIncomeNotify_VALUE, Wrapper.newWithMessage(Ss.Id.PARSER, StatisticSession.class,"queryIncomeNotify"));

            table.put((short) SsCode.OpCode.queryPlayerExchangeAmount_VALUE, Wrapper.newOnlyOpcode(StatisticSession.class, "queryPlayerExchangeAmount"));  //Query player transaction volume as of the current time
            table.put((short) SsCode.OpCode.queryPlayerGoodsCurve_VALUE, Wrapper.newWithMessage(Ss.PlayerGoodsCurve.PARSER,StatisticSession.class, "queryPlayerGoodsCurve"));// Query player transaction curve
            table.put((short) SsCode.OpCode.queryPlayerIncomePayCurve_VALUE, Wrapper.newWithMessage(Ss.Id.PARSER,StatisticSession.class, "queryPlayerIncomePayCurve")); //Query player income and expenditure curve
            table.put((short) SsCode.OpCode.queryGoodsSoldDetailCurve_VALUE, Wrapper.newWithMessage(Ss.Id.PARSER,StatisticSession.class, "queryGoodsSoldDetailCurve")); //Query product sales details curve
//            table.put((short) SsCode.OpCode.queryIndustryDevelopment_VALUE, Wrapper.newWithMessage(Ss.IndustryDevelopment.PARSER,StatisticSession.class, "queryIndustryDevelopment")); //Industry Information-Industry Development
            table.put((short) SsCode.OpCode.queryIndustryCompetition_VALUE, Wrapper.newWithMessage(Ss.IndustryCompetition.PARSER,StatisticSession.class, "queryIndustryCompetition")); //Industry Information-Industry Competition
            /*Construction operation details*/
            table.put((short) SsCode.OpCode.queryTodayBuildingSaleDetail_VALUE,Wrapper.newWithMessage(Ss.QueryBuildingSaleDetail.PARSER,StatisticSession.class, "queryBuildingSaleDetail"));
            table.put((short) SsCode.OpCode.queryHistoryBuildingSaleDetail_VALUE,Wrapper.newWithMessage(Ss.QueryHistoryBuildingSaleDetail.PARSER,StatisticSession.class, "queryHistoryBuildingSaleDetail")); //Query business details for a week

            // City Information. Industry Revenue
            table.put((short) SsCode.OpCode.queryIndustryIncome_VALUE, Wrapper.newOnlyOpcodeAsync(StatisticSession.class, "queryIndustryIncome")); //Industry revenue
            table.put((short) SsCode.OpCode.queryGroundOrApartmentAvgPrice_VALUE, Wrapper.newWithMessage(Gs.Bool.PARSER,StatisticSession.class, "queryGroundOrApartmentAvgPrice")); //Average transaction price of land or residence
            table.put((short) SsCode.OpCode.queryCityTransactionAmount_VALUE, Wrapper.newWithMessage(Gs.Bool.PARSER,StatisticSession.class, "queryCityTransactionAmount")); //Citywide sales
            table.put((short) SsCode.OpCode.queryCityMoneyPool_VALUE, Wrapper.newOnlyOpcodeAsync(StatisticSession.class, "queryCityMoneyPool")); //City Information-Bonus Pool
            table.put((short) SsCode.OpCode.queryItemAvgPrice_VALUE, Wrapper.newWithMessage(Ss.queryItemAvgPrice.PARSER,StatisticSession.class, "queryItemAvgPrice")); //Query the average price line chart of commodity transactions
            table.put((short) SsCode.OpCode.queryItemSales_VALUE, Wrapper.newWithMessage(Ss.queryItemSales.PARSER,StatisticSession.class, "queryItemSales")); //Query product turnover
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static StatisticEventDispatcher getInstance() {
        return instance;
    }

    public boolean call(Package pack, StatisticSession s) {
        if (GlobalConfig.debug())
            this.printRequest(pack.opcode, SsCode.OpCode.getDescriptor().findValueByNumber(pack.opcode));
        ParseResult o;
        try {
            o = parseMessage(pack);
        } catch (Exception e) {
            return false;
        }
        return invoke(o, pack.opcode, s);
    }
}