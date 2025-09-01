FROM openjdk:17-jdk-slim

WORKDIR /app

COPY . /app

RUN ./gradlew build || ./mvnw package

EXPOSE 8080

CMD ["java", "-jar", "build/libs/your-app-name.jar"]