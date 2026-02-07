package org.ah.sigas.broker;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.WritableByteChannel;

public interface Handler {

    public abstract void read(SelectionKey key, ReadableByteChannel channel) throws IOException;

    public abstract void write(SelectionKey key, WritableByteChannel channel) throws IOException;

    public void close();

}
