package Game.Gambling;

import gs.Gs;
import org.json.JSONObject;

import javax.persistence.Entity;
import javax.persistence.Id;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

@Entity
public class Flight {
    private static int toTs(String datetimeStr) throws ParseException {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date date = null;
        date = sdf.parse(datetimeStr);
        return (int) (date.getTime()/1000);
    }
    public Flight(JSONObject json) throws ParseException {
        id = json.getInt("id");
        depart_municipality = json.getString("depart_municipality");
        depart_name = json.getString("depart_name");
        destina_name = json.getString("destina_name");
        destina_municipality = json.getString("destina_municipality");
        origin_font_weight = json.getString("origin_font_weight");
        destination_font_weight = json.getString("destination_font_weight");
        airline_name = json.getString("airline_name");
        air_number = json.getString("air_number");

        estimatedarrivaltime = json.getString("estimatedarrivaltime");
        estimateddeparturetime = json.getString("estimateddeparturetime");
        filed_arrivaltime = json.getString("filed_arrivaltime");
        filed_departuretime = json.getString("filed_departuretime");
        intime_n = json.getString("intime_n");

        flightstatus = json.getString("flightstatus");
    }
    int getDelay() throws ParseException {
        int e = toTs(this.estimateddeparturetime);
        int a = toTs(this.filed_departuretime);
        return a - e;
    }
    protected Flight(){}
    @Id
    int id;
    String depart_municipality;//出发城市
    String depart_name;//                      出发机场
    String destina_name;//	                          到达机场
    String destina_municipality;//	              到达城市
    String origin_font_weight;//	              出发机场编码
    String destination_font_weight;//	  到达机场编码
    String airline_name;//	                           航空公司
    String air_number;//	                           航班号
    String estimatedarrivaltime;//	              预计到达时间
    String estimateddeparturetime;//	  预计起飞时间
    String filed_arrivaltime;//	              实际降落时间
    String filed_departuretime;//	              实际起飞时间
    String intime_n;//	                          准点率
    String flightstatus;//	                          航班状态

    boolean departured() {
        return !filed_departuretime.isEmpty();
    }

    Gs.FlightData toProto() {
        return Gs.FlightData.newBuilder()
                .setAirlineName(this.airline_name)
                .setAirNumber(this.air_number)
                .setDepartMunicipality(this.depart_municipality)
                .setDepartName(this.depart_name)
                .setDestinaName(destina_name)
                .setDestinaMunicipality(destina_municipality)
                .setOriginFontWeight(origin_font_weight)
                .setDestinationFontWeight(destination_font_weight)
                .setEstimatedarrivaltime(estimatedarrivaltime)
                .setEstimateddeparturetime(estimateddeparturetime)
                .setFiledArrivaltime(filed_arrivaltime)
                .setFiledDeparturetime(filed_departuretime)
                .setIntimeN(intime_n)
                .setFlightstatus(flightstatus)
                .build();
    }
}
