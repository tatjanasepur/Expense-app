FROM openjdk:17-jdk-slim
WORKDIR /app
COPY . /app
RUN javac WebServer.java ExpenseTracker.java
EXPOSE 8080
CMD ["java", "WebServer"]