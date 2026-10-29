import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ReverseProxy {
    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(8081);

        System.out.println(
                "Reverse proxy listening on http://localhost:8081"
        );

        while (true) {
            Socket client = serverSocket.accept();
            client.setSoTimeout(2000);

            System.out.println("Client connected");

            BufferedInputStream clientIn =
                    new BufferedInputStream(client.getInputStream());

            String requestLine = readHttpLine(clientIn);

            System.out.println("Incoming request: " + requestLine);

            String[] parts = requestLine.split(" ");

            if (parts.length != 3) {
                throw new IOException("Malformed request line: " + requestLine);
            }

            String method = parts[0];
            String target = parts[1];
            String version = parts[2];

            System.out.println("Method: " + method);
            System.out.println("Target: " + target);
            System.out.println("Version: " + version);

            while (true) {
                String headerLine = readHttpLine(clientIn);

                if (headerLine.isEmpty()) {
                    break;
                }

                System.out.println("Header: " + headerLine);
            }

            if (!method.equals("GET")) {
                throw new IOException("Unsupported method: " + method);
            }

            try (Socket backend = new Socket()) {

                backend.connect(
                        new InetSocketAddress("localhost", 8080),
                        2000
                );

                backend.setSoTimeout(2000);

                System.out.println("Connected to backend");

                OutputStream backendOut = backend.getOutputStream();

                String backendRequest =
                        "GET " + target + " HTTP/1.1\r\n" +
                                "Host: localhost:8080\r\n" +
                                "Connection: close\r\n" +
                                "\r\n";

                backendOut.write(
                        backendRequest.getBytes(StandardCharsets.US_ASCII)
                );
                backendOut.flush();

                BufferedInputStream backendIn =
                        new BufferedInputStream(backend.getInputStream());

                String backendStatus = readHttpLine(backendIn);

                System.out.println("Backend status: " + backendStatus);

                Integer contentLength = null;
                String contentType = null;

                while (true) {
                    String headerLine = readHttpLine(backendIn);

                    if (headerLine.isEmpty()) {
                        break;
                    }

                    int colon = headerLine.indexOf(':');

                    if (colon == -1) {
                        throw new IOException(
                                "Malformed backend header: " + headerLine
                        );
                    }

                    String name = headerLine.substring(0, colon).trim();
                    String value = headerLine.substring(colon + 1).trim();

                    if (name.equalsIgnoreCase("Content-Length")) {
                        contentLength = Integer.parseInt(value);
                    } else if (name.equalsIgnoreCase("Content-Type")) {
                        contentType = value;
                    }
                }

                if (contentLength == null) {
                    throw new IOException(
                            "Unsupported backend response framing: missing Content-Length"
                    );
                }

                if (contentLength < 0 || contentLength > 1024 * 1024) {
                    throw new IOException(
                            "Invalid backend Content-Length: " + contentLength
                    );
                }

                byte[] bodyBytes = readExactly(backendIn, contentLength);

                OutputStream clientOut = client.getOutputStream();

                String responseHeaders =
                        backendStatus + "\r\n" +
                                "Content-Type: " + contentType + "\r\n" +
                                "Content-Length: " + bodyBytes.length + "\r\n" +
                                "Connection: close\r\n" +
                                "\r\n";

                clientOut.write(
                        responseHeaders.getBytes(StandardCharsets.US_ASCII)
                );

                clientOut.write(bodyBytes);
                clientOut.flush();

            } catch (IOException e) {
                System.out.println("Backend error: " + e.getMessage());

                send502(client);
            }

            client.close();
        }
    }

    private static String readHttpLine(BufferedInputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        int previous = -1;

        while (true) {
            int current = in.read();

            if (current == -1) {
                throw new EOFException("Stream ended before CRLF");
            }

            if (previous == '\r' && current == '\n') {
                break;
            }

            if (previous != -1) {
                buffer.write(previous);
            }

            previous = current;
        }

        return new String(buffer.toByteArray(), StandardCharsets.US_ASCII);
    }

    private static byte[] readExactly(BufferedInputStream in, int count) throws IOException {
        byte[] data = new byte[count];

        int totalRead = 0;

        while (totalRead < count) {
            int bytesRead = in.read(data, totalRead, count - totalRead);

            if (bytesRead == -1) {
                throw new EOFException(
                        "Stream ended before reading "
                                + count + " body bytes"
                );
            }

            totalRead += bytesRead;
        }

        return data;
    }

    private static void send502(Socket client) throws IOException {
        String body = "Bad Gateway";
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);

        String headers =
                "HTTP/1.1 502 Bad Gateway\r\n" +
                        "Content-Type: text/plain; charset=utf-8\r\n" +
                        "Content-Length: " + bodyBytes.length + "\r\n" +
                        "Connection: close\r\n" +
                        "\r\n";

        OutputStream out = client.getOutputStream();

        out.write(headers.getBytes(StandardCharsets.US_ASCII));
        out.write(bodyBytes);
        out.flush();
    }
}