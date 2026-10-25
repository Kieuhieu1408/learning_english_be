# Stage 1: Build
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /workspace/app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline || true
COPY src src
RUN ./mvnw clean package -DskipTests

# Stage 2: Run
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=builder /workspace/app/target/english-0.0.1-SNAPSHOT.jar app.jar
ENV PORT=8080
EXPOSE 8080
CMD ["sh", "-c", "java --enable-preview -Dserver.port=${PORT} -jar app.jar"]
