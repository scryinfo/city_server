package Game.League;

import java.io.Serializable;
import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
public class BrandLeague implements Serializable
{
	   @Id
	   @Column(name = "buildingId")
       private UUID buildingId;
	   
	   @Id
	   @Column(name = "playerId")
	   private UUID playerId;
	   
	   @Id
	   @Column(name = "techId")
	   private int techId;

	   public BrandLeague() {
			super();
	   }
		
	   public BrandLeague(UUID buildingId, UUID playerId, int techId) {
			super();
			this.buildingId = buildingId;
			this.playerId = playerId;
			this.techId = techId;
	   }
	   
	   
}



