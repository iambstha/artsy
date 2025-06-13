FROM openjdk:21-jdk-slim
VOLUME /tmp
RUN #mvn clean install -U -DskipTests

COPY target/artsy-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java","-jar","/app.jar"]
