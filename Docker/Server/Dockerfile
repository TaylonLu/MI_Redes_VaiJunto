# ==========================================
# Estágio 1: Build (Compilação do Projeto)
# ==========================================
FROM maven:3.9-eclipse-temurin-24 AS builder

WORKDIR /app

# 1. Copia apenas os arquivos do Maven/POMs primeiro para aproveitar o CACHE de dependências
COPY pom.xml .
COPY Shared/pom.xml Shared/
COPY Server/pom.xml Server/
COPY Custom/pom.xml Custom/

# Baixa as dependências e guarda em cache (só roda de novo se algum pom.xml mudar)
RUN mvn dependency:go-offline -B

# 2. Agora copia todo o código fonte e gera o .jar do Server
COPY . .
RUN mvn clean package -pl Server -am -DskipTests

# ==========================================
# Estágio 2: Runtime (Execução Leve)
# ==========================================
FROM eclipse-temurin:24-jre-alpine

WORKDIR /app

# Cria diretório de logs e copia o JAR compilado no estágio anterior
RUN mkdir -p /app/logs
COPY --from=builder /app/Server/target/*.jar app.jar

EXPOSE 8080

# Executa diretamente o JAR com flags de performance
CMD ["java", "-Djava.awt.headless=true", "-jar", "app.jar"]