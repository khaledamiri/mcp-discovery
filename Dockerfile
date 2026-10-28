FROM maven:3.9-eclipse-temurin-26 AS build
WORKDIR /src
COPY . .
RUN mvn -B -q -pl mcp-discovery-server -am package -DskipTests

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 discovery
USER discovery
COPY --from=build /src/mcp-discovery-server/target/mcp-discovery-server.jar /app/mcp-discovery-server.jar
EXPOSE 8761
ENTRYPOINT ["java", "-jar", "/app/mcp-discovery-server.jar"]
