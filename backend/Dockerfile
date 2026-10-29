FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder
ARG STUDENT_ID
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src/ src/
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine AS runtime
ARG STUDENT_ID
RUN addgroup -S sensorgroup && adduser -S -u 10001 -G sensorgroup sensor_user
WORKDIR /app
COPY --from=builder /build/target/eco-air-backend-1.0.0.jar /app/app.jar
RUN chown -R sensor_user:sensorgroup /app
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
ENV OPERATOR_ID=${STUDENT_ID}
EXPOSE 8080
USER sensor_user
ENTRYPOINT ["/bin/sh", "-c", "exec java $JAVA_TOOL_OPTIONS -jar /app/app.jar"]
