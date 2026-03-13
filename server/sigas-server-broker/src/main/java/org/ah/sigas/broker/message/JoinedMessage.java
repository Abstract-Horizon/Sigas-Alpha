package org.ah.sigas.broker.message;

import java.util.LinkedHashMap;

import org.ah.sigas.json.JSON;

public class JoinedMessage extends Message {

    public JoinedMessage(String clientId, String alias) {
        this("JOIN", "  ", clientId, alias);
    }

    public JoinedMessage(String type, String flags, String clientId, String alias) {
        super(type, flags, clientId, JSON.asJSON(
                new LinkedHashMap<String, Object>() {{
                    put("client_id", clientId);
                    put("alias", alias);
                }}
        ));
    }

    public JoinedMessage(String type, String flags, String clientId, byte[] body) {
        super(type, flags, clientId, body);
    }
}
