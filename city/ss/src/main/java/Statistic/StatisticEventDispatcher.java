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
            table.put((short) SsCode.OpCode.queryGoodsNpcNum_VALUE, Wrapper.newOnlyOpcode(StatisticSession.class, "queryGoodsNpcNum"));
            
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