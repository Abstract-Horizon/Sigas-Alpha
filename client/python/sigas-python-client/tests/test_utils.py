import socket


def find_free_port() -> int:
    _socket = socket.socket()
    _socket.bind(('', 0))
    port = _socket.getsockname()[1]
    _socket.close()
    return port
