import Shared.LogDb;
import Statistic.IndustryInfo;
import Statistic.SummaryUtil;
import org.bson.Document;
import ss.Ss;

import java.util.ArrayList;
import java.util.List;

public class TestTop {
    @org.junit.Test
    public void test1() {
//        LogDb.init("mongodb://192.168.0.191:27017","cityLiuyi");
//        SummaryUtil.init();
        long millis = System.currentTimeMillis();
        long low = millis - 3600000 * 24;
        List<IndustryInfo> list = new ArrayList<>();
        list.add(new IndustryInfo(1,100l,555l));
        list.add(new IndustryInfo(2,200l,555l));
        list.add(new IndustryInfo(3,300l,555l));
        list.add(new IndustryInfo(4,400l,555l));
        //第二天
        list.add(new IndustryInfo(1,500l,777l));
        list.add(new IndustryInfo(2,600l,777l));
        list.add(new IndustryInfo(3,700l,777l));
        // 第三天

        list.add(new IndustryInfo(1,800l,999l));
        list.add(new IndustryInfo(2,900l,999l));
        list.add(new IndustryInfo(3,1000l,999l));
        list.add(new IndustryInfo(4,1100l,999l));
        Ss.IndustryIncome income = SummaryUtil.tests(list);
        System.err.println(income);
    }

    @org.junit.Test
    public void test2() {
        LogDb.init("mongodb://192.168.0.191:27017","city191");
                SummaryUtil.init();
        List<Document> list = LogDb.daySummaryGroundHistoryIncome(1, 2, LogDb.getBuyGround());
        SummaryUtil.insertDayIndustryIncomeData(SummaryUtil.IndustryType.GROUND, list, System.currentTimeMillis(), SummaryUtil.getDayIndustryIncome());
        System.err.println(list);
    }

}
