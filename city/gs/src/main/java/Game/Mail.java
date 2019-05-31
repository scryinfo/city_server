package Game;

import DB.Db;
import Shared.Util;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.vladmihalcea.hibernate.type.array.IntArrayType;
import com.vladmihalcea.hibernate.type.array.StringArrayType;
import gs.Gs;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.TypeDefs;
import org.hibernate.type.BigIntegerType;

import javax.persistence.*;
import java.util.Arrays;
import java.util.UUID;

@TypeDefs({
        @TypeDef(
                name = "int-array",
                typeClass = IntArrayType.class
        ),
        @TypeDef(
                name = "long-array",
                typeClass = StringArrayType.class
        )
})
@Entity
@Table(name = "Mail", indexes = {@Index(name = "ind_mail_ts", columnList = "ts")})
public class Mail {

    public enum MailType {
        APARTMENT_FULL(1),//住宅已满
        STORE_FULL(2), //仓库已满
        PRODUCTION_LINE_COMPLETION(3),//生产线完成
        LOCKOUT(4),//停工
        PUBLICFACILITY_APPOINTMENT(5),//推广公司预约
        LABORATORY_APPOINTMENT(6),//研究所预约
        PROMOTE_FINISH(7),// 推广完成
        INVENT_FINISH(8),//发明商品完成
        EVA_POINT_FINISH(9),//EVA点数研究完成
        LAND_AUCTION(10),// 土地拍卖
        ADD_FRIEND_SUCCESS(11),//好友通知
        LAND_SALE(12),//土地出售
        LAND_RENT(13),//土地出租
        SOCIETY_KICK_OUT(14),//踢出公会
        ADD_SOCIETY_SUCCESS(15),//同意公会申请
        ADD_SOCIETY_FAIL(16),//拒绝公会申请
        LAND_AUCTION_HIGHER(17);//更高出价


     /*   AD_SPACE_RENT_OUT(6),
        SCIENTIFIC_PAYOFFS(7),
        INVENTIONS(8),
        AD_PROMOTION_EXPIRE(10),
        SHELF_SALE(15),
        APARTMENT_CHECK_IN(16);*/

        private int mailType;

        MailType(int mailType) {
            this.mailType = mailType;
        }

        public int getMailType() {
            return this.mailType;
        }
    }

    public enum ParaType {
        PRODUCE_DEPARTMENT(1),
        MATERIAL_FACTORY(2),
        RETAIL_SHOP(3),
        PUBLIC_FACILITY(4),
        LABORATORY(5),
        APARTMENT(6),
        CENTER_STORE(7),
        TECHNOLOGY_EXCHANGE(8),
        TALENT_CENTER(9),
        TALENT_EXCHANGE_CENTER(10),
        EXCHANGE(11);

        private int paraType;

        ParaType(int paraType) {
            this.paraType = paraType;
        }

        public int getParaType() {
            return this.paraType;
        }
    }

    public Mail(int type, UUID playerId, int[] paras, int[] intParasArr) {
        id = UUID.randomUUID();
        this.playerId = playerId;
        this.type = type;
        this.paras = paras;
        this.intParasArr = intParasArr;
        ts = System.currentTimeMillis();
        read = false;
    }

    public Mail(int type, UUID playerId, int[] paras, UUID[] uuidParas, int[] intParasArr) {
        id = UUID.randomUUID();
        this.playerId = playerId;
        this.type = type;
        this.paras = paras;
        this.uuidParas = uuidParas;
        this.intParasArr = intParasArr;
        ts = System.currentTimeMillis();
        read = false;        //默认值为false,未读
    }
    public Mail(int type, UUID playerId, int[] paras, UUID[] uuidParas, int[] intParasArr,long[] tparas) {
        id = UUID.randomUUID();
        this.playerId = playerId;
        this.type = type;
        this.paras = paras;
        this.uuidParas = uuidParas;
        this.intParasArr = intParasArr;
        this.tparas = tparas;
        ts = System.currentTimeMillis();
        read = false;        //默认值为false,未读
    }

    protected Mail() {
    }

    @Id
    private UUID id;

    private UUID playerId;
    private int type;

    @Type(type = "int-array")
    @Column(
            name = "paras",
            columnDefinition = "int[]"
    )
    private int[] paras;

    @Type(type = "int-array")
    @Column(
            name = "intParasArr",
            columnDefinition = "int[]"
    )
    private int[] intParasArr;

    @Type(type = "long-array")
    @Column(
            name = "tparas",
            columnDefinition = "BIGINT[]"
    )
    private long[] tparas;
//    @ElementCollection(fetch = FetchType.EAGER)
//    @CollectionTable(name = "mail_uuidPara", joinColumns = { @JoinColumn(name = "Mail_id")})
//    private Set<UUID> uuidParas = new HashSet<>();
//    public Set<UUID> getUuidParas() { return uuidParas; }

    @Transient
    private UUID[] uuidParas = new UUID[]{};

    private byte[] uuids;

    @PostLoad
    private void _1() throws InvalidProtocolBufferException {
        Db.BytesArray ba = Db.BytesArray.PARSER.parseFrom(uuids);
        uuidParas = new UUID[ba.getICount()];
        for (int i = 0; i < ba.getICount(); ++i)
            uuidParas[i] = Util.toUuid(ba.getI(i).toByteArray());
    }

    @PrePersist
    @PreUpdate
    private void _2() {
        Db.BytesArray.Builder builder = Db.BytesArray.newBuilder();
        for (UUID uuidPara : uuidParas) {
            builder.addI(Util.toByteString(uuidPara));
        }
        uuids = builder.build().toByteArray();
    }

    private long ts;
    private boolean read;

    public Gs.Mail toProto() {
        Gs.Mail.Builder builder = Gs.Mail.newBuilder();
        builder.setId(Util.toByteString(id));
        builder.setTs(ts);
        builder.setType(type);
        builder.setRead(read);
        if (null != paras && paras.length != 0) {
            for (int p : paras) {
                builder.addParas(p);
            }
        }
        if (null != intParasArr && intParasArr.length != 0) {
            for (int a : intParasArr) {
                builder.addIntParasArr(a);
            }
        }
/*        if ((null != uuids && uuids.length != 0) && (null == uuidParas || uuidParas.length == 0)){
            Db.BytesArray ba = null;
            try {
                ba = Db.BytesArray.PARSER.parseFrom(uuids);
            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }
            uuidParas = new UUID[ba.getICount()];
            for (int i = 0; i < ba.getICount(); ++i)
                uuidParas[i] = Util.toUuid(ba.getI(i).toByteArray());
        }*/
        if (null != uuidParas && uuidParas.length != 0) {
            for (UUID uuidPara : uuidParas) {
                builder.addUuidParas(Util.toByteString(uuidPara));
            }
        }
        if (null != tparas && tparas.length != 0) {
            for (long a : tparas) {
                builder.addTparas(a);
            }
        }
        return builder.build();
    }

}
