package org.UEFS.Server.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.UEFS.shared.JsonUtils;
import org.UEFS.shared.dto.Trecho;
import org.UEFS.shared.dto.requests.ReservaRequest;
import org.UEFS.shared.dto.requests.ReservaTrechoRequest; // Ajustado para a classe correta
import org.UEFS.shared.enums.Status;
import org.UEFS.Server.domain.caronas.Carona;
import org.UEFS.Server.domain.caronas.CaronaRepo;
import org.UEFS.Server.domain.caronas.CaronaService;
import org.UEFS.shared.model.Request;
import org.UEFS.shared.model.Response;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ConfirmarReservaControllerTest {

    @Mock
    private UserController userController;

    @Mock
    private CaronaService caronaService;

    @Mock
    private CaronaRepo caronaRepo;

    @Mock
    private ControllerService mockDoSingleton;

    @Mock
    private Carona caronaMock;

    private CaronaController controller;
    private MockedStatic<ControllerService> mockedStaticControllerService;

    @BeforeEach
    public void setUp() {
        mockedStaticControllerService = Mockito.mockStatic(ControllerService.class);
        mockedStaticControllerService.when(ControllerService::getInstance).thenReturn(mockDoSingleton);

        Mockito.when(mockDoSingleton.get(CaronaService.class)).thenReturn(caronaService);
        Mockito.when(mockDoSingleton.get(UserController.class)).thenReturn(userController);

        controller = new CaronaController(caronaRepo);
    }

    @AfterEach
    public void tearDown() {
        if (mockedStaticControllerService != null) {
            mockedStaticControllerService.close();
        }
    }

    @Test
    public void testConfirmarReserva_ComSucesso() throws Exception {
        // ==========================================
        // 1. ARRANGE
        // ==========================================
        String tokenValido = "token-passageiro-123";
        String idPassageiro = "user_passageiro_uuid";
        String idCarona = "carona_772e493b-ab28-4067-bfa4-4d109ef1bdd3";

        // Simula o usuário logado
        Mockito.when(userController.validarUsuarioLogado(tokenValido)).thenReturn(idPassageiro);

        // Cria o trecho e o objeto de requisição individual de cada trecho da reserva
        Trecho trecho = new Trecho(3, 5);
        ReservaTrechoRequest trechoRequest = new ReservaTrechoRequest(idCarona, trecho);

        // Agrupa na lista que compõe o ReservaRequest principal
        ReservaRequest reservaDTO = new ReservaRequest(List.of(trechoRequest));

        String payloadJson = JsonUtils.toJson(reservaDTO);
        Request request = new Request("CONFIRMAR_RESERVA", payloadJson, tokenValido, payloadJson.length());

        // Simula o repositório encontrando a carona
        Mockito.when(caronaRepo.getByID(idCarona)).thenReturn(caronaMock);

        // Simula o sucesso na transação atômica do serviço de caronas
        Mockito.when(caronaService.confirmarReservaAtomica(eq(idPassageiro), anyList())).thenReturn(true);

        // ==========================================
        // 2. ACT
        // ==========================================
        Response response = controller.confirmarReserva(request);

        // ==========================================
        // 3. ASSERT
        // ==========================================
        assertNotNull(response);
        assertEquals(Status.RESERVA_CONFIRMADA.getCodigo(), response.getStatus());
        assertEquals("RESERVA_CONFIRMADA", response.getTipo());

        verify(userController, times(1)).validarUsuarioLogado(tokenValido);
        verify(caronaRepo, times(1)).getByID(idCarona);
        verify(caronaService, times(1)).confirmarReservaAtomica(eq(idPassageiro), anyList());
    }
}