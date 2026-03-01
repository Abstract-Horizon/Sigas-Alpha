package org.ah.sigas.broker.message;

import java.util.HashMap;

import org.ah.sigas.json.JSON;

public class JoinedMessage extends Message {

    private String alias;

    public JoinedMessage(String clientId, String alias) {
        this("JOIN", "  ", clientId, alias);
    }

    public JoinedMessage(String type, String flags, String clientId, String alias) {
        super(type, flags, clientId, JSON.asJSON(
                new HashMap<String, Object>() {{
                    put("client_id", clientId);
                    put("alias", alias);
                }}
        ));
        this.alias = alias;
    }

    public String getAlias() { return alias; }
}
