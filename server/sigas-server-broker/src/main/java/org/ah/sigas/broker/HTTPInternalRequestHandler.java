package org.ah.sigas.broker;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectionKey;
import java.util.HashMap;
import java.util.Map;

import org.ah.sigas.broker.game.Client;
import org.ah.sigas.broker.game.Game;
import org.ah.sigas.json.JSONParser;


class ErrorAlreadySent extends Exception {
}


public class HTTPInternalRequestHandler extends HTTPRequestHandler {

    private interface RequestWithBody {
        void received(SelectionKey key, ReadableByteChannel channel, String body);
    }

    private static Class<?>[] ROUTE_WITH_GAME_ID_METHOD_PARAMETERS = new Class[] {
            SelectionKey.class, String.class, String.class
    };

    private static Map<String, String> ROUTES_WITH_GAME_ID = new HashMap<>() {{
        put("POST:", "createGame");
        put("DELETE:", "deleteGame");
        put("PUT:start", "startGame");
        put("POST:client", "addClient");
        put("DELETE:client", "removeClient");
    }};


    private StringBuilder requestBody = new StringBuilder();
    private RequestWithBody receivingBodyCallback = null;

    public HTTPInternalRequestHandler(Broker broker) {
        super(broker);
    }

    @Override
    protected void processRequest(SelectionKey key, ReadableByteChannel channel) throws IOException {
        if (path.startsWith("/game/")) {

            String gameId;
            String subpath = "";
            path = path.substring(6);
            int i = path.indexOf('/');
            if (i > 0) {
                gameId = path.substring(0, i);
                subpath = path.substring(i + 1);
            } else {
                gameId = path;
            }

            String routeKey = method.toUpperCase() + ":" + subpath;

            String methodName = ROUTES_WITH_GAME_ID.get(routeKey);

            if (methodName != null) {
                try {
                    Method method = getClass().getMethod(methodName, ROUTE_WITH_GAME_ID_METHOD_PARAMETERS);
                    loadBody(key, channel, new RequestWithBody() {
                        public void received(SelectionKey key, ReadableByteChannel channel, String body) {
                            try {
                                method.invoke(HTTPInternalRequestHandler.this, key, gameId, body);
                            } catch (IllegalAccessException | InvocationTargetException e) {
                                handleError(key, e);
                            }
                        }
                    });
                    return;
                } catch (NoSuchMethodException | SecurityException e) {
                    handleError(key, e);
                }
            } else {
                System.err.println(gameId + ":- Not found '" + routeKey + "'");
            }
        } else if (path.equals("/stop") && method.toUpperCase().equals("POST")) {
            broker.stop();
            System.err.println("Stopping server...");
            createSimpleResponse(key, 200, "OK", "Stopping...");
            return;
        }

        createSimpleResponse(key, 404, "NOT FOUND", "Method " + method + ", path " + path + " not found");
    }

    public void createGame(SelectionKey key, String gameId, String body) {
        try {
            JSONParser parser = new JSONParser(body);

            Map<String, Object> res = new HashMap<>();

            parser.parse(res);

            if (!res.containsKey("master_token")) {
                handleError(key, "Missing 'master_token'");
                return;
            }

            String masterToken = (String)res.get("master_token");

            String id = extractClientId(key, res);

            if (broker.getGames().containsKey(gameId)) {
                handleError(key, gameId + ":- Game with same key already exists");
                return;
            }

            Game game = new Game(gameId);
            broker.getGames().put(gameId, game);
            Client client = new Client(game, masterToken, id, true);
            game.addClient(client);

            if (Broker.INFO) { System.out.println(gameId + ":" + client.getClientId() + ":" + client.getToken() + " Game created."); }

            createSimpleResponse(key, 204, "OK");

        } catch (ErrorAlreadySent ignore) {
        } catch (Exception e) {
            handleError(key, e);
        }
    }

    public void startGame(SelectionKey key, String gameId, String body) {
        Game game = broker.getGames().get(gameId);
        if (game == null) {
            handleError(key, gameId + ":- Game with supplied key does not exist");
            return;
        }

        game.setState(Game.State.RUNNING);

        if (Broker.INFO) { System.out.println(gameId + ":- Game started"); }

        createSimpleResponse(key, 204, "OK");
    }

    public void addClient(SelectionKey key, String gameId, String body) {
        try {
            JSONParser parser = new JSONParser(body);

            Map<String, Object> res = new HashMap<>();

            parser.parse(res);

            if (!res.containsKey("token")) {
                handleError(key, "Missing 'token'");
                return;
            }

            String token = (String)res.get("token");
            String id = extractClientId(key, res);

            Game game = broker.getGames().get(gameId);
            if (game == null) {
                handleError(key, "Game with key " + gameId + " does not exist");
                return;
            }

            for (Client client : game.getClients().values()) {
                if (client.getToken().equals(token)) {
                    if (Broker.INFO) { System.out.println(gameId + ":" + client.getClientId() + ":" + token + " Client with same token already exists for game"); }
                    createSimpleResponse(key, 304, "NOT MODIFIED");
                    return;
                }
            }

            Client client = new Client(game, token, id, false);
            game.addClient(client);

            if (Broker.INFO) { System.out.println(gameId + ":" + client.getClientId() + ":" + client.getToken() + " Added client to game"); }

            createSimpleResponse(key, 204, "OK");
        } catch (ErrorAlreadySent ignore) {
        } catch (Exception e) {
            handleError(key, e);
        }
    }

    private String extractClientId(SelectionKey key, Map<String, Object> map) throws ErrorAlreadySent {
        Object clientId = map.get("client_id");
        if (clientId == null) {
            handleError(key, "Missing 'client_id'");
            throw new ErrorAlreadySent();
        }
        if (clientId instanceof String) {
            String s = (String)clientId;
            if (s.length() != 2) {
                handleError(key, "Value for 'client_id' has to be exactly two characters long.");
                throw new ErrorAlreadySent();
            }
            return s;
        } else {
            handleError(key, "Value for 'client_id' has to be positive integer between 0 and 65535 or two letter string");
            throw new ErrorAlreadySent();
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
