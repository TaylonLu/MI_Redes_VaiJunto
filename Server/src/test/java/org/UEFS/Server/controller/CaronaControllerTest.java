package org.UEFS.Server.controller;

import org.UEFS.Server.domain.caronas.ArestaTrecho;
import org.UEFS.Server.domain.caronas.Carona;
import org.UEFS.Server.domain.caronas.CaronaRepo;
import org.UEFS.Server.domain.caronas.CaronaService;
import org.UEFS.Server.domain.usuarios.Usuario;
import org.UEFS.Server.exceptions.ForbiddenException;
import org.UEFS.Server.exceptions.IncorrectRequestException;
import org.UEFS.shared.JsonUtils;
import org.UEFS.shared.dto.ItinerarioDTO;
import org.UEFS.shared.dto.PassoItinerarioDTO;
import org.UEFS.shared.dto.requests.BuscaCaronaRequest;
import org.UEFS.shared.enums.Status;
import org.UEFS.shared.model.Request;
import org.UEFS.shared.model.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CaronaControllerTest {

    // ==========================================
    // 1. Mocks dos serviços e do repositório
    // ==========================================
    @Mock
    private UserController userController;

    @Mock
    private CaronaService caronaService;

    @Mock
    private CaronaRepo caronaRepo;

    @Mock
    private ControllerService mockDoSingleton;

    // Controlador do mock estático
    private MockedStatic<ControllerService> mockedStaticControllerService;

    // O nosso controller real que será testado
    private CaronaController controller;

    // Constantes para os testes de reserva
    private final String TOKEN_VALIDO = "token-auth-uuid-valido";
    private final String USER_ID = "passageiro-99";
    private final String CARONA_ID = "carona-abc";

    // ==========================================
    // 2. Setup e Teardown
    // ==========================================
    @BeforeEach
    public void setUp() {
        // Intercepta todos os métodos estáticos da classe ControllerService
        mockedStaticControllerService = Mockito.mockStatic(ControllerService.class);

        // Retorna o objeto falso em vez de criar um real
        mockedStaticControllerService.when(ControllerService::getInstance).thenReturn(mockDoSingleton);

        // Ensina o mockDoSingleton a devolver os nossos mocks de serviço
        Mockito.when(mockDoSingleton.get(CaronaService.class)).thenReturn(caronaService);
        Mockito.when(mockDoSingleton.get(UserController.class)).thenReturn(userController);

        // Instancia o Controller passando o mock do Repositório
        controller = new CaronaController(caronaRepo);
    }

    @AfterEach
    public void tearDown() {
        // Libera a classe estática para os próximos testes
        if (mockedStaticControllerService != null) {
            mockedStaticControllerService.close();
        }
    }

    // ==========================================
    // 3. Testes: BUSCAR ITINERÁRIOS
    // ==========================================
    @Test
    @DisplayName("Deve buscar itinerários com sucesso e retornar Status 200")
    public void testBuscarItinerarios_ComSucesso() throws Exception {
        BuscaCaronaRequest buscaDTO = new BuscaCaronaRequest(
                21, 4, 2, LocalDateTime.of(2026, 9, 14, 10, 0)
        );
        String payloadJson = JsonUtils.toJson(buscaDTO);
        Request request = new Request("BUSCAR_ITINERARIOS", payloadJson, TOKEN_VALIDO, payloadJson.length());

        Usuario usuarioFalso = new Usuario("email", "senha", "nome");
        Mockito.when(userController.validarUsuarioLogado(TOKEN_VALIDO)).thenReturn(usuarioFalso.getId());

        List<List<ArestaTrecho>> caminhosSimulados = new ArrayList<>();
        when(caronaService.buscarItinerarios(any(BuscaCaronaRequest.class))).thenReturn(caminhosSimulados);

        List<ItinerarioDTO> itinerariosDTOSimulados = new ArrayList<>();
        when(caronaService.toItinerariosDTO(caminhosSimulados)).thenReturn(itinerariosDTOSimulados);

        Response response = controller.buscarItinerarios(request);

        assertNotNull(response);
        assertEquals(Status.SUCESSO.getCodigo(), response.getStatus(), "O código de status deve ser sucesso (200)");
        assertEquals("ITINERARIOS_BUSCADOS", response.getTipo());
        assertNotNull(response.getDados());

        verify(userController, times(1)).validarUsuarioLogado(TOKEN_VALIDO);
        verify(caronaService, times(1)).buscarItinerarios(any(BuscaCaronaRequest.class));
        verify(caronaService, times(1)).toItinerariosDTO(caminhosSimulados);
    }

    @Test
    @DisplayName("Deve falhar ao buscar itinerário com token inválido")
    public void testBuscarItinerarios_FalhaTokenInvalido() {
        Request request = new Request("BUSCAR_ITINERARIOS", "{}", "token-falso", 2);

        doThrow(new ForbiddenException("Token inválido"))
                .when(userController).validarUsuarioLogado("token-falso");

        assertThrows(ForbiddenException.class, () -> controller.buscarItinerarios(request));
    }

    // ==========================================
    // 4. Testes: CONFIRMAR RESERVA
    // ==========================================
    @Test
    @DisplayName("Deve confirmar a reserva com sucesso recebendo ItinerarioDTO")
    void deveConfirmarReservaComSucesso() {
        PassoItinerarioDTO passo = new PassoItinerarioDTO(1, 2, CARONA_ID, "motoristaX");
        ItinerarioDTO itinerario = new ItinerarioDTO(List.of(passo));
        String jsonPayload = JsonUtils.toJson(itinerario);

        Request request = new Request("CONFIRMAR_RESERVA", jsonPayload, TOKEN_VALIDO, jsonPayload.length());
        Carona mockCarona = mock(Carona.class);

        when(userController.validarUsuarioLogado(TOKEN_VALIDO)).thenReturn(USER_ID);
        when(caronaRepo.getByID(CARONA_ID)).thenReturn(mockCarona);
        when(caronaService.confirmarReservaAtomica(eq(USER_ID), anyList(), any(ItinerarioDTO.class))).thenReturn(true);

        Response response = controller.confirmarReserva(request);

        // Assume-se que o Enum Status pode ser comparado diretamente ou pelo código
        assertEquals(Status.RESERVA_CONFIRMADA.getCodigo(), response.getStatus());

        ArgumentCaptor<List<ArestaTrecho>> captor = ArgumentCaptor.forClass(List.class);
        verify(caronaService).confirmarReservaAtomica(eq(USER_ID), captor.capture(), any(ItinerarioDTO.class));

        List<ArestaTrecho> itinerarioEnviadoAoService = captor.getValue();
        assertEquals(1, itinerarioEnviadoAoService.size());
        assertEquals(2, itinerarioEnviadoAoService.getFirst().destino());
        assertEquals(mockCarona, itinerarioEnviadoAoService.getFirst().carona());
    }

    @Test
    @DisplayName("Deve retornar CONFLITO_RESERVA se a reserva atômica falhar")
    void deveRetornarConflitoSeReservaFalhar() {
        PassoItinerarioDTO passo = new PassoItinerarioDTO(1, 2, CARONA_ID, "motoristaX");
        String jsonPayload = JsonUtils.toJson(new ItinerarioDTO(List.of(passo)));

        Carona caronaMock = mock(Carona.class);
        when(caronaRepo.getByID(anyString())).thenReturn(caronaMock);

        Request request = new Request("CONFIRMAR_RESERVA", jsonPayload, TOKEN_VALIDO, jsonPayload.length());

        when(userController.validarUsuarioLogado(TOKEN_VALIDO)).thenReturn(USER_ID);

        when(caronaService.confirmarReservaAtomica(eq(USER_ID), anyList(), any(ItinerarioDTO.class))).thenReturn(false);

        Response response = controller.confirmarReserva(request);

        assertEquals(Status.CONFLITO_RESERVA.getCodigo(), response.getStatus());
    }

    @Test
    @DisplayName("Deve lançar IncorrectRequestException se a carona não existir no banco")
    void deveLancarExcecaoSeCaronaNaoExistir() {
        PassoItinerarioDTO passo = new PassoItinerarioDTO(1, 2, "id-invalido", "motoristaX");
        String jsonPayload = JsonUtils.toJson(new ItinerarioDTO(List.of(passo)));
        Request request = new Request("CONFIRMAR_RESERVA", jsonPayload, TOKEN_VALIDO, jsonPayload.length());

        when(userController.validarUsuarioLogado(TOKEN_VALIDO)).thenReturn(USER_ID);
        when(caronaRepo.getByID("id-invalido")).thenReturn(null);

        IncorrectRequestException exception = assertThrows(IncorrectRequestException.class, () -> {
            controller.confirmarReserva(request);
        });

        assertEquals("Carona não encontrada.", exception.getMessage());
        verify(caronaService, never()).confirmarReservaAtomica(anyString(), anyList(), any(ItinerarioDTO.class));
    }
}