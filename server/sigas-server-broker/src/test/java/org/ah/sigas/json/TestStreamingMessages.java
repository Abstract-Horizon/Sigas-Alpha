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
                        "client_id": "01",
                        "alias": "game_master"
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
                        "client_id": "02",
                        "alias": "player1"
                    }
                    """).getResponseCode());

            assertEquals(204, sendHttpPostRequest(
                    gameId,
                    "PUT",
                    internalPort,
                    "/game/" + gameId + "/start",
                    "").getResponseCode());

            URL url = new URI("http://localhost:" + serverPort + "/game/stream/" + gameId).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("Authorization", "Token " + masterToken);
            connection.setRequestMethod("GET");
            connection.connect();
            try(InputStream masterInputStream = connection.getInputStream()) {

                try (
                        Socket socket = new Socket("127.0.0.1", serverPort);
                        OutputStream outputStream = socket.getOutputStream();
                        PrintStream out = new PrintStream(outputStream);
                    ) {


                    out.print("POST /game/stream/" + gameId + " HTTP/1.1" + CRLF);
                    out.print("Host: localhost:" + serverPort + CRLF);
                    out.print("Transfer-Encoding: chunked" + CRLF);
                    out.print("Connection: keep-alive" + CRLF);
                    out.print("User-Agent: MineTest" + CRLF);
                    out.print("Authorization: Token " + clientToken + CRLF);
                    out.print("Accept: */*" + CRLF);
                    out.print(CRLF);
                    out.flush();


                    byte[] joinJson = "{\"alias\":\"player1\",\"client_id\":\"02\"}".getBytes();
                    ByteBuffer joinBuffer = ByteBuffer.allocate(12 + joinJson.length);
                    joinBuffer.put("JOIN  01".getBytes());
                    joinBuffer.putInt(joinJson.length);
                    joinBuffer.put(joinJson);

                    ByteBuffer heloBuffer = ByteBuffer.allocate(12);
                    heloBuffer.put("HELO0000".getBytes());
                    heloBuffer.putInt(0);
                    sendMessage(out, heloBuffer);
                    outputStream.flush();

                    ByteBuffer pingBuffer = ByteBuffer.allocate(20);
                    pingBuffer.put("PING0000".getBytes());
                    pingBuffer.putInt(8);
                    pingBuffer.putLong(System.currentTimeMillis());
                    sendMessage(out, pingBuffer);
                    outputStream.flush();

                    Thread.sleep(100);

                    byte[] msg1Expected = joinBuffer.array();
                    msg1Expected[6] = '0';
                    msg1Expected[7] = '2';

                    byte[] msg2Expected = heloBuffer.array();
                    msg2Expected[6] = '0';
                    msg2Expected[7] = '2';

                    byte[] msg3Expected = pingBuffer.array();
                    msg3Expected[6] = '0';
                    msg3Expected[7] = '2';

                    byte[] msg1 = loadBuffer(masterInputStream);
                    assertArrayEquals(msg1Expected, msg1);

                    byte[] msg2 = loadBuffer(masterInputStream);
                    assertArrayEquals(msg2Expected, msg2);

                    byte[] msg3 = loadBuffer(masterInputStream);
                    assertArrayEquals(msg3Expected, msg3);
                }
            }
            System.out.println("Finished");
        } finally {
            broker.stop();
        }
    }

    private static byte[] loadBuffer(InputStream is) throws IOException {
        byte[] buf = new byte[12];
        int p = 0;
        while (p < buf.length) {
            int r = is.read(buf, p, buf.length - p);
            p += r;
        }
        int len = 0;
        for (int i = 0; i < 4; i++) {
            len = len * 256 + buf[8 + i];
        }

        byte[] res = new byte[12 + len];
        System.arraycopy(buf, 0, res, 0, 12);
        p = 12;
        while (p < res.length) {
            int r = is.read(res, p, res.length - p);
            p += r;
        }
        return res;
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
