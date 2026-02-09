package org.ah.sigas.broker;

import java.nio.channels.SelectionKey;

public interface ClientHandler extends Handler {
    public SelectionKey getAssociatedKey();

    public void open(SelectionKey associatedKey);
    public void close();
}
