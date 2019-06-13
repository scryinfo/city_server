package Game.Gambling;

import com.google.common.collect.Sets;
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
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

public class ThirdPartyDataSource {
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
    //private int totalPages;
    private final Set<Set<String>> airPortCombination = Sets.combinations(new HashSet<>(Arrays.asList("ABC","BCD")), 2);
    private Map<String, Flight> data = new HashMap<>();
    private Map<String, String> departured = new HashMap<>();
    private ReadWriteLock lock = new ReentrantReadWriteLock(true);
    private MessageDigest md;
    public List<Flight> getAllFlight() {
        lock.readLock().lock();
        List<Flight> res = data.values().stream().collect(Collectors.toList());
        lock.readLock().unlock();
        return res;
    }
    public Map<String, String> getDepartured() {
        lock.writeLock().lock();
        Map<String, String> res = new HashMap<>(departured);
        lock.writeLock().unlock();
        return res;
    }
    public void clear(String id) {
        lock.writeLock().lock();
        this.data.remove(id);
        this.departured.remove(id);
        lock.writeLock().unlock();
    }

    private void pullData(){
        airPortCombination.forEach(s->{
            Iterator<String> iterator = s.iterator();
            String a = iterator.next();
            String b = iterator.next();
            getFlightJson(a, b);
            getFlightJson(b, a);
        });
    }

    private void getFlightJson(String srcAirPortCode, String dstAirPortCode) {
        URIBuilder uriBuilder = getFightBaseUriBuilder()
                .setParameter("appid", "10512")
                .setParameter("arr", srcAirPortCode)
                .setParameter("date", LocalDate.now().toString())
                .setParameter("dep", dstAirPortCode);
        List<JSONObject> jsonList = doGetFight(uriBuilder);
        if(jsonList == null)
            return;
        for (JSONObject json : jsonList) {
            Flight flight = new Flight(json);
            lock.writeLock().lock();
            this.data.putIfAbsent(flight.id, flight);
            lock.writeLock().unlock();
        }
    }

    private List<String> getUnDeparturedIds() {
        lock.readLock().lock();
        List<String> res = this.data.values().stream().filter(o->!o.departured()).map(o->o.id).collect(Collectors.toList());
        lock.readLock().unlock();
        return res;
    }
    public void update() {
        pullData();

        List<String> ids = getUnDeparturedIds();
        for (String id : ids) {
            String t = updateDepartureTime(id);
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

    private URIBuilder getFightBaseUriBuilder() {
        return new URIBuilder()
                .setScheme("http")
                .setHost("open-al.variflight.com")
                .setPath("/api/flight");
    }

    private String updateDepartureTime(String id) {
        URIBuilder uriBuilder = getFightBaseUriBuilder()
                .setParameter("appid", "10512")
                .setParameter("date", LocalDate.now().toString())
                .setParameter("fnum", id);
        List<JSONObject> o = doGetFight(uriBuilder);
        if(o == null || o.isEmpty())
            return null;
        Flight fd = new Flight(o.get(0));
        if(!fd.departured())
            return null;
        return fd.FlightDeptimeDate;
    }
    private URI sign(URIBuilder uriBuilder) throws URISyntaxException {
        URI uri = uriBuilder.build();
        md.update((uri.getQuery()+"5b0b5cfa5b903").getBytes());
        md.update(DatatypeConverter.printHexBinary(md.digest()).toLowerCase().getBytes());
        String token = DatatypeConverter.printHexBinary(md.digest()).toLowerCase();
        uriBuilder.setParameter("token", token);
        return uriBuilder.build();
    }
    public List<JSONObject> doGetFight(URIBuilder uriBuilder) {
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

            return toList(new JSONArray(json));
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
