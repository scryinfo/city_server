package Shared;

import com.google.protobuf.ByteString;
import org.bson.BsonBinaryReader;
import org.bson.BsonBinaryWriter;
import org.bson.Document;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.DocumentCodec;
import org.bson.codecs.EncoderContext;
import org.bson.io.BasicOutputBuffer;
import org.bson.types.ObjectId;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class Util {
    public static final ObjectId NullOid = new ObjectId(new byte[12]);
    private static final byte[] UUID_APPENDS = new byte[4];
    public static ByteString toByteString(ObjectId id) {
        return ByteString.copyFrom(id.toByteArray());
    }
    public static long getTimerDelay(int hour, int min)
    {
        long delay;
        LocalTime localTime = LocalTime.now();
        int now_hour = localTime.getHour();
        int now_min = localTime.getMinute();
        int now_sec = localTime.getSecond();
        if(now_hour >= hour)
            delay = TimeUnit.HOURS.toMillis(24-now_hour+hour) + TimeUnit.MINUTES.toMillis(min-now_min) + TimeUnit.SECONDS.toMillis(-now_sec);
		else
            delay = TimeUnit.HOURS.toMillis(hour-now_hour) + TimeUnit.MINUTES.toMillis(min-now_min) + TimeUnit.SECONDS.toMillis(-now_sec);
        return delay;
    }
//    public static LocalTime getLocalTime(int offset) {
//         return LocalTime.now(ZoneId.ofOffset("UTC", ZoneOffset.ofHours(offset)));
//    }
    public static UUID toUuid(byte[] bytes) {
        ByteBuffer bb = ByteBuffer.wrap(bytes); // if bytes.length < 16, getLong will throw java.nio.BufferUnderflowException
        long firstLong = bb.getLong();
        long secondLong = bb.getLong();
        return new UUID(firstLong, secondLong);
    }
    public static UUID toUuid(ObjectId oid) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            output.write(oid.toByteArray());
            output.write(UUID_APPENDS);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return toUuid(output.toByteArray());
    }
    public static byte[] toBytes(UUID uuid) {
        ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        return bb.array();
    }
    public static ByteString toByteString(UUID uuid) {
        return ByteString.copyFrom(toBytes(uuid));
    }

    public static <T extends Object> T randomChoose(Collection<T> collection) {
        if(collection instanceof List) {
            List list = (List)collection;
            return (T) list.get(random(0, list.size()));
        }
        else {
            final int idx = random(0, collection.size());
            Iterator<T> iterator = collection.iterator();
            int i = 0;
            T t = null;
            while(i != idx) {
                t = iterator.next();
                ++i;
            }
            return t;
        }
    }
    // range is [l, r]
    public static int random(int l, int r) {
        if(l == r)
            return l;
        else if(l > r)
            return ThreadLocalRandom.current().nextInt(r, l);
        else
            return ThreadLocalRandom.current().nextInt(l, r);
    }
//    public static double random(double l, double r) {
//        return ThreadLocalRandom.current().nextDouble(l, r);
//    }
    private static final Codec<Document> codec = new DocumentCodec();

    public static byte[] toBytes(final Document document) {
        BasicOutputBuffer outputBuffer = new BasicOutputBuffer();
        BsonBinaryWriter writer = new BsonBinaryWriter(outputBuffer);
        codec.encode(writer, document, EncoderContext.builder().isEncodingCollectibleDocument(true).build());
        return outputBuffer.toByteArray();
    }

    public static Document toDocument(final byte[] input) {
        BsonBinaryReader bsonReader = new BsonBinaryReader(ByteBuffer.wrap(input));
        return codec.decode(bsonReader, DecoderContext.builder().build());
    }
}