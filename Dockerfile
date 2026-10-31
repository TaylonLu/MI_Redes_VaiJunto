FROM maven:3.9-eclipse-temurin-24

WORKDIR /app

# 1. Copia apenas os descritores do Maven (ajuste os caminhos se os POMs dos submódulos estiverem noutros diretórios)
COPY pom.xml .
COPY Server/pom.xml Server/
COPY Shared/pom.xml Shared/

# 2. Baixa as dependências. Esta camada ficará em cache até que um pom.xml seja alterado!
RUN mvn dependency:go-offline

# 3. Agora sim, copia todo o código-fonte (.java, resources, etc)
COPY . /app

# Expõe as portas do consórcio
EXPOSE 2602 2603 2604 2605

# O comando compila o Server E as suas dependências (Shared), instala no cache e depois executa
CMD sh -c "mvn -q -am -pl Server install -DskipTests && mvn -q -pl Server exec:java"