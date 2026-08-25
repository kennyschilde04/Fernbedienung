"""Mock-Beamer: spricht das Android-TV-Remote-Protokoll v2 gegen den Kotlin-Client.

Nutzt die Original-Protos (polo.proto / remotemessage.proto) mit der offiziellen
protobuf-Laufzeit, ist also eine unabhaengige Gegenprobe zum handgeschriebenen
Kotlin-Codec.
"""
import datetime, hashlib, os, socket, ssl, struct, sys, threading, traceback

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from protos import polo_pb2, remotemessage_pb2  # noqa: E402  (siehe run.sh)
from google.protobuf.internal.decoder import _DecodeVarint
from google.protobuf.internal.encoder import _EncodeVarint
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import NameOID

HERE = os.path.dirname(os.path.abspath(__file__))
RESULTS = []


def log(msg):
    print(f"[beamer] {msg}", flush=True)


def make_server_cert():
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "MockBeamer")])
    now = datetime.datetime.now(datetime.timezone.utc)
    cert = (
        x509.CertificateBuilder()
        .subject_name(name)
        .issuer_name(name)
        .public_key(key.public_key())
        .serial_number(x509.random_serial_number())
        .not_valid_before(now - datetime.timedelta(days=1))
        .not_valid_after(now + datetime.timedelta(days=365))
        .sign(key, hashes.SHA256())
    )
    with open(f"{HERE}/server.key", "wb") as f:
        f.write(key.private_bytes(serialization.Encoding.PEM,
                                  serialization.PrivateFormat.TraditionalOpenSSL,
                                  serialization.NoEncryption()))
    with open(f"{HERE}/server.crt", "wb") as f:
        f.write(cert.public_bytes(serialization.Encoding.PEM))
    return cert


def send(sock, msg):
    data = msg.SerializeToString()
    out = bytearray()
    _EncodeVarint(out.extend, len(data))
    sock.sendall(bytes(out) + data)


def recv(sock, cls):
    buf = b""
    while True:
        b = sock.recv(1)
        if not b:
            raise EOFError("Verbindung zu")
        buf += b
        if b[0] & 0x80 == 0:
            break
    length, _ = _DecodeVarint(buf, 0)
    data = b""
    while len(data) < length:
        chunk = sock.recv(length - len(data))
        if not chunk:
            raise EOFError("Verbindung zu")
        data += chunk
    msg = cls()
    msg.ParseFromString(data)
    return msg


def numbers(cert):
    pub = cert.public_key().public_numbers()
    return pub.n, pub.e


def hexbytes(value):
    h = f"{value:X}"
    if len(h) % 2:
        h = "0" + h
    return bytes.fromhex(h)


def make_context(client_cert_pem):
    ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    ctx.load_cert_chain(f"{HERE}/server.crt", f"{HERE}/server.key")
    ctx.verify_mode = ssl.CERT_REQUIRED
    ctx.load_verify_locations(client_cert_pem)
    return ctx


def pairing_server(server_cert, client_cert_pem, port):
    ctx = make_context(client_cert_pem)
    srv = socket.socket()
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(("127.0.0.1", port))
    srv.listen(1)
    log(f"Pairing-Port {port} bereit")
    raw, _ = srv.accept()
    conn = ctx.wrap_socket(raw, server_side=True)
    client_cert = x509.load_der_x509_certificate(conn.getpeercert(True))
    log("Client-Zertifikat empfangen (TLS mit Client-Auth funktioniert)")
    RESULTS.append(("client cert presented", True))

    msg = recv(conn, polo_pb2.OuterMessage)
    assert msg.HasField("pairing_request"), msg
    assert msg.protocol_version == 2, msg.protocol_version
    assert msg.status == polo_pb2.OuterMessage.STATUS_OK
    log(f"PairingRequest: service={msg.pairing_request.service_name!r} "
        f"client={msg.pairing_request.client_name!r}")
    RESULTS.append(("pairing_request", msg.pairing_request.service_name == "atvremote"))

    out = polo_pb2.OuterMessage(protocol_version=2, status=polo_pb2.OuterMessage.STATUS_OK)
    out.pairing_request_ack.server_name = "MockBeamer"
    send(conn, out)

    msg = recv(conn, polo_pb2.OuterMessage)
    assert msg.HasField("options"), msg
    enc = msg.options.input_encodings[0]
    ok = (enc.type == polo_pb2.Options.Encoding.ENCODING_TYPE_HEXADECIMAL
          and enc.symbol_length == 6
          and msg.options.preferred_role == polo_pb2.Options.ROLE_TYPE_INPUT)
    log(f"Options: encoding={enc.type} len={enc.symbol_length} role={msg.options.preferred_role}")
    RESULTS.append(("options", ok))

    out = polo_pb2.OuterMessage(protocol_version=2, status=polo_pb2.OuterMessage.STATUS_OK)
    e = out.options.input_encodings.add()
    e.type = polo_pb2.Options.Encoding.ENCODING_TYPE_HEXADECIMAL
    e.symbol_length = 6
    out.options.preferred_role = polo_pb2.Options.ROLE_TYPE_INPUT
    send(conn, out)

    msg = recv(conn, polo_pb2.OuterMessage)
    assert msg.HasField("configuration"), msg
    ok = (msg.configuration.encoding.type == polo_pb2.Options.Encoding.ENCODING_TYPE_HEXADECIMAL
          and msg.configuration.encoding.symbol_length == 6
          and msg.configuration.client_role == polo_pb2.Options.ROLE_TYPE_INPUT)
    log(f"Configuration: role={msg.configuration.client_role}")
    RESULTS.append(("configuration", ok))

    out = polo_pb2.OuterMessage(protocol_version=2, status=polo_pb2.OuterMessage.STATUS_OK)
    out.configuration_ack.SetInParent()
    send(conn, out)

    # Code wie ein echtes Geraet berechnen: 2 Zufallsbytes + erstes Byte des Hashes
    nonce = os.urandom(2)
    cn, ce = numbers(client_cert)
    sn, se = numbers(server_cert)
    h = hashlib.sha256()
    h.update(hexbytes(cn)); h.update(hexbytes(ce))
    h.update(hexbytes(sn)); h.update(hexbytes(se))
    h.update(nonce)
    digest = h.digest()
    code = f"{digest[0]:02X}{nonce.hex().upper()}"
    with open(f"{HERE}/code.txt", "w") as f:
        f.write(code)
    log(f"Angezeigter Code: {code}")

    msg = recv(conn, polo_pb2.OuterMessage)
    assert msg.HasField("secret"), msg
    match = msg.secret.secret == digest
    log(f"Secret vom Client stimmt: {match}")
    RESULTS.append(("pairing secret", match))

    out = polo_pb2.OuterMessage(protocol_version=2, status=polo_pb2.OuterMessage.STATUS_OK)
    out.secret_ack.secret = digest
    send(conn, out)
    conn.close()
    srv.close()
    log("Pairing abgeschlossen")


