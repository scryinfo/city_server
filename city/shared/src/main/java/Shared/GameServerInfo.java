package Shared;

import org.bson.Document;

import java.time.Instant;

public class GameServerInfo {
    private int id;
    private String name;
    private String ip;
    private int port;
    private String gameDbUrl;
    private String logDbUri;
    private String metaDbUri;
    private Instant maintainEndTime;
    private Instant createTime;
    private String ssIp;
    private int ssPort;
    private String logDbName;

    public String getLogDbName()
    {
        return logDbName;
    }

    public String getSsIp()
    {
        return ssIp;
    }

    public int getSsPort()
    {
        return ssPort;
    }

    public String getLogDbUri() {
        return logDbUri;
    }

    public String getMetaDbUri() {
        return metaDbUri;
    }

   public Instant getMaintainEndTime() {
        return maintainEndTime;
    }

    public Instant getCreateTime() {
        return createTime;
    }

   public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

//    public GameServerInfo(int id, String name, String ip, short port) {
//        this.id = id;
//        this.name = name;
//        this.ip = ip;
//        this.port = port;
//    }
    public GameServerInfo(Document doc) {
        this.id = doc.getInteger("_id");
        this.name = doc.getString("name");
        this.ip = doc.getString("ip");
        this.port = doc.getInteger("port");
        this.maintainEndTime = doc.getDate("maintainEndTime").toInstant();
        this.createTime = doc.getDate("createTime").toInstant();
        this.logDbUri = doc.getString("logDbUri");
        this.gameDbUrl = doc.getString("gameDbUrl");
        this.metaDbUri = doc.getString("metaDbUri");
        this.ssIp = doc.getString("ssIp");
        this.ssPort = doc.getInteger("ssPort");
        this.logDbName = doc.getString("logDbName");
    }

    public String getGameDbUrl() {
        return gameDbUrl;
    }
}
