import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class RawHttpClient {
    public static void main(String[] args) throws IOException {
        String host = "localhost";
        int port = 8080;
        String path =  args.length > 0 ? args[0] : "/";

        try (Socket socket = new Socket(host, port)) {
            OutputStream out = socket.getOutputStream();

            BufferedInputStream in = new BufferedInputStream(socket.getInputStream());

            String request =
                    "GET " + path + " HTTP/1.1\r\n" +
                            "Host: " + host + ":" + port + "\r\n" +
                            "Connection: close\r\n" +
                            "\r\n";

            out.write(request.getBytes(StandardCharsets.US_ASCII));
            out.flush();

            String statusLine = readHttpLine(in);
            System.out.println("Status: " + statusLine);

            Integer contentLength = null;

            while (true) {
                String headerLine = readHttpLine(in);

                if (headerLine.isEmpty()) {
                    break;
                }

                System.out.println("Header: " + headerLine);

                int colon = headerLine.indexOf(':');

                if (colon == -1) {
                    throw new IOException("Malformed header: " + headerLine);
                }

                String name = headerLine.substring(0, colon).trim();
                String value = headerLine.substring(colon + 1).trim();

                if (name.equalsIgnoreCase("Content-Length")) {
                    contentLength = Integer.parseInt(value);
                }
            }

            if (contentLength == null) {
                throw new IOException(
                        "Unsupported response framing: missing Content-Length"
                );
            }

            System.out.println("Body length: " + contentLength);

            byte[] bodyBytes = readExactly(in, contentLength);

            String body = new String(bodyBytes, StandardCharsets.UTF_8);

            System.out.println("Body: " + body);
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

}
