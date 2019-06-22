package Game.Gambling;

import gs.Gs;
import org.json.JSONObject;

import javax.persistence.Embeddable;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

public class Flight {

    private static long toTs(String datetimeStr) throws ParseException {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date date = sdf.parse(datetimeStr);
        return date.getTime();
    }

    public Flight(JSONObject json) {
        FlightDepcode = json.getString("FlightDepcode");
        FlightArr = json.getString("FlightArr");
        FlightDep = json.getString("FlightDep");
        FlightArrtimePlanDate = json.getString("FlightArrtimePlanDate");
        FlightDeptimePlanDate = json.getString("FlightDeptimePlanDate");
        FlightArrAirport = json.getString("FlightArrAirport");
        id = json.getString("FlightNo");
        FlightCompany = json.getString("FlightCompany");

        FlightArrcode = json.getString("FlightArrcode");
        FlightDeptimeDate = json.getString("FlightDeptimeDate");
        FlightArrtimeDate = json.getString("FlightArrtimeDate");
        FlightDepAirport = json.getString("FlightDepAirport");
        FlightState = json.getString("FlightState");
    }
    String getDate() {
        return this.FlightDeptimePlanDate.substring(0, 10);
    }
    int getDelayMinute() throws ParseException {
        long e = toTs(this.FlightDeptimePlanDate);
        long a = toTs(this.FlightDeptimeDate);
        return (int) ((a - e)/60000);
    }
    protected Flight(){}

    String FlightDepcode;//"NKG"
    String FlightArr;//成都"
    String FlightDep;//南京"
    String FlightArrtimePlanDate;//2019-05-22 10:10:00"
    String FlightDeptimePlanDate;//2019-05-22 07:25:00"
    String FlightArrAirport;//成都双流"
    String id;//MU2805"
    String FlightCompany;//中国东方航空股份有限公司"
    String FlightArrcode;//CTU"
    String FlightDeptimeDate;//2019-05-22 07:27:00"
    String FlightArrtimeDate;//2019-05-22 09:41:00"
    String FlightDepAirport;//南京禄口"
    String FlightState;//到达"

    public boolean departured() {
        return !FlightDeptimeDate.isEmpty();
    }
    public boolean canBet() {
        return this.FlightState.equals("计划");
    }
    public Gs.FlightData toProto() {
        return Gs.FlightData.newBuilder()
                .setFlightArr(this.FlightArr)
                .setFlightDepcode(this.FlightDepcode)
                .setFlightDep(this.FlightDep)
                .setFlightArrtimePlanDate(this.FlightArrtimePlanDate)
                .setFlightDeptimePlanDate(FlightDeptimePlanDate)
                .setFlightArrAirport(FlightArrAirport)
                .setFlightNo(id)
                .setFlightCompany(FlightCompany)
                .setFlightArrcode(FlightArrcode)
                .setFlightDeptimeDate(FlightDeptimeDate)
                .setFlightArrtimeDate(FlightArrtimeDate)
                .setFlightDepAirport(FlightDepAirport)
                .setFlightState(FlightState)
                .build();
    }
}
