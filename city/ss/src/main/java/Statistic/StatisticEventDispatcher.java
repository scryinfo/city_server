package Statistic;

import Shared.DispatcherBase;
import Shared.GlobalConfig;
import Shared.Package;
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
            table.put((short) SsCode.OpCode.queryGoodsNpcNum_VALUE,Wrapper.newWithMessage(Ss.GoodNpcNumInfo.PARSER, StatisticSession.class, "queryGoodsNpcNum"));
            table.put((short) SsCode.OpCode.queryNpcExchangeAmount_VALUE, Wrapper.newOnlyOpcode(StatisticSession.class, "queryNpcExchangeAmount"));
            table.put((short) SsCode.OpCode.queryExchangeAmount_VALUE, Wrapper.newOnlyOpcode(StatisticSession.class, "queryExchangeAmount"));
            table.put((short) SsCode.OpCode.queryGoodsNpcNumCurve_VALUE, Wrapper.newWithMessage(Ss.GoodsNpcNumCurve.PARSER,StatisticSession.class, "queryGoodsNpcNumCurve"));
            table.put((short) SsCode.OpCode.queryCityBroadcast_VALUE, Wrapper.newOnlyOpcode(StatisticSession.class, "queryCityBroadcast"));
            table.put((short) SsCode.OpCode.queryNpcTypeNum_VALUE, Wrapper.newOnlyOpcode(StatisticSession.class, "queryNpcTypeNum"));
            table.put((short) SsCode.OpCode.queryBuildingLift_VALUE, Wrapper.newWithMessage(Ss.Id.PARSER,StatisticSession.class, "queryBuildingLift"));
            table.put((short) SsCode.OpCode.queryBuildingFlow_VALUE, Wrapper.newWithMessage(Ss.Id.PARSER,StatisticSession.class, "queryBuildingFlow"));

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