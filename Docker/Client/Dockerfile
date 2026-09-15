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
    nodejs \
    npm \
    && npm install -g nodemon \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY . .

RUN mvn dependency:go-offline

CMD ["sh", "-c", "nodemon --watch Client/src --watch Custom/src --watch Shared/src -e java,fxml --exec 'mvn install -pl Client -am -DskipTests && mvn -pl Client javafx:run'"]