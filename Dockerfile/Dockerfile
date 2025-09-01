FROM openjdk:17
WORKDIR /app

# Kopiraj sve fajlove u kontejner
COPY . /app

# Kompajliraj Java fajlove
RUN javac src/*.java

# Pokreni aplikaciju
CMD ["java", "src.WebServer"]