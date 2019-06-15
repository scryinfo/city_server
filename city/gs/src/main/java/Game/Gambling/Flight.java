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

@Entity
public class Flight {
    private static int toTs(String datetimeStr) throws ParseException {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date date = null;
        date = sdf.parse(datetimeStr);
        return (int) (date.getTime()/1000);
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
    int getDelay() throws ParseException {
        int e = toTs(this.FlightDeptimePlanDate);
        int a = toTs(this.FlightDeptimeDate);
        return (a - e)/60;
    }
    protected Flight(){}

    String FlightDepcode;//"NKG"
    String FlightArr;//成都"
    String FlightDep;//南京"
    String FlightArrtimePlanDate;//2019-05-22 10:10:00"
    String FlightDeptimePlanDate;//2019-05-22 07:25:00"
    String FlightArrAirport;//成都双流"

    @Id
    String id;//MU2805"
    String FlightCompany;//中国东方航空股份有限公司"
    String FlightArrcode;//CTU"
    String FlightDeptimeDate;//2019-05-22 07:27:00"
    String FlightArrtimeDate;//2019-05-22 09:41:00"
    String FlightDepAirport;//南京禄口"
    String FlightState;//到达"

    boolean departured() {
        return !FlightDeptimeDate.isEmpty();
    }

    Gs.FlightData toProto() {
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
