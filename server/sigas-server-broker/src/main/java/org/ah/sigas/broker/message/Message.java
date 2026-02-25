package org.ah.sigas.broker.message;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

public abstract class Message {

    private static Map<String, Class<? extends Message>> MESSAGE_TYPES = new HashMap<>();

    private String type;
    private byte[] body;
    private String clientId = null;
    private String flags = null;

    public Message(String type, String header, byte[] body) {
        this.type = type;
        this.flags = header.substring(0, 2);
        this.clientId = header.substring(2, 4);
        this.body = body;
    }

    public String getType() { return type; }
    public byte[] getBody() { return body; }

    public String getFlags() { return this.flags; }
    public String getClientId() { return this.clientId; }

    public static void registerMessageType(String msgType, Class<? extends Message> msgClass) {
        MESSAGE_TYPES.put(msgType, msgClass);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Message> T createMessage(String type, String header, byte[] body) {
        Class<? extends Message> cls = MESSAGE_TYPES.get(type);

        if (cls == null) {
            cls = SimpleBytesMessage.class;
        }

        try {
            Constructor<? extends Message> constructor = cls.getConstructor(String.class, String.class, byte[].class);

            return (T) constructor.newInstance(type, header, body);
        } catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new IllegalAccessError("Cannot make new instance of message with type '" + type + "'; " + e.getMessage());
        }
    }
}
