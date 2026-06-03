FROM maven:3.9.8-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/carbonlens-backend-0.1.0.jar app.jar
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8000
ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT:-8000} -jar app.jar"]
