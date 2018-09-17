package Shared;

import com.google.protobuf.ByteString;
import org.bson.types.ObjectId;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

public class Util {
    public static final ObjectId NullOid = new ObjectId(new byte[12]);
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
    public static LocalTime getLocalTime(int offset) {
         return LocalTime.now(ZoneId.ofOffset("UTC", ZoneOffset.ofHours(offset)));
    }
}