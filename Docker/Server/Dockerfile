FROM maven:3.9-eclipse-temurin-24

RUN apt-get update \
    && apt-get install -y nodejs npm \
    && npm install -g nodemon \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY . .

RUN mvn dependency:go-offline

CMD ["sh", "-c", "nodemon --watch Server/src --watch Shared/src -e java --exec 'mvn install -pl Server -am -DskipTests && mvn -pl Server exec:java'"]