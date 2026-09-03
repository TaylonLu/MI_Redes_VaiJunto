FROM eclipse-temurin:24-jdk

WORKDIR /app

COPY src ./src

# Agora o javac vai procurar os arquivos .java no caminho completo do Maven
RUN javac -d out $(find src/main/java -name "*.java")

EXPOSE 2602

# O comando continua o mesmo, pois o pacote do Main.java não muda
CMD ["java", "-cp", "out", "org.UEFS.vaijunto.Main", "2602"]