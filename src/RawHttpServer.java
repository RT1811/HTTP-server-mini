import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class RawHttpServer {
    public static void main (String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(8080);

        System.out.println("Listening on http://localhost:8080");

        while (true) {
            Socket client = serverSocket.accept();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                            client.getInputStream(),
                            StandardCharsets.UTF_8
                    )
            );

            String requestLine = reader.readLine();
            System.out.println("Request line: " + requestLine);

            String[] parts = requestLine.split(" ");

            String method = parts[0];
            String path = parts[1];
            String version = parts[2];

            System.out.println("Method: " + method);
            System.out.println("Path: " + path);
            System.out.println("Version: " + version);

            String line;

            while ((line = reader.readLine()) != null) {
                System.out.println(line);

                if (line.isEmpty()) {
                    break;
                }
            }

            String statusLine;
            String body;

            if (path.equals("/")) {
                statusLine = "HTTP/1.1 200 OK\r\n";
                body = "Welcome!";
            } else if (path.equals("/hello")) {
                statusLine = "HTTP/1.1 200 OK\r\n";
                body = "Hello, world!";
            } else if (path.equals("/hey")) {
                statusLine = "HTTP/1.1 200 OK\r\n";
                body = "Hello 👋";
            } else {
                statusLine = "HTTP/1.1 404 Not Found\r\n";
                body = "Not found";
            }

            byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);

            String responseHeaders =
                    statusLine +
                            "Content-Type: text/plain; charset=utf-8\r\n" +
                            "Content-Length: " + bodyBytes.length + "\r\n" +
                            "Connection: close\r\n" +
                            "\r\n";

            OutputStream out = client.getOutputStream();

            out.write(responseHeaders.getBytes(StandardCharsets.US_ASCII));
            out.write(bodyBytes);
            out.flush();

            client.close();
        }
    }
}