def remote_server(client_cert_pem, port):
    ctx = make_context(client_cert_pem)
    srv = socket.socket()
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(("127.0.0.1", port))
    srv.listen(1)
    log(f"Steuer-Port {port} bereit")
    raw, _ = srv.accept()
    conn = ctx.wrap_socket(raw, server_side=True)
    RM = remotemessage_pb2.RemoteMessage

    supported = 1 | 2 | 4 | 32 | 64 | 512
    out = RM()
    out.remote_configure.code1 = supported
    out.remote_configure.device_info.model = "MockBeamer 4K"
    out.remote_configure.device_info.vendor = "Testhersteller"
    out.remote_configure.device_info.unknown1 = 1
    out.remote_configure.device_info.unknown2 = "1"
    out.remote_configure.device_info.package_name = "com.google.android.tv.remote.service"
    out.remote_configure.device_info.app_version = "1.0.0"
    send(conn, out)

    msg = recv(conn, RM)
    assert msg.HasField("remote_configure"), msg
    info = msg.remote_configure.device_info
    log(f"Client-Configure: features={msg.remote_configure.code1} "
        f"model={info.model!r} paket={info.package_name!r}")
    RESULTS.append(("remote_configure reply", msg.remote_configure.code1 == (supported & (1 | 2 | 32 | 64 | 512))))

    out = RM()
    out.remote_set_active.active = 622
    send(conn, out)
    msg = recv(conn, RM)
    assert msg.HasField("remote_set_active"), msg
    log(f"Client-SetActive: {msg.remote_set_active.active}")
    RESULTS.append(("remote_set_active reply", msg.remote_set_active.active > 0))

    out = RM()
    out.remote_start.started = True
    send(conn, out)

    out = RM()
    out.remote_set_volume_level.volume_max = 100
    out.remote_set_volume_level.volume_level = 42
    out.remote_set_volume_level.volume_muted = False
    send(conn, out)

    out = RM()
    out.remote_ping_request.val1 = 4711
    out.remote_ping_request.val2 = 1
    send(conn, out)
    msg = recv(conn, RM)
    assert msg.HasField("remote_ping_response"), msg
    log(f"Ping-Antwort: {msg.remote_ping_response.val1}")
    RESULTS.append(("ping/pong", msg.remote_ping_response.val1 == 4711))

    seen_keys = []
    seen_links = []
    conn.settimeout(15)
    try:
        while len(seen_keys) < 2 or len(seen_links) < 2:
            msg = recv(conn, RM)
            if msg.HasField("remote_key_inject"):
                k = msg.remote_key_inject
                seen_keys.append((k.key_code, k.direction))
                log(f"Taste: {remotemessage_pb2.RemoteKeyCode.Name(k.key_code)} "
                    f"({k.key_code}), Richtung {remotemessage_pb2.RemoteDirection.Name(k.direction)}")
            elif msg.HasField("remote_app_link_launch_request"):
                seen_links.append(msg.remote_app_link_launch_request.app_link)
                log(f"App-Link: {msg.remote_app_link_launch_request.app_link}")
    except (socket.timeout, EOFError, ssl.SSLError) as exc:
        log(f"Ende: {exc}")

    RESULTS.append(("Taste OK (23/SHORT)", (23, 3) in seen_keys))
    RESULTS.append(("Taste HDMI 1 (243)", (243, 3) in seen_keys))
    RESULTS.append(("App-Link YouTube", "https://www.youtube.com" in seen_links))
    RESULTS.append((
        "App-Link HDMI-Passthrough",
        "content://android.media.tv/passthrough/com.droidlogic.tvinput/.services.Hdmi1InputService/HW5" in seen_links,
    ))
    conn.close()
    srv.close()


def main():
    client_cert_pem = sys.argv[1]
    server_cert = make_server_cert()
    try:
        pairing_server(server_cert, client_cert_pem, 16467)
        remote_server(client_cert_pem, 16466)
    except Exception:
        traceback.print_exc()
        RESULTS.append(("Ausnahme im Mock-Server", False))

    print("\n===== ERGEBNIS =====", flush=True)
    ok = True
    for name, good in RESULTS:
        print(f"{'OK  ' if good else 'FEHL'}  {name}")
        ok = ok and good
    print("GESAMT:", "BESTANDEN" if ok else "FEHLGESCHLAGEN")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
