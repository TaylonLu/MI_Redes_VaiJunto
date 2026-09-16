# ==========================================
# Estágio 1: Build da aplicação
# ==========================================
FROM maven:3.9-eclipse-temurin-24 AS builder

WORKDIR /app

# Cache de dependências do Maven
COPY pom.xml .
COPY Shared/pom.xml Shared/
COPY Custom/pom.xml Custom/
COPY Client/pom.xml Client/

RUN mvn dependency:go-offline -B

# Compila o módulo Client e suas dependências
COPY . .
RUN mvn clean package -pl Client -am -DskipTests

# ==========================================
# Estágio 2: Runtime com dependências de GUI
# ==========================================
FROM eclipse-temurin:24-jdk-ubuntu

# Instala apenas as bibliotecas gráficas essenciais para o JavaFX
RUN apt-get update && apt-get install -y --no-install-recommends \
    libgtk-3-0 \
    libx11-6 \
    libxext6 \
    libxrender1 \
    libxtst6 \
    libxi6 \
    libgl1 \
    libglx0 \
    libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copia o JAR compilado no estágio anterior
COPY --from=builder /app/Client/target/*.jar app.jar

# Define a variável de ambiente para conexão gráfica (X11 ou Display virtual)
ENV DISPLAY=:0

CMD ["java", "-jar", "app.jar"]