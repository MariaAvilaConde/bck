# Dockerfile
FROM maven:3.9.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B -Dmaven.test.skip=true -Dspring.profiles.active=prod

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
COPY --from=builder /build/target/*.jar app.jar

ENV JAVA_OPTS="\
-Xms64m \
-Xmx256m \
-XX:MaxMetaspaceSize=96m \
-XX:CompressedClassSpaceSize=32m \
-Xss512k \
-XX:+UseSerialGC \
-XX:+AlwaysPreTouch \
-XX:+UseStringDeduplication \
-Djava.awt.headless=true \
-Dspring.main.lazy-initialization=true \
-Dspring.output.ansi.enabled=ALWAYS \
-Dserver.tomcat.max-threads=50 \
-Dreactor.netty.ioWorkerCount=1"

EXPOSE 8087
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
