package org.ah.sigas.broker.message;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

public abstract class Message {

    private static Map<String, Class<? extends Message>> MESSAGE_TYPES = new HashMap<>();

    private String type;
    private byte[] body;

    public Message(String type, byte[] body) {
        this.type = type;
        this.body = body;
    }

    public String getType() { return type; }
    public byte[] getBody() { return body; }
    public boolean isSystemMessage() { return type.charAt(0) == '!'; }

    public static void registerMessageType(String msgType, Class<? extends Message> msgClass) {
        MESSAGE_TYPES.put(msgType, msgClass);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Message> T createMessage(String type, byte[] body) {
        Class<? extends Message> cls = MESSAGE_TYPES.get(type);

        if (cls == null) {
            cls = SimpleBytesMessage.class;
        }

        try {
            Constructor<? extends Message> constructor = cls.getConstructor(String.class, byte[].class);

            return (T) constructor.newInstance(type, body);
        } catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new IllegalAccessError("Cannot make new instance of message with type '" + type + "'; " + e.getMessage());
        }
    }
}
