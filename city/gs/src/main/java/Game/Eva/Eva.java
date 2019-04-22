package Game.Eva;

import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;

import org.apache.log4j.Logger;

import Shared.Util;
import gs.Gs;

@Entity(name = "Eva")
@Table(name = "Eva",indexes = {@Index(columnList = "pid")})
public class Eva {
    private static final Logger logger = Logger.getLogger(Eva.class);
    @Id
    private UUID id;
    
    @Column(name = "pid")
    private UUID pid;
    
    @Column(name = "at")
    private int at;
    
    @Column(name = "bt")
    private int bt;
    
    @Column(name = "lv")
    private int lv;
    
    @Column(name = "cexp")
    private long cexp;
    
    @Column(name = "b")
    private long b;
    
    @Column
    private UUID techPlayId;
    
	public Eva() {
		super();
	}

	public Eva(UUID pid, int at, int bt, int lv, long cexp, long b) {
		super();
	    this.id = UUID.randomUUID();
		this.pid = pid;
		this.at = at;
		this.bt = bt;
		this.lv = lv;
		this.cexp = cexp;
		this.b = b;
	}

    public Gs.Eva toProto() {
        Gs.Eva.Builder builder = Gs.Eva.newBuilder();
        builder.setId(Util.toByteString(id))
				.setPid(Util.toByteString(pid))
				.setAt(at)
				.setBt(Gs.Eva.Btype.valueOf(bt)) 
				.setLv(lv)
				.setCexp(cexp)
				.setB(b)
				.setTechPlayId(Util.toByteString(techPlayId));
        return builder.build();
    }
    
	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public UUID getPid() {
		return pid;
	}

	public void setPid(UUID pid) {
		this.pid = pid;
	}

	public int getAt() {
		return at;
	}

	public void setAt(int at) {
		this.at = at;
	}

	public int getBt() {
		return bt;
	}

	public void setBt(int bt) {
		this.bt = bt;
	}

	public int getLv() {
		return lv;
	}

	public void setLv(int lv) {
		this.lv = lv;
	}

	public long getCexp() {
		return cexp;
	}

	public void setCexp(long cexp) {
		this.cexp = cexp;
	}

	public long getB() {
		return b;
	}

	public void setB(long b) {
		this.b = b;
	}
	
	public void setTechPlayId(UUID techPlayId) {
		this.techPlayId = techPlayId;
	}

	public UUID getTechPlayId() {
		return techPlayId;
	}
}
