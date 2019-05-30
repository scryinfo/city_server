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
			table.put((short) GsCode.OpCode.bidGround_VALUE, Wrapper.newWithMessageAsync(Gs.IntNum.PARSER, GameSession.class,"bidGround"));
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
			table.put((short) GsCode.OpCode.queryBuildingTech_VALUE, Wrapper.newWithMessageAsync(Gs.ByteNum.PARSER, GameSession.class, "queryBuildingTech"));

			//===========================================================

			//llb========================================================
			table.put((short) GsCode.OpCode.getAllMails_VALUE, Wrapper.newOnlyOpcodeAsync(GameSession.class,"getAllMails"));
			table.put((short) GsCode.OpCode.mailRead_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class, "mailRead"));
			table.put((short) GsCode.OpCode.delMail_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class, "delMail"));

			//===========================================================
			table.put((short) GsCode.OpCode.eachTypeNpcNum_VALUE, Wrapper.newOnlyOpcodeAsync(GameSession.class,"eachTypeNpcNum"));
			table.put((short) GsCode.OpCode.queryIndustryWages_VALUE, Wrapper.newWithMessage(Gs.QueryIndustryWages.PARSER,GameSession.class, "QueryIndustryWages"));
			table.put((short) GsCode.OpCode.queryMyBuildings_VALUE, Wrapper.newWithMessageAsync(Gs.QueryMyBuildings.PARSER, GameSession.class,"queryMyBuildings"));
			table.put((short) GsCode.OpCode.queryMyEva_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class,"queryMyEva"));
			table.put((short) GsCode.OpCode.updateMyEva_VALUE, Wrapper.newWithMessageAsync(Gs.Eva.PARSER, GameSession.class,"updateMyEva"));
			//todo：eva改版
			table.put((short) GsCode.OpCode.updateMyEvas_VALUE, Wrapper.newWithMessageAsync(Gs.Evas.PARSER, GameSession.class,"updateMyEvas"));
			table.put((short) GsCode.OpCode.queryMyBrands_VALUE, Wrapper.newWithMessageAsync(Gs.QueryMyBrands.PARSER, GameSession.class,"queryMyBrands"));
			table.put((short) GsCode.OpCode.queryMyBrandDetail_VALUE, Wrapper.newWithMessageAsync(Gs.QueryMyBrandDetail.PARSER, GameSession.class,"queryMyBrandDetail"));
			table.put((short) GsCode.OpCode.updateMyBrandDetail_VALUE, Wrapper.newWithMessageAsync(Gs.BrandLeague.PARSER, GameSession.class,"updateMyBrandDetail"));
			table.put((short) GsCode.OpCode.modyfyMyBrandName_VALUE, Wrapper.newWithMessageAsync(Gs.ModyfyMyBrandName.PARSER, GameSession.class,"modyfyMyBrandName"));
			table.put((short) GsCode.OpCode.modifyCompanyName_VALUE, Wrapper.newWithMessageAsync(Gs.ModifyCompanyName.PARSER, GameSession.class,"modifyCompanyName"));

			table.put((short) GsCode.OpCode.getOneSocietyInfo_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class, "getOneSocietyInfo"));
			table.put((short) GsCode.OpCode.getPlayerAmount_VALUE, Wrapper.newOnlyOpcodeAsync(GameSession.class,"getPlayerAmount"));
			//获取原料推荐价格
			table.put((short) GsCode.OpCode.queryMaterialRecommendPrice_VALUE, Wrapper.newWithMessageAsync(Gs.MaterialMsg.PARSER, GameSession.class, "queryMaterialRecommendPrice"));
			//获取推广推荐价格
			table.put((short) GsCode.OpCode.queryPromotionRecommendPrice_VALUE, Wrapper.newWithMessageAsync(Gs.PromotionMsg.PARSER, GameSession.class, "queryPromotionRecommendPrice"));
			//获取研究所推荐价格
			table.put((short) GsCode.OpCode.queryLaboratoryRecommendPrice_VALUE, Wrapper.newWithMessageAsync(Gs.LaboratoryMsg.PARSER, GameSession.class, "queryLaboratoryRecommendPrice"));
			//获取加工厂商品推荐价格
			table.put((short) GsCode.OpCode.queryProduceDepRecommendPrice_VALUE, Wrapper.newWithMessageAsync(Gs.ProduceDepMsg.PARSER, GameSession.class, "queryProduceDepRecommendPrice"));

