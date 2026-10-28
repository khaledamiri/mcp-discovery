# Builds one sample application. Build context = repository root:
#   docker build -f samples/Dockerfile --build-arg MODULE=jira-mcp-mock -t jira-mcp-mock .
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY . .
RUN mvn -B -q install -DskipTests \
 && mvn -B -q -f samples/pom.xml package -DskipTests

FROM eclipse-temurin:21-jre
ARG MODULE
RUN useradd --system --uid 10001 app
USER app
COPY --from=build /src/samples/${MODULE}/target/${MODULE}.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
