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
    private static MongoCollection<Document> dayPlayerIncome;
    private static MongoCollection<Document> dayPlayerPay;
    //初始化
    static {
        MongoDatabase database = LogDb.getDatabase();
        playerExchangeAmount = database.getCollection(PLAYER_EXCHANGE_AMOUNT)
                .withWriteConcern(WriteConcern.UNACKNOWLEDGED);
        dayPlayerIncome = database.getCollection(DAY_PLAYER_INCOME)
                .withWriteConcern(WriteConcern.UNACKNOWLEDGED);
        dayPlayerPay = database.getCollection(DAY_PLAYER_PAY)
                .withWriteConcern(WriteConcern.UNACKNOWLEDGED);
    }

    //获取成交量
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

    public static MongoCollection<Document> getDayPlayerIncome() {
        return dayPlayerIncome;
    }

    public static MongoCollection<Document> getDayPlayerPay() {
        return dayPlayerPay;
    }

    //查询玩家每日收入或支出
    public static List<Integer> queryDayPlayerIncomeOrPay(MongoCollection<Document> collection,UUID id)
    {
        List<Integer> dayTotal = new ArrayList<>();
        collection.find(and(
                eq("id",id)
        )).projection(fields(include("time", "total"), excludeId()))
                .sort(Sorts.descending("time"))
                .forEach((Block<? super Document>) document ->{
                    dayTotal.add(Math.toIntExact(document.getLong("total")));
                });
        return dayTotal;
    }
}
