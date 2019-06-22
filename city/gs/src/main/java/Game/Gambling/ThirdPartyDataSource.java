package Game.Gambling;

import Game.Timers.PeriodicTimer;
import com.google.common.base.Strings;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.codec.Charsets;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

public class ThirdPartyDataSource {
    private static final Logger logger = Logger.getLogger(ThirdPartyDataSource.class);
    private static ThirdPartyDataSource instance = new ThirdPartyDataSource();
    public static ThirdPartyDataSource instance() {
        return instance;
    }
    private ThirdPartyDataSource() {
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    public Map<String, Flight> getFlights(List<TrackInfo> infos) {
        Map<String, Flight> res = new HashMap<>();
        for (TrackInfo trackInfo : infos) {
            res.put(trackInfo.id, this.getFlightJson(trackInfo.id, trackInfo.date));
        }
        return res;
    }

    public static final class TrackInfo {
        public TrackInfo(String id, String date) {
            this.id = id;
            this.date = date;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TrackInfo trackInfo = (TrackInfo) o;
            return Objects.equals(id, trackInfo.id) &&
                    Objects.equals(date, trackInfo.date);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, date);
        }

        String id;
        String date;
    }
    private Set<TrackInfo> track = new HashSet<>();
    private Map<String, String> departured = new HashMap<>();
    private ReadWriteLock lock = new ReentrantReadWriteLock(true);
    private MessageDigest md;

    public Map<String, String> getDepartured() {
        lock.readLock().lock();
        Map<String, String> res = new HashMap<>(departured);
        lock.readLock().unlock();
        return res;
    }
    public void clear(String id) {
        lock.writeLock().lock();
        this.departured.remove(id);
        lock.writeLock().unlock();
    }

    private List<Flight> getFlightJson(String srcAirPortCode, String dstAirPortCode, String date) {
        List<Flight> res = new ArrayList<>();
        URIBuilder uriBuilder = getFlightBaseUriBuilder()
                .setParameter("appid", "10512")
                .setParameter("arr", srcAirPortCode)
                .setParameter("date", date)//LocalDate.now().toString())
                .setParameter("dep", dstAirPortCode);
        List<JSONObject> jsonList = doGetFlight(uriBuilder);
        if(jsonList != null) {
            for (JSONObject json : jsonList) {
                res.add(new Flight(json));
            }
        }
        return res;
    }

    private Flight getFlightJson(String flightNo, String date) {
        URIBuilder uriBuilder = getFlightBaseUriBuilder()
                .setParameter("appid", "10512")
                .setParameter("date", date)
                .setParameter("fnum", flightNo);
        List<JSONObject> o = doGetFlight(uriBuilder);
        if(o == null || o.isEmpty())
            return null;
        return new Flight(o.get(0));
    }

    public void trackDeparture(String id, String date) {
        lock.writeLock().lock();
        track.add(new TrackInfo(id, date));
        lock.writeLock().unlock();
    }
    public Set<TrackInfo> getTrack() {
        Set<TrackInfo> res;
        lock.readLock().lock();
        res = new HashSet<>(track);
        lock.readLock().unlock();
        return res;
    }

    private PeriodicTimer timer = new PeriodicTimer((int) TimeUnit.MINUTES.toMillis(15));
    public void update(long updatePeriod) {
        long start = System.currentTimeMillis();
        while(!queue.isEmpty()) {
            queue.poll().run();
        }
        for (TrackInfo t : getTrack()) {
            Flight flight = this.getFlightJson(t.id, t.date);
            if(flight != null && flight.departured())
            {
                lock.writeLock().lock();
                departured.put(flight.id, flight.FlightDeptimeDate);
                this.track.remove(t);
                lock.writeLock().unlock();
            }
        }
        long consume = System.currentTimeMillis() - start;
        consume = consume > updatePeriod ? consume : updatePeriod;
        if (timer.update(TimeUnit.MILLISECONDS.toNanos(consume)))
        {
            updateWeatherInfo();
        }
    }

    private <T> List<T> toList(JSONArray jsonArray) {
        List<T> list = new ArrayList<>();
        if (jsonArray != null) {
            int len = jsonArray.length();
            for (int i = 0; i < len; i++) {
                list.add((T) jsonArray.opt(i));
            }
        }
        return list;
    }

