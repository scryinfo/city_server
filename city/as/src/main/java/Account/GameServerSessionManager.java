package Account;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

public class GameServerSessionManager {
	private ConcurrentHashMap<Integer, GameServerSession> data = new ConcurrentHashMap<>();
	private static GameServerSessionManager singleInstance = new GameServerSessionManager();
	private GameServerSessionManager(){
	}
	
	public static GameServerSessionManager getInstance(){
		return singleInstance;
	}
	
	public GameServerSession findOne(int id){
		return data.search(1000L, (k,v) ->{
			if(k.compareTo(id) == 0)
				return v;
			return null;
		});
	}
	
	public void findServerInfos(ArrayList<GameServerSession.Info> infos){
		data.forEach((k,v)->{infos.add(v.getInfo());});
	}
	
	public GameServerSession add(int id, GameServerSession session){
		return data.putIfAbsent(id, session);
	}
	
	public void del(int id){
		data.computeIfPresent(id, (k,v)->{return null;});
	}

}
