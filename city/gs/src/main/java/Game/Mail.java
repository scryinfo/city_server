package Game;

import DB.Db;
import Shared.Util;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.vladmihalcea.hibernate.type.array.IntArrayType;
import gs.Gs;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.TypeDefs;

import javax.persistence.*;
import java.util.Arrays;
import java.util.UUID;

@TypeDefs({
        @TypeDef(
                name = "int-array",
                typeClass = IntArrayType.class
        )
})
@Entity
@Table(name = "Mail", indexes = {@Index(name = "ind_mail_ts", columnList = "ts")})
public class Mail {

    public enum MailType {
        EXCHANGE_GOODS_SOLD(1),
        STORE_FULL(2),
        PRODUCTION_LINE_COMPLETION(3),
        EMPLOYEE_SATISFACTION(4),
        LOCKOUT(5),
        AD_SPACE_RENT_OUT(6),
        SCIENTIFIC_PAYOFFS(7),
        INVENTIONS(8),
        LAND_AUCTION(9),
        AD_SPACE_EXPIRE(10),
        LAND_AUCTION_HIGHER(11),
        ADD_FRIEND_SUCCESS(12),
        LAND_SALE(13),
        LAND_RENT(14),
        SHELF_SALE(15),
        APARTMENT_CHECK_IN(16);

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

        return builder.build();
    }

}
