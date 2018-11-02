package Shared;

import java.util.UUID;

public class RoleBriefInfo {
    public UUID id;
    public String name;
    public long lastLoginTs;

    public RoleBriefInfo() {
    }

    public RoleBriefInfo(UUID id, String name, long lastLoginTs) {
        this.id = id;
        this.name = name;
        this.lastLoginTs = lastLoginTs;
    }
}
