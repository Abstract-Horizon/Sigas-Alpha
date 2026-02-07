package org.ah.sigas.broker;

import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectionKey;

public class HTTPServerRequestHandler extends HTTPRequestHandler {

    public HTTPServerRequestHandler(Broker broker) {
        super(broker);
    }

    @Override
    protected void processRequest(SelectionKey key, ReadableByteChannel channel) {
        createSimpleResponse(key, 404, "NOT FOUND", "Method " + method + ", path " + path + " not found");
    }

}
