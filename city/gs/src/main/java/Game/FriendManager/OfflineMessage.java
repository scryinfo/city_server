package Game.FriendManager;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.util.UUID;

@Entity(name = "offline_message")
public class OfflineMessage
{
    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID from_id;
    @Column(nullable = false)
    private UUID to_id;
    @Column(nullable = false)
    private String msg;

    @Column(nullable = false)
    private String from_name;
    @Column
    private long time;
    @Column
    private int image;

    public OfflineMessage() {}

    public OfflineMessage(UUID from_id, UUID to_id, String msg, String from_name)
    {
        this.id = UUID.randomUUID();
        this.from_id = from_id;
        this.to_id = to_id;
        this.msg = msg;
        this.from_name = from_name;
    }

    public void setTime(long time)
    {
        this.time = time;
    }

    public void setImage(int image)
    {
        this.image = image;
    }

    public UUID getFrom_id()
    {
        return from_id;
    }

    public UUID getTo_id()
    {
        return to_id;
    }

    public String getMsg()
    {
        return msg;
    }

    public String getFrom_name()
    {
        return from_name;
    }

    public long getTime()
    {
        return time;
    }

    public int getImage()
    {
        return image;
    }
}
