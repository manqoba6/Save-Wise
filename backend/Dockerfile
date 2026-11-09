# ---- build stage: compiles the app with Maven ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

# ---- run stage: just a JRE and the jar ----
FROM eclipse-temurin:21-jre
RUN useradd --system --create-home app \
    && mkdir -p /app/uploads \
    && chown -R app /app
WORKDIR /app
COPY --from=build /build/target/savewise-backend-*.jar app.jar
USER app
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
