FROM maven:3.9-eclipse-temurin-24

RUN apt-get update && apt-get install -y nodejs npm && npm install -g nodemon

WORKDIR /app

COPY . .

RUN mvn dependency:go-offline
RUN mvn compile

# Observa os módulos do servidor e shared. Executa especificando o módulo Server.
CMD nodemon --watch Server/src --watch Shared/src -e java --exec "mvn compile exec:java -pl Server"