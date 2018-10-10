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
     * <code>detailApartment = 1020;</code>
     *
     * <pre>
     *c 建筑物详情界面关闭也必须发送此消息！
     * </pre>
     */
    detailApartment(8, 1020),
    /**
     * <code>detailMaterialFactory = 1021;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    detailMaterialFactory(9, 1021),
    /**
     * <code>detailProductingDepartment = 1022;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    detailProductingDepartment(10, 1022),
    /**
     * <code>detailRetailShop = 1023;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    detailRetailShop(11, 1023),
    /**
     * <code>detailLaboratory = 1024;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    detailLaboratory(12, 1024),
    /**
     * <code>closeDetail = 1025;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    closeDetail(13, 1025),
    /**
     * <code>setRent = 1027;</code>
     *
     * <pre>
     *c 设置房租
     * </pre>
     */
    setRent(14, 1027),
    /**
     * <code>setSalary = 1028;</code>
     *
     * <pre>
     *c 设置薪水
     * </pre>
     */
    setSalary(15, 1028),
    /**
     * <code>addLine = 1029;</code>
     *
     * <pre>
     *c 增加加工厂生产线
     * </pre>
     */
    addLine(16, 1029),
    /**
     * <code>lineChangeInform = 1032;</code>
     *
     * <pre>
     *s 生产线变化推送
     * </pre>
     */
    lineChangeInform(17, 1032),
    /**
     * <code>changeLine = 1033;</code>
     *
     * <pre>
     *c 改变生成线员工数量或目标产量
     * </pre>
     */
    changeLine(18, 1033),
    /**
     * <code>addBuilding = 1050;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    addBuilding(19, 1050),
    /**
     * <code>delBuilding = 1051;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    delBuilding(20, 1051),
    /**
     * <code>queryGroundAuction = 1100;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    queryGroundAuction(21, 1100),
    /**
     * <code>bidGround = 1101;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    bidGround(22, 1101),
    /**
     * <code>queryMetaGroundAuction = 1102;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    queryMetaGroundAuction(23, 1102),
    /**
     * <code>registGroundBidInform = 1103;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    registGroundBidInform(24, 1103),
    /**
     * <code>unregistGroundBidInform = 1104;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    unregistGroundBidInform(25, 1104),
    /**
     * <code>bidChangeInform = 1105;</code>
     *
     * <pre>
     *s
     * </pre>
     */
    bidChangeInform(26, 1105),
    /**
     * <code>auctionEnd = 1106;</code>
     *
     * <pre>
     *s  when a auction's end time reached, server send this to inform this auction is dealed
     * </pre>
     */
    auctionEnd(27, 1106),
    /**
     * <code>metaGroundAuctionAddInform = 1107;</code>
     *
     * <pre>
     *s  this inform message might send after client login to game server but not yet do roleLogin
     * </pre>
     */
    metaGroundAuctionAddInform(28, 1107),
    /**
     * <code>bidFailInform = 1108;</code>
     *
     * <pre>
     *s  other player bid a higher price
     * </pre>
     */
    bidFailInform(29, 1108),
    /**
     * <code>bidWinInform = 1109;</code>
     *
     * <pre>
     *s  you win this auction
     * </pre>
     */
    bidWinInform(30, 1109),
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
     * <code>detailProductingDepartment = 1022;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    public static final int detailProductingDepartment_VALUE = 1022;
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
     * <code>closeDetail = 1025;</code>
     *
     * <pre>
     *c
     * </pre>
     */
    public static final int closeDetail_VALUE = 1025;
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
     *c 增加加工厂生产线
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
        case 1020: return detailApartment;
        case 1021: return detailMaterialFactory;
        case 1022: return detailProductingDepartment;
        case 1023: return detailRetailShop;
        case 1024: return detailLaboratory;
        case 1025: return closeDetail;
        case 1027: return setRent;
        case 1028: return setSalary;
        case 1029: return addLine;
        case 1032: return lineChangeInform;
        case 1033: return changeLine;
        case 1050: return addBuilding;
        case 1051: return delBuilding;
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
      "\n\014gsCode.proto\022\006gscode*\375\004\n\006OpCode\022\n\n\005log" +
      "in\020\350\007\022\016\n\theartBeat\020\351\007\022\016\n\troleLogin\020\352\007\022\017\n" +
      "\ncreateRole\020\353\007\022\t\n\004move\020\354\007\022\017\n\nunitCreate\020" +
      "\355\007\022\017\n\nunitRemove\020\356\007\022\017\n\nunitChange\020\357\007\022\024\n\017" +
      "detailApartment\020\374\007\022\032\n\025detailMaterialFact" +
      "ory\020\375\007\022\037\n\032detailProductingDepartment\020\376\007\022" +
      "\025\n\020detailRetailShop\020\377\007\022\025\n\020detailLaborato" +
      "ry\020\200\010\022\020\n\013closeDetail\020\201\010\022\014\n\007setRent\020\203\010\022\016\n" +
      "\tsetSalary\020\204\010\022\014\n\007addLine\020\205\010\022\025\n\020lineChang" +
      "eInform\020\210\010\022\017\n\nchangeLine\020\211\010\022\020\n\013addBuildi",
      "ng\020\232\010\022\020\n\013delBuilding\020\233\010\022\027\n\022queryGroundAu" +
      "ction\020\314\010\022\016\n\tbidGround\020\315\010\022\033\n\026queryMetaGro" +
      "undAuction\020\316\010\022\032\n\025registGroundBidInform\020\317" +
      "\010\022\034\n\027unregistGroundBidInform\020\320\010\022\024\n\017bidCh" +
      "angeInform\020\321\010\022\017\n\nauctionEnd\020\322\010\022\037\n\032metaGr" +
      "oundAuctionAddInform\020\323\010\022\022\n\rbidFailInform" +
      "\020\324\010\022\021\n\014bidWinInform\020\325\010"
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
