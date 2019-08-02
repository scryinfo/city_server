package Game.Eva;

import org.apache.log4j.Logger;

import javax.persistence.*;
import java.util.UUID;

@Entity(name = "EvaSalary")
@Table(name = "EvaSalary",indexes = {@Index(columnList = "id")})
public class EvaSalary {
    private static final Logger logger = Logger.getLogger(EvaSalary.class);
    @Id
    private UUID id;

    @Column(name = "point")
    private int point=0;

	public EvaSalary(int point) {
		this.id = UUID.randomUUID();
		this.point = point;
	}

	public UUID getId() {
		return id;
	}

	public int getPoint() {
		return point;
	}

	public void addPoit(int point){
		this.point+=point;
	}
}
