package Game;

import Shared.Util;
import com.vladmihalcea.hibernate.type.array.StringArrayType;
import gs.Gs;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.TypeDefs;

import javax.persistence.*;
import java.util.UUID;

@TypeDefs({
        @TypeDef(
                name = "string-array",
                typeClass = StringArrayType.class
        )
})
@Entity
@Table(name = "Mail",indexes={@Index(name="ind_mail_ts",columnList="ts")})
public class Mail {

    public enum MailType {
        EXCHANGE_GOODS_SOLD(1),
        STORE_FULL(2),
        PRODUC_LINE_COMPLETION(3),
        EMPLOYEE_SATISFACTION(4),
        LOCKOUT(5),
        AD_SPACE_RENT_OUT(6),
        SCIENTIFIC_PAYOFFS(7),
        INVENTIONS(8),
        LAND_AUCTION(9),
        LAND_LEASE(10),
        LAND_AUCTION_HIGHERPRICE(11),
        FINANCE(12),
        ADD_FRIEND_FAIL(13),
        ADD_FRIEND_REFUSED(14),
        LAND_TRANSACTION(15),
        AD_SPACE_EXPIRE(16),
        RETAIL_SHOP_MERCHANDISE(17),
        PARK_TICKET_REVENUE(18);

        private int mailType;

        MailType(int mailType) {
            this.mailType = mailType;
        }

        public int getMailType() {
            return this.mailType;
        }
    }

    public enum ParaType {
        PRODUCE_DEPARTMENT("ProduceDepartment"),
        MATERIAL_FACTORY("MaterialFactory"),
        RETAIL_SHOP("RetailShop"),
        PUBLIC_FACILITY("PublicFacility"),
        LABORATORY("Laboratory"),
        APARTMENT("Apartment");

        private String paraType;

        ParaType(String paraType) {
            this.paraType = paraType;
        }

        public String getParaType() {
            return this.paraType;
        }
    }

    public Mail(int type, UUID playerId, String[] paras) {
        id = UUID.randomUUID();
        this.playerId = playerId;
        this.type = type;
        this.paras = paras;
        ts = System.currentTimeMillis();
        read = false;        //默认值为false,未读
    }

    public Mail(int type, UUID playerId) {
        id = UUID.randomUUID();
        this.playerId = playerId;
        this.type = type;
        ts = System.currentTimeMillis();
        read = false;
    }

    protected Mail() {
    }

    @Id
    private UUID id;

    private UUID playerId;
    private int type;

    @Type(type = "string-array")
    @Column(
            name = "paras",
            columnDefinition = "text[]"
    )
    private String[] paras;

    private long ts;
    private boolean read;

    public Gs.Mail toProto() {
        Gs.Mail.Builder builder = Gs.Mail.newBuilder();
        builder.setId(Util.toByteString(id));
        builder.setTs(ts);
        builder.setType(type);
        builder.setRead(read);
        if (null != paras && paras.length != 0) {
            for (String p : paras) {
                builder.addParas(p);
            }
        }
        return builder.build();
    }

}
