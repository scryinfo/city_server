package Account;

import Shared.DispatcherBase;
import Shared.GlobalConfig;
import Shared.Package;
import ga.Ga;
import gacode.GaCode;

public class GameServerEventDispatcher extends DispatcherBase {
	private static GameServerEventDispatcher singleInstance = new GameServerEventDispatcher();
	
	private GameServerEventDispatcher(){
		try {
			Class<?> type = Package.class;
			table.put((short)GaCode.OpCode.login_VALUE, Wrapper.newWithMessage(Ga.Login.parser(), GameServerSession.class,"login"));
			table.put((short)GaCode.OpCode.stateReport_VALUE, Wrapper.newWithMessage(Ga.StateReport.parser(), GameServerSession.class,"stateReport"));
			table.put((short)GaCode.OpCode.validateAck_VALUE, Wrapper.newWithMessage(Ga.ValidationCode.parser(), GameServerSession.class,"validateAck"));
		}catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static GameServerEventDispatcher getInstance(){
		return singleInstance;
	}
	
	public boolean call(Package pack, GameServerSession s) {
		if(GlobalConfig.debug())
			this.printRequest(pack.opcode, GaCode.OpCode.getDescriptor().findValueByNumber(pack.opcode));
		if(!s.valid()){
			if(pack.opcode != GaCode.OpCode.login_VALUE)
				return false;
		}
		else{
			if(pack.opcode == GaCode.OpCode.login_VALUE)
				return false;
		}
		ParseResult o;
		try {
			o = parseMessage(pack);
		} catch (Exception e) {
			return false;
		}
		return invoke(o, pack.opcode, s);
	}
}
