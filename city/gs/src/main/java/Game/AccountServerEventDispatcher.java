package Game;

import Shared.DispatcherBase;
import Shared.GlobalConfig;
import Shared.Package;
import common.Common;
import ga.Ga;
import gacode.GaCode;

public class AccountServerEventDispatcher extends DispatcherBase {
	private static AccountServerEventDispatcher instance = new AccountServerEventDispatcher();

	private AccountServerEventDispatcher() {
		try {
			table.put((short) GaCode.OpCode.validateInfo_VALUE,  Wrapper.newWithMessage(Ga.ValidationCode.PARSER, AccountServerSession.class,"validationCode"));
			table.put((short) GaCode.OpCode.login_VALUE,  Wrapper.newOnlyOpcode(AccountServerSession.class,"loginACK"));
			table.put((short) Common.OpCode.error_VALUE,  Wrapper.newWithMessage(Common.Fail.PARSER, AccountServerSession.class,"handleError"));
			table.put((short) GaCode.OpCode.heartInfo_VALUE,  Wrapper.newOnlyOpcode(AccountServerSession.class,"heartInfo"));
			if (GlobalConfig.debug()) {
				// gsMap.put(GameEvent.CHEAT_ADD_COMMISSION,
				// Game.GameSession.class.getMethod("cheatAddCommission"));
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static AccountServerEventDispatcher getInstance() {
		return instance;
	}

	public boolean process(Package pack, AccountServerSession s) {
		if(GlobalConfig.debug())
			this.printRequest(pack.opcode, GaCode.OpCode.getDescriptor().findValueByNumber(pack.opcode));
		ParseResult o;
		try {
			o = parseMessage(pack);
		} catch (Exception e) {
			return false;
		}
		return invoke(o, pack.opcode, s);
	}
}
