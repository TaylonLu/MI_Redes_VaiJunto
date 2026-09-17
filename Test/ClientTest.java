import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.UUID;

public class ClientTest {
    public static void main(String[] args) {
        String ipServidor = "192.168.0.100";
        int porta = 2602;

        System.out.println("Iniciando Teste Automatizado de Cliente (ClientTest)...");

        try (Socket socket = new Socket(ipServidor, porta);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            System.out.println("Conectado com sucesso ao Servidor VaiJunto na porta " + porta);

            // Gerando um usuário único para o teste
            String emailUnico = "client_auto_" + UUID.randomUUID().toString().substring(0, 6) + "@teste.com";
            String senha = "senha123";
            String nome = "Usuário Automatizado";

            // 1. SIMULAR CADASTRO DE NOVO USUÁRIO
            String jsonCadastro = String.format("{\"email\":\"%s\",\"senha\":\"%s\",\"nome\":\"%s\"}", emailUnico, senha, nome);
            String cmdCadastro = String.format("CADASTRO|%s|null|%d", jsonCadastro, jsonCadastro.length());

            System.out.println("\n--- [1] Enviando Requisição: CADASTRO ---");
            out.println(cmdCadastro);
            String respCadastro = in.readLine();
            System.out.println("📥 Servidor respondeu: " + respCadastro);

            // 2. SIMULAR LOGIN PARA OBTER O TOKEN DE SESSÃO
            String jsonLogin = String.format("{\"email\":\"%s\",\"senha\":\"%s\"}", emailUnico, senha);
            String cmdLogin = String.format("LOGIN|%s|null|%d", jsonLogin, jsonLogin.length());

            System.out.println("\n--- [2] Enviando Requisição: LOGIN ---");
            out.println(cmdLogin);
            String respLogin = in.readLine();
            System.out.println("📥 Servidor respondeu: " + respLogin);

            // Extraindo o token da resposta do login (ex: 200|LOGIN_EFETUADO|<token>|...)
            String token = extrairToken(respLogin);
            System.out.println("🔑 Token extraído com sucesso: " + token);

            // 3. ENVIAR REQUEST AUTENTICADA (Consultar Reservas do Passageiro)
            String cmdReservas = String.format("MINHAS_RESERVAS||%s|1", token);

            System.out.println("\n--- [3] Enviando Requisição Autenticada: MINHAS_RESERVAS ---");
            out.println(cmdReservas);
            String respReservas = in.readLine();
            System.out.println("📥 Servidor respondeu: " + respReservas);

            // 4. ENVIAR REQUEST DE BUSCA DE ITINERÁRIOS
            String jsonBusca = "{\"origemDesejada\":1,\"destinoDesejado\":3}";
            String cmdBusca = String.format("BUSCAR_ITINERARIOS|%s|%s|%d", jsonBusca, token, jsonBusca.length());

            System.out.println("\n--- [4] Enviando Requisição: BUSCAR_ITINERARIOS ---");
            out.println(cmdBusca);
            String respBusca = in.readLine();
            System.out.println("📥 Servidor respondeu: " + respBusca);

            System.out.println("\n✨ Ciclo de requisições e respostas simulado com sucesso!");

        } catch (Exception e) {
            System.out.println("❌ Erro na comunicação com o servidor: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String extrairToken(String respostaLogin) {
        try {
            String[] partes = respostaLogin.split("\\|");
            if (partes.length >= 3) {
                return partes[2]; // Pega o token retornado na terceira coluna do protocolo
            }
        } catch (Exception ignored) {}
        return "null";
    }
}