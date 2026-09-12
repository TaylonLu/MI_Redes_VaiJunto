FROM maven:3.9-eclipse-temurin-24

RUN apt-get update && apt-get install -y \
    libx11-6 \
    libxext6 \
    libxrender1 \
    libxtst6 \
    libxi6 \
    libgtk-3-0 \
    libgl1 \
    libglx0 \
    libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY pom.xml .

RUN mvn dependency:go-offline
RUN mvn compile

COPY src ./src

CMD ["mvn", "javafx:run"]
