FROM openjdk:17-jdk-slim
WORKDIR /app

# Kopiraj sve fajlove u kontejner
COPY . /app

# Kompajliraj sve .java fajlove sa classpath-om
RUN find src -name "*.java" > sources.txt && javac -cp "lib/*" @sources.txt

# Pokreni aplikaciju sa classpath-om
CMD ["java", "-cp", "lib/*:src", "WebServer"]

EXPOSE 8080