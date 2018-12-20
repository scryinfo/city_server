package Game;

import Shared.Util;
import com.vladmihalcea.hibernate.type.array.IntArrayType;
import gs.Gs;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.TypeDefs;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.util.UUID;

@TypeDefs({
        @TypeDef(
                name = "int-array",
                typeClass = IntArrayType.class
        )
})
@Entity
public class Mail {
    public Mail(int type, UUID playerId, int[] paras) {
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
    protected Mail(){}

    @Id
    private UUID id;

    private UUID playerId;
    private int type;

    @Type( type = "int-array" )
    @Column(
            name = "paras",
            columnDefinition = "integer[]"
    )
    private int[] paras;
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
        return builder.build();
    }

}
