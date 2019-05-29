package Account;

import com.yunpian.sdk.YunpianClient;
import com.yunpian.sdk.model.Result;
import com.yunpian.sdk.model.SmsSingleSend;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

public class YunSmsManager
{
    private static final YunSmsManager instance = new YunSmsManager();
    private YunpianClient yunpianClient;
    private static final String template = "【Scry City】您的验证码是{0}。如非本人操作，请忽略本短信";

    private YunSmsManager()
    {
         yunpianClient = new YunpianClient("d2cce3dab45638da3f1d385ac3b8e334").init();
    }

    public static YunSmsManager getInstance()
    {
        return instance;
    }

    public Result<SmsSingleSend> sendAuthCode(String phoneNumber, String authCode)
    {
        Map<String, String> params = new HashMap<>();
        params.put(YunpianClient.MOBILE, phoneNumber);
        params.put(YunpianClient.TEXT, MessageFormat.format(template,authCode));
        return yunpianClient.sms().single_send(params);
    }

    public void shutDown()
    {
        if (yunpianClient != null)
        {
            yunpianClient.close();
        }
    }
}