    private URIBuilder getFlightBaseUriBuilder() {
        return new URIBuilder()
                .setScheme("http")
                .setHost("open-al.variflight.com")
                .setPath("/api/flight");
    }

    private URI sign(URIBuilder uriBuilder) throws URISyntaxException {
        URI uri = uriBuilder.build();
        md.update((uri.getQuery()+"5b0b5cfa5b903").getBytes());
        md.update(DatatypeConverter.printHexBinary(md.digest()).toLowerCase().getBytes());
        String token = DatatypeConverter.printHexBinary(md.digest()).toLowerCase();
        uriBuilder.setParameter("token", token);
        return uriBuilder.build();
    }
    public List<JSONObject> doGetFlight(URIBuilder uriBuilder) {
        URI uri = null;
        try {
            uri = sign(uriBuilder);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        if(uri == null)
            return null;

        CloseableHttpClient httpclient = HttpClients.createDefault();
        HttpGet httpget = new HttpGet(uri);
        CloseableHttpResponse response = null;
        try {
            response = httpclient.execute(httpget);
            HttpEntity entity = response.getEntity();
            Header encodingHeader = entity.getContentEncoding();

            Charset encoding = encodingHeader == null ? StandardCharsets.UTF_8 : Charsets.toCharset(encodingHeader.getValue());
            String json = EntityUtils.toString(entity, encoding);
            try {
                return toList(new JSONArray(json));
            }
            catch(JSONException exception) {
                return new ArrayList<>();
            }
        } catch (ClientProtocolException e) {

        } catch (IOException e) {

        } finally {
            try {
                if (response != null)
                    response.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
    public void postFlightSearchRequest(String date, String arrCode, String depCode, Consumer<List<Flight>> callback) {
        queue.offer(()->{
            callback.accept(this.getFlightJson(arrCode, depCode, date));
        });
    }
    public void postFlightSearchRequest(String date, String flightNo, Consumer<Flight> callback) {
        queue.offer(()->{
            callback.accept(this.getFlightJson(flightNo, date));
        });
    }



    private static final String WEATHER_APPID = "da375880b6f3f0f045ad1d8f6c0ca583";
    private static final String TOKYO_CITY_ID = "1850147";
    private static final String CHENGDU_CITY_ID = "1815286";
    private volatile Weather weather;

    public  Weather getWeather()
    {
        return weather;
    }

    public String doHttpGet(URI uri)
    {
        CloseableHttpClient httpclient = HttpClients.createDefault();
        HttpGet httpget = new HttpGet(uri);
        CloseableHttpResponse response = null;
        try {
            response = httpclient.execute(httpget);
            HttpEntity entity = response.getEntity();
            Header encodingHeader = entity.getContentEncoding();
            Charset encoding = encodingHeader == null ? StandardCharsets.UTF_8 : Charsets.toCharset(encodingHeader.getValue());
            return EntityUtils.toString(entity, encoding);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        } finally {
            try {
                if (response != null)
                    response.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public void updateWeatherInfo()
    {
        String str = String.format("http://api.openweathermap.org/data/2.5/weather?id=%s&appid=%s",CHENGDU_CITY_ID,WEATHER_APPID);
        URI uri = URI.create(str);
        String weatherJson = doHttpGet(uri);
        logger.info("update weather info : " + weatherJson);
        if (!Strings.isNullOrEmpty(weatherJson))
        {
            JsonParser jsonParser = new JsonParser();
            JsonElement jsonElement = jsonParser.parse(weatherJson);
            if (jsonElement.isJsonObject())
            {
                JsonObject jsonObject = jsonElement.getAsJsonObject();
                Iterator<JsonElement> iterator = jsonObject.getAsJsonArray("weather").iterator();
                String icon ="";
                if (iterator.hasNext()) {
                    icon = iterator.next().getAsJsonObject().get("icon").getAsString();
                }
                double temp = jsonObject.getAsJsonObject("main").get("temp").getAsDouble() - 273.15;
                //long sunrise = jsonObject.getAsJsonObject("sys").get("sunrise").getAsLong();
                //long sunset = jsonObject.getAsJsonObject("sys").get("sunset").getAsLong();
                if (!Strings.isNullOrEmpty(icon))
                {
                    this.weather = new Weather(icon, temp);
                }
            }
        }
    }
}
