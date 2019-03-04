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
			table.put((short) GsCode.OpCode.heartBeat_VALUE, Wrapper.newWithMessage(Gs.HeartBeat.PARSER, GameSession.class, "heartBeat"));
			table.put((short) GsCode.OpCode.setRoleFaceId_VALUE, Wrapper.newWithMessageAsync(Gs.Str.PARSER, GameSession.class,"setRoleFaceId"));
			table.put((short) GsCode.OpCode.queryMarketDetail_VALUE, Wrapper.newWithMessageAsync(Gs.QueryMarketDetail.PARSER, GameSession.class,"queryMarketDetail"));
			table.put((short) GsCode.OpCode.queryMarketSummary_VALUE, Wrapper.newWithMessageAsync(Gs.Num.PARSER, GameSession.class,"queryMarketSummary"));
			table.put((short) GsCode.OpCode.queryGroundSummary_VALUE, Wrapper.newOnlyOpcodeAsync(GameSession.class,"queryGroundSummary"));
			table.put((short) GsCode.OpCode.createRole_VALUE, Wrapper.newWithMessageAsync(Gs.CreateRole.PARSER, GameSession.class,"createRole"));
			table.put((short) GsCode.OpCode.queryPlayerBuildings_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class,"queryPlayerBuildings"));
			table.put((short) GsCode.OpCode.getAllBuildingDetail_VALUE, Wrapper.newOnlyOpcodeAsync(GameSession.class,"getAllBuildingDetail"));
			table.put((short) GsCode.OpCode.cheat_VALUE, Wrapper.newWithMessageAsync(Gs.Str.PARSER, GameSession.class,"cheat"));
			table.put((short) GsCode.OpCode.setRoleDescription_VALUE, Wrapper.newWithMessageAsync(Gs.Str.PARSER, GameSession.class,"setRoleDescription"));
			table.put((short) GsCode.OpCode.extendBag_VALUE, Wrapper.newOnlyOpcodeAsync(GameSession.class,"extendBag"));
			table.put((short) GsCode.OpCode.queryPlayerInfo_VALUE, Wrapper.newWithMessageAsync(Gs.Bytes.PARSER, GameSession.class,"queryPlayerInfo"));
			table.put((short) GsCode.OpCode.roleLogin_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class,"roleLogin"));
			table.put((short) GsCode.OpCode.move_VALUE, Wrapper.newWithMessageAsync(Gs.GridIndex.PARSER, GameSession.class, "move"));
			table.put((short) GsCode.OpCode.queryGroundAuction_VALUE, Wrapper.newOnlyOpcodeAsync(GameSession.class, "queryGroundAuction"));
			table.put((short) GsCode.OpCode.bidGround_VALUE, Wrapper.newWithMessageAsync(Gs.IntNum.PARSER, GameSession.class,"bidGround"));
			table.put((short) GsCode.OpCode.addBuilding_VALUE, Wrapper.newWithMessageAsync(Gs.AddBuilding.PARSER, GameSession.class,"addBuilding"));
			table.put((short) GsCode.OpCode.delBuilding_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class,"delBuilding"));
			table.put((short) GsCode.OpCode.startBusiness_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class,"startBusiness"));
			table.put((short) GsCode.OpCode.shutdownBusiness_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class,"shutdownBusiness"));
			table.put((short) GsCode.OpCode.transferItem_VALUE, Wrapper.newWithMessageAsync(Gs.TransferItem.PARSER, GameSession.class,"transferItem"));
			table.put((short) GsCode.OpCode.shelfAdd_VALUE, Wrapper.newWithMessageAsync(Gs.ShelfAdd.PARSER, GameSession.class,"shelfAdd"));
			table.put((short) GsCode.OpCode.shelfDel_VALUE, Wrapper.newWithMessageAsync(Gs.ShelfDel.PARSER, GameSession.class,"shelfDel"));
			table.put((short) GsCode.OpCode.shelfSet_VALUE, Wrapper.newWithMessageAsync(Gs.ShelfSet.PARSER, GameSession.class,"shelfSet"));
			table.put((short) GsCode.OpCode.buyInShelf_VALUE, Wrapper.newWithMessageAsync(Gs.BuyInShelf.PARSER, GameSession.class,"buyInShelf"));
			table.put((short) GsCode.OpCode.stopListenBuildingDetailInform_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class,"stopListenBuildingDetailInform"));
			table.put((short) GsCode.OpCode.setBuildingInfo_VALUE, Wrapper.newWithMessageAsync(Gs.SetBuildingInfo.PARSER, GameSession.class,"setBuildingInfo"));
			table.put((short) GsCode.OpCode.queryMoneyPoolInfo_VALUE, Wrapper.newOnlyOpcodeAsync(GameSession.class,"queryMoneyPoolInfo"));

			table.put((short) GsCode.OpCode.exchangeItemList_VALUE, Wrapper.newOnlyOpcodeAsync(GameSession.class,"exchangeItemList"));
			table.put((short) GsCode.OpCode.exchangeBuy_VALUE, Wrapper.newWithMessageAsync(Gs.ExchangeBuy.PARSER, GameSession.class,"exchangeBuy"));
			table.put((short) GsCode.OpCode.exchangeSell_VALUE, Wrapper.newWithMessageAsync(Gs.ExchangeSell.PARSER, GameSession.class,"exchangeSell"));
			table.put((short) GsCode.OpCode.exchangeCancel_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class,"exchangeCancel"));
			table.put((short) GsCode.OpCode.exchangeWatchItemDetail_VALUE, Wrapper.newWithMessageAsync(Gs.Num.PARSER, GameSession.class,"exchangeWatch"));
			table.put((short) GsCode.OpCode.exchangeStopWatchItemDetail_VALUE, Wrapper.newOnlyOpcodeAsync(GameSession.class,"exchangeStopWatch"));
			table.put((short) GsCode.OpCode.exchangeMyOrder_VALUE, Wrapper.newOnlyOpcodeAsync(GameSession.class,"exchangeMyOrder"));
			table.put((short) GsCode.OpCode.exchangeMyDealLog_VALUE, Wrapper.newOnlyOpcodeAsync(GameSession.class,"exchangeMyDealLog"));
			table.put((short) GsCode.OpCode.exchangeAllDealLog_VALUE, Wrapper.newWithMessageAsync(Gs.Num.PARSER, GameSession.class,"exchangeAllDealLog"));
			table.put((short) GsCode.OpCode.exchangeCollect_VALUE, Wrapper.newWithMessageAsync(Gs.Num.PARSER, GameSession.class,"exchangeCollect"));
			table.put((short) GsCode.OpCode.exchangeUnCollect_VALUE, Wrapper.newWithMessageAsync(Gs.Num.PARSER, GameSession.class,"exchangeUnCollect"));
			table.put((short) GsCode.OpCode.exchangeGetItemDealHistory_VALUE, Wrapper.newWithMessageAsync(Gs.Num.PARSER, GameSession.class,"exchangeGetItemDealHistory"));

			table.put((short) GsCode.OpCode.adAddSlot_VALUE, Wrapper.newWithMessageAsync(Gs.AddSlot.PARSER, GameSession.class,"adAddSlot"));
			table.put((short) GsCode.OpCode.adDelSlot_VALUE, Wrapper.newWithMessageAsync(Gs.AdDelSlot.PARSER, GameSession.class,"adDelSlot"));
			table.put((short) GsCode.OpCode.adPutAdToSlot_VALUE, Wrapper.newWithMessageAsync(Gs.AddAd.PARSER, GameSession.class,"adPutAdToSlot"));
			table.put((short) GsCode.OpCode.adBuySlot_VALUE, Wrapper.newWithMessageAsync(Gs.AdBuySlot.PARSER, GameSession.class,"adBuySlot"));
			table.put((short) GsCode.OpCode.adDelAdFromSlot_VALUE, Wrapper.newWithMessageAsync(Gs.AdDelAdFromSlot.PARSER, GameSession.class,"adDelAdFromSlot"));
			table.put((short) GsCode.OpCode.adSetTicket_VALUE, Wrapper.newWithMessageAsync(Gs.AdSetTicket.PARSER, GameSession.class,"adSetTicket"));
			table.put((short) GsCode.OpCode.adSetSlot_VALUE, Wrapper.newWithMessageAsync(Gs.AdSetSlot.PARSER, GameSession.class,"adSetSlot"));

			table.put((short) GsCode.OpCode.delItem_VALUE, Wrapper.newWithMessageAsync(Gs.DelItem.PARSER, GameSession.class,"delItem"));

			table.put((short) GsCode.OpCode.detailApartment_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class,"detailApartment"));
			table.put((short) GsCode.OpCode.detailMaterialFactory_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class,"detailMaterialFactory"));
			table.put((short) GsCode.OpCode.detailProduceDepartment_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class,"detailProduceDepartment"));
			table.put((short) GsCode.OpCode.detailRetailShop_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class,"detailRetailShop"));
			table.put((short) GsCode.OpCode.detailLaboratory_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class,"detailLaboratory"));
			table.put((short) GsCode.OpCode.detailPublicFacility_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class,"detailPublicFacility"));


			table.put((short) GsCode.OpCode.setRent_VALUE, Wrapper.newWithMessageAsync(Gs.SetRent.PARSER, GameSession.class,"setRent"));
			table.put((short) GsCode.OpCode.setSalary_VALUE, Wrapper.newWithMessageAsync(Gs.SetSalary.PARSER, GameSession.class,"setSalaryRatio"));
			table.put((short) GsCode.OpCode.ftyAddLine_VALUE, Wrapper.newWithMessageAsync(Gs.AddLine.PARSER, GameSession.class,"ftyAddLine"));
			table.put((short) GsCode.OpCode.ftyChangeLine_VALUE, Wrapper.newWithMessageAsync(Gs.ChangeLine.PARSER, GameSession.class,"ftyChangeLine"));
			table.put((short) GsCode.OpCode.ftyDelLine_VALUE, Wrapper.newWithMessageAsync(Gs.DelLine.PARSER, GameSession.class,"ftyDelLine"));

			table.put((short) GsCode.OpCode.rentOutGround_VALUE, Wrapper.newWithMessageAsync(Gs.GroundRent.PARSER, GameSession.class,"rentOutGround"));
			table.put((short) GsCode.OpCode.rentGround_VALUE, Wrapper.newWithMessageAsync(Gs.RentGround.PARSER, GameSession.class,"rentGround"));
			table.put((short) GsCode.OpCode.sellGround_VALUE, Wrapper.newWithMessageAsync(Gs.GroundSale.PARSER, GameSession.class,"sellGround"));
			table.put((short) GsCode.OpCode.buyGround_VALUE, Wrapper.newWithMessageAsync(Gs.GroundSale.PARSER, GameSession.class,"buyGround"));
			table.put((short) GsCode.OpCode.cancelRentGround_VALUE, Wrapper.newWithMessageAsync(Gs.MiniIndexCollection.PARSER, GameSession.class,"cancelRentGround"));
			table.put((short) GsCode.OpCode.cancelSellGround_VALUE, Wrapper.newWithMessageAsync(Gs.MiniIndexCollection.PARSER, GameSession.class,"cancelSellGround"));

			table.put((short) GsCode.OpCode.labLineAdd_VALUE, Wrapper.newWithMessageAsync(Gs.LabAddLine.PARSER, GameSession.class,"labLineAdd"));
			table.put((short) GsCode.OpCode.labLineDel_VALUE, Wrapper.newWithMessageAsync(Gs.LabDelLine.PARSER, GameSession.class,"labLineDel"));
			table.put((short) GsCode.OpCode.labLineSetWorkerNum_VALUE, Wrapper.newWithMessageAsync(Gs.LabSetLineWorkerNum.PARSER, GameSession.class,"labLineSetWorkerNum"));
			table.put((short) GsCode.OpCode.labLaunchLine_VALUE, Wrapper.newWithMessageAsync(Gs.LabLaunchLine.PARSER, GameSession.class,"labLaunchLine"));
			table.put((short) GsCode.OpCode.labRoll_VALUE, Wrapper.newWithMessageAsync(Gs.LabRoll.PARSER, GameSession.class,"labRoll"));

			table.put((short) GsCode.OpCode.techTradeAdd_VALUE, Wrapper.newWithMessageAsync(Gs.TechTradeAdd.PARSER, GameSession.class,"techTradeAdd"));
			table.put((short) GsCode.OpCode.techTradeBuy_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class,"techTradeBuy"));
			table.put((short) GsCode.OpCode.techTradeDel_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class,"techTradeDel"));
			table.put((short) GsCode.OpCode.techTradeGetSummary_VALUE, Wrapper.newOnlyOpcodeAsync(GameSession.class,"techTradeGetSummary"));
			table.put((short) GsCode.OpCode.techTradeGetDetail_VALUE, Wrapper.newWithMessageAsync(Gs.Num.PARSER, GameSession.class,"techTradeGetDetail"));

			table.put((short) GsCode.OpCode.talentAddLine_VALUE, Wrapper.newWithMessageAsync(Gs.TalentAddLine.PARSER, GameSession.class,"talentAddLine"));
			table.put((short) GsCode.OpCode.talentDelLine_VALUE, Wrapper.newWithMessageAsync(Gs.TalentDelLine.PARSER, GameSession.class,"talentDelLine"));
			table.put((short) GsCode.OpCode.talentFinishLine_VALUE, Wrapper.newWithMessageAsync(Gs.TalentFinishLine.PARSER, GameSession.class,"talentFinishLine"));
			table.put((short) GsCode.OpCode.allocTalent_VALUE, Wrapper.newWithMessageAsync(Gs.AllocTalent.PARSER, GameSession.class,"allocTalent"));
			table.put((short) GsCode.OpCode.unallocTalent_VALUE, Wrapper.newWithMessageAsync(Gs.AllocTalent.PARSER, GameSession.class,"unallocTalent"));

			//wxj=================================================
			table.put((short) GsCode.OpCode.searchPlayer_VALUE, Wrapper.newWithMessageAsync(Gs.Str.PARSER, GameSession.class, "searchPlayerByName"));
			table.put((short) GsCode.OpCode.addFriend_VALUE, Wrapper.newWithMessageAsync(Gs.ByteStr.PARSER, GameSession.class, "addFriend"));
			table.put((short) GsCode.OpCode.addFriendReq_VALUE, Wrapper.newWithMessageAsync(Gs.ByteBool.PARSER, GameSession.class, "addFriendResult"));
			table.put((short) GsCode.OpCode.deleteFriend_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class, "deleteFriend"));

			table.put((short) GsCode.OpCode.addBlacklist_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class, "addBlacklist"));
			table.put((short) GsCode.OpCode.deleteBlacklist_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class, "deleteBlacklist"));
			table.put((short) GsCode.OpCode.roleCommunication_VALUE, Wrapper.newWithMessageAsync(Gs.CommunicationReq.PARSER, GameSession.class, "communication"));

			table.put((short) GsCode.OpCode.getGroundInfo_VALUE, Wrapper.newOnlyOpcodeAsync(GameSession.class, "getGroundInfo"));
			table.put((short) GsCode.OpCode.createSociety_VALUE, Wrapper.newWithMessageAsync(Gs.CreateSociety.PARSER, GameSession.class, "createSociety"));
			table.put((short) GsCode.OpCode.modifySocietyName_VALUE, Wrapper.newWithMessageAsync(Gs.BytesStrings.PARSER, GameSession.class, "modifySocietyName"));
			table.put((short) GsCode.OpCode.modifyDeclaration_VALUE, Wrapper.newWithMessageAsync(Gs.BytesStrings.PARSER, GameSession.class, "modifyDeclaration"));
			table.put((short) GsCode.OpCode.modifyIntroduction_VALUE, Wrapper.newWithMessageAsync(Gs.BytesStrings.PARSER, GameSession.class, "modifyIntroduction"));
			table.put((short) GsCode.OpCode.getSocietyInfo_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class, "getSocietyInfo"));
			table.put((short) GsCode.OpCode.getSocietyList_VALUE, Wrapper.newOnlyOpcodeAsync(GameSession.class, "getSocietyList"));

			table.put((short) GsCode.OpCode.joinSociety_VALUE, Wrapper.newWithMessageAsync(Gs.ByteStr.PARSER, GameSession.class, "joinSociety"));
			table.put((short) GsCode.OpCode.joinHandle_VALUE, Wrapper.newWithMessageAsync(Gs.JoinHandle.PARSER, GameSession.class, "joinHandle"));
			table.put((short) GsCode.OpCode.exitSociety_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class, "exitSociety"));
			table.put((short) GsCode.OpCode.appointerPost_VALUE, Wrapper.newWithMessageAsync(Gs.AppointerReq.PARSER, GameSession.class, "appointerPost"));
			table.put((short) GsCode.OpCode.kickMember_VALUE, Wrapper.newWithMessageAsync(Gs.Ids.PARSER, GameSession.class, "kickMember"));
			//===========================================================

			//llb========================================================
			table.put((short) GsCode.OpCode.getAllMails_VALUE, Wrapper.newOnlyOpcodeAsync(GameSession.class,"getAllMails"));
			table.put((short) GsCode.OpCode.mailRead_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class, "mailRead"));
			table.put((short) GsCode.OpCode.delMail_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class, "delMail"));

			//===========================================================
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
