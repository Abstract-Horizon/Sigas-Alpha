package org.ah.sigas.broker;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectionKey;
import java.util.HashMap;
import java.util.Map;

import org.ah.sigas.broker.game.Client;
import org.ah.sigas.broker.game.Game;
import org.ah.sigas.json.JSONParser;

public class HTTPInternalRequestHandler extends HTTPRequestHandler {

    private interface RequestWithBody {
        void received(SelectionKey key, ReadableByteChannel channel, String body);
    }

    private static String CREATE_GAME_PATH = "/create-game/";
    private StringBuilder requestBody = new StringBuilder();
    private RequestWithBody receivingBodyCallback = null;

    public HTTPInternalRequestHandler(Broker broker) {
        super(broker);
    }

    @Override
    protected void processRequest(SelectionKey key, ReadableByteChannel channel) throws IOException {
          if (method.equalsIgnoreCase("POST") && path.startsWith(CREATE_GAME_PATH)) {
              String gameId = path.substring(CREATE_GAME_PATH.length());

              loadBody(key, channel, new RequestWithBody() {
                  public void received(SelectionKey key, ReadableByteChannel channel, String body) { createGame(key, gameId, body); }
              });
          } else {
              createSimpleResponse(key, 404, "NOT FOUND", "Method " + method + ", path " + path + " not found");
          }
    }


    private void createGame(SelectionKey key, String gameId, String body) {
        try {
            JSONParser parser = new JSONParser(body);

            Map<String, Object> res = new HashMap<>();

            parser.parse(res);

            if (!res.containsKey("master_token")) {
                handleError(key, "Missing 'master_token'");
                return;
            }

            String masterToken = (String)res.get("master_token");

            if (broker.getGames().containsKey(gameId)) {
                handleError(key, "Game with key " + gameId + " already exists");
                return;
            }

            Game game = new Game(gameId);
            broker.getGames().put(gameId, game);
            Client client = new Client(masterToken, true);
            game.addClient(client);

            if (Broker.INFO) { System.out.println("Game " + gameId + " created"); }

            createSimpleResponse(key, 204, "OK");

        } catch (Exception e) {
            handleError(key, e);
        }
    }


    private void loadBody(SelectionKey key, ReadableByteChannel channel, RequestWithBody receivingBodyCallback) throws IOException {
        if (!headers.containsKey("content-length")) {
            createSimpleResponse(key, 411, "MISSING CONTENT LENGTH", "Missing content length header");
        }

        if (pos < max) {
            requestBody.append(new String(bytes, pos, max - pos));
        }

        this.receivingBodyCallback = receivingBodyCallback;

        read(key, channel);
    }

    @Override
    public void read(SelectionKey key, ReadableByteChannel channel) throws IOException {
        if (receivingBodyCallback != null) {
            int contentLength = Integer.parseInt(headers.get("content-length"));

            if (requestBody.length() < contentLength) {

                int read = channel.read(buffer);
                while (read > 0 && requestBody.length() < contentLength) {

                    buffer.flip();

                    bytes = buffer.array();
                    pos = 0;
                    max = buffer.limit();

                    requestBody.append(new String(bytes, pos, max - pos));

                    buffer.clear();

                    if (requestBody.length() < contentLength) {
                        read = channel.read(buffer);
                    }
                }
            }
            if (requestBody.length() >= contentLength) {
                receivingBodyCallback.received(key, channel, requestBody.toString());
            }
        } else {
            super.read(key, channel);
        }
    }
}
