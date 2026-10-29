FROM eclipse-temurin:22-jdk

WORKDIR /app

COPY src/RawHttpServer.java .
COPY src/ReverseProxy.java .

RUN  javac RawHttpServer.java ReverseProxy.java

CMD ["java", "RawHttpServer"]