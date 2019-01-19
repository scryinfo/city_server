package Game.Meta;

import org.bson.Document;

public class MetaNpc {
	MetaNpc(Document d) {
		id = d.getInteger("id");
	}
	public int id;
}
