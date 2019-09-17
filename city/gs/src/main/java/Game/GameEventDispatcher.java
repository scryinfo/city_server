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
			table.put((short) GsCode.OpCode.queryLabSummary_VALUE, Wrapper.newOnlyOpcodeAsync(GameSession.class,"queryLabSummary"));
			table.put((short) GsCode.OpCode.adQueryPromoSummary_VALUE, Wrapper.newOnlyOpcodeAsync(GameSession.class,"queryPromoSummary"));
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
			table.put((short) GsCode.OpCode.bidGround_VALUE, Wrapper.newWithMessageAsync(Gs.BidGround.PARSER, GameSession.class,"bidGround"));
			table.put((short) GsCode.OpCode.addBuilding_VALUE, Wrapper.newWithMessageAsync(Gs.AddBuilding.PARSER, GameSession.class,"addBuilding"));
			table.put((short) GsCode.OpCode.delBuilding_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class,"delBuilding"));
			table.put((short) GsCode.OpCode.startBusiness_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class,"startBusiness"));
			table.put((short) GsCode.OpCode.shutdownBusiness_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class,"shutdownBusiness"));
			table.put((short) GsCode.OpCode.transferItem_VALUE, Wrapper.newWithMessageAsync(Gs.TransferItem.PARSER, GameSession.class,"transferItem"));
			table.put((short) GsCode.OpCode.shelfAdd_VALUE, Wrapper.newWithMessageAsync(Gs.ShelfAdd.PARSER, GameSession.class,"shelfAdd"));
			table.put((short) GsCode.OpCode.setAutoReplenish_VALUE, Wrapper.newWithMessageAsync(Gs.setAutoReplenish.PARSER, GameSession.class,"setAutoReplenish"));
			table.put((short) GsCode.OpCode.shelfDel_VALUE, Wrapper.newWithMessageAsync(Gs.ShelfDel.PARSER, GameSession.class,"shelfDel"));
			table.put((short) GsCode.OpCode.shelfSet_VALUE, Wrapper.newWithMessageAsync(Gs.ShelfSet.PARSER, GameSession.class,"shelfSet"));
			table.put((short) GsCode.OpCode.buyInShelf_VALUE, Wrapper.newWithMessageAsync(Gs.BuyInShelf.PARSER, GameSession.class,"buyInShelf"));
			table.put((short) GsCode.OpCode.stopListenBuildingDetailInform_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class,"stopListenBuildingDetailInform"));
			table.put((short) GsCode.OpCode.setBuildingInfo_VALUE, Wrapper.newWithMessageAsync(Gs.SetBuildingInfo.PARSER, GameSession.class,"setBuildingInfo"));
			table.put((short) GsCode.OpCode.queryMoneyPoolInfo_VALUE, Wrapper.newOnlyOpcodeAsync(GameSession.class,"queryMoneyPoolInfo"));
			table.put((short) GsCode.OpCode.queryLabDetail_VALUE, Wrapper.newWithMessageAsync(Gs.QueryLabDetail.PARSER, GameSession.class,"queryLabDetail"));
			table.put((short) GsCode.OpCode.adQueryPromoDetail_VALUE, Wrapper.newWithMessageAsync(Gs.QueryPromoDetail.PARSER, GameSession.class,"queryPromoDetail"));

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

			table.put((short) GsCode.OpCode.getAllFlight_VALUE, Wrapper.newOnlyOpcodeAsync(GameSession.class,"getAllFlight"));
			table.put((short) GsCode.OpCode.betFlight_VALUE, Wrapper.newWithMessageAsync(Gs.BetFlight.PARSER, GameSession.class,"betFlight"));
			table.put((short) GsCode.OpCode.getFlightBetHistory_VALUE, Wrapper.newOnlyOpcodeAsync(GameSession.class,"getFlightBetHistory"));
			table.put((short) GsCode.OpCode.searchFlight_VALUE, Wrapper.newWithMessageAsync(Gs.SearchFlight.PARSER, GameSession.class,"searchFlight"));

			table.put((short) GsCode.OpCode.adAddSlot_VALUE, Wrapper.newWithMessageAsync(Gs.AddSlot.PARSER, GameSession.class,"adAddSlot"));
			table.put((short) GsCode.OpCode.adDelSlot_VALUE, Wrapper.newWithMessageAsync(Gs.AdDelSlot.PARSER, GameSession.class,"adDelSlot"));
			table.put((short) GsCode.OpCode.adPutAdToSlot_VALUE, Wrapper.newWithMessageAsync(Gs.AddAd.PARSER, GameSession.class,"adPutAdToSlot"));
			table.put((short) GsCode.OpCode.adBuySlot_VALUE, Wrapper.newWithMessageAsync(Gs.AdBuySlot.PARSER, GameSession.class,"adBuySlot"));
			table.put((short) GsCode.OpCode.adDelAdFromSlot_VALUE, Wrapper.newWithMessageAsync(Gs.AdDelAdFromSlot.PARSER, GameSession.class,"adDelAdFromSlot"));
			table.put((short) GsCode.OpCode.adSetTicket_VALUE, Wrapper.newWithMessageAsync(Gs.AdSetTicket.PARSER, GameSession.class,"adSetTicket"));
			table.put((short) GsCode.OpCode.adSetSlot_VALUE, Wrapper.newWithMessageAsync(Gs.AdSetSlot.PARSER, GameSession.class,"adSetSlot"));
			table.put((short) GsCode.OpCode.adQueryPromotion_VALUE, Wrapper.newWithMessageAsync(Gs.AdQueryPromotion.PARSER, GameSession.class,"AdQueryPromotion"));
			table.put((short) GsCode.OpCode.adGetAllMyFlowSign_VALUE, Wrapper.newWithMessageAsync(Gs.GetAllMyFlowSign.PARSER, GameSession.class,"GetAllMyFlowSign"));
			table.put((short) GsCode.OpCode.adAddNewPromoOrder_VALUE, Wrapper.newWithMessageAsync(Gs.AdAddNewPromoOrder.PARSER, GameSession.class,"AdAddNewPromoOrder"));
			table.put((short) GsCode.OpCode.adRemovePromoOrder_VALUE, Wrapper.newWithMessageAsync(Gs.AdRemovePromoOrder.PARSER, GameSession.class,"AdRemovePromoOrder"));
			table.put((short) GsCode.OpCode.adGetPromoAbilityHistory_VALUE, Wrapper.newWithMessageAsync(Gs.AdGetPromoAbilityHistory.PARSER, GameSession.class,"AdGetPromoAbilityHistory"));
			table.put((short) GsCode.OpCode.adQueryPromoCurAbilitys_VALUE, Wrapper.newWithMessageAsync(Gs.AdQueryPromoCurAbilitys.PARSER, GameSession.class,"adQueryPromoCurAbilitys"));
			table.put((short) GsCode.OpCode.adjustPromoSellingSetting_VALUE, Wrapper.newWithMessageAsync(Gs.AdjustPromoSellingSetting.PARSER, GameSession.class,"AdjustPromoSellingSetting"));

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
			table.put((short) GsCode.OpCode.ftySetLineOrder_VALUE, Wrapper.newWithMessageAsync(Gs.SetLineOrder.PARSER, GameSession.class,"ftySetLineOrder"));
			table.put((short) GsCode.OpCode.ftyDelLine_VALUE, Wrapper.newWithMessageAsync(Gs.DelLine.PARSER, GameSession.class,"ftyDelLine"));

			table.put((short) GsCode.OpCode.rentOutGround_VALUE, Wrapper.newWithMessageAsync(Gs.GroundRent.PARSER, GameSession.class,"rentOutGround"));
			table.put((short) GsCode.OpCode.rentGround_VALUE, Wrapper.newWithMessageAsync(Gs.RentGround.PARSER, GameSession.class,"rentGround"));
			table.put((short) GsCode.OpCode.sellGround_VALUE, Wrapper.newWithMessageAsync(Gs.GroundSale.PARSER, GameSession.class,"sellGround"));
			table.put((short) GsCode.OpCode.buyGround_VALUE, Wrapper.newWithMessageAsync(Gs.GroundSale.PARSER, GameSession.class,"buyGround"));
			table.put((short) GsCode.OpCode.cancelRentGround_VALUE, Wrapper.newWithMessageAsync(Gs.MiniIndexCollection.PARSER, GameSession.class,"cancelRentGround"));
			table.put((short) GsCode.OpCode.cancelSellGround_VALUE, Wrapper.newWithMessageAsync(Gs.MiniIndexCollection.PARSER, GameSession.class,"cancelSellGround"));

			table.put((short) GsCode.OpCode.labAddLine_VALUE, Wrapper.newWithMessageAsync(Gs.LabAddLine.PARSER, GameSession.class,"labLineAdd"));
			table.put((short) GsCode.OpCode.labCancelLine_VALUE, Wrapper.newWithMessageAsync(Gs.LabCancelLine.PARSER, GameSession.class,"labLineCancel"));
			table.put((short) GsCode.OpCode.labSetting_VALUE, Wrapper.newWithMessageAsync(Gs.LabSetting.PARSER, GameSession.class,"labSetting"));
			table.put((short) GsCode.OpCode.labRoll_VALUE, Wrapper.newWithMessageAsync(Gs.LabRoll.PARSER, GameSession.class,"labRoll"));
			table.put((short) GsCode.OpCode.labExclusive_VALUE, Wrapper.newWithMessageAsync(Gs.LabExclusive.PARSER, GameSession.class,"labExclusive"));

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

			table.put((short) GsCode.OpCode.setPlayerName_VALUE, Wrapper.newWithMessageAsync(Gs.Str.PARSER, GameSession.class,"setPlayerName"));
			table.put((short) GsCode.OpCode.getAllFlight_VALUE, Wrapper.newOnlyOpcodeAsync(GameSession.class,"getAllFlight"));
			table.put((short) GsCode.OpCode.betFlight_VALUE, Wrapper.newWithMessageAsync(Gs.BetFlight.PARSER, GameSession.class,"betFlight"));
			table.put((short) GsCode.OpCode.getFlightBetHistory_VALUE, Wrapper.newOnlyOpcodeAsync(GameSession.class,"getFlightBetHistory"));

			//wxj=================================================
			table.put((short) GsCode.OpCode.searchPlayer_VALUE, Wrapper.newWithMessageAsync(Gs.Str.PARSER, GameSession.class, "searchPlayerByName"));
			table.put((short) GsCode.OpCode.addFriend_VALUE, Wrapper.newWithMessageAsync(Gs.ByteStr.PARSER, GameSession.class, "addFriend"));
			table.put((short) GsCode.OpCode.addFriendReq_VALUE, Wrapper.newWithMessageAsync(Gs.ByteBool.PARSER, GameSession.class, "addFriendResult"));
			table.put((short) GsCode.OpCode.deleteFriend_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class, "deleteFriend"));

			table.put((short) GsCode.OpCode.addBlacklist_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class, "addBlacklist"));
			table.put((short) GsCode.OpCode.deleteBlacklist_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class, "deleteBlacklist"));
			table.put((short) GsCode.OpCode.roleCommunication_VALUE, Wrapper.newWithMessageAsync(Gs.CommunicationReq.PARSER, GameSession.class, "communication"));

			table.put((short) GsCode.OpCode.getGroundInfo_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER,GameSession.class, "getGroundInfo"));
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
			table.put((short) GsCode.OpCode.getPrivateBuildingCommonInfo_VALUE, Wrapper.newWithMessageAsync(Gs.Bytes.PARSER, GameSession.class, "getPrivateBuildingCommonInfo"));

			table.put((short) GsCode.OpCode.closeContract_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class, "closeContract"));
			table.put((short) GsCode.OpCode.settingContract_VALUE, Wrapper.newWithMessageAsync(Gs.ContractSetting.PARSER, GameSession.class, "settingContract"));
			table.put((short) GsCode.OpCode.cancelContract_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class, "cancelContract"));
			table.put((short) GsCode.OpCode.signContract_VALUE, Wrapper.newWithMessageAsync(Gs.SignContract.PARSER, GameSession.class, "signContract"));
			table.put((short) GsCode.OpCode.getCompanyContracts_VALUE, Wrapper.newOnlyOpcodeAsync(GameSession.class, "getCompanyContracts"));
			table.put((short) GsCode.OpCode.queryContractSummary_VALUE, Wrapper.newOnlyOpcodeAsync(GameSession.class, "queryContractSummary"));
			table.put((short) GsCode.OpCode.queryContractGridDetail_VALUE, Wrapper.newWithMessageAsync(Gs.GridIndex.PARSER,GameSession.class, "queryContractGridDetail"));


			table.put((short) GsCode.OpCode.getLeagueInfo_VALUE, Wrapper.newWithMessageAsync(Gs.Num.PARSER, GameSession.class, "getLeagueInfo"));
			table.put((short) GsCode.OpCode.setLeagueInfo_VALUE, Wrapper.newWithMessageAsync(Gs.LeagueInfoSetting.PARSER, GameSession.class, "setLeagueInfo"));
			table.put((short) GsCode.OpCode.closeLeagueInfo_VALUE, Wrapper.newWithMessageAsync(Gs.Num.PARSER, GameSession.class, "closeLeagueInfo"));
			table.put((short) GsCode.OpCode.queryLeagueTechList_VALUE, Wrapper.newWithMessageAsync(Gs.Num.PARSER, GameSession.class, "queryLeagueTechList"));
			table.put((short) GsCode.OpCode.queryBuildingListByPlayerTech_VALUE, Wrapper.newWithMessageAsync(Gs.ByteNum.PARSER, GameSession.class, "queryBuildingListByPlayerTech"));
			table.put((short) GsCode.OpCode.joinLeague_VALUE, Wrapper.newWithMessageAsync(Gs.JoinLeague.PARSER, GameSession.class, "joinLeague"));
			table.put((short) GsCode.OpCode.queryWeatherInfo_VALUE, Wrapper.newOnlyOpcodeAsync(GameSession.class, "queryWeatherInfo"));

			//===========================================================

			//llb========================================================
			table.put((short) GsCode.OpCode.getAllMails_VALUE, Wrapper.newOnlyOpcodeAsync(GameSession.class,"getAllMails"));
			table.put((short) GsCode.OpCode.mailRead_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class, "mailRead"));
			table.put((short) GsCode.OpCode.delMail_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class, "delMail"));

			//===========================================================
			table.put((short) GsCode.OpCode.eachTypeNpcNum_VALUE, Wrapper.newOnlyOpcodeAsync(GameSession.class,"eachTypeNpcNum"));
			table.put((short) GsCode.OpCode.queryIndustryWages_VALUE, Wrapper.newWithMessage(Gs.QueryIndustryWages.PARSER,GameSession.class, "QueryIndustryWages"));
			table.put((short) GsCode.OpCode.queryMyBuildings_VALUE, Wrapper.newWithMessageAsync(Gs.QueryMyBuildings.PARSER, GameSession.class,"queryMyBuildings"));
			table.put((short) GsCode.OpCode.queryMyEva_VALUE, Wrapper.newWithMessageAsync(Gs.QueryMyEva.PARSER, GameSession.class,"queryMyEva"));
			//todo：eva改版
			table.put((short) GsCode.OpCode.updateMyEvas_VALUE, Wrapper.newWithMessageAsync(Gs.UpdateMyEvas.PARSER, GameSession.class,"updateMyEvas"));
			table.put((short) GsCode.OpCode.modifyCompanyName_VALUE, Wrapper.newWithMessageAsync(Gs.ModifyCompanyName.PARSER, GameSession.class,"modifyCompanyName"));

			table.put((short) GsCode.OpCode.getOneSocietyInfo_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class, "getOneSocietyInfo"));
			table.put((short) GsCode.OpCode.getPlayerAmount_VALUE, Wrapper.newOnlyOpcodeAsync(GameSession.class,"getPlayerAmount"));

			table.put((short) GsCode.OpCode.queryBuildingName_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class, "queryBuildingName"));

			//小地图繁荣度
			table.put((short) GsCode.OpCode.queryMapProsperity_VALUE, Wrapper.newOnlyOpcodeAsync(GameSession.class, "queryMapProsperity"));
			//小地图建筑摘要
			table.put((short) GsCode.OpCode.queryMapBuidlingSummary_VALUE, Wrapper.newWithMessageAsync(Gs.MiniIndex.PARSER, GameSession.class, "queryMapBuidlingSummary"));
			//小地图新版研究所摘要
			table.put((short) GsCode.OpCode.queryTechnologySummary_VALUE, Wrapper.newWithMessageAsync(Gs.Num.PARSER, GameSession.class, "queryTechnologySummary"));
			// 新研究所详情
			table.put((short) GsCode.OpCode.queryTechnologyDetail_VALUE, Wrapper.newWithMessageAsync(Gs.queryTechnologyDetail.PARSER, GameSession.class, "queryTechnologyDetail"));
			//小地图新版数据公司摘要
			table.put((short) GsCode.OpCode.queryPromotionSummary_VALUE, Wrapper.newWithMessageAsync(Gs.Num.PARSER, GameSession.class, "queryPromotionSummary"));
			// 数据公司详情
			table.put((short) GsCode.OpCode.queryPromotionsDetail_VALUE, Wrapper.newWithMessageAsync(Gs.queryPromotionsDetail.PARSER, GameSession.class, "queryPromotionsDetail"));
			// 行业供需
			table.put((short) GsCode.OpCode.querySupplyAndDemand_VALUE, Wrapper.newWithMessageAsync(Gs.SupplyAndDemand.PARSER, GameSession.class, "querySupplyAndDemand"));
			// 城市发展等级点数
			table.put((short) GsCode.OpCode.queryCityLevel_VALUE, Wrapper.newOnlyOpcodeAsync(GameSession.class, "queryCityLevel"));
			// Eva等级分布
			table.put((short) GsCode.OpCode.queryEvaGrade_VALUE, Wrapper.newWithMessageAsync(Gs.queryEvaGrade.PARSER, GameSession.class, "queryEvaGrade"));
			// 商品供需
			table.put((short) GsCode.OpCode.queryItemSupplyAndDemand_VALUE, Wrapper.newWithMessageAsync(Gs.queryItemSupplyAndDemand.PARSER, GameSession.class, "queryItemSupplyAndDemand"));

			//修改建筑名称
			table.put((short) GsCode.OpCode.updateBuildingName_VALUE, Wrapper.newWithMessageAsync(Gs.UpdateBuildingName.PARSER, GameSession.class,"updateBuildingName"));
			//查询原料厂信息
			table.put((short) GsCode.OpCode.queryMaterialInfo_VALUE, Wrapper.newWithMessageAsync(Gs.QueryBuildingInfo.PARSER, GameSession.class,"queryMaterialInfo"));
			//查询加工厂信息	
			table.put((short) GsCode.OpCode.queryProduceDepInfo_VALUE, Wrapper.newWithMessageAsync(Gs.QueryBuildingInfo.PARSER, GameSession.class,"queryProduceDepInfo"));
			//查询零售店或住宅信息
			table.put((short) GsCode.OpCode.queryRetailShopOrApartmentInfo_VALUE, Wrapper.newWithMessageAsync(Gs.QueryBuildingInfo.PARSER, GameSession.class,"queryRetailShopOrApartmentInfo"));
			//查询推广公司信息
			table.put((short) GsCode.OpCode.queryPromotionCompanyInfo_VALUE, Wrapper.newWithMessageAsync(Gs.QueryBuildingInfo.PARSER, GameSession.class,"queryPromotionCompanyInfo"));
			//查询研究所信息
			table.put((short) GsCode.OpCode.queryLaboratoryInfo_VALUE, Wrapper.newWithMessageAsync(Gs.QueryBuildingInfo.PARSER, GameSession.class,"queryLaboratoryInfo"));

			//查询原料厂的原料信息
			table.put((short) GsCode.OpCode.queryBuildingMaterialInfo_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class,"queryBuildingMaterialInfo"));
			//查询加工厂的商品信息
			table.put((short) GsCode.OpCode.queryBuildingGoodInfo_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class,"queryBuildingGoodInfo"));

			//查询货架数据（用于客户端的更新）
			table.put((short) GsCode.OpCode.getShelfData_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class,"getShelfData"));
			//查询仓库数据（用于客户端的更新）
			table.put((short) GsCode.OpCode.getStorageData_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class,"getStorageData"));
			//查询生产线数据
			table.put((short) GsCode.OpCode.getLineData_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class,"getLineData"));
			//充提
			table.put((short) GsCode.OpCode.ct_createUser_VALUE, Wrapper.newWithMessageAsync(ccapi.Dddbind.ct_createUser.PARSER, GameSession.class,"ct_createUser"));
			table.put((short) GsCode.OpCode.ct_GenerateOrderReq_VALUE, Wrapper.newWithMessageAsync(ccapi.Dddbind.ct_GenerateOrderReq.PARSER, GameSession.class,"ct_GenerateOrderReq"));
			table.put((short) GsCode.OpCode.ct_RechargeRequestReq_VALUE, Wrapper.newWithMessageAsync(ccapi.Dddbind.ct_RechargeRequestReq.PARSER, GameSession.class,"ct_RechargeRequestReq"));
			table.put((short) GsCode.OpCode.ct_DisChargeReq_VALUE, Wrapper.newWithMessageAsync(ccapi.Dddbind.ct_DisChargeReq.PARSER, GameSession.class,"ct_DisChargeReq"));
			table.put((short) GsCode.OpCode.ct_DisPaySmVefifyReq_VALUE, Wrapper.newWithMessageAsync(ccapi.Dddbind.ct_DisPaySmVefifyReq.PARSER, GameSession.class,"ct_DisPaySmVefifyReq"));
			table.put((short) GsCode.OpCode.ct_GetTradingRecords_VALUE, Wrapper.newWithMessageAsync(ccapi.Dddbind.ct_GetTradingRecords.PARSER, GameSession.class,"ct_GetTradingRecords"));

			//小地图改版（查询建筑生产线状态）
			table.put((short) GsCode.OpCode.queryBuildingProduceStatue_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class,"queryBuildingProduceStatue"));
			table.put((short) GsCode.OpCode.queryRetailShopGoods_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class,"queryRetailShopGoods"));
			//离线通知
			table.put((short) GsCode.OpCode.queryOffLineInformation_VALUE, Wrapper.newOnlyOpcodeAsync(GameSession.class,"queryOffLineInformation"));
			//查询建筑繁荣度（1小时更新）
			table.put((short) GsCode.OpCode.queryBuildingProsperity_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER,GameSession.class,"queryBuildingProsperity"));
			//查询建筑摘要
			table.put((short) GsCode.OpCode.queryTypeBuildingSummary_VALUE, Wrapper.newWithMessageAsync(Gs.Num.PARSER,GameSession.class,"queryTypeBuildingSummary"));
			//查询小地图建筑类别
			table.put((short) GsCode.OpCode.queryTypeBuildingDetail_VALUE, Wrapper.newWithMessageAsync(Gs.QueryTypeBuildingDetail.PARSER,GameSession.class,"queryTypeBuildingDetail"));
            //查询玩家在原料厂、加工厂、零售店、住宅的收入支出
			table.put((short) GsCode.OpCode.queryPlayerIncomePay_VALUE, Wrapper.newWithMessage(Gs.PlayerIncomePay.PARSER,GameSession.class, "queryPlayerIncomePay"));
			//查询各行业信息-玩家收入排行榜
			table.put((short) GsCode.OpCode.queryPlayerIncomeRanking_VALUE, Wrapper.newWithMessage(Gs.PlayerIncomeRanking.PARSER,GameSession.class, "queryPlayerIncomeRanking"));

			//新版研究所===============================
			table.put((short) GsCode.OpCode.detailTechnology_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER,GameSession.class,"detailTechnology"));//研究所建筑详情
			table.put((short) GsCode.OpCode.openScienceBox_VALUE, Wrapper.newWithMessageAsync(Gs.OpenScience.PARSER,GameSession.class,"openScienceBox"));
			table.put((short) GsCode.OpCode.useSciencePoint_VALUE, Wrapper.newWithMessageAsync(Gs.OpenScience.PARSER,GameSession.class,"useSciencePoint"));

			//新版广告公司=============================
			table.put((short) GsCode.OpCode.detailPromotionCompany_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER,GameSession.class,"detailPromotionCompany"));//建筑详情
			table.put((short) GsCode.OpCode.usePromotionPoint_VALUE, Wrapper.newWithMessageAsync(Gs.OpenScience.PARSER,GameSession.class,"usePromotionPoint"));//建筑详情

			/*研究所、推广公司公用协议*/
			table.put((short) GsCode.OpCode.addScienceLine_VALUE, Wrapper.newWithMessageAsync(Gs.AddLine.PARSER,GameSession.class,"addScienceLine"));//添加生产线
			table.put((short) GsCode.OpCode.delScienceLine_VALUE, Wrapper.newWithMessageAsync(Gs.DelLine.PARSER,GameSession.class,"delScienceLine"));//添加生产线
			table.put((short) GsCode.OpCode.setScienceLineOrder_VALUE, Wrapper.newWithMessageAsync(Gs.SetLineOrder.PARSER,GameSession.class,"setScienceLineOrder"));//调整生产线顺序
			table.put((short) GsCode.OpCode.scienceShelfAdd_VALUE, Wrapper.newWithMessageAsync(Gs.ShelfAdd.PARSER,GameSession.class,"scienceShelfAdd"));
			table.put((short) GsCode.OpCode.scienceShelfDel_VALUE, Wrapper.newWithMessageAsync(Gs.ShelfDel.PARSER,GameSession.class,"scienceShelfDel"));
			table.put((short) GsCode.OpCode.scienceShelfSet_VALUE, Wrapper.newWithMessageAsync(Gs.ShelfSet.PARSER,GameSession.class,"scienceShelfSet"));
			table.put((short) GsCode.OpCode.buySciencePoint_VALUE, Wrapper.newWithMessageAsync(Gs.BuySciencePoint.PARSER,GameSession.class,"buySciencePoint"));
			table.put((short) GsCode.OpCode.getScienceShelfData_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER,GameSession.class,"getScienceShelfData"));
			table.put((short) GsCode.OpCode.getScienceStorageData_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER,GameSession.class,"getScienceStorageData"));
			table.put((short) GsCode.OpCode.getScienceLineData_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER,GameSession.class,"getScienceLineData"));
			table.put((short) GsCode.OpCode.getScienceItemSpeed_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER,GameSession.class,"getScienceItemSpeed"));

			table.put((short) GsCode.OpCode.queryGroundProsperity_VALUE, Wrapper.newWithMessageAsync(Gs.MiniIndex.PARSER,GameSession.class,"queryGroundProsperity"));
			table.put((short) GsCode.OpCode.queryAuctionProsperity_VALUE, Wrapper.newWithMessageAsync(Gs.Num.PARSER,GameSession.class,"queryAuctionProsperity"));
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
