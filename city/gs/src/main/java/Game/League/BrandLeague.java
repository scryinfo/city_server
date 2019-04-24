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
   @Column(name = "techId")
   private int techId;
   
   @Column(name = "playerId")
   private UUID playerId;

   public BrandLeague() {
		super();
   }
	
   public BrandLeague(UUID buildingId,int techId, UUID playerId) {
		super();
		this.buildingId = buildingId;
		this.techId = techId;
		this.playerId = playerId;
   }

   public UUID getBuildingId() {
		return buildingId;
   }

   public void setBuildingId(UUID buildingId) {
		this.buildingId = buildingId;
   }

   public int getTechId() {
		return techId;
   }

   public void setTechId(int techId) {
		this.techId = techId;
   }
   
   public UUID getPlayerId() {
		return playerId;
   }

   public void setPlayerId(UUID playerId) {
		this.playerId = playerId;
   }
}



