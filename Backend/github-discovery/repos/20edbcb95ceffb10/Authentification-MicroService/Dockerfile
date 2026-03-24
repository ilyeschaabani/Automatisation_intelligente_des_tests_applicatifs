FROM openjdk:17-alpine as mvn-build
WORKDIR /app/build
COPY ./src ./src
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN ./mvnw clean install -Dmaven.test.skip=true

FROM openjdk:17-alpine
WORKDIR /app
COPY --from=mvn-build /app/build/target/*.jar ./auth-server.jar
EXPOSE 9006
CMD ["java", "-jar", "/app/auth-server.jar"]