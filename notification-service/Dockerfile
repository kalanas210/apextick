# ===== Stage 1: build the jar =====
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy only what's needed to download dependencies first. Docker caches
# this layer and only re-runs it when pom.xml changes — so ordinary code
# edits skip the slow dependency download entirely.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Now the source. Only this layer rebuilds when you change code.
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# ===== Stage 2: slim runtime image =====
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Take ONLY the finished jar out of the build stage — none of the JDK,
# Maven cache, or source comes with it. That's the whole point of a
# multi-stage build: a clean, small final image.
COPY --from=build /app/target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]