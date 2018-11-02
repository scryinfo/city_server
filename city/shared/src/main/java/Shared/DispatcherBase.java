package Shared;

import com.google.protobuf.Descriptors;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.Parser;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class DispatcherBase {
    public enum ProcessType {
        SYNC,
        ASYNC
    }
    public static class Wrapper {
        public Wrapper(Parser parser, Method method, ProcessType pt) {
            this.parser = parser;
            this.method = method;
            this.pt = pt;
        }

        public Parser parser;
        public Method method;
        public ProcessType pt;

        public static Wrapper newOnlyOpcode(Class sessionClass, String methodName) throws NoSuchMethodException {
            return new Wrapper(null, sessionClass.getMethod(methodName, short.class), ProcessType.SYNC);
        }
        public static Wrapper newOnlyOpcodeAsync(Class sessionClass, String methodName) throws NoSuchMethodException {
            return new Wrapper(null, sessionClass.getMethod(methodName, short.class), ProcessType.ASYNC);
        }
        public static Wrapper newWithMessage(Parser parser, Class sessionClass, String methodName) throws NoSuchMethodException {
            return new Wrapper(parser, sessionClass.getMethod(methodName, short.class, Message.class), ProcessType.SYNC);
        }
        public static Wrapper newWithMessageAsync(Parser parser, Class sessionClass, String methodName) throws NoSuchMethodException {
            return new Wrapper(parser, sessionClass.getMethod(methodName, short.class, Message.class), ProcessType.ASYNC);
        }
    }
    protected Map<Short, Wrapper> table = new HashMap<>();
    protected ProcessType processType(short cmd)
    {
        Wrapper w = table.get(cmd);
        if(w == null)
            return null;
        return w.pt;
    }
    public class ParseResult {
        public Message message;
        public Method method;
    }
    protected ParseResult parseMessage(Package pack) throws Exception {
        ParseResult res = new ParseResult();
        Wrapper w = table.get(pack.opcode);
        if(w == null)
            throw new Exception();
        res.method = w.method;
        if(w.parser == null) {
            if (pack.body != null)
                throw new Exception();
        }
        else {
            if (pack.body != null) {
                try {
                    res.message = (Message) w.parser.parseFrom(pack.body);
                } catch (InvalidProtocolBufferException e) {
                    e.printStackTrace();
                    throw new Exception();
                }
            }
        }
        return res;
    }
    protected void printRequest(short cmd, Descriptors.EnumValueDescriptor descriptor) {
        if(descriptor == null)
            System.out.println("error opcode: " + cmd);
        else
            System.out.println(descriptor.getName());
    }
    protected boolean invoke(ParseResult o, short opcode, Object session) {
        try {
            if (o.message == null) {
                try {
                    o.method.invoke(session, opcode);
                } catch (IllegalArgumentException e) {
                    if(GlobalConfig.debug())
                        e.printStackTrace();
                    return false;
                }
            } else {
                try {
                    o.method.invoke(session, opcode, o.message);
                } catch (IllegalArgumentException e) {
                    if(GlobalConfig.debug())
                        e.printStackTrace();
                    return false;
                }
            }
        }
        catch (InvocationTargetException e) {
            e.printStackTrace();
            return false;
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
}
