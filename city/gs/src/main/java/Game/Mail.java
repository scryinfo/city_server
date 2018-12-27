package Game;

import Shared.Util;
import com.vladmihalcea.hibernate.type.array.StringArrayType;
import gs.Gs;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.TypeDefs;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;
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
        ADD_FRIEND_SUCCESS(11),
        LAND_TRANSACTION(12),
        AD_SPACE_EXPIRE(13),
        RETAIL_SHOP_MERCHANDISE(14),
        PARK_TICKET_REVENUE(15);

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

/*
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "mail_uuidPara", joinColumns = { @JoinColumn(name = "Mail_id", referencedColumnName = "id")})
    private Set<UUID> uuidParas = new HashSet<>();
    public Set<UUID> getUuidParas() { return uuidParas; }
*/

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
