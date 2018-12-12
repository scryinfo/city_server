package Game.FriendManager;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.util.UUID;

@Entity(name = "friend_request")
public class FriendRequest
{
    @Id
    private UUID id;
    @Column(name = "from_id" ,nullable = false)
    private UUID from_id;

    @Column(name = "to_id",nullable = false)
    private UUID to_id;

    @Column(nullable = false)
    private int count;

    @Column
    private String descp;

    public FriendRequest() { }

    public FriendRequest(UUID from_id, UUID to_id, String desc)
    {
        this.descp = desc;
        this.id = UUID.randomUUID();
        this.from_id = from_id;
        this.to_id = to_id;
        this.count = 1;
    }

    public UUID getFrom_id()
    {
        return from_id;
    }

    public UUID getTo_id()
    {
        return to_id;
    }

    public int getCount()
    {
        return count;
    }

    public void setCount(int count)
    {
        this.count = count;
    }

    public String getDescp()
    {
        return descp;
    }
}
