package Game;

public interface ISessionCache {
    enum CacheType {
        LongLiving,     // cached by long living session
        NoCache,        // let hibernate session omit this object
        Temporary       // after sync with db, evict it from long living session
    }
    CacheType getCacheType();
}
