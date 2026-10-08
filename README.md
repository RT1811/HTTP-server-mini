# Raw Java HTTP Server

A tiny HTTP/1.1 server built directly on top of Java TCP sockets.

The project uses `ServerSocket` and `Socket` to manually read HTTP requests, inspect the request line and headers, choose a response, and write a complete HTTP response back to the client.

No HTTP framework or built-in HTTP server is used.

## What This Project Demonstrates

The main goal of this project is to understand what happens underneath frameworks such as Express.

A request such as:

```http
GET /hello HTTP/1.1
Host: localhost:8080
User-Agent: curl/8.21.0
Accept: */*
```

arrives through a TCP connection as bytes.

The server manually:

1. Accepts the TCP connection.
2. Reads the HTTP request line.
3. Reads headers until the blank line.
4. Extracts the HTTP method, path, and version.
5. Chooses a response based on the requested path.
6. Constructs an HTTP/1.1 response.
7. Writes the response bytes to the socket.
8. Closes the connection.

## Supported Routes

The server currently supports:

```text
GET /        → Welcome!
GET /hello   → Hello, world!
```

Any other path returns:

```text
404 Not Found
```

## HTTP Request Structure

A request begins with a request line:

```text
GET /hello HTTP/1.1
```

which contains:

```text
GET        /hello        HTTP/1.1
│             │              │
method        path           version
```

Headers follow the request line:

```http
Host: localhost:8080
User-Agent: curl/8.21.0
Accept: */*
```

The header section ends with a blank line.

On the wire, HTTP lines are terminated with:

```text
\r\n
```

so the end of the headers is represented by:

```text
\r\n\r\n
```

## HTTP Response Structure

A successful response looks like:

```http
HTTP/1.1 200 OK
Content-Type: text/plain; charset=utf-8
Content-Length: 13
Connection: close

Hello, world!
```

The response consists of:

```text
status line
headers
blank line
body
```

For an unknown path, the server returns:

```http
HTTP/1.1 404 Not Found
Content-Type: text/plain; charset=utf-8
Content-Length: 9
Connection: close

Not found
```

## Content-Length

`Content-Length` represents the number of bytes in the response body.

The body is encoded as UTF-8 before calculating its length:

```java
byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
```

The server then uses:

```java
bodyBytes.length
```

as the value of `Content-Length`.

## Connection Handling

The server stays running and continuously accepts new TCP connections:

```text
server starts
    ↓
accept client
    ↓
handle one HTTP request
    ↓
send one HTTP response
    ↓
close client connection
    ↓
accept next client
```

Each individual HTTP connection is non-persistent.

The response includes:

```http
Connection: close
```

and the socket is closed after one request/response exchange.

The server process itself remains running and waits for the next connection.

## Running the Server

### Requirements

- Java JDK
- No external dependencies

### Start the server

Run:

```text
RawHttpServer
```

The server listens on:

```text
http://localhost:8080
```

## Testing With curl

Test the root route:

```bash
curl.exe http://localhost:8080/
```

Expected response body:

```text
Welcome!
```

Test the hello route:

```bash
curl.exe http://localhost:8080/hello
```

Expected response body:

```text
Hello, world!
```

To inspect the HTTP response headers as well:

```bash
curl.exe -i http://localhost:8080/hello
```

Test the fallback route:

```bash
curl.exe -i http://localhost:8080/whatever
```

Expected status:

```text
HTTP/1.1 404 Not Found
```

with the body:

```text
Not found
```

## Testing With a Browser

The routes can also be visited directly:

```text
http://localhost:8080/
```

and:

```text
http://localhost:8080/hello
```

## Scope

This project intentionally implements only a very small portion of HTTP.

It does not include:

- Request bodies
- POST requests
- Persistent HTTP connections
- Keep-alive handling
- Middleware
- Routing frameworks
- Templates
- TLS
- HTTP/2
- Full HTTP specification compliance
- Concurrent client handling

The purpose is to understand the basic path from a TCP socket to an HTTP request and response.

## Key Takeaway

A high-level route such as:

```javascript
app.get("/hello", (req, res) => {
    res.send("Hello, world!");
});
```

ultimately sits on top of work similar to:

```text
TCP connection
      ↓
read request line
      ↓
read headers
      ↓
identify method and path
      ↓
choose response
      ↓
construct HTTP status + headers
      ↓
write body bytes
      ↓
close connection
```

HTTP is an application-layer protocol carried over the TCP byte stream.