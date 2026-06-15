# ---- Stage 1: build the jar ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy only the pom first, then pre-download dependencies (this layer caches)
COPY pom.xml .
RUN mvn -B dependency:go-offline

# Now copy the source and build (skip tests — they need Docker)
COPY src ./src
RUN mvn -B clean package -DskipTests

# ---- Stage 2: run the jar ----
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Copy ONLY the finished jar out of the build stage
COPY --from=build /app/target/*.jar app.jar

# Run as a non-root user (don't run containers as root)
RUN useradd --system --no-create-home appuser
USER appuser

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]