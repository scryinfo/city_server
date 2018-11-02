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
     * <code>queryPlayerInfo = 1010;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    queryPlayerInfo(8, 1010),
    /**
     * <code>extendBag = 1015;</code>
     *
     * <pre>
     *c 扩充中心仓库容量
     * </pre>
     */
    extendBag(9, 1015),
    /**
     * <code>detailApartment = 1020;</code>
     *
     * <pre>
     *c 建筑物详情界面关闭也必须发送此消息！
     * </pre>
     */
    detailApartment(10, 1020),
    /**
     * <code>detailMaterialFactory = 1021;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    detailMaterialFactory(11, 1021),
    /**
     * <code>detailProduceDepartment = 1022;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    detailProduceDepartment(12, 1022),
    /**
     * <code>detailRetailShop = 1023;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    detailRetailShop(13, 1023),
    /**
     * <code>detailLaboratory = 1024;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    detailLaboratory(14, 1024),
    /**
     * <code>detailVirtual = 1025;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    detailVirtual(15, 1025),
    /**
     * <code>setRent = 1027;</code>
     *
     * <pre>
     *c 设置房租
     * </pre>
     */
    setRent(16, 1027),
    /**
     * <code>setSalary = 1028;</code>
     *
     * <pre>
     *c 设置薪水
     * </pre>
     */
    setSalary(17, 1028),
    /**
     * <code>addLine = 1029;</code>
     *
     * <pre>
     *c 增加生产线
     * </pre>
     */
    addLine(18, 1029),
    /**
     * <code>lineChangeInform = 1032;</code>
     *
     * <pre>
     *s 生产线变化推送
     * </pre>
     */
    lineChangeInform(19, 1032),
    /**
     * <code>changeLine = 1033;</code>
     *
     * <pre>
     *c 改变生成线员工数量或目标产量
     * </pre>
     */
    changeLine(20, 1033),
    /**
     * <code>addBuilding = 1050;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    addBuilding(21, 1050),
    /**
     * <code>delBuilding = 1051;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    delBuilding(22, 1051),
    /**
     * <code>construct = 1052;</code>
     *
     * <pre>
     *c 建造
     * </pre>
     */
    construct(23, 1052),
    /**
     * <code>transform = 1053;</code>
     *
     * <pre>
     *c 把虚拟建筑转换为真实建筑
     * </pre>
     */
    transform(24, 1053),
    /**
     * <code>startBusiness = 1054;</code>
     *
     * <pre>
     *c 开业
     * </pre>
     */
    startBusiness(25, 1054),
    /**
     * <code>transferItem = 1055;</code>
     *
     * <pre>
     *c 运输
     * </pre>
     */
    transferItem(26, 1055),
    /**
     * <code>shelfAdd = 1056;</code>
     *
     * <pre>
     *c 上架
     * </pre>
     */
    shelfAdd(27, 1056),
    /**
     * <code>shelfDel = 1057;</code>
     *
     * <pre>
     *c 下架
     * </pre>
     */
    shelfDel(28, 1057),
    /**
     * <code>shelfSet = 1058;</code>
     *
     * <pre>
     *c 改变货架商品数量价格
     * </pre>
     */
    shelfSet(29, 1058),
    /**
     * <code>buyInShelf = 1059;</code>
     *
     * <pre>
     *c 购买货架商品
     * </pre>
     */
    buyInShelf(30, 1059),
    /**
     * <code>exchangeItemList = 1070;</code>
     *
     * <pre>
     *c 交易所主界面
     * </pre>
     */
    exchangeItemList(31, 1070),
    /**
     * <code>exchangeBuy = 1071;</code>
     *
     * <pre>
     *c 挂买单
     * </pre>
     */
    exchangeBuy(32, 1071),
    /**
     * <code>exchangeSell = 1072;</code>
     *
     * <pre>
     *c 挂卖单
     * </pre>
     */
    exchangeSell(33, 1072),
    /**
     * <code>exchangeCancel = 1073;</code>
     *
     * <pre>
     *c 撤单
     * </pre>
     */
    exchangeCancel(34, 1073),
    /**
     * <code>exchangeWatchItemDetail = 1074;</code>
     *
     * <pre>
     *c 交易物品详情
     * </pre>
     */
    exchangeWatchItemDetail(35, 1074),
    /**
     * <code>exchangeItemDetailInform = 1075;</code>
     *
     * <pre>
     *s 详情改变推送
     * </pre>
     */
    exchangeItemDetailInform(36, 1075),
    /**
     * <code>exchangeStopWatchItemDetail = 1076;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    exchangeStopWatchItemDetail(37, 1076),
    /**
     * <code>exchangeDealInform = 1077;</code>
     *
     * <pre>
     *s 成交通知
     * </pre>
     */
    exchangeDealInform(38, 1077),
    /**
     * <code>exchangeMyOrder = 1078;</code>
     *
     * <pre>
     *c 获取自己的挂单
     * </pre>
     */
    exchangeMyOrder(39, 1078),
    /**
     * <code>exchangeMyDealLog = 1079;</code>
     *
     * <pre>
     *c 获取自己的成交历史
     * </pre>
     */
    exchangeMyDealLog(40, 1079),
    /**
     * <code>exchangeAllDealLog = 1080;</code>
     *
     * <pre>
     *c 获取所有的成交历史
     * </pre>
     */
    exchangeAllDealLog(41, 1080),
    /**
     * <code>exchangeCollect = 1081;</code>
     *
     * <pre>
     *c 收藏
     * </pre>
     */
    exchangeCollect(42, 1081),
    /**
     * <code>exchangeUnCollect = 1082;</code>
     *
     * <pre>
     *c 收藏
     * </pre>
     */
    exchangeUnCollect(43, 1082),
    /**
     * <code>exchangeGetItemDealHistory = 1083;</code>
     *
     * <pre>
     *c 获取道具成交历史
     * </pre>
     */
    exchangeGetItemDealHistory(44, 1083),
    /**
     * <code>queryGroundAuction = 1100;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    queryGroundAuction(45, 1100),
    /**
     * <code>bidGround = 1101;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    bidGround(46, 1101),
    /**
     * <code>queryMetaGroundAuction = 1102;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    queryMetaGroundAuction(47, 1102),
    /**
     * <code>registGroundBidInform = 1103;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    registGroundBidInform(48, 1103),
    /**
     * <code>unregistGroundBidInform = 1104;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    unregistGroundBidInform(49, 1104),
    /**
     * <code>bidChangeInform = 1105;</code>
     *
     * <pre>
     *s
     * </pre>
     */
    bidChangeInform(50, 1105),
    /**
     * <code>auctionEnd = 1106;</code>
     *
     * <pre>
     *s  when a auction's end time reached, server send this to inform this auction is dealed
     * </pre>
     */
    auctionEnd(51, 1106),
    /**
     * <code>metaGroundAuctionAddInform = 1107;</code>
     *
     * <pre>
     *s  this inform message might send after client login to game server but not yet do roleLogin
     * </pre>
     */
    metaGroundAuctionAddInform(52, 1107),
    /**
     * <code>bidFailInform = 1108;</code>
     *
     * <pre>
     *s  other player bid a higher price
     * </pre>
     */
    bidFailInform(53, 1108),
    /**
     * <code>bidWinInform = 1109;</code>
     *
     * <pre>
     *s  you win this auction
     * </pre>
     */
    bidWinInform(54, 1109),
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
     * <code>queryPlayerInfo = 1010;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    public static final int queryPlayerInfo_VALUE = 1010;
    /**
     * <code>extendBag = 1015;</code>
     *
     * <pre>
     *c 扩充中心仓库容量
     * </pre>
     */
    public static final int extendBag_VALUE = 1015;
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
     * <code>detailVirtual = 1025;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    public static final int detailVirtual_VALUE = 1025;
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
        case 1010: return queryPlayerInfo;
        case 1015: return extendBag;
        case 1020: return detailApartment;
        case 1021: return detailMaterialFactory;
        case 1022: return detailProduceDepartment;
        case 1023: return detailRetailShop;
        case 1024: return detailLaboratory;
        case 1025: return detailVirtual;
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
      "\n\014gsCode.proto\022\006gscode*\206\t\n\006OpCode\022\n\n\005log" +
      "in\020\350\007\022\016\n\theartBeat\020\351\007\022\016\n\troleLogin\020\352\007\022\017\n" +
      "\ncreateRole\020\353\007\022\t\n\004move\020\354\007\022\017\n\nunitCreate\020" +
      "\355\007\022\017\n\nunitRemove\020\356\007\022\017\n\nunitChange\020\357\007\022\024\n\017" +
      "queryPlayerInfo\020\362\007\022\016\n\textendBag\020\367\007\022\024\n\017de" +
      "tailApartment\020\374\007\022\032\n\025detailMaterialFactor" +
      "y\020\375\007\022\034\n\027detailProduceDepartment\020\376\007\022\025\n\020de" +
      "tailRetailShop\020\377\007\022\025\n\020detailLaboratory\020\200\010" +
      "\022\022\n\rdetailVirtual\020\201\010\022\014\n\007setRent\020\203\010\022\016\n\tse" +
      "tSalary\020\204\010\022\014\n\007addLine\020\205\010\022\025\n\020lineChangeIn",
      "form\020\210\010\022\017\n\nchangeLine\020\211\010\022\020\n\013addBuilding\020" +
      "\232\010\022\020\n\013delBuilding\020\233\010\022\016\n\tconstruct\020\234\010\022\016\n\t" +
      "transform\020\235\010\022\022\n\rstartBusiness\020\236\010\022\021\n\014tran" +
      "sferItem\020\237\010\022\r\n\010shelfAdd\020\240\010\022\r\n\010shelfDel\020\241" +
      "\010\022\r\n\010shelfSet\020\242\010\022\017\n\nbuyInShelf\020\243\010\022\025\n\020exc" +
      "hangeItemList\020\256\010\022\020\n\013exchangeBuy\020\257\010\022\021\n\014ex" +
      "changeSell\020\260\010\022\023\n\016exchangeCancel\020\261\010\022\034\n\027ex" +
      "changeWatchItemDetail\020\262\010\022\035\n\030exchangeItem" +
      "DetailInform\020\263\010\022 \n\033exchangeStopWatchItem" +
      "Detail\020\264\010\022\027\n\022exchangeDealInform\020\265\010\022\024\n\017ex",
      "changeMyOrder\020\266\010\022\026\n\021exchangeMyDealLog\020\267\010" +
      "\022\027\n\022exchangeAllDealLog\020\270\010\022\024\n\017exchangeCol" +
      "lect\020\271\010\022\026\n\021exchangeUnCollect\020\272\010\022\037\n\032excha" +
      "ngeGetItemDealHistory\020\273\010\022\027\n\022queryGroundA" +
      "uction\020\314\010\022\016\n\tbidGround\020\315\010\022\033\n\026queryMetaGr" +
      "oundAuction\020\316\010\022\032\n\025registGroundBidInform\020" +
      "\317\010\022\034\n\027unregistGroundBidInform\020\320\010\022\024\n\017bidC" +
      "hangeInform\020\321\010\022\017\n\nauctionEnd\020\322\010\022\037\n\032metaG" +
      "roundAuctionAddInform\020\323\010\022\022\n\rbidFailInfor" +
      "m\020\324\010\022\021\n\014bidWinInform\020\325\010"
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
