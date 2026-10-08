# Tiny HTTP/1.1 Server and Client in Java

A small HTTP/1.1 server and client built directly on top of Java TCP sockets.

The server manually parses incoming HTTP requests and constructs HTTP responses. The client manually constructs GET requests, parses HTTP responses, and determines the end of the response body using `Content-Length`.

No HTTP server, HTTP client, framework, or external networking library is used.

## What This Project Demonstrates

The goal of this project is to understand what happens underneath higher-level HTTP tools and frameworks.

The server performs:

```text
accept TCP connection
        ↓
read request line
        ↓
read headers until blank line
        ↓
extract method and path
        ↓
choose response
        ↓
construct HTTP status + headers
        ↓
write response body
        ↓
close connection
```

The client performs:

```text
open TCP connection
        ↓
construct HTTP GET request
        ↓
send request bytes
        ↓
read status line
        ↓
read headers until blank line
        ↓
find Content-Length
        ↓
read exactly N body bytes
        ↓
decode body as UTF-8
        ↓
print status and body
```

Together, they demonstrate both sides of a simple HTTP/1.1 exchange over TCP.

## Project Structure

```text
RawHttpServer.java
RawHttpClient.java
```

## HTTP Message Structure

HTTP headers are line-oriented.

A request to `/hello` looks like:

```http
GET /hello HTTP/1.1
Host: localhost:8080
Connection: close

```

On the wire, each line is terminated using:

```text
\r\n
```

and the header section ends with:

```text
\r\n\r\n
```

A successful response may look like:

```http
HTTP/1.1 200 OK
Content-Type: text/plain; charset=utf-8
Content-Length: 13
Connection: close

Hello, world!
```

The blank line separates the headers from the body.

---

# Server

The server uses `ServerSocket` and `Socket` directly.

It listens on:

```text
localhost:8080
```

and handles one HTTP request per TCP connection.

## Supported Routes

```text
GET /        → Welcome!
GET /hello   → Hello, world!
```

Any other path returns:

```text
HTTP/1.1 404 Not Found
```

with:

```text
Not found
```

as the body.

## Request Parsing

The first request line is split into:

```text
GET        /hello        HTTP/1.1
│             │              │
method        path           version
```

The remaining headers are read until the blank line marking the end of the HTTP header section.

## Response Construction

The server manually constructs:

```text
status line
headers
blank line
body
```

For example:

```http
HTTP/1.1 200 OK
Content-Type: text/plain; charset=utf-8
Content-Length: 13
Connection: close

Hello, world!
```

The response body is first encoded as UTF-8:

```java
byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
```

and `Content-Length` is calculated using:

```java
bodyBytes.length
```

rather than the Java string length.

## Connection Handling

The server process stays running and continuously accepts connections:

```text
server starts
    ↓
accept client A
    ↓
handle one request
    ↓
send one response
    ↓
close client A
    ↓
accept client B
    ↓
...
```

The HTTP connections themselves are non-persistent.

Each response includes:

```http
Connection: close
```

and the client socket is closed after one request/response exchange.

---

# Client

The client also uses `Socket` and raw streams directly.

It connects to the local server, manually creates an HTTP GET request, parses the response, and prints the returned status and body.

## Request Construction

A request is constructed manually:

```http
GET /hello HTTP/1.1
Host: localhost:8080
Connection: close

```

The request path can be supplied as a command-line argument.

If no path is supplied, the client defaults to:

```text
/
```

## Response Parsing

The client reads the response status line first:

```text
HTTP/1.1 200 OK
```

It then reads headers one line at a time until the blank line.

For example:

```text
Content-Type: text/plain; charset=utf-8
Content-Length: 13
Connection: close
```

The client extracts `Content-Length` to determine how many body bytes follow.

## Content-Length and Body Framing

Headers are line-oriented, but the body is byte-oriented.

For example:

```text
Content-Length: 13
```

means:

> Read exactly 13 body bytes.

The client uses a `readExactly()` loop because a single `InputStream.read()` call is not guaranteed to return every requested byte.

Conceptually:

```text
Content-Length: N
        ↓
allocate N-byte buffer
        ↓
read until totalRead == N
        ↓
decode completed byte array as UTF-8
```

If EOF is reached before all body bytes arrive, the client throws an `EOFException` rather than silently accepting a truncated response.

## Why One Buffered Byte Stream Is Used

The client uses one consistent buffered byte stream for both header and body parsing.

It does not mix a `BufferedReader` with direct reads from the underlying socket stream.

This avoids losing body bytes that may have already been read ahead into another buffer.

## UTF-8 Bodies

The response body is decoded only after all bytes have been collected:

```java
new String(bodyBytes, StandardCharsets.UTF_8);
```

This correctly handles non-ASCII bodies such as:

```text
Hello 👋
```

For that body:

```text
Hello    → 5 bytes
space    → 1 byte
👋       → 4 bytes

total    → 10 UTF-8 bytes
```

so the correct HTTP header is:

```text
Content-Length: 10
```

---

# Running the Project

## Requirements

- Java JDK
- No external dependencies

## Start the Server

Run:

```text
RawHttpServer
```

The server listens at:

```text
http://localhost:8080
```

## Run the Client

Run the client with a path:

```bash
java RawHttpClient /
```

or:

```bash
java RawHttpClient /hello
```

or:

```bash
java RawHttpClient /whatever
```

The server must already be running before the client connects.

## Example

Request:

```text
/hello
```

Output:

```text
HTTP/1.1 200 OK
Hello, world!
```

Unknown route:

```text
/whatever
```

Output:

```text
HTTP/1.1 404 Not Found
Not found
```

The server can also be tested independently with:

```bash
curl.exe -i http://localhost:8080/hello
```

or directly through a browser.

---

# Verification

The server and client were tested with:

- `GET /`
- `GET /hello`
- Unknown paths returning `404 Not Found`
- Multiple sequential HTTP connections
- Non-ASCII UTF-8 response bodies
- Artificially limited body reads to force partial-read handling
- A deliberately truncated body to verify premature EOF detection

During the partial-read test, the client was limited to reading only a small number of bytes per `read()` call. The complete body was still reconstructed correctly.

During the truncated-body test, the server advertised a larger `Content-Length` than the number of bytes it actually sent. The client correctly detected EOF before the promised body length was reached.

---

# Limitations

This project intentionally implements only a small controlled subset of HTTP/1.1.

It does not support:

- HTTPS
- Chunked transfer encoding
- Redirects
- Compression
- Cookies
- Request bodies
- POST requests
- Persistent HTTP connections
- Connection pooling
- Concurrent server clients
- Middleware
- Templates
- HTTP/2
- Full HTTP specification compliance

The client expects responses from the accompanying server using `Content-Length`.

---

# Key Takeaways

TCP provides a byte stream. HTTP defines structure on top of that stream.

On the server side:

```text
TCP bytes
→ HTTP request
→ method + path
→ HTTP response
→ TCP bytes
```

On the client side:

```text
TCP connection
→ HTTP request bytes
→ HTTP response headers
→ Content-Length
→ exact body bytes
→ decoded response
```

A high-level server route such as:

```javascript
app.get("/hello", (req, res) => {
    res.send("Hello, world!");
});
```

and a high-level HTTP client both hide much of this work.

This project implements a small portion of that work directly to make the request/response flow visible.