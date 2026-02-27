# Messages

Messages in Sigas have the following format:

- 4 bytes message type - normally ASCII (example 'HELO')
- 2 byets for flags - normally ASCII defaulting to two spaces
- 2 bytes for client id - ASCII hex number
- 4 byets for message size - big endian 'long' integer

Client ids have special format:
- '--' no destination
- '00' game master sending broadcastmessage
- '01' normally designated to the game master
- '02'.. normally designated to clients

When clients send messages, client id of message is going to be overwritten
with client's id no matter of what was originally put in and message routed
to the game master. Only special messages will have specially handling - see below.

## System Messages

Here are predefined, system used message types:

### HRTB

Zero body message sent by client every 2s (or set timeout) by client and then sent
back to it again. That way we know connection is good on both sides.

### HELO

Zero body message to be sent by client to game master when first time joined the game.

### JOIN

Message sent by broker to game master when client joins the broker. This is sent
when hub notifies broker that player has joined the game.

Message will have simple json in following format:
```json
{
  "client_id": client_id,
  "alias": alias
}
```

where client_id will match client id of the message and alias will be game alias.
In future there might be more fields passed to the game master.

### LEFT

Zero body message sent by broker to game master if broker has been notified by hub that player
has left the game.

### DISC

TODO - shape this message better and revisit semantics
Zero length message sent by broker to game master when detected client disconnect (inbound or outbound stream).
This is sent if for timeout period there's no connection, heart beat or such.

### RECN

TODO - shape this message better and revisit semantics
Zero length message sent by broker to game master when detected client reconnects 

### STRT

Broadcast message from game master to all client. Body is json which will be game specific.

### GEND

Zero body message send by broker to all clients and game master when notified by hub that game has ended.
After this message there won't be any more messages.

### PMSG

Private message send by game master or clients. Only message which client id won't be affected allowing
client to client private messages being sent during the game. Suggested format is:

```json
{
  "body": message_body
}
```

Other fields might be added in the future.
