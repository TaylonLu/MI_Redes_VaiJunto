package org.UEFS.Server.controller;

import org.UEFS.shared.JsonUtils;
import org.UEFS.shared.dto.requests.CadastroMotoristaRequest;
import org.UEFS.shared.dto.requests.CadastroRequest;
import org.UEFS.shared.dto.requests.LoginRequest;
import org.UEFS.shared.dto.responses.LoginResponse;
import org.UEFS.shared.enums.Status;
import org.UEFS.Server.domain.usuarios.UserRepo;
import org.UEFS.Server.domain.usuarios.Usuario;
import org.UEFS.Server.exceptions.*;
import org.UEFS.shared.model.Request;
import org.UEFS.shared.model.Response;

public class UserController {
    private final ControllerService service = ControllerService.getInstance();
    private final SessionsController sessions = service.get(SessionsController.class);
    private final UserRepo repo;

    public UserController(UserRepo repositorio) {
        this.repo = repositorio;
    }

    public Response login(Request RQ) {
        LoginRequest login = JsonUtils.fromJson(RQ.getDados(), LoginRequest.class);
        Usuario user = repo.buscaPorEmail(login.email());

        if (user == null) throw new RecursoNaoEncontradoException("Usuário não encontrado.");
        if (!login.senha().equals(user.getSenha())) throw new DadosIncorretosException("Email ou senha incorretos.");

        String token = sessions.criarSessao(user.getId());

        LoginResponse dados = new LoginResponse(token, user.getSelfData());

        return new Response(Status.LOGIN_EFETUADO, JsonUtils.toJson(dados));
    }

    public Response logout(Request RQ) {
        String userID = sessions.validarToken(RQ.getToken());
        if (userID == null) throw new TokenInvalidoException();

        sessions.encerrarSessao(RQ.getToken());
        return new Response(Status.SUCESSO, "LOGOUT_EFETUADO", "");
    }

    public Response cadastrar(Request RQ) {
        CadastroRequest cadastro = JsonUtils.fromJson(RQ.getDados(), CadastroRequest.class);

        if (repo.buscaPorEmail(cadastro.email()) != null)
            throw new UsuarioJaCadastradoException();

        Usuario novoUser = new Usuario(cadastro.email(), cadastro.senha(), cadastro.nome());

        repo.salvar(novoUser);

        String token = sessions.criarSessao(novoUser.getId());

        LoginResponse dados = new LoginResponse(token, novoUser.getSelfData());

        return new Response(Status.CADASTRO_COMPLETO, JsonUtils.toJson(dados));
    }

    public Response cadastrarMotorista(Request RQ) {
        String userID = sessions.validarToken(RQ.getToken());
        if (userID == null) throw new TokenInvalidoException();

        Usuario user = repo.getByID(userID);

        if (user.isMotorista()) return new Response(Status.SEM_ALTERACAO);

        CadastroMotoristaRequest cadastro = JsonUtils.fromJson(RQ.getDados(), CadastroMotoristaRequest.class);
        user.tornarMotorista(cadastro.cnh(), cadastro.placaCarro(), cadastro.modeloCarro(), cadastro.corCarro());

        repo.salvar(user);

        String payload = JsonUtils.toJson(user.getSelfData());
        return new Response(Status.CADASTRO_COMPLETO, payload);
    }

    public String validarUsuarioLogado(String token) throws ForbiddenException {
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
