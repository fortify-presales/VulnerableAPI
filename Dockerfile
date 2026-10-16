FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY .mvn/ .mvn/
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src/ src/
RUN mvn -B -DskipTests package && cp target/vulnapp-*.jar /src/app.jar

FROM eclipse-temurin:17-jre
RUN useradd --system --uid 10001 --no-create-home vulnapp \
    && mkdir -p /tmp/vulnapp-files \
    && chown vulnapp /tmp/vulnapp-files
WORKDIR /app
COPY --from=build /src/app.jar app.jar
USER 10001
EXPOSE 8080
# Railway injects PORT; default to the application's 8080 elsewhere.
ENTRYPOINT ["sh", "-c", "exec java -Dserver.port=${PORT:-8080} -jar /app/app.jar"]
