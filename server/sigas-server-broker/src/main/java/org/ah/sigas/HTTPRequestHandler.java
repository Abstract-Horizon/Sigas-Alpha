package org.ah.sigas;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.WritableByteChannel;
import java.util.HashMap;
import java.util.Map;

public class HTTPRequestHandler implements Handler {


    private ByteBuffer buffer = ByteBuffer.allocate(16384);

    private Broker broker;

    private int connectionNo;

    private String method;
    private String path;
    private String protocol;

    private Map<String, String> headers = new HashMap<String, String>();

    private byte[] bytes;
    private int pos = 0;
    private int max = 0;

    // Scanner vars
    private int ss = 0;

    private boolean lineParsed = false;
    private boolean parsingError = false;
    private boolean parsingComplete = false;

    private StringBuilder tokenValue = new StringBuilder();
    private String previousHeader;

    public HTTPRequestHandler(Broker broker, int connectionNo) {
        this.broker = broker;
        this.connectionNo = connectionNo;
    }


    private void parse() {
        while (pos < max && !parsingComplete) {
            while (pos < max && !lineParsed) {
                char ch = (char)bytes[pos];
                pos ++;

                if (ss == 0) {
                    if (ch == 13) {
                        ss = 1;
                    } else {
                        tokenValue.append(ch);
                    }
                } else if (ss == 1) {
                    if (ch == 10) {
                        lineParsed = true;
                        ss = 0;
                    } else {
                        parsingError = true;
                        lineParsed = true;
                        ss = 0;
                    }
                }
            }
            if (parsingError) {
                return;
            }

            if (lineParsed) {
                try {
                    if (tokenValue.length() == 0) {
                        parsingComplete = true;
                        return;
                    }

                    if (method == null) {
                        int firstSpace = tokenValue.indexOf(" ");
                        if (firstSpace < 1) {
                            parsingError = true;
                            return;
                        }

                        method = tokenValue.substring(0, firstSpace);

                        int secondSpace = tokenValue.indexOf(" ", firstSpace + 1);
                        if (secondSpace <=  firstSpace + 1) {
                            parsingError = true;
                            return;
                        }
                        path = tokenValue.substring(firstSpace, secondSpace);
                        protocol = tokenValue.substring(secondSpace + 1);
                    } else {
                        if (tokenValue.charAt(0) == ' ') {
                            if (previousHeader == null) {
                                parsingError = true;
                                return;
                            } else {
                                String previousHeaderValue = headers.get(previousHeader);
                                previousHeaderValue += tokenValue.toString().trim();
                                headers.put(previousHeader, previousHeaderValue);
                            }
                            return;
                        }

                        int i = tokenValue.indexOf(": ");
                        if (i < 1) {
                            parsingError = true;
                            return;
                        }
                        String headerKey = tokenValue.substring(0, i);
                        previousHeader = headerKey;
                        String headerValue = tokenValue.substring(i + 2).trim();
                        headers.put(headerKey, headerValue);
                    }
                } finally {
                    tokenValue.delete(0, tokenValue.length());
                    lineParsed = false;
                }
            }
        }
    }

    @Override
    public void read(SelectionKey key, ReadableByteChannel channel) throws IOException {

        int read = channel.read(buffer);
        while (read > 0 && !parsingError && !parsingComplete) {

            buffer.flip();

            bytes = buffer.array();
            pos = 0;
            max = buffer.limit();

            parse();

            buffer.clear();

            read = channel.read(buffer);
        }

        if (parsingError) {
            System.out.println("Got malformed request");
            broker.closeChannel(key);
            return;
        }

        if (parsingComplete) {
            System.out.println("Method: '" + method + "' on path '" + path + "' with protocol '" + protocol + "'");
            System.out.println("Headers:");
            for (Map.Entry<String, String> header : headers.entrySet()) {
                System.out.println("  " + header.getKey() + ": " + header.getValue());
            }

            key.attach(new HTTPResponseHandler(broker, connectionNo, 200, "OK",
                new HashMap<String, String>() {{
                    put("Content-Type", "text/plain");
                }}, new byte[0]));
            key.interestOps(SelectionKey.OP_WRITE);
        }
    }

    @Override
    public void write(SelectionKey key, WritableByteChannel channel) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() {
    }

}
