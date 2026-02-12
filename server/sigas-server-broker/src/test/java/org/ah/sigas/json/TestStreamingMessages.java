package org.ah.sigas.json;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;

import org.ah.sigas.broker.Broker;
import org.junit.Test;

public class TestStreamingMessages {

    public static final String CRLF = "\r\n";

    @Test public void testSendingMessages() throws IOException, URISyntaxException, InterruptedException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {

        ServerSocket serverSocket = new ServerSocket(0);
        int serverPort = serverSocket.getLocalPort();
        serverSocket.close();

        ServerSocket internalSocket = new ServerSocket(0);
        int internalPort = internalSocket.getLocalPort();
        internalSocket.close();

        URI hubURI = new URI("http://localhost:8080");

        Broker broker = new Broker(serverPort, internalPort, hubURI);

        Thread brokerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                broker.loop();
            }
        });
        brokerThread.start();
        Thread.sleep(10);

        try {

            String gameId = "123";
            String masterToken = "1234";
            String clientToken = "1235";

            assertEquals(204, sendHttpPostRequest(
                    gameId,
                    "POST",
                    internalPort,
                    "/game/" + gameId,
                    """
                    {
                        "master_token": "1234",
                        "client_id": "01"
                    }
                    """).getResponseCode());

            assertEquals(204, sendHttpPostRequest(
                    gameId,
                    "POST",
                    internalPort,
                    "/game/" + gameId + "/client",
                    """
                    {
                        "token": "1235",
                        "client_id": "02"
                    }
                    """).getResponseCode());

            assertEquals(204, sendHttpPostRequest(
                    gameId,
                    "PUT",
                    internalPort,
                    "/game/" + gameId + "/start",
                    "").getResponseCode());

            URL url = new URI("http://localhost:" + serverPort + "/game/" + gameId + "/" + masterToken).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.connect();
            try(InputStream masterInputStream = connection.getInputStream()) {

                try (
                        Socket socket = new Socket("127.0.0.1", serverPort);
                        OutputStream outputStream = socket.getOutputStream();
                        PrintStream out = new PrintStream(outputStream);
                    ) {


                    out.print("POST /game/" + gameId + "/" + clientToken + " HTTP/1.1" + CRLF);
                    out.print("Host: localhost:" + serverPort + CRLF);
                    out.print("Transfer-Encoding: chunked" + CRLF);
                    out.print("Connection: keep-alive" + CRLF);
                    out.print("User-Agent: MineTest" + CRLF);
                    out.print("Accept: */*" + CRLF);
                    out.print(CRLF);
                    out.flush();

                    ByteBuffer heloBuffer = ByteBuffer.allocate(10);
                    heloBuffer.put("HELO".getBytes());
                    heloBuffer.putInt(2);
                    heloBuffer.putShort((short)0);
                    sendMessage(out, heloBuffer);
                    outputStream.flush();

                    ByteBuffer pingBuffer = ByteBuffer.allocate(18);
                    pingBuffer.put("PING".getBytes());
                    pingBuffer.putInt(10);
                    pingBuffer.putShort((short)0);
                    pingBuffer.putLong(System.currentTimeMillis());
                    sendMessage(out, pingBuffer);
                    outputStream.flush();

                    Thread.sleep(100);

                    byte[] msg1 = new byte[heloBuffer.array().length];
                    byte[] msg2 = new byte[pingBuffer.array().length];

                    byte[] msg1Expected = heloBuffer.array();
                    msg1Expected[8] = '0';
                    msg1Expected[9] = '2';

                    byte[] msg2Expected = pingBuffer.array();
                    msg2Expected[8] = '0';
                    msg2Expected[9] = '2';

                    loadBuffer(masterInputStream, msg1);
                    assertArrayEquals(msg1Expected, msg1);

                    loadBuffer(masterInputStream, msg2);
                    assertArrayEquals(msg2Expected, msg2);
                }
            }
            System.out.println("Finished");
        } finally {
            broker.stop();
        }
    }

    private static void loadBuffer(InputStream is, byte[] buf) throws IOException {
        int p = 0;
        while (p < buf.length) {
            int r = is.read(buf, p, buf.length - p);
            p += r;
        }
    }

    private static void sendMessage(PrintStream out, ByteBuffer buf) {
        out.print(Integer.toHexString(buf.capacity()).toUpperCase() + CRLF);
        out.write(buf.array(), 0, buf.capacity());
        out.print(CRLF);
        out.flush();
    }

    private HttpURLConnection sendHttpPostRequest(String gameId, String method, int port, String path, String body) throws URISyntaxException, IOException {
        URL url = new URI("http://localhost:" + port + path).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoOutput(true);
        connection.setRequestMethod(method);
        connection.connect();
        try(OutputStream os = connection.getOutputStream()) {
            os.write(body.getBytes());
        }
        return connection;
    }
}
