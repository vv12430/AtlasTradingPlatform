FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
ARG SERVICE
RUN mvn -B -pl ${SERVICE}-service -am package -DskipTests
RUN cp ${SERVICE}-service/target/${SERVICE}-service-1.0.0.jar /app.jar
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system atlas && useradd --system --gid atlas atlas
COPY --from=build /app.jar app.jar
USER atlas
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-jar","app.jar"]
