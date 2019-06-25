package Game.Gambling;

import com.google.common.base.Strings;
import com.google.protobuf.Message;
import gs.Gs;

public class Weather
{
    private String icon = "";
    private double temp;

    public Weather(String icon, double temp)
    {
        this.icon = icon;
        this.temp = temp;
    }

    public String getIcon()
    {
        return icon;
    }

    public double getTemp()
    {
        return temp;
    }

    public Gs.Weather toProto()
    {
        return Strings.isNullOrEmpty(icon) ?
                Gs.Weather.newBuilder().setIcon("01d").setTemp(20).build()
                :Gs.Weather.newBuilder().setIcon(icon).setTemp(temp).build();
    }
}
