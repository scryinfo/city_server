// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: gsCode.proto

package gscode;

public final class GsCode {
  private GsCode() {}
  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistry registry) {
  }
  /**
   * Protobuf enum {@code gscode.OpCode}
   */
  public enum OpCode
      implements com.google.protobuf.ProtocolMessageEnum {
    /**
     * <code>login = 1000;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    login(0, 1000),
    /**
     * <code>heartBeat = 1001;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    heartBeat(1, 1001),
    /**
     * <code>roleLogin = 1002;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    roleLogin(2, 1002),
    /**
     * <code>createRole = 1003;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    createRole(3, 1003),
    /**
     * <code>move = 1004;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    move(4, 1004),
    /**
     * <code>unitCreate = 1005;</code>
     *
     * <pre>
     *s
     * </pre>
     */
    unitCreate(5, 1005),
    /**
     * <code>unitRemove = 1006;</code>
     *
     * <pre>
     *s
     * </pre>
     */
    unitRemove(6, 1006),
    /**
     * <code>unitChange = 1007;</code>
     *
     * <pre>
     *s
     * </pre>
     */
    unitChange(7, 1007),
    /**
     * <code>groundChange = 1008;</code>
     *
     * <pre>
     *s
     * </pre>
     */
    groundChange(8, 1008),
    /**
     * <code>moneyChange = 1009;</code>
     *
     * <pre>
     *s
     * </pre>
     */
    moneyChange(9, 1009),
    /**
     * <code>queryPlayerInfo = 1010;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    queryPlayerInfo(10, 1010),
    /**
     * <code>delItem = 1011;</code>
     *
     * <pre>
     *c 删除仓库中的道具
     * </pre>
     */
    delItem(11, 1011),
    /**
     * <code>extendBag = 1015;</code>
     *
     * <pre>
     *c 扩充中心仓库容量
     * </pre>
     */
    extendBag(12, 1015),
    /**
     * <code>detailPublicFacility = 1019;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    detailPublicFacility(13, 1019),
    /**
     * <code>detailApartment = 1020;</code>
     *
     * <pre>
     *c 建筑物详情界面关闭也必须发送此消息！
     * </pre>
     */
    detailApartment(14, 1020),
    /**
     * <code>detailMaterialFactory = 1021;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    detailMaterialFactory(15, 1021),
    /**
     * <code>detailProduceDepartment = 1022;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    detailProduceDepartment(16, 1022),
    /**
     * <code>detailRetailShop = 1023;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    detailRetailShop(17, 1023),
    /**
     * <code>detailLaboratory = 1024;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    detailLaboratory(18, 1024),
    /**
     * <code>setRent = 1027;</code>
     *
     * <pre>
     *c 设置房租
     * </pre>
     */
    setRent(19, 1027),
    /**
     * <code>setSalary = 1028;</code>
     *
     * <pre>
     *c 设置薪水
     * </pre>
     */
    setSalary(20, 1028),
    /**
     * <code>addLine = 1029;</code>
     *
     * <pre>
     *c 增加生产线
     * </pre>
     */
    addLine(21, 1029),
    /**
     * <code>lineChangeInform = 1032;</code>
     *
     * <pre>
     *s 生产线变化推送
     * </pre>
     */
    lineChangeInform(22, 1032),
    /**
     * <code>changeLine = 1033;</code>
     *
     * <pre>
     *c 改变生成线员工数量或目标产量
     * </pre>
     */
    changeLine(23, 1033),
    /**
     * <code>addBuilding = 1050;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    addBuilding(24, 1050),
    /**
     * <code>delBuilding = 1051;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    delBuilding(25, 1051),
    /**
     * <code>construct = 1052;</code>
     *
     * <pre>
     *c 建造
     * </pre>
     */
    construct(26, 1052),
    /**
     * <code>transform = 1053;</code>
     *
     * <pre>
     *c 把虚拟建筑转换为真实建筑
     * </pre>
     */
    transform(27, 1053),
    /**
     * <code>startBusiness = 1054;</code>
     *
     * <pre>
     *c 开业
     * </pre>
     */
    startBusiness(28, 1054),
    /**
     * <code>transferItem = 1055;</code>
     *
     * <pre>
     *c 运输
     * </pre>
     */
    transferItem(29, 1055),
    /**
     * <code>shelfAdd = 1056;</code>
     *
     * <pre>
     *c 上架
     * </pre>
     */
    shelfAdd(30, 1056),
    /**
     * <code>shelfDel = 1057;</code>
     *
     * <pre>
     *c 下架
     * </pre>
     */
    shelfDel(31, 1057),
    /**
     * <code>shelfSet = 1058;</code>
     *
     * <pre>
     *c 改变货架商品数量价格
     * </pre>
     */
    shelfSet(32, 1058),
    /**
     * <code>buyInShelf = 1059;</code>
     *
     * <pre>
     *c 购买货架商品
     * </pre>
     */
    buyInShelf(33, 1059),
    /**
     * <code>exchangeItemList = 1070;</code>
     *
     * <pre>
     *c 交易所主界面
     * </pre>
     */
    exchangeItemList(34, 1070),
    /**
     * <code>exchangeBuy = 1071;</code>
     *
     * <pre>
     *c 挂买单
     * </pre>
     */
    exchangeBuy(35, 1071),
    /**
     * <code>exchangeSell = 1072;</code>
     *
     * <pre>
     *c 挂卖单
     * </pre>
     */
    exchangeSell(36, 1072),
    /**
     * <code>exchangeCancel = 1073;</code>
     *
     * <pre>
     *c 撤单
     * </pre>
     */
    exchangeCancel(37, 1073),
    /**
     * <code>exchangeWatchItemDetail = 1074;</code>
     *
     * <pre>
     *c 交易物品详情
     * </pre>
     */
    exchangeWatchItemDetail(38, 1074),
    /**
     * <code>exchangeItemDetailInform = 1075;</code>
     *
     * <pre>
     *s 详情改变推送
     * </pre>
     */
    exchangeItemDetailInform(39, 1075),
    /**
     * <code>exchangeStopWatchItemDetail = 1076;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    exchangeStopWatchItemDetail(40, 1076),
    /**
     * <code>exchangeDealInform = 1077;</code>
     *
     * <pre>
     *s 成交通知
     * </pre>
     */
    exchangeDealInform(41, 1077),
    /**
     * <code>exchangeMyOrder = 1078;</code>
     *
     * <pre>
     *c 获取自己的挂单
     * </pre>
     */
    exchangeMyOrder(42, 1078),
    /**
     * <code>exchangeMyDealLog = 1079;</code>
     *
     * <pre>
     *c 获取自己的成交历史
     * </pre>
     */
    exchangeMyDealLog(43, 1079),
    /**
     * <code>exchangeAllDealLog = 1080;</code>
     *
     * <pre>
     *c 获取所有的成交历史
     * </pre>
     */
    exchangeAllDealLog(44, 1080),
    /**
     * <code>exchangeCollect = 1081;</code>
     *
     * <pre>
     *c 收藏
     * </pre>
     */
    exchangeCollect(45, 1081),
    /**
     * <code>exchangeUnCollect = 1082;</code>
     *
     * <pre>
     *c 收藏
     * </pre>
     */
    exchangeUnCollect(46, 1082),
    /**
     * <code>exchangeGetItemDealHistory = 1083;</code>
     *
     * <pre>
     *c 获取道具成交历史
     * </pre>
     */
    exchangeGetItemDealHistory(47, 1083),
    /**
     * <code>queryGroundAuction = 1100;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    queryGroundAuction(48, 1100),
    /**
     * <code>bidGround = 1101;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    bidGround(49, 1101),
    /**
     * <code>queryMetaGroundAuction = 1102;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    queryMetaGroundAuction(50, 1102),
    /**
     * <code>registGroundBidInform = 1103;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    registGroundBidInform(51, 1103),
    /**
     * <code>unregistGroundBidInform = 1104;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    unregistGroundBidInform(52, 1104),
    /**
     * <code>bidChangeInform = 1105;</code>
     *
     * <pre>
     *s
     * </pre>
     */
    bidChangeInform(53, 1105),
    /**
     * <code>auctionEnd = 1106;</code>
     *
     * <pre>
     *s  when a auction's end time reached, server send this to inform this auction is dealed
     * </pre>
     */
    auctionEnd(54, 1106),
    /**
     * <code>metaGroundAuctionAddInform = 1107;</code>
     *
     * <pre>
     *s  this inform message might send after client login to game server but not yet do roleLogin
     * </pre>
     */
    metaGroundAuctionAddInform(55, 1107),
    /**
     * <code>bidFailInform = 1108;</code>
     *
     * <pre>
     *s  other player bid a higher price
     * </pre>
     */
    bidFailInform(56, 1108),
    /**
     * <code>bidWinInform = 1109;</code>
     *
     * <pre>
     *s  you win this auction
     * </pre>
     */
    bidWinInform(57, 1109),
    /**
     * <code>adAddSlot = 1120;</code>
     *
     * <pre>
     *c 添加槽位
     * </pre>
     */
    adAddSlot(58, 1120),
    /**
     * <code>adDelSlot = 1121;</code>
     *
     * <pre>
     *c 删除槽位
     * </pre>
     */
    adDelSlot(59, 1121),
    /**
     * <code>adBuySlot = 1122;</code>
     *
     * <pre>
     *c 购买广告槽位
     * </pre>
     */
    adBuySlot(60, 1122),
    /**
     * <code>adPutAdToSlot = 1123;</code>
     *
     * <pre>
     *c 在槽位上打广告
     * </pre>
     */
    adPutAdToSlot(61, 1123),
    /**
     * <code>adDelAdFromSlot = 1124;</code>
     *
     * <pre>
     *c 把广告从槽位上撤掉
     * </pre>
     */
    adDelAdFromSlot(62, 1124),
    /**
     * <code>adSlotTimeoutInform = 1125;</code>
     *
     * <pre>
     *s 槽位超期通知
     * </pre>
     */
    adSlotTimeoutInform(63, 1125),
    /**
     * <code>adSetTicket = 1126;</code>
     *
     * <pre>
     *c 设置门票
     * </pre>
     */
    adSetTicket(64, 1126),
    /**
     * <code>adSetSlot = 1127;</code>
     *
     * <pre>
     *c 设置槽位
     * </pre>
     */
    adSetSlot(65, 1127),
    /**
     * <code>rentOutGround = 1130;</code>
     *
     * <pre>
     *c 出租自己的地
     * </pre>
     */
    rentOutGround(66, 1130),
    /**
     * <code>rentGround = 1131;</code>
     *
     * <pre>
     *c 租别人的地
     * </pre>
     */
    rentGround(67, 1131),
    /**
     * <code>sellGround = 1132;</code>
     *
     * <pre>
     *c 出售自己的地
     * </pre>
     */
    sellGround(68, 1132),
    /**
     * <code>buyGround = 1133;</code>
     *
     * <pre>
     *c 购买别人出售中的地
     * </pre>
     */
    buyGround(69, 1133),
    /**
     * <code>techTradeGetSummary = 1140;</code>
     *
     * <pre>
     *c 获取科技交易一级列表
     * </pre>
     */
    techTradeGetSummary(70, 1140),
    /**
     * <code>techTradeGetDetail = 1141;</code>
     *
     * <pre>
     *c 获取科技交易二级列表
     * </pre>
     */
    techTradeGetDetail(71, 1141),
    /**
     * <code>techTradeAdd = 1142;</code>
     *
     * <pre>
     *c 上架
     * </pre>
     */
    techTradeAdd(72, 1142),
    /**
     * <code>techTradeDel = 1143;</code>
     *
     * <pre>
     *c 下架
     * </pre>
     */
    techTradeDel(73, 1143),
    /**
     * <code>techTradeBuy = 1144;</code>
     *
     * <pre>
     *c 购买
     * </pre>
     */
    techTradeBuy(74, 1144),
    /**
     * <code>labLineChange = 1150;</code>
     *
     * <pre>
     *s 研究所生产线阶段改变通知
     * </pre>
     */
    labLineChange(75, 1150),
    /**
     * <code>newItem = 1151;</code>
     *
     * <pre>
     *s 商品发明研究推送
     * </pre>
     */
    newItem(76, 1151),
    /**
     * <code>labLineAdd = 1153;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    labLineAdd(77, 1153),
    /**
     * <code>labLineDel = 1154;</code>
     *
     * <pre>
     *cs
     * </pre>
     */
    labLineDel(78, 1154),
    /**
     * <code>labLineSetWorkerNum = 1155;</code>
     *
     * <pre>
     *c 设置生产线员工数量
     * </pre>
     */
    labLineSetWorkerNum(79, 1155),
    /**
     * <code>labLaunchLine = 1156;</code>
     *
     * <pre>
     *c 开始生产
     * </pre>
     */
    labLaunchLine(80, 1156),
    /**
     * <code>cheat = 2000;</code>
     */
    cheat(81, 2000),
    ;

    /**
     * <code>login = 1000;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    public static final int login_VALUE = 1000;
    /**
     * <code>heartBeat = 1001;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    public static final int heartBeat_VALUE = 1001;
    /**
     * <code>roleLogin = 1002;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    public static final int roleLogin_VALUE = 1002;
    /**
     * <code>createRole = 1003;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    public static final int createRole_VALUE = 1003;
    /**
     * <code>move = 1004;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    public static final int move_VALUE = 1004;
    /**
     * <code>unitCreate = 1005;</code>
     *
     * <pre>
     *s
     * </pre>
     */
    public static final int unitCreate_VALUE = 1005;
    /**
     * <code>unitRemove = 1006;</code>
     *
     * <pre>
     *s
     * </pre>
     */
    public static final int unitRemove_VALUE = 1006;
    /**
     * <code>unitChange = 1007;</code>
     *
     * <pre>
     *s
     * </pre>
     */
    public static final int unitChange_VALUE = 1007;
    /**
     * <code>groundChange = 1008;</code>
     *
     * <pre>
     *s
     * </pre>
     */
    public static final int groundChange_VALUE = 1008;
    /**
     * <code>moneyChange = 1009;</code>
     *
     * <pre>
     *s
     * </pre>
     */
    public static final int moneyChange_VALUE = 1009;
    /**
     * <code>queryPlayerInfo = 1010;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    public static final int queryPlayerInfo_VALUE = 1010;
    /**
     * <code>delItem = 1011;</code>
     *
     * <pre>
     *c 删除仓库中的道具
     * </pre>
     */
    public static final int delItem_VALUE = 1011;
    /**
     * <code>extendBag = 1015;</code>
     *
     * <pre>
     *c 扩充中心仓库容量
     * </pre>
     */
    public static final int extendBag_VALUE = 1015;
    /**
     * <code>detailPublicFacility = 1019;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    public static final int detailPublicFacility_VALUE = 1019;
    /**
     * <code>detailApartment = 1020;</code>
     *
     * <pre>
     *c 建筑物详情界面关闭也必须发送此消息！
     * </pre>
     */
    public static final int detailApartment_VALUE = 1020;
    /**
     * <code>detailMaterialFactory = 1021;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    public static final int detailMaterialFactory_VALUE = 1021;
    /**
     * <code>detailProduceDepartment = 1022;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    public static final int detailProduceDepartment_VALUE = 1022;
    /**
     * <code>detailRetailShop = 1023;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    public static final int detailRetailShop_VALUE = 1023;
    /**
     * <code>detailLaboratory = 1024;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    public static final int detailLaboratory_VALUE = 1024;
    /**
     * <code>setRent = 1027;</code>
     *
     * <pre>
     *c 设置房租
     * </pre>
     */
    public static final int setRent_VALUE = 1027;
    /**
     * <code>setSalary = 1028;</code>
     *
     * <pre>
     *c 设置薪水
     * </pre>
     */
    public static final int setSalary_VALUE = 1028;
    /**
     * <code>addLine = 1029;</code>
     *
     * <pre>
     *c 增加生产线
     * </pre>
     */
    public static final int addLine_VALUE = 1029;
    /**
     * <code>lineChangeInform = 1032;</code>
     *
     * <pre>
     *s 生产线变化推送
     * </pre>
     */
    public static final int lineChangeInform_VALUE = 1032;
    /**
     * <code>changeLine = 1033;</code>
     *
     * <pre>
     *c 改变生成线员工数量或目标产量
     * </pre>
     */
    public static final int changeLine_VALUE = 1033;
    /**
     * <code>addBuilding = 1050;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    public static final int addBuilding_VALUE = 1050;
    /**
     * <code>delBuilding = 1051;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    public static final int delBuilding_VALUE = 1051;
    /**
     * <code>construct = 1052;</code>
     *
     * <pre>
     *c 建造
     * </pre>
     */
    public static final int construct_VALUE = 1052;
    /**
     * <code>transform = 1053;</code>
     *
     * <pre>
     *c 把虚拟建筑转换为真实建筑
     * </pre>
     */
    public static final int transform_VALUE = 1053;
    /**
     * <code>startBusiness = 1054;</code>
     *
     * <pre>
     *c 开业
     * </pre>
     */
    public static final int startBusiness_VALUE = 1054;
    /**
     * <code>transferItem = 1055;</code>
     *
     * <pre>
     *c 运输
     * </pre>
     */
    public static final int transferItem_VALUE = 1055;
    /**
     * <code>shelfAdd = 1056;</code>
     *
     * <pre>
     *c 上架
     * </pre>
     */
    public static final int shelfAdd_VALUE = 1056;
    /**
     * <code>shelfDel = 1057;</code>
     *
     * <pre>
     *c 下架
     * </pre>
     */
    public static final int shelfDel_VALUE = 1057;
    /**
     * <code>shelfSet = 1058;</code>
     *
     * <pre>
     *c 改变货架商品数量价格
     * </pre>
     */
    public static final int shelfSet_VALUE = 1058;
    /**
     * <code>buyInShelf = 1059;</code>
     *
     * <pre>
     *c 购买货架商品
     * </pre>
     */
    public static final int buyInShelf_VALUE = 1059;
    /**
     * <code>exchangeItemList = 1070;</code>
     *
     * <pre>
     *c 交易所主界面
     * </pre>
     */
    public static final int exchangeItemList_VALUE = 1070;
    /**
     * <code>exchangeBuy = 1071;</code>
     *
     * <pre>
     *c 挂买单
     * </pre>
     */
    public static final int exchangeBuy_VALUE = 1071;
    /**
     * <code>exchangeSell = 1072;</code>
     *
     * <pre>
     *c 挂卖单
     * </pre>
     */
    public static final int exchangeSell_VALUE = 1072;
    /**
     * <code>exchangeCancel = 1073;</code>
     *
     * <pre>
     *c 撤单
     * </pre>
     */
    public static final int exchangeCancel_VALUE = 1073;
    /**
     * <code>exchangeWatchItemDetail = 1074;</code>
     *
     * <pre>
     *c 交易物品详情
     * </pre>
     */
    public static final int exchangeWatchItemDetail_VALUE = 1074;
    /**
     * <code>exchangeItemDetailInform = 1075;</code>
     *
     * <pre>
     *s 详情改变推送
     * </pre>
     */
    public static final int exchangeItemDetailInform_VALUE = 1075;
    /**
     * <code>exchangeStopWatchItemDetail = 1076;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    public static final int exchangeStopWatchItemDetail_VALUE = 1076;
    /**
     * <code>exchangeDealInform = 1077;</code>
     *
     * <pre>
     *s 成交通知
     * </pre>
     */
    public static final int exchangeDealInform_VALUE = 1077;
    /**
     * <code>exchangeMyOrder = 1078;</code>
     *
     * <pre>
     *c 获取自己的挂单
     * </pre>
     */
    public static final int exchangeMyOrder_VALUE = 1078;
    /**
     * <code>exchangeMyDealLog = 1079;</code>
     *
     * <pre>
     *c 获取自己的成交历史
     * </pre>
     */
    public static final int exchangeMyDealLog_VALUE = 1079;
    /**
     * <code>exchangeAllDealLog = 1080;</code>
     *
     * <pre>
     *c 获取所有的成交历史
     * </pre>
     */
    public static final int exchangeAllDealLog_VALUE = 1080;
    /**
     * <code>exchangeCollect = 1081;</code>
     *
     * <pre>
     *c 收藏
     * </pre>
     */
    public static final int exchangeCollect_VALUE = 1081;
    /**
     * <code>exchangeUnCollect = 1082;</code>
     *
     * <pre>
     *c 收藏
     * </pre>
     */
    public static final int exchangeUnCollect_VALUE = 1082;
    /**
     * <code>exchangeGetItemDealHistory = 1083;</code>
     *
     * <pre>
     *c 获取道具成交历史
     * </pre>
     */
    public static final int exchangeGetItemDealHistory_VALUE = 1083;
    /**
     * <code>queryGroundAuction = 1100;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    public static final int queryGroundAuction_VALUE = 1100;
    /**
     * <code>bidGround = 1101;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    public static final int bidGround_VALUE = 1101;
    /**
     * <code>queryMetaGroundAuction = 1102;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    public static final int queryMetaGroundAuction_VALUE = 1102;
    /**
     * <code>registGroundBidInform = 1103;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    public static final int registGroundBidInform_VALUE = 1103;
    /**
     * <code>unregistGroundBidInform = 1104;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    public static final int unregistGroundBidInform_VALUE = 1104;
    /**
     * <code>bidChangeInform = 1105;</code>
     *
     * <pre>
     *s
     * </pre>
     */
    public static final int bidChangeInform_VALUE = 1105;
    /**
     * <code>auctionEnd = 1106;</code>
     *
     * <pre>
     *s  when a auction's end time reached, server send this to inform this auction is dealed
     * </pre>
     */
    public static final int auctionEnd_VALUE = 1106;
    /**
     * <code>metaGroundAuctionAddInform = 1107;</code>
     *
     * <pre>
     *s  this inform message might send after client login to game server but not yet do roleLogin
     * </pre>
     */
    public static final int metaGroundAuctionAddInform_VALUE = 1107;
    /**
     * <code>bidFailInform = 1108;</code>
     *
     * <pre>
     *s  other player bid a higher price
     * </pre>
     */
    public static final int bidFailInform_VALUE = 1108;
    /**
     * <code>bidWinInform = 1109;</code>
     *
     * <pre>
     *s  you win this auction
     * </pre>
     */
    public static final int bidWinInform_VALUE = 1109;
    /**
     * <code>adAddSlot = 1120;</code>
     *
     * <pre>
     *c 添加槽位
     * </pre>
     */
    public static final int adAddSlot_VALUE = 1120;
    /**
     * <code>adDelSlot = 1121;</code>
     *
     * <pre>
     *c 删除槽位
     * </pre>
     */
    public static final int adDelSlot_VALUE = 1121;
    /**
     * <code>adBuySlot = 1122;</code>
     *
     * <pre>
     *c 购买广告槽位
     * </pre>
     */
    public static final int adBuySlot_VALUE = 1122;
    /**
     * <code>adPutAdToSlot = 1123;</code>
     *
     * <pre>
     *c 在槽位上打广告
     * </pre>
     */
    public static final int adPutAdToSlot_VALUE = 1123;
    /**
     * <code>adDelAdFromSlot = 1124;</code>
     *
     * <pre>
     *c 把广告从槽位上撤掉
     * </pre>
     */
    public static final int adDelAdFromSlot_VALUE = 1124;
    /**
     * <code>adSlotTimeoutInform = 1125;</code>
     *
     * <pre>
     *s 槽位超期通知
     * </pre>
     */
    public static final int adSlotTimeoutInform_VALUE = 1125;
    /**
     * <code>adSetTicket = 1126;</code>
     *
     * <pre>
     *c 设置门票
     * </pre>
     */
    public static final int adSetTicket_VALUE = 1126;
    /**
     * <code>adSetSlot = 1127;</code>
     *
     * <pre>
     *c 设置槽位
     * </pre>
     */
    public static final int adSetSlot_VALUE = 1127;
    /**
     * <code>rentOutGround = 1130;</code>
     *
     * <pre>
     *c 出租自己的地
     * </pre>
     */
    public static final int rentOutGround_VALUE = 1130;
    /**
     * <code>rentGround = 1131;</code>
     *
     * <pre>
     *c 租别人的地
     * </pre>
     */
    public static final int rentGround_VALUE = 1131;
    /**
     * <code>sellGround = 1132;</code>
     *
     * <pre>
     *c 出售自己的地
     * </pre>
     */
    public static final int sellGround_VALUE = 1132;
    /**
     * <code>buyGround = 1133;</code>
     *
     * <pre>
     *c 购买别人出售中的地
     * </pre>
     */
    public static final int buyGround_VALUE = 1133;
    /**
     * <code>techTradeGetSummary = 1140;</code>
     *
     * <pre>
     *c 获取科技交易一级列表
     * </pre>
     */
    public static final int techTradeGetSummary_VALUE = 1140;
    /**
     * <code>techTradeGetDetail = 1141;</code>
     *
     * <pre>
     *c 获取科技交易二级列表
     * </pre>
     */
    public static final int techTradeGetDetail_VALUE = 1141;
    /**
     * <code>techTradeAdd = 1142;</code>
     *
     * <pre>
     *c 上架
     * </pre>
     */
    public static final int techTradeAdd_VALUE = 1142;
    /**
     * <code>techTradeDel = 1143;</code>
     *
     * <pre>
     *c 下架
     * </pre>
     */
    public static final int techTradeDel_VALUE = 1143;
    /**
     * <code>techTradeBuy = 1144;</code>
     *
     * <pre>
     *c 购买
     * </pre>
     */
    public static final int techTradeBuy_VALUE = 1144;
    /**
     * <code>labLineChange = 1150;</code>
     *
     * <pre>
     *s 研究所生产线阶段改变通知
     * </pre>
     */
    public static final int labLineChange_VALUE = 1150;
    /**
     * <code>newItem = 1151;</code>
     *
     * <pre>
     *s 商品发明研究推送
     * </pre>
     */
    public static final int newItem_VALUE = 1151;
    /**
     * <code>labLineAdd = 1153;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    public static final int labLineAdd_VALUE = 1153;
    /**
     * <code>labLineDel = 1154;</code>
     *
     * <pre>
     *cs
     * </pre>
     */
    public static final int labLineDel_VALUE = 1154;
    /**
     * <code>labLineSetWorkerNum = 1155;</code>
     *
     * <pre>
     *c 设置生产线员工数量
     * </pre>
     */
    public static final int labLineSetWorkerNum_VALUE = 1155;
    /**
     * <code>labLaunchLine = 1156;</code>
     *
     * <pre>
     *c 开始生产
     * </pre>
     */
    public static final int labLaunchLine_VALUE = 1156;
    /**
     * <code>cheat = 2000;</code>
     */
    public static final int cheat_VALUE = 2000;


    public final int getNumber() { return value; }

    public static OpCode valueOf(int value) {
      switch (value) {
        case 1000: return login;
        case 1001: return heartBeat;
        case 1002: return roleLogin;
        case 1003: return createRole;
        case 1004: return move;
        case 1005: return unitCreate;
        case 1006: return unitRemove;
        case 1007: return unitChange;
        case 1008: return groundChange;
        case 1009: return moneyChange;
        case 1010: return queryPlayerInfo;
        case 1011: return delItem;
        case 1015: return extendBag;
        case 1019: return detailPublicFacility;
        case 1020: return detailApartment;
        case 1021: return detailMaterialFactory;
        case 1022: return detailProduceDepartment;
        case 1023: return detailRetailShop;
        case 1024: return detailLaboratory;
        case 1027: return setRent;
        case 1028: return setSalary;
        case 1029: return addLine;
        case 1032: return lineChangeInform;
        case 1033: return changeLine;
        case 1050: return addBuilding;
        case 1051: return delBuilding;
        case 1052: return construct;
        case 1053: return transform;
        case 1054: return startBusiness;
        case 1055: return transferItem;
        case 1056: return shelfAdd;
        case 1057: return shelfDel;
        case 1058: return shelfSet;
        case 1059: return buyInShelf;
        case 1070: return exchangeItemList;
        case 1071: return exchangeBuy;
        case 1072: return exchangeSell;
        case 1073: return exchangeCancel;
        case 1074: return exchangeWatchItemDetail;
        case 1075: return exchangeItemDetailInform;
        case 1076: return exchangeStopWatchItemDetail;
        case 1077: return exchangeDealInform;
        case 1078: return exchangeMyOrder;
        case 1079: return exchangeMyDealLog;
        case 1080: return exchangeAllDealLog;
        case 1081: return exchangeCollect;
        case 1082: return exchangeUnCollect;
        case 1083: return exchangeGetItemDealHistory;
        case 1100: return queryGroundAuction;
        case 1101: return bidGround;
        case 1102: return queryMetaGroundAuction;
        case 1103: return registGroundBidInform;
        case 1104: return unregistGroundBidInform;
        case 1105: return bidChangeInform;
        case 1106: return auctionEnd;
        case 1107: return metaGroundAuctionAddInform;
        case 1108: return bidFailInform;
        case 1109: return bidWinInform;
        case 1120: return adAddSlot;
        case 1121: return adDelSlot;
        case 1122: return adBuySlot;
        case 1123: return adPutAdToSlot;
        case 1124: return adDelAdFromSlot;
        case 1125: return adSlotTimeoutInform;
        case 1126: return adSetTicket;
        case 1127: return adSetSlot;
        case 1130: return rentOutGround;
        case 1131: return rentGround;
        case 1132: return sellGround;
        case 1133: return buyGround;
        case 1140: return techTradeGetSummary;
        case 1141: return techTradeGetDetail;
        case 1142: return techTradeAdd;
        case 1143: return techTradeDel;
        case 1144: return techTradeBuy;
        case 1150: return labLineChange;
        case 1151: return newItem;
        case 1153: return labLineAdd;
        case 1154: return labLineDel;
        case 1155: return labLineSetWorkerNum;
        case 1156: return labLaunchLine;
        case 2000: return cheat;
        default: return null;
      }
    }

    public static com.google.protobuf.Internal.EnumLiteMap<OpCode>
        internalGetValueMap() {
      return internalValueMap;
    }
    private static com.google.protobuf.Internal.EnumLiteMap<OpCode>
        internalValueMap =
          new com.google.protobuf.Internal.EnumLiteMap<OpCode>() {
            public OpCode findValueByNumber(int number) {
              return OpCode.valueOf(number);
            }
          };

    public final com.google.protobuf.Descriptors.EnumValueDescriptor
        getValueDescriptor() {
      return getDescriptor().getValues().get(index);
    }
    public final com.google.protobuf.Descriptors.EnumDescriptor
        getDescriptorForType() {
      return getDescriptor();
    }
    public static final com.google.protobuf.Descriptors.EnumDescriptor
        getDescriptor() {
      return gscode.GsCode.getDescriptor().getEnumTypes().get(0);
    }

    private static final OpCode[] VALUES = values();

    public static OpCode valueOf(
        com.google.protobuf.Descriptors.EnumValueDescriptor desc) {
      if (desc.getType() != getDescriptor()) {
        throw new java.lang.IllegalArgumentException(
          "EnumValueDescriptor is not for this type.");
      }
      return VALUES[desc.getIndex()];
    }

    private final int index;
    private final int value;

    private OpCode(int index, int value) {
      this.index = index;
      this.value = value;
    }

    // @@protoc_insertion_point(enum_scope:gscode.OpCode)
  }


  public static com.google.protobuf.Descriptors.FileDescriptor
      getDescriptor() {
    return descriptor;
  }
  private static com.google.protobuf.Descriptors.FileDescriptor
      descriptor;
  static {
    java.lang.String[] descriptorData = {
      "\n\014gsCode.proto\022\006gscode*\206\r\n\006OpCode\022\n\n\005log" +
      "in\020\350\007\022\016\n\theartBeat\020\351\007\022\016\n\troleLogin\020\352\007\022\017\n" +
      "\ncreateRole\020\353\007\022\t\n\004move\020\354\007\022\017\n\nunitCreate\020" +
      "\355\007\022\017\n\nunitRemove\020\356\007\022\017\n\nunitChange\020\357\007\022\021\n\014" +
      "groundChange\020\360\007\022\020\n\013moneyChange\020\361\007\022\024\n\017que" +
      "ryPlayerInfo\020\362\007\022\014\n\007delItem\020\363\007\022\016\n\textendB" +
      "ag\020\367\007\022\031\n\024detailPublicFacility\020\373\007\022\024\n\017deta" +
      "ilApartment\020\374\007\022\032\n\025detailMaterialFactory\020" +
      "\375\007\022\034\n\027detailProduceDepartment\020\376\007\022\025\n\020deta" +
      "ilRetailShop\020\377\007\022\025\n\020detailLaboratory\020\200\010\022\014",
      "\n\007setRent\020\203\010\022\016\n\tsetSalary\020\204\010\022\014\n\007addLine\020" +
      "\205\010\022\025\n\020lineChangeInform\020\210\010\022\017\n\nchangeLine\020" +
      "\211\010\022\020\n\013addBuilding\020\232\010\022\020\n\013delBuilding\020\233\010\022\016" +
      "\n\tconstruct\020\234\010\022\016\n\ttransform\020\235\010\022\022\n\rstartB" +
      "usiness\020\236\010\022\021\n\014transferItem\020\237\010\022\r\n\010shelfAd" +
      "d\020\240\010\022\r\n\010shelfDel\020\241\010\022\r\n\010shelfSet\020\242\010\022\017\n\nbu" +
      "yInShelf\020\243\010\022\025\n\020exchangeItemList\020\256\010\022\020\n\013ex" +
      "changeBuy\020\257\010\022\021\n\014exchangeSell\020\260\010\022\023\n\016excha" +
      "ngeCancel\020\261\010\022\034\n\027exchangeWatchItemDetail\020" +
      "\262\010\022\035\n\030exchangeItemDetailInform\020\263\010\022 \n\033exc",
      "hangeStopWatchItemDetail\020\264\010\022\027\n\022exchangeD" +
      "ealInform\020\265\010\022\024\n\017exchangeMyOrder\020\266\010\022\026\n\021ex" +
      "changeMyDealLog\020\267\010\022\027\n\022exchangeAllDealLog" +
      "\020\270\010\022\024\n\017exchangeCollect\020\271\010\022\026\n\021exchangeUnC" +
      "ollect\020\272\010\022\037\n\032exchangeGetItemDealHistory\020" +
      "\273\010\022\027\n\022queryGroundAuction\020\314\010\022\016\n\tbidGround" +
      "\020\315\010\022\033\n\026queryMetaGroundAuction\020\316\010\022\032\n\025regi" +
      "stGroundBidInform\020\317\010\022\034\n\027unregistGroundBi" +
      "dInform\020\320\010\022\024\n\017bidChangeInform\020\321\010\022\017\n\nauct" +
      "ionEnd\020\322\010\022\037\n\032metaGroundAuctionAddInform\020",
      "\323\010\022\022\n\rbidFailInform\020\324\010\022\021\n\014bidWinInform\020\325" +
      "\010\022\016\n\tadAddSlot\020\340\010\022\016\n\tadDelSlot\020\341\010\022\016\n\tadB" +
      "uySlot\020\342\010\022\022\n\radPutAdToSlot\020\343\010\022\024\n\017adDelAd" +
      "FromSlot\020\344\010\022\030\n\023adSlotTimeoutInform\020\345\010\022\020\n" +
      "\013adSetTicket\020\346\010\022\016\n\tadSetSlot\020\347\010\022\022\n\rrentO" +
      "utGround\020\352\010\022\017\n\nrentGround\020\353\010\022\017\n\nsellGrou" +
      "nd\020\354\010\022\016\n\tbuyGround\020\355\010\022\030\n\023techTradeGetSum" +
      "mary\020\364\010\022\027\n\022techTradeGetDetail\020\365\010\022\021\n\014tech" +
      "TradeAdd\020\366\010\022\021\n\014techTradeDel\020\367\010\022\021\n\014techTr" +
      "adeBuy\020\370\010\022\022\n\rlabLineChange\020\376\010\022\014\n\007newItem",
      "\020\377\010\022\017\n\nlabLineAdd\020\201\t\022\017\n\nlabLineDel\020\202\t\022\030\n" +
      "\023labLineSetWorkerNum\020\203\t\022\022\n\rlabLaunchLine" +
      "\020\204\t\022\n\n\005cheat\020\320\017"
    };
    com.google.protobuf.Descriptors.FileDescriptor.InternalDescriptorAssigner assigner =
      new com.google.protobuf.Descriptors.FileDescriptor.InternalDescriptorAssigner() {
        public com.google.protobuf.ExtensionRegistry assignDescriptors(
            com.google.protobuf.Descriptors.FileDescriptor root) {
          descriptor = root;
          return null;
        }
      };
    com.google.protobuf.Descriptors.FileDescriptor
      .internalBuildGeneratedFileFrom(descriptorData,
        new com.google.protobuf.Descriptors.FileDescriptor[] {
        }, assigner);
  }

  // @@protoc_insertion_point(outer_class_scope)
}
