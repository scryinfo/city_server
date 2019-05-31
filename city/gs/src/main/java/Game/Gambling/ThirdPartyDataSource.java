package Game.Gambling;

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
import org.json.JSONArray;
import org.json.JSONObject;

import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

public class ThirdPartyDataSource {
    private static ThirdPartyDataSource instance = new ThirdPartyDataSource();
    public static ThirdPartyDataSource instance() {
        return instance;
    }
    private ThirdPartyDataSource() {}
    private int totalPages;
    private Map<Integer, Flight> data = new HashMap<>();
    private Map<Integer, String> departured = new HashMap<>();
    private ReadWriteLock lock = new ReentrantReadWriteLock(true);
    public List<Flight> getAllFlight() {
        lock.readLock().lock();
        List<Flight> res = data.values().stream().collect(Collectors.toList());
        lock.readLock().unlock();
        return res;
    }
    public Map<Integer, String> getDepartured() {
        lock.writeLock().lock();
        Map<Integer, String> res = new HashMap<>(departured);
        lock.writeLock().unlock();
        return res;
    }
    public void clear(int id) {
        lock.writeLock().lock();
        this.data.remove(id);
        this.departured.remove(id);
        lock.writeLock().unlock();
    }
    private void syncPages() {
        try {
            URI uri = getFightBaseUriBuilder().build();
            JSONObject o = doGetFight(uri);
            totalPages = o==null?totalPages:o.getJSONObject("paginator").getInt("total_pages");
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }
    private void pullCurrentPageData(){
        try {
            URI uri = getFightBaseUriBuilder().setParameter("_page", String.valueOf(totalPages)).build();
            JSONObject o = doGetFight(uri);
            if(o == null)
                return;
            List<JSONObject> jsonList  = extractResult(o);
            for (JSONObject json : jsonList) {
                try {
                    Flight flight = new Flight(json);
                    lock.writeLock().lock();
                    this.data.put(flight.id, flight);
                    lock.writeLock().unlock();
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            }
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }
    private List<Integer> getUnDeparturedIds() {
        lock.readLock().lock();
        List<Integer> res = this.data.values().stream().filter(o->!o.departured()).mapToInt(o->o.id).boxed().collect(Collectors.toList());
        lock.readLock().unlock();
        return res;
    }
    public void update() {
        syncPages();
        pullCurrentPageData();

        List<Integer> ids = getUnDeparturedIds();
        for (Integer id : ids) {
            String t = null;
            try {
                t = updateDepartureTime(id);
            } catch (URISyntaxException e) {
                e.printStackTrace();
            } catch (ParseException e) {
                e.printStackTrace();
            }
            if(t != null) {
                lock.writeLock().lock();
                departured.put(id, t);
                lock.writeLock().unlock();
            }
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
    private List<JSONObject> extractResult(JSONObject o) {
        return toList(o.getJSONArray("results"));
    }
    private URIBuilder getFightBaseUriBuilder() {
        return new URIBuilder()
                .setScheme("https")
                .setHost("api.scrydepot.info")
                .setPath("/api/flight_data");
    }

    private String updateDepartureTime(int id) throws URISyntaxException, ParseException {
        URI uri = getFightBaseUriBuilder().setParameter("id", String.valueOf(id)).build();
        JSONObject o = doGetFight(uri);
        if(o == null)
            return null;
        Flight fd = new Flight(extractResult(o).get(0));
        if(!fd.departured())
            return null;
        return fd.filed_departuretime;
    }
    public JSONObject doGetFight(URI uri) {
        CloseableHttpClient httpclient = HttpClients.createDefault();
        HttpGet httpget = new HttpGet(uri);
        httpget.addHeader("api-id", "gY2F4kA7J8Fpt3XDLKVl");
        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {

        }
        int ts = (int) (System.currentTimeMillis() / 1000);
        md.update(("NKA5CK9fLtcoGYo0onBxWm147ZczY4PR" + "flight_data" + String.valueOf(ts)).getBytes());
        httpget.addHeader("api-signature", DatatypeConverter.printHexBinary(md.digest()));
        httpget.addHeader("timestamp", String.valueOf(ts));
        CloseableHttpResponse response = null;
        try {
            response = httpclient.execute(httpget);
            HttpEntity entity = response.getEntity();
            Header encodingHeader = entity.getContentEncoding();

            Charset encoding = encodingHeader == null ? StandardCharsets.UTF_8 : Charsets.toCharset(encodingHeader.getValue());
            String json = EntityUtils.toString(entity, encoding);
            return new JSONObject(json);
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
}
