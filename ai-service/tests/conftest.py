import socket
import ipaddress

import pytest


@pytest.fixture(autouse=True)
def no_real_network_or_google_client(monkeypatch):
    """Fail closed: automated tests must never use ADC or a live provider."""
    from google.cloud import vision

    def forbidden(*args, **kwargs):
        raise AssertionError("Tests must inject a fake transport/provider/client")

    original_connect = socket.socket.connect

    def local_event_loop_only(sock, address):
        # Windows asyncio uses a loopback socket pair for its internal wakeup pipe.
        try:
            if isinstance(address, tuple) and ipaddress.ip_address(address[0]).is_loopback:
                return original_connect(sock, address)
        except ValueError:
            pass
        return forbidden()

    monkeypatch.setattr(vision, "ImageAnnotatorClient", forbidden)
    monkeypatch.setattr(socket.socket, "connect", local_event_loop_only)
    monkeypatch.setattr(socket, "getaddrinfo", forbidden)
    monkeypatch.delenv("GOOGLE_APPLICATION_CREDENTIALS", raising=False)
