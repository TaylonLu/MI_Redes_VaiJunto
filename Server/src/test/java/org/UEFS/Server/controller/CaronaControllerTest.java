package org.UEFS.Server.controller;

import org.UEFS.shared.dto.ItinerarioDTO;
import org.UEFS.shared.enums.Status;
import org.UEFS.Server.domain.caronas.ArestaTrecho;
import org.UEFS.Server.domain.caronas.CaronaRepo;
import org.UEFS.Server.domain.caronas.CaronaService;
import org.UEFS.Server.domain.usuarios.Usuario;
import org.UEFS.Server.exceptions.ForbiddenException;
import org.UEFS.shared.model.Request;
import org.UEFS.shared.model.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.UEFS.shared.dto.requests.BuscaCaronaRequest;
import org.UEFS.shared.JsonUtils;
// Importe suas classes de Response, Request, Status, ItinerarioDTO, etc.

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CaronaControllerTest {

    // 1. Mocks dos serviços e do repositório
    @Mock
    private UserController userController;

    @Mock
    private CaronaService caronaService;

    @Mock
    private CaronaRepo caronaRepo; // Adicionado o mock do repositório!

    // 2. Mock da instância do Singleton
    @Mock
    private ControllerService mockDoSingleton;

    // O nosso controller real que será testado (sem o @InjectMocks)
    private CaronaController controller;

    // Controlador do mock estático
    private MockedStatic<ControllerService> mockedStaticControllerService;

    @BeforeEach
    public void setUp() {
        // Passo 1: Intercepta todos os métodos estáticos da classe ControllerService
        mockedStaticControllerService = Mockito.mockStatic(ControllerService.class);

        // Passo 2: Ensina que, quando chamarem ControllerService.instance(),
        // deve retornar o nosso objeto falso (mockDoSingleton) em vez de criar um real
        mockedStaticControllerService.when(ControllerService::getInstance)
                .thenReturn(mockDoSingleton);

        // Passo 3: Ensina o mockDoSingleton a devolver os nossos mocks de serviço
        // NOTA: Se o seu método .get() receber uma Classe (ex: CaronaService.class),
        // troque as Strings abaixo pela Classe correspondente.
        Mockito.when(mockDoSingleton.get(CaronaService.class)).thenReturn(caronaService);
        Mockito.when(mockDoSingleton.get(UserController.class)).thenReturn(userController);

        // Passo 4: Instancia o Controller passando o mock do Repositório!
        controller = new CaronaController(caronaRepo);
    }

    @AfterEach
    public void tearDown() {
        // Passo 5: Libera a classe estática para os próximos testes
        if (mockedStaticControllerService != null) {
            mockedStaticControllerService.close();
        }
    }

    @Test
    public void testBuscarItinerarios_ComSucesso() throws Exception {
        // ==========================================
        // 1. ARRANGE (Preparação do cenário)
        // ==========================================
        String tokenValido = "token-auth-uuid-valido";

        // Criando a requisição baseada nos dados do seu caronas.json
        // Simulando a busca pela carona de origem 21 para destino 4
        BuscaCaronaRequest buscaDTO = new BuscaCaronaRequest(
                21,                                     // origemDesejada
                4,                                      // destinoDesejado
                2,                                      // vagasRequeridas
                LocalDateTime.of(2026, 9, 14, 10, 0)    // data_desejada
        );

        // Transformando o DTO em JSON, assim como o cliente enviaria
        String payloadJson = JsonUtils.toJson(buscaDTO);

        // Usando o seu construtor real: tipo, dados, token, tamanho
        Request request = new Request(
                "BUSCAR_ITINERARIOS",  // tipo da requisição
                payloadJson,           // dados (JSON)
                tokenValido,           // token
                payloadJson.length()   // tamanho do payload
        );

        // --- Configurando o comportamento dos Mocks ---

        // 1. Simula que o usuário logado é válido (o método retorna void e não lança exceção)
        Usuario usuarioFalso = new Usuario("email", "senha", "nome"); // ou o construtor da sua classe
        Mockito.when(userController.validarUsuarioLogado(tokenValido)).thenReturn(usuarioFalso.getId());

        // 2. Simula o retorno do algoritmo de grafos do caronaService
        List<List<ArestaTrecho>> caminhosSimulados = new ArrayList<>();
        when(caronaService.buscarItinerarios(any(BuscaCaronaRequest.class))).thenReturn(caminhosSimulados);

        // 3. Simula a conversão para DTOs
        List<ItinerarioDTO> itinerariosDTOSimulados = new ArrayList<>();
        // (Aqui você poderia adicionar um itinerário mockado na lista para testar o JSON de saída com mais precisão)
        when(caronaService.toItinerariosDTO(caminhosSimulados)).thenReturn(itinerariosDTOSimulados);

        // ==========================================
        // 2. ACT (Execução do método)
        // ==========================================
        Response response = controller.buscarItinerarios(request);

        // ==========================================
        // 3. ASSERT (Validação dos resultados)
        // ==========================================
        assertNotNull(response);
        assertEquals(Status.SUCESSO.getCodigo(), response.getStatus(), "O código de status deve ser sucesso (ex: 200)");
        assertEquals("ITINERARIOS_BUSCADOS", response.getTipo(), "O tipo de resposta deve corresponder ao esperado");

        // Verifica se o JSON retornado não é nulo ou vazio
        assertNotNull(response.getDados());

        // Verifica se os métodos das dependências foram realmente chamados
        verify(userController, times(1)).validarUsuarioLogado(tokenValido);
        verify(caronaService, times(1)).buscarItinerarios(any(BuscaCaronaRequest.class));
        verify(caronaService, times(1)).toItinerariosDTO(caminhosSimulados);
    }

    @Test
    public void testBuscarItinerarios_FalhaTokenInvalido() {
        Request request = new Request("BUSCAR_ITINERARIOS", "{}", "token-falso", 2);

        doThrow(new ForbiddenException("Token inválido"))
                .when(userController).validarUsuarioLogado("token-falso");

        assertThrows(ForbiddenException.class, () -> controller.buscarItinerarios(request));
    }
}