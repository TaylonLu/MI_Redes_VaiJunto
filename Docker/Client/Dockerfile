FROM maven:3.9-eclipse-temurin-24

# Instala as dependências gráficas do JavaFX e o nodemon em um único comando
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
RUN mvn compile

# Executa o Nodemon observando as pastas do Cliente, Custom e Shared
CMD nodemon --watch Client/src --watch Custom/src --watch Shared/src -e java,fxml --exec "mvn compile javafx:run -pl Client"