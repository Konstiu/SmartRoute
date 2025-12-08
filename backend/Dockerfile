# Stage 1: Build with Maven
FROM maven:3-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime with Python
FROM eclipse-temurin:21-jdk

# Install Python 3.12
RUN apt-get update && \
    apt-get install -y software-properties-common && \
    add-apt-repository ppa:deadsnakes/ppa -y && \
    apt-get update && \
    apt-get install -y python3.12 python3.12-venv python3.12-pip && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy Python scripts
COPY python/ /app/python/

# Copy JAR from builder stage
COPY --from=builder /build/target/*.jar app.jar

# Make setup script executable
RUN chmod +x /app/python/setup.sh

EXPOSE 8080
ENTRYPOINT ["/bin/bash", "-c", "/app/python/setup.sh && java -jar /app.jar"]
