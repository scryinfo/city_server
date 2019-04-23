package Game.League;

import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
public class BrandLeague
{
	   @Id
	   private UUID id;
	   
	   @Column(name = "buildingId")
       private UUID buildingId;
	    
	   @Column(name = "playerId")
	   private UUID playerId;
	    
	   @Column(name = "techId")
	   private int techId;

	   public BrandLeague() {
			super();
	   }
		
	   public BrandLeague(UUID buildingId, UUID playerId, int techId) {
			super();
			this.id = UUID.randomUUID();
			this.buildingId = buildingId;
			this.playerId = playerId;
			this.techId = techId;
	   }
	   
	   
}



