FROM maven:3.9-eclipse-temurin-24

RUN apt-get update && apt-get install -y nodejs npm && npm install -g nodemon

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn dependency:go-offline
RUN mvn compile

CMD nodemon --watch src -e java,fxml --exec "mvn compile exec:java"