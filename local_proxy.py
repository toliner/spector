#!/usr/bin/env python3
"""
Local HTTP proxy that forwards requests to the actual proxy with Basic authentication.
This works around Java's limitation with Bearer-only proxies.
"""

import os
import socket
import threading
import base64
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse
import ssl

# Get proxy settings from environment
UPSTREAM_PROXY = os.environ.get('https_proxy', '')
parsed = urlparse(UPSTREAM_PROXY)
PROXY_HOST = parsed.hostname
PROXY_PORT = parsed.port
PROXY_USER = parsed.username
PROXY_PASS = parsed.password

print(f"Starting local proxy server...")
print(f"Upstream proxy: {PROXY_HOST}:{PROXY_PORT}")
print(f"Proxy user: {PROXY_USER}")

class ProxyHandler(BaseHTTPRequestHandler):
    def do_CONNECT(self):
        """Handle CONNECT requests for HTTPS tunneling"""
        try:
            # Parse the destination
            host, port = self.path.split(':')
            port = int(port)

            # Connect to upstream proxy
            proxy_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            proxy_sock.connect((PROXY_HOST, PROXY_PORT))

            # Send CONNECT request with authentication
            auth_string = f"{PROXY_USER}:{PROXY_PASS}"
            auth_bytes = base64.b64encode(auth_string.encode('utf-8')).decode('ascii')

            connect_request = (
                f"CONNECT {host}:{port} HTTP/1.1\r\n"
                f"Host: {host}:{port}\r\n"
                f"Proxy-Authorization: Basic {auth_bytes}\r\n"
                f"Proxy-Connection: Keep-Alive\r\n"
                f"\r\n"
            )

            proxy_sock.sendall(connect_request.encode('utf-8'))

            # Read proxy response
            response = b""
            while b"\r\n\r\n" not in response:
                chunk = proxy_sock.recv(1024)
                if not chunk:
                    break
                response += chunk

            # Check if connection succeeded
            if b"200" in response.split(b"\r\n")[0]:
                # Send success response to client
                self.send_response(200, "Connection Established")
                self.end_headers()

                # Start bidirectional forwarding
                client_sock = self.connection
                client_sock.settimeout(None)  # Remove timeout for data transfer
                proxy_sock.settimeout(None)

                def forward(source, destination, name):
                    try:
                        while True:
                            data = source.recv(8192)
                            if not data:
                                break
                            destination.sendall(data)
                    except Exception as e:
                        pass
                    finally:
                        try:
                            source.shutdown(socket.SHUT_RD)
                        except:
                            pass
                        try:
                            destination.shutdown(socket.SHUT_WR)
                        except:
                            pass

                # Start forwarding threads
                client_to_proxy = threading.Thread(target=forward, args=(client_sock, proxy_sock, "client->proxy"))
                proxy_to_client = threading.Thread(target=forward, args=(proxy_sock, client_sock, "proxy->client"))

                client_to_proxy.daemon = True
                proxy_to_client.daemon = True

                client_to_proxy.start()
                proxy_to_client.start()

                # Wait for threads to complete
                client_to_proxy.join()
                proxy_to_client.join()
            else:
                self.send_error(502, "Bad Gateway")
                proxy_sock.close()

        except Exception as e:
            print(f"Error in CONNECT: {e}")
            self.send_error(502, "Bad Gateway")

    def log_message(self, format, *args):
        """Minimal logging"""
        print(f"{self.address_string()} - {format % args}")

if __name__ == "__main__":
    PORT = 8888
    server = HTTPServer(('127.0.0.1', PORT), ProxyHandler)
    print(f"Local proxy listening on 127.0.0.1:{PORT}")
    print(f"Configure Java to use: -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort={PORT}")
    print(f"                        -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort={PORT}")
    print()

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nShutting down...")
        server.shutdown()
