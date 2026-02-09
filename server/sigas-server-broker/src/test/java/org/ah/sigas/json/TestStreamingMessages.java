package org.ah.sigas.json;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.net.spi.InetAddressResolver;
import java.net.spi.InetAddressResolver.LookupPolicy;
import java.nio.ByteBuffer;
import java.util.stream.Stream;

import org.ah.sigas.broker.Broker;
import org.ah.sigas.broker.game.Client;
import org.ah.sigas.broker.game.Game;
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
                        "master_token": "1234"
                    }
                    """).getResponseCode());

            assertEquals(204, sendHttpPostRequest(
                    gameId,
                    "POST",
                    internalPort,
                    "/game/" + gameId + "/client",
                    """
                    {
                        "token": "1235"
                    }
                    """).getResponseCode());

            assertEquals(204, sendHttpPostRequest(
                    gameId,
                    "PUT",
                    internalPort,
                    "/game/" + gameId + "/start",
                    "").getResponseCode());

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

                ByteBuffer heloBuffer = ByteBuffer.allocate(8);
                heloBuffer.putInt(8);
                heloBuffer.put("HELO".getBytes());
                sendMessage(out, heloBuffer);
                outputStream.flush();

                ByteBuffer pingBuffer = ByteBuffer.allocate(19);
                pingBuffer.putInt(19);
                pingBuffer.put("PING".getBytes());
                pingBuffer.putLong(System.currentTimeMillis());
                pingBuffer.put("END".getBytes());
                sendMessage(out, pingBuffer);
                outputStream.flush();


                Thread.sleep(100);

                Game game = broker.getGames().get(gameId);

                Client client = game.getClients().get(1);

                assertArrayEquals(heloBuffer.array(), client.getReceivedMessages().get(0));
                assertArrayEquals(pingBuffer.array(), client.getReceivedMessages().get(1));

            }

            System.out.println("Finished");
        } finally {
            broker.stop();
        }
    }

    private static void sendMessage(PrintStream out, ByteBuffer buf) {
        out.print(Integer.toHexString(buf.capacity()) + CRLF);
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
