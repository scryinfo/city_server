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
			table.put((short) GsCode.OpCode.bidGround_VALUE, Wrapper.newWithMessageAsync(Gs.ByteNum.PARSER, GameSession.class,"bidGround"));
			table.put((short) GsCode.OpCode.addBuilding_VALUE, Wrapper.newWithMessageAsync(Gs.AddBuilding.PARSER, GameSession.class,"addBuilding"));
			table.put((short) GsCode.OpCode.delBuilding_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class,"delBuilding"));
			table.put((short) GsCode.OpCode.construct_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class,"construct"));
			table.put((short) GsCode.OpCode.startBusiness_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class,"startBusiness"));
			table.put((short) GsCode.OpCode.transform_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class,"transform"));
			table.put((short) GsCode.OpCode.transferItem_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class,"transferItem"));

			table.put((short) GsCode.OpCode.exchangeItemList_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class,"exchangeItemList"));
			table.put((short) GsCode.OpCode.exchangeBuy_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class,"exchangeBuy"));
			table.put((short) GsCode.OpCode.exchangeSell_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class,"exchangeSell"));
			table.put((short) GsCode.OpCode.exchangeCancel_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class,"exchangeCancel"));
			table.put((short) GsCode.OpCode.exchangeWatchItemDetail_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class,"exchangeWatch"));
			table.put((short) GsCode.OpCode.exchangeStopWatchItemDetail_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class,"exchangeStopWatch"));



			table.put((short) GsCode.OpCode.detailApartment_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class,"detailApartment"));
			table.put((short) GsCode.OpCode.detailMaterialFactory_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class,"detailMaterialFactory"));
			table.put((short) GsCode.OpCode.detailProduceDepartment_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class,"detailProduceDepartment"));
			table.put((short) GsCode.OpCode.detailRetailShop_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class,"detailRetailShop"));
			table.put((short) GsCode.OpCode.detailLaboratory_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class,"detailLaboratory"));


			table.put((short) GsCode.OpCode.setRent_VALUE, Wrapper.newWithMessageAsync(Gs.ByteNum.PARSER, GameSession.class,"setRent"));
			table.put((short) GsCode.OpCode.setSalary_VALUE, Wrapper.newWithMessageAsync(Gs.ByteNum.PARSER, GameSession.class,"setSalary"));
			table.put((short) GsCode.OpCode.addLine_VALUE, Wrapper.newWithMessageAsync(Gs.ByteNum.PARSER, GameSession.class,"addLine"));
			table.put((short) GsCode.OpCode.changeLine_VALUE, Wrapper.newWithMessageAsync(Gs.ByteNum.PARSER, GameSession.class,"changeLine"));


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
