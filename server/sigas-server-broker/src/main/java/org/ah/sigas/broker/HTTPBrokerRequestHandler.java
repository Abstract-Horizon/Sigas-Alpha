package org.ah.sigas.broker;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.WritableByteChannel;

public class HTTPBrokerRequestHandler implements Handler {

    @Override
    public void read(SelectionKey key, ReadableByteChannel channel) throws IOException {
    }

    @Override
    public void write(SelectionKey key, WritableByteChannel channel) throws IOException {
    }

    @Override
    public void close() {
    }

}
