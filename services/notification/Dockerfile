FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /build
COPY pom.xml ./pom.xml
COPY libs ./libs
COPY services ./services
RUN apk add --no-cache maven \
 && mvn -B -pl services/notification -am -DskipTests package

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /build/services/notification/target/notification-service.jar app.jar
USER app
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=20s \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"UP"' || exit 1
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","/app/app.jar"]
