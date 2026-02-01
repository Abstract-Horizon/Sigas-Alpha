package org.ah.sigas;

public class Main {

    public static void main(String[] args) throws Exception {
        int port = 8080;

        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Expected port number but got '" + args[0] + "'");
                System.exit(1);
            }
        }

        System.out.println("Starting server on port " +  port);

        Broker broker = new Broker(port);
        broker.loop();
    }

}
