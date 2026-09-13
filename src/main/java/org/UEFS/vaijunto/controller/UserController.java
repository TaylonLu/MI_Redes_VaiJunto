package org.UEFS.vaijunto.controller;

import org.UEFS.shared.JsonUtils;
import org.UEFS.shared.dto.requests.CadastroMotoristaRequest;
import org.UEFS.shared.dto.requests.CadastroRequest;
import org.UEFS.shared.dto.requests.LoginRequest;
import org.UEFS.shared.dto.responses.LoginResponse;
import org.UEFS.shared.enums.Status;
import org.UEFS.vaijunto.domain.usuarios.UserRepo;
import org.UEFS.vaijunto.domain.usuarios.Usuario;
import org.UEFS.vaijunto.exceptions.ForbiddenException;
import org.UEFS.vaijunto.exceptions.IncorrectDataException;
import org.UEFS.vaijunto.server.Request;
import org.UEFS.vaijunto.server.Response;

public class UserController {
    private final ControllerService service = ControllerService.getInstance();
    private final SessionsController sessions = service.get(SessionsController.class);
    private final UserRepo repo;

    public UserController(UserRepo repositorio) {
        this.repo = repositorio;
    }

    public Response login(Request RQ) {
        try {
            LoginRequest login = JsonUtils.fromJson(RQ.getDados(), LoginRequest.class);
            Usuario user = repo.buscaPorEmail(login.email());

            if (user == null) return new Response(Status.USUARIO_NAO_CADASTRADO);
            if (!login.senha().equals(user.getSenha())) return new Response(Status.DADOS_INCORRETOS);

            String token = sessions.criarSessao(user.getId());

            LoginResponse dados = new LoginResponse(token, user.getData());

            return new Response(Status.SUCESSO.getCodigo(), "LOGIN_EFETUADO", JsonUtils.toJson(dados));
        } catch (IncorrectDataException e) {
            return new Response(Status.DADOS_INCORRETOS);
        }
    }


    public Response cadastrar(Request RQ) {
        try {
            CadastroRequest cadastro = JsonUtils.fromJson(RQ.getDados(), CadastroRequest.class);

            Usuario novoUser = new Usuario(cadastro.email(), cadastro.senha(), cadastro.nome());

            repo.salvar(novoUser);

            String token = sessions.criarSessao(novoUser.getId());

            LoginResponse dados = new LoginResponse(token, novoUser.getData());

            return new Response(Status.SUCESSO.getCodigo(), "CADASTRO_COMPLETO", JsonUtils.toJson(dados));

        } catch (IncorrectDataException e) {
            return new Response(Status.DADOS_INCORRETOS);
        }
    }

    public Response cadastrarMotorista(Request RQ) {
        try {

            String userID = sessions.validarToken(RQ.getToken());
            if (userID == null) return new Response(Status.TOKEN_INVALIDO);

            Usuario user = repo.getByID(userID);

            if (user.isMotorista()) return new Response(Status.ACAO_INVALIDA);

            CadastroMotoristaRequest cadastro = JsonUtils.fromJson(RQ.getDados(), CadastroMotoristaRequest.class);
            user.tornarMotorista(cadastro.cnh(), cadastro.placaCarro(), cadastro.modeloCarro(), cadastro.corCarro());

            repo.salvar(user);

            String payload = JsonUtils.toJson(user.getData());
            return new Response(Status.SUCESSO.getCodigo(), "CADASTRO_COMPLETO", payload);

        } catch (IncorrectDataException e) {
            return new Response(Status.DADOS_INCORRETOS);
        }
    }

    public String vaidadeUsuarioLogado(String token) throws ForbiddenException {
        String userID = sessions.validarToken(token);
        if (userID == null || userID.isBlank()) throw new ForbiddenException("Usuário não está logado.");

        return userID;
    }

    public Response atualizar(Request RQ) {
        return new Response(Status.NAO_IMPLEMENTADO);
    }

    public Usuario getByID(String userID) {
        return repo.getByID(userID);
    }
}
