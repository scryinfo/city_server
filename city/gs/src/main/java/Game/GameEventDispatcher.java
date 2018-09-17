package Game;

import Shared.DispatcherBase;
import Shared.GlobalConfig;
import Shared.Package;
import gs.Gs;
import gscode.GsCode;
import org.apache.log4j.Logger;

public class GameEventDispatcher extends DispatcherBase {
	private static final Logger logger = Logger.getLogger(GameEventDispatcher.class);
	private static GameEventDispatcher instance = new GameEventDispatcher();
	private GameEventDispatcher(){
		try {
			table.put((short) GsCode.OpCode.login_VALUE, Wrapper.newWithMessage(Gs.Login.PARSER, GameSession.class,"login"));
			table.put((short) GsCode.OpCode.createRole_VALUE, Wrapper.newWithMessage(Gs.Str.PARSER, GameSession.class,"createRole"));
			table.put((short) GsCode.OpCode.queryMetaGroundAuction_VALUE, Wrapper.newOnlyOpcode(GameSession.class,"queryMetaGroundAuction"));
			table.put((short) GsCode.OpCode.heartBeat_VALUE, Wrapper.newWithMessage(Gs.HeartBeat.PARSER, GameSession.class, "heartBeat"));

			table.put((short) GsCode.OpCode.roleLogin_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class,"roleLogin"));
			table.put((short) GsCode.OpCode.move_VALUE, Wrapper.newWithMessageAsync(Gs.GridIndex.PARSER, GameSession.class, "move"));
			table.put((short) GsCode.OpCode.queryGroundAuction_VALUE, Wrapper.newOnlyOpcodeAsync(GameSession.class, "queryGroundAuction"));
			table.put((short) GsCode.OpCode.registGroundBidInform_VALUE, Wrapper.newOnlyOpcodeAsync(GameSession.class, "registGroundBidInform"));
			table.put((short) GsCode.OpCode.unregistGroundBidInform_VALUE, Wrapper.newOnlyOpcodeAsync(GameSession.class, "unregistGroundBidInform"));
			table.put((short) GsCode.OpCode.bidGround_VALUE, Wrapper.newWithMessageAsync(Gs.IdNum.PARSER, GameSession.class,"bidGround"));
			table.put((short) GsCode.OpCode.addBuilding_VALUE, Wrapper.newWithMessageAsync(Gs.AddBuilding.PARSER, GameSession.class,"addBuilding"));
			table.put((short) GsCode.OpCode.delBuilding_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class,"delBuilding"));


			if(GlobalConfig.debug()){

			}
		}catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static GameEventDispatcher getInstance(){
		return instance;
	}

	public boolean process(Package pack, GameSession s) {
		if(GlobalConfig.debug())
			this.printRequest(pack.opcode, GsCode.OpCode.getDescriptor().findValueByNumber(pack.opcode));
		if(!s.valid())
		{
			if(pack.opcode != GsCode.OpCode.login_VALUE)
				return false;
		}
		else
		{
			if(pack.opcode == GsCode.OpCode.login_VALUE)
				return false;
			if(s.roleLogin()) {
				if(pack.opcode == GsCode.OpCode.createRole_VALUE || pack.opcode == GsCode.OpCode.roleLogin_VALUE)
					return false;
			}
			else {
				if(!(pack.opcode == GsCode.OpCode.createRole_VALUE || pack.opcode == GsCode.OpCode.roleLogin_VALUE))
					return false;
			}
		}
		ProcessType t = processType(pack.opcode);
		if(t == null)
			return false;

		ParseResult o;
		try {
			o = parseMessage(pack);
		} catch (Exception e) {
			return false;
		}

		if(t == ProcessType.SYNC) {
			return invoke(o, pack.opcode, s);
		}
		else {
			s.asyncExecute(o.method, pack.opcode, o.message);
			return true;
		}
	}
}
