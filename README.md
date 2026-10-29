# Tiny HTTP/1.1 Server, Client, and Reverse Proxy in Java

A small networking project that implements three pieces of HTTP infrastructure directly on top of Java TCP sockets:

- A raw HTTP/1.1 server
- A raw HTTP/1.1 client
- A simple reverse proxy

No HTTP frameworks, built-in HTTP servers, HTTP clients, or external networking libraries are used.

The goal is to make the request/response flow visible underneath higher-level tools such as Express, `HttpClient`, and production reverse proxies.

---

## Project Structure

```text
RawHttpServer.java
RawHttpClient.java
ReverseProxy.java
```

The components run locally using:

```text
RawHttpServer  → localhost:8080
ReverseProxy   → localhost:8081
```

The client can connect directly to the backend or through the proxy.

---

# HTTP Server

The raw server uses `ServerSocket` and `Socket` directly.

It:

1. Accepts a TCP connection.
2. Reads the HTTP request line.
3. Reads headers until the blank line.
4. Extracts the method, request target, and HTTP version.
5. Chooses a response.
6. Manually constructs the HTTP status line and headers.
7. Sends the response body.
8. Closes the client connection.
9. Accepts the next request.

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

## Example Response

```http
HTTP/1.1 200 OK
Content-Type: text/plain; charset=utf-8
Content-Length: 13
Connection: close

Hello, world!
```

The server calculates `Content-Length` from the UTF-8 encoded body bytes:

```java
byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
```

rather than relying on Java string length.

---

# HTTP Client

The raw client uses `Socket` and raw streams to manually construct requests and parse responses.

The flow is:

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

## Example Request

```http
GET /hello HTTP/1.1
Host: localhost:8080
Connection: close

```

The request path can be supplied as a command-line argument.

For example:

```bash
java RawHttpClient /hello
```

## Response Framing

HTTP headers are line-oriented and terminated using:

```text
\r\n
```

The header section ends with:

```text
\r\n\r\n
```

The response body is different: it is byte-oriented.

If the server sends:

```text
Content-Length: 13
```

the client reads exactly 13 body bytes.

The client uses a `readExactly()` loop because one `InputStream.read()` call is not guaranteed to return all requested bytes.

If EOF occurs before the promised body length is received, the client throws an `EOFException` rather than silently accepting a truncated response.

## UTF-8 Handling

The body is decoded only after every body byte has been collected:

```java
new String(bodyBytes, StandardCharsets.UTF_8);
```

For example:

```text
Hello 👋
```

has a UTF-8 body length of 10 bytes even though its visible character count is smaller.

---

# Reverse Proxy

The reverse proxy listens on:

```text
localhost:8081
```

and forwards requests to the fixed backend:

```text
localhost:8080
```

The central idea is that the proxy participates in **two separate TCP connections**:

```text
Client
  │
  │ TCP connection #1
  ▼
ReverseProxy :8081
  │
  │ TCP connection #2
  ▼
RawHttpServer :8080
```

On the client-facing connection, the proxy behaves like an HTTP server.

On the backend-facing connection, it behaves like an HTTP client.

## Proxy Request Flow

```text
client sends request
        ↓
proxy parses request line + headers
        ↓
proxy preserves request target
        ↓
proxy opens new backend TCP connection
        ↓
proxy constructs new HTTP request
        ↓
backend sends response
        ↓
proxy parses status + headers
        ↓
proxy reads exact Content-Length body
        ↓
proxy constructs new client-facing response
        ↓
proxy forwards original body bytes
        ↓
close both connections
```

## Request Target Preservation

The proxy preserves the complete request target, including query strings.

For example:

```text
GET /hello?x=1 HTTP/1.1
```

becomes:

```text
GET /hello?x=1 HTTP/1.1
```

on the backend connection.

## Reconstructed Backend Request

The proxy does not blindly copy all incoming headers.

Instead it constructs a new backend request:

```http
GET /hello HTTP/1.1
Host: localhost:8080
Connection: close

```

This matters because the incoming client is connected to:

```text
localhost:8081
```

while the backend connection is addressed to:

