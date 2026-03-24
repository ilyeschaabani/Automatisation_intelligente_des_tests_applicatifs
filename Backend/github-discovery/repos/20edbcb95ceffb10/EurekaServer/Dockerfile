FROM openjdk:17-alpine as mvn-build
WORKDIR /app/build
COPY ./src ./src
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN ./mvnw clean install -Dmaven.test.skip=true

FROM openjdk:17-alpine
WORKDIR /app
COPY --from=mvn-build /app/build/target/*.jar ./eureka-server.jar
EXPOSE 8761
CMD ["java", "-jar", "/app/eureka-server.jar"]