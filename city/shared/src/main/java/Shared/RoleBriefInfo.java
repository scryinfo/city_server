package Shared;

import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.Arrays;
import java.util.List;

public class RoleBriefInfo {
    public ObjectId id;
    public String name;
    public long lastLoginTs;
    public RoleBriefInfo(){}
    public RoleBriefInfo(Document bson) {
        id = bson.getObjectId("_id");
        name = bson.getString(RoleFieldName.NameFieldName);
        lastLoginTs = bson.getLong(RoleFieldName.OnlineTsFieldName);
    }
    public static List<String> queryFieldsName() {
        return Arrays.asList("_id", RoleFieldName.NameFieldName, RoleFieldName.OnlineTsFieldName);
    }
}