//			//获取住宅推荐价格
//			table.put((short) GsCode.OpCode.queryApartmentRecommendPrice_VALUE, Wrapper.newWithMessageAsync(Gs.QueryBuildingInfo.PARSER, GameSession.class, "queryApartmentRecommendPrice"));
//			//获取零售店推荐价格
//			table.put((short) GsCode.OpCode.queryRetailShopRecommendPrice_VALUE, Wrapper.newWithMessageAsync(Gs.RetailShopMsg.PARSER, GameSession.class, "queryRetailShopRecommendPrice"));

			/*WareHouse*/
			table.put((short) GsCode.OpCode.detailWareHouse_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER, GameSession.class,"detailWareHouse"));
			//设置仓库出租信息
			table.put((short) GsCode.OpCode.setWareHouseRent_VALUE, Wrapper.newWithMessageAsync(Gs.SetWareHouseRent.PARSER, GameSession.class,"setWareHouseRent"));
			//销毁货物
			table.put((short) GsCode.OpCode.delItems_VALUE, Wrapper.newWithMessageAsync(Gs.ItemsInfo.PARSER, GameSession.class,"delItems"));
			//运输（集散中心运输）
			table.put((short) GsCode.OpCode.transportGood_VALUE, Wrapper.newWithMessageAsync(Gs.TransportGood.PARSER,GameSession.class,"transportGood"));
			//关闭出租
			table.put((short) GsCode.OpCode.closeWareHouseRent_VALUE, Wrapper.newWithMessageAsync(Gs.SetWareHouseRent.PARSER, GameSession.class,"closeWareHouseRent"));
			//租用仓库
			table.put((short) GsCode.OpCode.rentWareHouse_VALUE, Wrapper.newWithMessageAsync(Gs.rentWareHouse.PARSER, GameSession.class,"rentWareHouse"));
			//查询玩家的（建筑）仓库信息（包含租用的仓库信息）
			table.put((short) GsCode.OpCode.getPlayerBuildingDetail_VALUE, Wrapper.newOnlyOpcodeAsync(GameSession.class,"getPlayerBuildingDetail"));
			//购买商品
			table.put((short) GsCode.OpCode.buyShelfGood_VALUE, Wrapper.newWithMessageAsync(Gs.BuyInShelfGood.PARSER,GameSession.class,"buyInShelfGood"));
			//上架
			table.put((short) GsCode.OpCode.putAway_VALUE, Wrapper.newWithMessageAsync(Gs.PutAway.PARSER,GameSession.class,"putAway"));
			//修改租用仓库上架商品
			table.put((short) GsCode.OpCode.rentWarehouseShelfSet_VALUE, Wrapper.newWithMessageAsync(Gs.RentWarehouseShelfSet.PARSER,GameSession.class,"rentWarehouseShelfSet"));
			//下架
			table.put((short) GsCode.OpCode.soldOutShelf_VALUE, Wrapper.newWithMessageAsync(Gs.SoldOutShelf.PARSER,GameSession.class,"soldOutShelf"));
			//设置自动补货
			table.put((short) GsCode.OpCode.setRentAutoReplenish_VALUE, Wrapper.newWithMessageAsync(Gs.SetRentAutoReplenish.PARSER,GameSession.class,"setRentAutoReplenish"));
			//获取集散中心收入情况
			table.put((short) GsCode.OpCode.getWareHouseIncomeInfo_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER,GameSession.class,"getWareHouseIncomeInfo"));
			//获取集散中心租户详情
			table.put((short) GsCode.OpCode.detailWareHouseRenter_VALUE, Wrapper.newWithMessageAsync(Gs.Id.PARSER,GameSession.class,"detailWareHouseRenter"));
			//获取集散中心的数据摘要
			table.put((short) GsCode.OpCode.queryWareHouseSummary_VALUE, Wrapper.newOnlyOpcodeAsync(GameSession.class,"queryWareHouseSummary"));
			//查询集散中心详情（非建筑详情）
			table.put((short) GsCode.OpCode.queryWareHouseDetail_VALUE, Wrapper.newWithMessageAsync(Gs.QueryWareHouseDetail.PARSER,GameSession.class,"queryWareHouseDetail"));
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
			//查询仓库信息
			table.put((short) GsCode.OpCode.queryWarehouseInfo_VALUE, Wrapper.newWithMessageAsync(Gs.QueryBuildingInfo.PARSER, GameSession.class,"queryWarehouseInfo"));
			//查询研究所信息
			table.put((short) GsCode.OpCode.queryLaboratoryInfo_VALUE, Wrapper.newWithMessageAsync(Gs.QueryBuildingInfo.PARSER, GameSession.class,"queryLaboratoryInfo"));

			//充提
			table.put((short) GsCode.OpCode.cc_createUser_VALUE, Wrapper.newWithMessageAsync(Gs.Cc_createUser.PARSER, GameSession.class,"cc_CreateUserReq"));
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
