// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: common.proto

package common;

public final class Common {
  private Common() {}
  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistry registry) {
  }
  /**
   * Protobuf enum {@code common.OpCode}
   */
  public enum OpCode
      implements com.google.protobuf.ProtocolMessageEnum {
    /**
     * <code>error = 0;</code>
     */
    error(0, 0),
    /**
     * <code>compressed = 1;</code>
     *
     * <pre>
     * preserve to 999 max
     * </pre>
     */
    compressed(1, 1),
    ;

    /**
     * <code>error = 0;</code>
     */
    public static final int error_VALUE = 0;
    /**
     * <code>compressed = 1;</code>
     *
     * <pre>
     * preserve to 999 max
     * </pre>
     */
    public static final int compressed_VALUE = 1;


    public final int getNumber() { return value; }

    public static OpCode valueOf(int value) {
      switch (value) {
        case 0: return error;
        case 1: return compressed;
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
      return common.Common.getDescriptor().getEnumTypes().get(0);
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

    // @@protoc_insertion_point(enum_scope:common.OpCode)
  }

  public interface FailOrBuilder
      extends com.google.protobuf.MessageOrBuilder {

    // required int32 opcode = 1;
    /**
     * <code>required int32 opcode = 1;</code>
     */
    boolean hasOpcode();
    /**
     * <code>required int32 opcode = 1;</code>
     */
    int getOpcode();

    // optional .common.Fail.Reason reason = 2;
    /**
     * <code>optional .common.Fail.Reason reason = 2;</code>
     */
    boolean hasReason();
    /**
     * <code>optional .common.Fail.Reason reason = 2;</code>
     */
    common.Common.Fail.Reason getReason();

    // optional string s = 3;
    /**
     * <code>optional string s = 3;</code>
     */
    boolean hasS();
    /**
     * <code>optional string s = 3;</code>
     */
    java.lang.String getS();
    /**
     * <code>optional string s = 3;</code>
     */
    com.google.protobuf.ByteString
        getSBytes();

    // optional int32 i = 4;
    /**
     * <code>optional int32 i = 4;</code>
     */
    boolean hasI();
    /**
     * <code>optional int32 i = 4;</code>
     */
    int getI();
  }
  /**
   * Protobuf type {@code common.Fail}
   */
  public static final class Fail extends
      com.google.protobuf.GeneratedMessage
      implements FailOrBuilder {
    // Use Fail.newBuilder() to construct.
    private Fail(com.google.protobuf.GeneratedMessage.Builder<?> builder) {
      super(builder);
      this.unknownFields = builder.getUnknownFields();
    }
    private Fail(boolean noInit) { this.unknownFields = com.google.protobuf.UnknownFieldSet.getDefaultInstance(); }

    private static final Fail defaultInstance;
    public static Fail getDefaultInstance() {
      return defaultInstance;
    }

    public Fail getDefaultInstanceForType() {
      return defaultInstance;
    }

    private final com.google.protobuf.UnknownFieldSet unknownFields;
    @java.lang.Override
    public final com.google.protobuf.UnknownFieldSet
        getUnknownFields() {
      return this.unknownFields;
    }
    private Fail(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      initFields();
      int mutable_bitField0_ = 0;
      com.google.protobuf.UnknownFieldSet.Builder unknownFields =
          com.google.protobuf.UnknownFieldSet.newBuilder();
      try {
        boolean done = false;
        while (!done) {
          int tag = input.readTag();
          switch (tag) {
            case 0:
              done = true;
              break;
            default: {
              if (!parseUnknownField(input, unknownFields,
                                     extensionRegistry, tag)) {
                done = true;
              }
              break;
            }
            case 8: {
              bitField0_ |= 0x00000001;
              opcode_ = input.readInt32();
              break;
            }
            case 16: {
              int rawValue = input.readEnum();
              common.Common.Fail.Reason value = common.Common.Fail.Reason.valueOf(rawValue);
              if (value == null) {
                unknownFields.mergeVarintField(2, rawValue);
              } else {
                bitField0_ |= 0x00000002;
                reason_ = value;
              }
              break;
            }
            case 26: {
              bitField0_ |= 0x00000004;
              s_ = input.readBytes();
              break;
            }
            case 32: {
              bitField0_ |= 0x00000008;
              i_ = input.readInt32();
              break;
            }
          }
        }
      } catch (com.google.protobuf.InvalidProtocolBufferException e) {
        throw e.setUnfinishedMessage(this);
      } catch (java.io.IOException e) {
        throw new com.google.protobuf.InvalidProtocolBufferException(
            e.getMessage()).setUnfinishedMessage(this);
      } finally {
        this.unknownFields = unknownFields.build();
        makeExtensionsImmutable();
      }
    }
    public static final com.google.protobuf.Descriptors.Descriptor
        getDescriptor() {
      return common.Common.internal_static_common_Fail_descriptor;
    }

    protected com.google.protobuf.GeneratedMessage.FieldAccessorTable
        internalGetFieldAccessorTable() {
      return common.Common.internal_static_common_Fail_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              common.Common.Fail.class, common.Common.Fail.Builder.class);
    }

    public static com.google.protobuf.Parser<Fail> PARSER =
        new com.google.protobuf.AbstractParser<Fail>() {
      public Fail parsePartialFrom(
          com.google.protobuf.CodedInputStream input,
          com.google.protobuf.ExtensionRegistryLite extensionRegistry)
          throws com.google.protobuf.InvalidProtocolBufferException {
        return new Fail(input, extensionRegistry);
      }
    };

    @java.lang.Override
    public com.google.protobuf.Parser<Fail> getParserForType() {
      return PARSER;
    }

    /**
     * Protobuf enum {@code common.Fail.Reason}
     */
    public enum Reason
        implements com.google.protobuf.ProtocolMessageEnum {
      /**
       * <code>noReason = 0;</code>
       */
      noReason(0, 0),
      /**
       * <code>accountInFreeze = 1;</code>
       */
      accountInFreeze(1, 1),
      /**
       * <code>gameServerNotOnline = 2;</code>
       */
      gameServerNotOnline(2, 2),
      /**
       * <code>roleNameDuplicated = 3;</code>
       */
      roleNameDuplicated(3, 3),
      /**
       * <code>auctionNotFound = 4;</code>
       */
      auctionNotFound(4, 4),
      /**
       * <code>auctionPriceIsLow = 5;</code>
       */
      auctionPriceIsLow(5, 5),
      ;

      /**
       * <code>noReason = 0;</code>
       */
      public static final int noReason_VALUE = 0;
      /**
       * <code>accountInFreeze = 1;</code>
       */
      public static final int accountInFreeze_VALUE = 1;
      /**
       * <code>gameServerNotOnline = 2;</code>
       */
      public static final int gameServerNotOnline_VALUE = 2;
      /**
       * <code>roleNameDuplicated = 3;</code>
       */
      public static final int roleNameDuplicated_VALUE = 3;
      /**
       * <code>auctionNotFound = 4;</code>
       */
      public static final int auctionNotFound_VALUE = 4;
      /**
       * <code>auctionPriceIsLow = 5;</code>
       */
      public static final int auctionPriceIsLow_VALUE = 5;


      public final int getNumber() { return value; }

      public static Reason valueOf(int value) {
        switch (value) {
          case 0: return noReason;
          case 1: return accountInFreeze;
          case 2: return gameServerNotOnline;
          case 3: return roleNameDuplicated;
          case 4: return auctionNotFound;
          case 5: return auctionPriceIsLow;
          default: return null;
        }
      }

      public static com.google.protobuf.Internal.EnumLiteMap<Reason>
          internalGetValueMap() {
        return internalValueMap;
      }
      private static com.google.protobuf.Internal.EnumLiteMap<Reason>
          internalValueMap =
            new com.google.protobuf.Internal.EnumLiteMap<Reason>() {
              public Reason findValueByNumber(int number) {
                return Reason.valueOf(number);
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
        return common.Common.Fail.getDescriptor().getEnumTypes().get(0);
      }

      private static final Reason[] VALUES = values();

      public static Reason valueOf(
          com.google.protobuf.Descriptors.EnumValueDescriptor desc) {
        if (desc.getType() != getDescriptor()) {
          throw new java.lang.IllegalArgumentException(
            "EnumValueDescriptor is not for this type.");
        }
        return VALUES[desc.getIndex()];
      }

      private final int index;
      private final int value;

      private Reason(int index, int value) {
        this.index = index;
        this.value = value;
      }

      // @@protoc_insertion_point(enum_scope:common.Fail.Reason)
    }

    private int bitField0_;
    // required int32 opcode = 1;
    public static final int OPCODE_FIELD_NUMBER = 1;
    private int opcode_;
    /**
     * <code>required int32 opcode = 1;</code>
     */
    public boolean hasOpcode() {
      return ((bitField0_ & 0x00000001) == 0x00000001);
    }
    /**
     * <code>required int32 opcode = 1;</code>
     */
    public int getOpcode() {
      return opcode_;
    }

    // optional .common.Fail.Reason reason = 2;
    public static final int REASON_FIELD_NUMBER = 2;
    private common.Common.Fail.Reason reason_;
    /**
     * <code>optional .common.Fail.Reason reason = 2;</code>
     */
    public boolean hasReason() {
      return ((bitField0_ & 0x00000002) == 0x00000002);
    }
    /**
     * <code>optional .common.Fail.Reason reason = 2;</code>
     */
    public common.Common.Fail.Reason getReason() {
      return reason_;
    }

    // optional string s = 3;
    public static final int S_FIELD_NUMBER = 3;
    private java.lang.Object s_;
    /**
     * <code>optional string s = 3;</code>
     */
    public boolean hasS() {
      return ((bitField0_ & 0x00000004) == 0x00000004);
    }
    /**
     * <code>optional string s = 3;</code>
     */
    public java.lang.String getS() {
      java.lang.Object ref = s_;
      if (ref instanceof java.lang.String) {
        return (java.lang.String) ref;
      } else {
        com.google.protobuf.ByteString bs = 
            (com.google.protobuf.ByteString) ref;
        java.lang.String s = bs.toStringUtf8();
        if (bs.isValidUtf8()) {
          s_ = s;
        }
        return s;
      }
    }
    /**
     * <code>optional string s = 3;</code>
     */
    public com.google.protobuf.ByteString
        getSBytes() {
      java.lang.Object ref = s_;
      if (ref instanceof java.lang.String) {
        com.google.protobuf.ByteString b = 
            com.google.protobuf.ByteString.copyFromUtf8(
                (java.lang.String) ref);
        s_ = b;
        return b;
      } else {
        return (com.google.protobuf.ByteString) ref;
      }
    }

    // optional int32 i = 4;
    public static final int I_FIELD_NUMBER = 4;
    private int i_;
    /**
     * <code>optional int32 i = 4;</code>
     */
    public boolean hasI() {
      return ((bitField0_ & 0x00000008) == 0x00000008);
    }
    /**
     * <code>optional int32 i = 4;</code>
     */
    public int getI() {
      return i_;
    }

    private void initFields() {
      opcode_ = 0;
      reason_ = common.Common.Fail.Reason.noReason;
      s_ = "";
      i_ = 0;
    }
    private byte memoizedIsInitialized = -1;
    public final boolean isInitialized() {
      byte isInitialized = memoizedIsInitialized;
      if (isInitialized != -1) return isInitialized == 1;

      if (!hasOpcode()) {
        memoizedIsInitialized = 0;
        return false;
      }
      memoizedIsInitialized = 1;
      return true;
    }

    public void writeTo(com.google.protobuf.CodedOutputStream output)
                        throws java.io.IOException {
      getSerializedSize();
      if (((bitField0_ & 0x00000001) == 0x00000001)) {
        output.writeInt32(1, opcode_);
      }
      if (((bitField0_ & 0x00000002) == 0x00000002)) {
        output.writeEnum(2, reason_.getNumber());
      }
      if (((bitField0_ & 0x00000004) == 0x00000004)) {
        output.writeBytes(3, getSBytes());
      }
      if (((bitField0_ & 0x00000008) == 0x00000008)) {
        output.writeInt32(4, i_);
      }
      getUnknownFields().writeTo(output);
    }

    private int memoizedSerializedSize = -1;
    public int getSerializedSize() {
      int size = memoizedSerializedSize;
      if (size != -1) return size;

      size = 0;
      if (((bitField0_ & 0x00000001) == 0x00000001)) {
        size += com.google.protobuf.CodedOutputStream
          .computeInt32Size(1, opcode_);
      }
      if (((bitField0_ & 0x00000002) == 0x00000002)) {
        size += com.google.protobuf.CodedOutputStream
          .computeEnumSize(2, reason_.getNumber());
      }
      if (((bitField0_ & 0x00000004) == 0x00000004)) {
        size += com.google.protobuf.CodedOutputStream
          .computeBytesSize(3, getSBytes());
      }
      if (((bitField0_ & 0x00000008) == 0x00000008)) {
        size += com.google.protobuf.CodedOutputStream
          .computeInt32Size(4, i_);
      }
      size += getUnknownFields().getSerializedSize();
      memoizedSerializedSize = size;
      return size;
    }

    private static final long serialVersionUID = 0L;
    @java.lang.Override
    protected java.lang.Object writeReplace()
        throws java.io.ObjectStreamException {
      return super.writeReplace();
    }

    public static common.Common.Fail parseFrom(
        com.google.protobuf.ByteString data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data);
    }
    public static common.Common.Fail parseFrom(
        com.google.protobuf.ByteString data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data, extensionRegistry);
    }
    public static common.Common.Fail parseFrom(byte[] data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data);
    }
    public static common.Common.Fail parseFrom(
        byte[] data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data, extensionRegistry);
    }
    public static common.Common.Fail parseFrom(java.io.InputStream input)
        throws java.io.IOException {
      return PARSER.parseFrom(input);
    }
    public static common.Common.Fail parseFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return PARSER.parseFrom(input, extensionRegistry);
    }
    public static common.Common.Fail parseDelimitedFrom(java.io.InputStream input)
        throws java.io.IOException {
      return PARSER.parseDelimitedFrom(input);
    }
    public static common.Common.Fail parseDelimitedFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return PARSER.parseDelimitedFrom(input, extensionRegistry);
    }
    public static common.Common.Fail parseFrom(
        com.google.protobuf.CodedInputStream input)
        throws java.io.IOException {
      return PARSER.parseFrom(input);
    }
    public static common.Common.Fail parseFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return PARSER.parseFrom(input, extensionRegistry);
    }

    public static Builder newBuilder() { return Builder.create(); }
    public Builder newBuilderForType() { return newBuilder(); }
    public static Builder newBuilder(common.Common.Fail prototype) {
      return newBuilder().mergeFrom(prototype);
    }
    public Builder toBuilder() { return newBuilder(this); }

    @java.lang.Override
    protected Builder newBuilderForType(
        com.google.protobuf.GeneratedMessage.BuilderParent parent) {
      Builder builder = new Builder(parent);
      return builder;
    }
    /**
     * Protobuf type {@code common.Fail}
     */
    public static final class Builder extends
        com.google.protobuf.GeneratedMessage.Builder<Builder>
       implements common.Common.FailOrBuilder {
      public static final com.google.protobuf.Descriptors.Descriptor
          getDescriptor() {
        return common.Common.internal_static_common_Fail_descriptor;
      }

      protected com.google.protobuf.GeneratedMessage.FieldAccessorTable
          internalGetFieldAccessorTable() {
        return common.Common.internal_static_common_Fail_fieldAccessorTable
            .ensureFieldAccessorsInitialized(
                common.Common.Fail.class, common.Common.Fail.Builder.class);
      }

      // Construct using common.Common.Fail.newBuilder()
      private Builder() {
        maybeForceBuilderInitialization();
      }

      private Builder(
          com.google.protobuf.GeneratedMessage.BuilderParent parent) {
        super(parent);
        maybeForceBuilderInitialization();
      }
      private void maybeForceBuilderInitialization() {
        if (com.google.protobuf.GeneratedMessage.alwaysUseFieldBuilders) {
        }
      }
      private static Builder create() {
        return new Builder();
      }

      public Builder clear() {
        super.clear();
        opcode_ = 0;
        bitField0_ = (bitField0_ & ~0x00000001);
        reason_ = common.Common.Fail.Reason.noReason;
        bitField0_ = (bitField0_ & ~0x00000002);
        s_ = "";
        bitField0_ = (bitField0_ & ~0x00000004);
        i_ = 0;
        bitField0_ = (bitField0_ & ~0x00000008);
        return this;
      }

      public Builder clone() {
        return create().mergeFrom(buildPartial());
      }

      public com.google.protobuf.Descriptors.Descriptor
          getDescriptorForType() {
        return common.Common.internal_static_common_Fail_descriptor;
      }

      public common.Common.Fail getDefaultInstanceForType() {
        return common.Common.Fail.getDefaultInstance();
      }

      public common.Common.Fail build() {
        common.Common.Fail result = buildPartial();
        if (!result.isInitialized()) {
          throw newUninitializedMessageException(result);
        }
        return result;
      }

      public common.Common.Fail buildPartial() {
        common.Common.Fail result = new common.Common.Fail(this);
        int from_bitField0_ = bitField0_;
        int to_bitField0_ = 0;
        if (((from_bitField0_ & 0x00000001) == 0x00000001)) {
          to_bitField0_ |= 0x00000001;
        }
        result.opcode_ = opcode_;
        if (((from_bitField0_ & 0x00000002) == 0x00000002)) {
          to_bitField0_ |= 0x00000002;
        }
        result.reason_ = reason_;
        if (((from_bitField0_ & 0x00000004) == 0x00000004)) {
          to_bitField0_ |= 0x00000004;
        }
        result.s_ = s_;
        if (((from_bitField0_ & 0x00000008) == 0x00000008)) {
          to_bitField0_ |= 0x00000008;
        }
        result.i_ = i_;
        result.bitField0_ = to_bitField0_;
        onBuilt();
        return result;
      }

      public Builder mergeFrom(com.google.protobuf.Message other) {
        if (other instanceof common.Common.Fail) {
          return mergeFrom((common.Common.Fail)other);
        } else {
          super.mergeFrom(other);
          return this;
        }
      }

      public Builder mergeFrom(common.Common.Fail other) {
        if (other == common.Common.Fail.getDefaultInstance()) return this;
        if (other.hasOpcode()) {
          setOpcode(other.getOpcode());
        }
        if (other.hasReason()) {
          setReason(other.getReason());
        }
        if (other.hasS()) {
          bitField0_ |= 0x00000004;
          s_ = other.s_;
          onChanged();
        }
        if (other.hasI()) {
          setI(other.getI());
        }
        this.mergeUnknownFields(other.getUnknownFields());
        return this;
      }

      public final boolean isInitialized() {
        if (!hasOpcode()) {
          
          return false;
        }
        return true;
      }

      public Builder mergeFrom(
          com.google.protobuf.CodedInputStream input,
          com.google.protobuf.ExtensionRegistryLite extensionRegistry)
          throws java.io.IOException {
        common.Common.Fail parsedMessage = null;
        try {
          parsedMessage = PARSER.parsePartialFrom(input, extensionRegistry);
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
          parsedMessage = (common.Common.Fail) e.getUnfinishedMessage();
          throw e;
        } finally {
          if (parsedMessage != null) {
            mergeFrom(parsedMessage);
          }
        }
        return this;
      }
      private int bitField0_;

      // required int32 opcode = 1;
      private int opcode_ ;
      /**
       * <code>required int32 opcode = 1;</code>
       */
      public boolean hasOpcode() {
        return ((bitField0_ & 0x00000001) == 0x00000001);
      }
      /**
       * <code>required int32 opcode = 1;</code>
       */
      public int getOpcode() {
        return opcode_;
      }
      /**
       * <code>required int32 opcode = 1;</code>
       */
      public Builder setOpcode(int value) {
        bitField0_ |= 0x00000001;
        opcode_ = value;
        onChanged();
        return this;
      }
      /**
       * <code>required int32 opcode = 1;</code>
       */
      public Builder clearOpcode() {
        bitField0_ = (bitField0_ & ~0x00000001);
        opcode_ = 0;
        onChanged();
        return this;
      }

      // optional .common.Fail.Reason reason = 2;
      private common.Common.Fail.Reason reason_ = common.Common.Fail.Reason.noReason;
      /**
       * <code>optional .common.Fail.Reason reason = 2;</code>
       */
      public boolean hasReason() {
        return ((bitField0_ & 0x00000002) == 0x00000002);
      }
      /**
       * <code>optional .common.Fail.Reason reason = 2;</code>
       */
      public common.Common.Fail.Reason getReason() {
        return reason_;
      }
      /**
       * <code>optional .common.Fail.Reason reason = 2;</code>
       */
      public Builder setReason(common.Common.Fail.Reason value) {
        if (value == null) {
          throw new NullPointerException();
        }
        bitField0_ |= 0x00000002;
        reason_ = value;
        onChanged();
        return this;
      }
      /**
       * <code>optional .common.Fail.Reason reason = 2;</code>
       */
      public Builder clearReason() {
        bitField0_ = (bitField0_ & ~0x00000002);
        reason_ = common.Common.Fail.Reason.noReason;
        onChanged();
        return this;
      }

      // optional string s = 3;
      private java.lang.Object s_ = "";
      /**
       * <code>optional string s = 3;</code>
       */
      public boolean hasS() {
        return ((bitField0_ & 0x00000004) == 0x00000004);
      }
      /**
       * <code>optional string s = 3;</code>
       */
      public java.lang.String getS() {
        java.lang.Object ref = s_;
        if (!(ref instanceof java.lang.String)) {
          java.lang.String s = ((com.google.protobuf.ByteString) ref)
              .toStringUtf8();
          s_ = s;
          return s;
        } else {
          return (java.lang.String) ref;
        }
      }
      /**
       * <code>optional string s = 3;</code>
       */
      public com.google.protobuf.ByteString
          getSBytes() {
        java.lang.Object ref = s_;
        if (ref instanceof String) {
          com.google.protobuf.ByteString b = 
              com.google.protobuf.ByteString.copyFromUtf8(
                  (java.lang.String) ref);
          s_ = b;
          return b;
        } else {
          return (com.google.protobuf.ByteString) ref;
        }
      }
      /**
       * <code>optional string s = 3;</code>
       */
      public Builder setS(
          java.lang.String value) {
        if (value == null) {
    throw new NullPointerException();
  }
  bitField0_ |= 0x00000004;
        s_ = value;
        onChanged();
        return this;
      }
      /**
       * <code>optional string s = 3;</code>
       */
      public Builder clearS() {
        bitField0_ = (bitField0_ & ~0x00000004);
        s_ = getDefaultInstance().getS();
        onChanged();
        return this;
      }
      /**
       * <code>optional string s = 3;</code>
       */
      public Builder setSBytes(
          com.google.protobuf.ByteString value) {
        if (value == null) {
    throw new NullPointerException();
  }
  bitField0_ |= 0x00000004;
        s_ = value;
        onChanged();
        return this;
      }

      // optional int32 i = 4;
      private int i_ ;
      /**
       * <code>optional int32 i = 4;</code>
       */
      public boolean hasI() {
        return ((bitField0_ & 0x00000008) == 0x00000008);
      }
      /**
       * <code>optional int32 i = 4;</code>
       */
      public int getI() {
        return i_;
      }
      /**
       * <code>optional int32 i = 4;</code>
       */
      public Builder setI(int value) {
        bitField0_ |= 0x00000008;
        i_ = value;
        onChanged();
        return this;
      }
      /**
       * <code>optional int32 i = 4;</code>
       */
      public Builder clearI() {
        bitField0_ = (bitField0_ & ~0x00000008);
        i_ = 0;
        onChanged();
        return this;
      }

      // @@protoc_insertion_point(builder_scope:common.Fail)
    }

    static {
      defaultInstance = new Fail(true);
      defaultInstance.initFields();
    }

    // @@protoc_insertion_point(class_scope:common.Fail)
  }

  private static com.google.protobuf.Descriptors.Descriptor
    internal_static_common_Fail_descriptor;
  private static
    com.google.protobuf.GeneratedMessage.FieldAccessorTable
      internal_static_common_Fail_fieldAccessorTable;

  public static com.google.protobuf.Descriptors.FileDescriptor
      getDescriptor() {
    return descriptor;
  }
  private static com.google.protobuf.Descriptors.FileDescriptor
      descriptor;
  static {
    java.lang.String[] descriptorData = {
      "\n\014common.proto\022\006common\"\334\001\n\004Fail\022\016\n\006opcod" +
      "e\030\001 \002(\005\022#\n\006reason\030\002 \001(\0162\023.common.Fail.Re" +
      "ason\022\t\n\001s\030\003 \001(\t\022\t\n\001i\030\004 \001(\005\"\210\001\n\006Reason\022\014\n" +
      "\010noReason\020\000\022\023\n\017accountInFreeze\020\001\022\027\n\023game" +
      "ServerNotOnline\020\002\022\026\n\022roleNameDuplicated\020" +
      "\003\022\023\n\017auctionNotFound\020\004\022\025\n\021auctionPriceIs" +
      "Low\020\005*#\n\006OpCode\022\t\n\005error\020\000\022\016\n\ncompressed" +
      "\020\001"
    };
    com.google.protobuf.Descriptors.FileDescriptor.InternalDescriptorAssigner assigner =
      new com.google.protobuf.Descriptors.FileDescriptor.InternalDescriptorAssigner() {
        public com.google.protobuf.ExtensionRegistry assignDescriptors(
            com.google.protobuf.Descriptors.FileDescriptor root) {
          descriptor = root;
          internal_static_common_Fail_descriptor =
            getDescriptor().getMessageTypes().get(0);
          internal_static_common_Fail_fieldAccessorTable = new
            com.google.protobuf.GeneratedMessage.FieldAccessorTable(
              internal_static_common_Fail_descriptor,
              new java.lang.String[] { "Opcode", "Reason", "S", "I", });
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
