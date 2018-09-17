package Shared;

import org.bson.Document;

public class AccountServerInfo {
    private int lanPortGs;
    private String lanIp;
    private int internetPort;
    private String internetIp;

    public int getLanPortGs() {
        return lanPortGs;
    }
    public String getLanIp() {
        return lanIp;
    }
    public String getInternetIp() {
        return internetIp;
    }
    public int getInternetPort() {
        return internetPort;
    }

    public AccountServerInfo(Document bson) {
        this.lanPortGs = bson.getInteger("lanPortGs");
        this.lanIp = bson.getString("lanIp");
        this.internetPort = bson.getInteger("internetPort");
        this.internetIp = bson.getString("internetIp");
    }
}
