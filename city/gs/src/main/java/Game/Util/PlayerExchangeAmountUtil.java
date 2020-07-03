package Game.Util;

import Shared.LogDb;
import com.mongodb.Block;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Sorts;
import org.bson.Document;

import java.util.*;

import static Shared.LogDb.KEY_TOTAL;
import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Projections.*;

public class PlayerExchangeAmountUtil {
    public static final String PLAYER_EXCHANGE_AMOUNT = "playerExchangeAmount";
    private static final String DAY_PLAYER_INCOME = "dayPlayerIncome";
    private static final String DAY_PLAYER_PAY = "dayPlayerPay";
    private static final String COUNTTYPE = "countType";
    private static MongoCollection<Document> playerExchangeAmount;
    //initialization
    static {
        MongoDatabase database = LogDb.getDatabase();
        playerExchangeAmount = database.getCollection(PLAYER_EXCHANGE_AMOUNT)
                .withWriteConcern(WriteConcern.UNACKNOWLEDGED);
    }

    //Get volume
    public static Long getExchangeAmount(int countType){
        long a=0;
        Map<Long, Long> map = new LinkedHashMap<>();
        List<Document> documentList = new ArrayList<>();
        Document projectObject = new Document()
                .append("id", "$_id")
                .append(KEY_TOTAL, "$" + KEY_TOTAL)
                .append("_id",0);
        playerExchangeAmount.aggregate(
                Arrays.asList(
                        Aggregates.match(eq(COUNTTYPE, countType)),
                        Aggregates.group(null, Accumulators.sum(KEY_TOTAL, "$total")),
                        Aggregates.project(projectObject)
                )
        ).forEach((Block<? super Document>) documentList::add);
        documentList.forEach((document -> map.put(document.getLong("time"), document.getLong("total"))));
        for (Map.Entry<Long, Long> entry : map.entrySet()) {
            a=entry.getValue();
        }
        return a;
    }
}
