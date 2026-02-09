package org.ah.sigas.broker;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectionKey;

import org.ah.sigas.broker.game.Client;

public class ClientInboundHandlerImpl extends BaseClientHandler {


    protected ByteBuffer buffer = ByteBuffer.allocate(16384);

    private byte[] bytes;
    private int pos = 0;
    private int max = 0;

    private int cs = 0;  // chunk state
    private int chunkLen = 0;

    private int ms = 0; // message state
    private int msgLen = 0;
    private boolean error = false;

    private byte[] messageType = new byte[4];
    private byte[] messageBytes;
    private int ptr = 0;

    public ClientInboundHandlerImpl(Broker broker, Client client) {
        super(broker, client);
    }

    public ByteBuffer getBuffer() { return buffer; }

    @Override
    public void read(SelectionKey key, ReadableByteChannel channel) throws IOException {
        int read = channel.read(buffer);
        processInput(key, channel, read);
    }

    private boolean readMessage() {
        int l = msgLen - ptr;
        if (l > max - pos) {
            l = max - pos;
        }
        if (l > chunkLen) {
            l = chunkLen;
        }
        System.arraycopy(bytes, pos, messageBytes, ptr, l);

        pos += l;
        ptr += l;
        chunkLen -= l;

        if (ptr >= msgLen) {
            client.receivedMessage(new String(messageType), messageBytes);
            return true;
        }
        return false;
    }

    private void parseMessage() {
        while (pos < max && chunkLen > 0) {
            byte b = bytes[pos];
            if (ms != 8) {
                pos++;
                chunkLen--;
            }

            if (ms == 0) {
                messageType[0] = b;
                ms = 1;
            } else if (ms == 1) {
                messageType[1] = b;
                ms = 2;
            } else if (ms == 2) {
                messageType[2] = b;
                ms = 3;
            } else if (ms == 3) {
                messageType[3] = b;
                ms = 4;
            } else if (ms == 4) {
                ptr = 0;
                msgLen = b;
                ms = 5;
            } else if (ms == 5) {
                msgLen = msgLen * 8 + b;
                ms = 6;
            } else if (ms == 6) {
                msgLen = msgLen * 8 + b;
                ms = 7;
            } else if (ms == 7) {
                msgLen = msgLen * 8 + b;

                messageBytes = new byte[msgLen];

                if (readMessage()) {
                    ms = 0;
                } else {
                    ms = 8;
                }
            } else if (ms == 8) {
                if (readMessage()) {
                    ms = 0;
                }
            }
        }
    }

    private void parse() {
        while (pos < max && !error) {
            byte b = bytes[pos];
            pos++;
            if (cs == 0) {
                // beginning of chunk
                if (b >= '0' && b <= '9') {
                    chunkLen = chunkLen * 16 + (b - '0');
                } else if (b >= 'A' && b <= 'F') {
                    chunkLen = chunkLen * 16 + (b - 'A' + 10);
                } else if (b == 13) {
                    cs = 1;
                } else  {
                    if (Broker.DEBUG) { println("Wrong input in Chunked-Encoding size, expected '0-9A-F' or CR; got '" + Integer.toString(b) + "'", true); }
                    error = true;
                }
            } else if (cs == 1) {
                if (b == 10) {
                    if (Broker.TRACE) { println("Got chunk of " + chunkLen + " bytes"); }
                    parseMessage();
                    if (chunkLen == 0) {
                        cs = 3;
                    } else {
                        cs = 2;
                    }
                } else {
                    if (Broker.DEBUG) { println("Wrong input after CR in Chunked-Encoding size; expected LF and got '" + Integer.toString(b) + "'", true); }
                    error = true;
                }
            } else if (cs == 2) {
                parseMessage();

                if (chunkLen == 0) {
                    cs = 3;
                }
            } else if (cs == 3) {
                if (b == 13) {
                    cs = 4;
                } else {
                    if (Broker.DEBUG) { println("Expected chunk end (LF) but got '" + Integer.toString(b) + "'", true); }
                    error = true;
                }
            } else if (cs == 4) {
                if (b == 10) {
                    if (Broker.TRACE) { println("Completed chunk"); }
                    cs = 0;
                } else {
                    if (Broker.DEBUG) { println("Expected chunk (CR) end but got '" + Integer.toString(b) + "'", true); }
                    error = true;
                }
            }
        }
    }

    public void processInput(SelectionKey key, ReadableByteChannel channel, int initalReadCount) throws IOException {
        int read = initalReadCount;
        while (read > 0) {

            buffer.flip();

            bytes = buffer.array();
            pos = 0;
            max = buffer.limit();

            parse();

            buffer.clear();
            if (error) {
                broker.closeChannel(key);
                return;
            }

            read = channel.read(buffer);
        }
    }
}