```text
localhost:8080
```

Headers describing one connection should not automatically be reused for another.

## Backend Response Handling

The proxy reads:

- The backend status line
- `Content-Type`
- `Content-Length`
- The body bytes

It then constructs a new client-facing response using:

```text
backend status
Content-Type
Content-Length
Connection: close
```

The response body is forwarded as raw bytes.

The proxy intentionally does not decode and re-encode the body:

```text
backend bytes
    ↓
byte[]
    ↓
same bytes
    ↓
original client
```

This preserves content such as:

```text
Hello 👋
```

unchanged.

## Body Size Limit

Backend response bodies are limited to:

```text
1 MiB
```

before allocation.

This keeps the exercise simple while avoiding unbounded allocation from an invalid `Content-Length`.

## Timeouts

The proxy uses basic socket timeouts.

Backend connection timeout:

```text
2 seconds
```

Backend read timeout:

```text
2 seconds
```

Incoming client read timeout:

```text
2 seconds
```

These prevent the sequential proxy from blocking indefinitely on a stalled connection.

## 502 Bad Gateway

If the backend is unavailable or the backend exchange fails, the proxy returns:

```http
HTTP/1.1 502 Bad Gateway
Content-Type: text/plain; charset=utf-8
Content-Length: 11
Connection: close

Bad Gateway
```

The proxy process itself remains running and can continue accepting later requests.

---

# Running the Project

## Requirements

- Java JDK
- No external dependencies

## 1. Start the Backend Server

Run:

```text
RawHttpServer
```

It listens on:

```text
localhost:8080
```

## 2. Start the Reverse Proxy

Run:

```text
ReverseProxy
```

It listens on:

```text
localhost:8081
```

## 3. Test Through the Proxy

Using curl:

```bash
curl.exe -i http://localhost:8081/
```

```bash
curl.exe -i http://localhost:8081/hello
```

```bash
curl.exe -i http://localhost:8081/whatever
```

Requests can also be made directly to the backend:

```bash
curl.exe -i http://localhost:8080/hello
```

## Run the Raw Client

Example:

```bash
java RawHttpClient /
```

```bash
java RawHttpClient /hello
```

```bash
java RawHttpClient /whatever
```

---

# Verification

The project was tested with:

- `GET /`
- `GET /hello`
- Unknown routes returning `404 Not Found`
- Several sequential requests without restarting the server or proxy
- UTF-8 content such as `Hello 👋`
- Artificially limited client reads to verify partial-read handling
- Deliberately truncated HTTP bodies to verify premature EOF detection
- Query-string preservation through the reverse proxy
- Backend shutdown producing `502 Bad Gateway`
- Exact response-body byte forwarding through the proxy

---

# Limitations

This project intentionally implements only a small controlled subset of HTTP/1.1.

It does not support:

- HTTPS
- CONNECT
- Arbitrary proxy destinations
- Request bodies
- POST requests
- Chunked transfer encoding
- Persistent HTTP connections
- Connection pooling
- Compression
- Cookies
- Authentication
- Caching
- Load balancing
- Concurrent proxy connections
- Concurrent server connections
- Redirect handling
- HTTP/2
- Full HTTP specification compliance

The server, client, and proxy are designed to communicate with each other under known conditions using `Content-Length` and `Connection: close`.

---

# Key Takeaways

TCP provides a stream of bytes. HTTP defines structure on top of that stream.

The server turns:

```text
TCP bytes
→ HTTP request
→ method + target
→ HTTP response
→ TCP bytes
```

The client performs the opposite direction:

```text
TCP connection
→ HTTP request bytes
→ response status + headers
→ Content-Length
→ exact body bytes
→ decoded response
```

The reverse proxy combines both roles:

```text
Client
   ↓
Proxy acts as server
   ↓
Proxy acts as client
   ↓
Backend
   ↓
Proxy acts as client
   ↓
Proxy acts as server
   ↓
Client
```

The important distinction is that a reverse proxy does not simply reuse the original TCP connection.

It terminates one connection, understands enough of the HTTP message to construct another request, communicates with the backend over a second connection, and constructs a new response for the original client.