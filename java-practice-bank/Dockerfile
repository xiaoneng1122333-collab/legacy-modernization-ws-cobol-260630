ARG MODULE=online:inquiry-api
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build
COPY . .
RUN ./gradlew :${MODULE}:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine AS runtime
ARG MODULE
WORKDIR /app
# Copy module jar: replace : with / to get directory path
COPY --from=builder /build/online/inquiry-api/build/libs/app.jar* ./app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
