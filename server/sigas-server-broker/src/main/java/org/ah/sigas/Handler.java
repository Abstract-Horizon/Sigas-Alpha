package org.ah.sigas;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

public interface Handler {

    public abstract boolean read(ReadableByteChannel channel) throws IOException;

    public abstract void write(WritableByteChannel channel) throws IOException;

    public abstract boolean isCompleted();

    public void close();

}
