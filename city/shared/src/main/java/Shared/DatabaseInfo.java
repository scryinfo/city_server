package Shared;

public class DatabaseInfo {
    public final static class Game {
        public static final String USERNAME = "postgres";
        public static final String PASSWORD = "123456";

        public final static class Player {
            public static final String Table = "player";
            public static final String OnlineTs = "online_ts";
            public static final String OfflineTs = "offline_ts";
            public static final String AccountName = "account_name";
            public static final String Name = "name";
            public static final String Money = "money";
            public static final String Id = "id";
        }
    }
}