package org.ah.sigas;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

public class HTTPRequestHandler implements Handler {

    private ByteBuffer buffer = ByteBuffer.allocate(16384);
    private StringBuilder sb = new StringBuilder();


    private String RESPONSE_STRING = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 0\r\n\r\n";
    private byte[] RESPONSE_BYTES = RESPONSE_STRING.getBytes();

    private int connectionNo;
    private boolean responseSent = false;

    public HTTPRequestHandler(int connectionNo) {
        this.connectionNo = connectionNo;
    }

    @Override
    public boolean read(ReadableByteChannel channel) throws IOException {

        int read = channel.read(buffer);
        while (read > 0) {

            buffer.flip();
            byte[] bytes = new byte[buffer.limit()];
            buffer.get(bytes);
            sb.append(new String(bytes));
            buffer.clear();
            read = channel.read(buffer);
        }

        System.out.println("Read '" + sb + "'");

        if (sb.length() >= 4 && sb.substring(sb.length() - 4).equals("\r\n\r\n")) {
            return true;
        }

        return false;
    }

    @Override
    public void write(WritableByteChannel channel) throws IOException {
        buffer.put(RESPONSE_BYTES);
        buffer.flip();
        channel.write(buffer);
        buffer.clear();
        responseSent = true;
    }

    @Override
    public boolean isCompleted() {
        return responseSent;
    }

    @Override
    public void close() {

    }

}
