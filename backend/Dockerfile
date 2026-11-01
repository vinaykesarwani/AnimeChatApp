# Stage 1: Build the executable JAR.
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /build

# Only files allowed by .dockerignore are copied.
COPY . .
RUN chmod +x mvnw

# Runs Maven to download dependencies, compile the code, and package the application into a JAR.
RUN ./mvnw package -Dmaven.test.skip=true

# Stage 2: Run the application using the JRE.
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY --from=build /build/target/*.jar app.jar

# Run the application as a non-root user.
RUN groupadd --gid 10001 app
RUN useradd --uid 10001 --gid app --no-create-home app
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
