package org.UEFS.Server.controller;

import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import org.UEFS.Server.domain.usuarios.UserRepo;
import org.UEFS.Server.domain.usuarios.Usuario;
import org.UEFS.Server.exceptions.*;
import org.UEFS.Server.http.HttpServer;
import org.UEFS.shared.JsonUtils;
import org.UEFS.shared.dto.requests.CadastroMotoristaRequest;
import org.UEFS.shared.dto.requests.CadastroRequest;
import org.UEFS.shared.dto.requests.LoginRequest;
import org.UEFS.shared.dto.responses.LoginResponse;
import org.UEFS.shared.enums.Status;
import org.UEFS.shared.model.Request;
import org.UEFS.shared.model.Response;
import org.jetbrains.annotations.NotNull;

public class UserController {
    private final ControllerService service = ControllerService.getInstance();
    private final SessionsController sessions = service.get(SessionsController.class);
    private final UserRepo repo;

    public UserController(UserRepo repositorio) {
        this.repo = repositorio;
    }

    public Response login(Request RQ) {
        LoginRequest login = JsonUtils.fromJson(RQ.getDados(), LoginRequest.class);
        return new Response(Status.LOGIN_EFETUADO, JsonUtils.toJson(doLogin(login)));
    }

    @Deprecated
    public Response logout(Request RQ) {
        String userID = sessions.validarToken(RQ.getToken());
        if (userID == null) throw new TokenInvalidoException();

        sessions.encerrarSessao(RQ.getToken());
        return new Response(Status.SUCESSO, "LOGOUT_EFETUADO", "");
    }

    @Deprecated
    public Response cadastrar(Request RQ) {
        CadastroRequest cadastro = JsonUtils.fromJson(RQ.getDados(), CadastroRequest.class);

        if (repo.buscaPorEmail(cadastro.email()) != null)
            throw new UsuarioJaCadastradoException();

        Usuario novoUser = new Usuario(cadastro.email(), cadastro.senha(), cadastro.nome());

        repo.salvar(novoUser);
        repo.salvarNoDisco();

        String token = sessions.criarSessao(novoUser.getId());

        LoginResponse dados = new LoginResponse(token, novoUser.getSelfData());

        return new Response(Status.CADASTRO_COMPLETO, JsonUtils.toJson(dados));
    }

    @Deprecated
    public Response cadastrarMotorista(Request RQ) {
        String userID = sessions.validarToken(RQ.getToken());
        if (userID == null) throw new TokenInvalidoException();

        Usuario user = repo.getByID(userID);

        if (user.isMotorista()) return new Response(Status.SEM_ALTERACAO);

        CadastroMotoristaRequest cadastro = JsonUtils.fromJson(RQ.getDados(), CadastroMotoristaRequest.class);
        user.tornarMotorista(cadastro.cnh(), cadastro.placaCarro(), cadastro.modeloCarro(), cadastro.corCarro());

        repo.salvar(user);
        repo.salvarNoDisco();

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

    public void getByID(@NotNull Context context) {
        System.out.println("Pegando dados.");
        var usuario = repo.getByID(context.pathParam("id"));
        System.out.println("ID buscado: " + context.pathParam("id"));
        if (usuario == null) throw new NotFoundResponse("Usuário não encontrado.");
        context.json(usuario.getSelfData());
    }

    public HttpServer.Acesso getAcesso(String token) {
        if (token == null) return null;

        String userID = sessions.validarToken(token);
        if (userID == null) return null;

        Usuario user = repo.getByID(userID);
        if (user == null) return null;

        return user.isMotorista() ? HttpServer.Acesso.MOTORISTA : HttpServer.Acesso.PASSAGEIRO;
    }

    public void login(@NotNull Context context) {
        System.out.println("Logando...");
        LoginRequest login = context.bodyAsClass(LoginRequest.class);
        LoginResponse dados = doLogin(login);
        context.json(dados);
        System.out.println("Logado.");
    }

    private LoginResponse doLogin(LoginRequest login) {
        Usuario user = repo.buscaPorEmail(login.email());

        if (user == null) throw new RecursoNaoEncontradoException("Usuário não encontrado.");
        if (!login.senha().equals(user.getSenha())) throw new DadosIncorretosException("Email ou senha incorretos.");

        String token = sessions.getSession(user.getId()); // Pega a sessão caso já exista ou cria uma

        return new LoginResponse(token, user.getSelfData());
    }

    public void cadastrar(@NotNull Context context) {
        System.out.println("Cadastrando...");
        CadastroRequest cadastro = context.bodyAsClass(CadastroRequest.class);

        if (repo.buscaPorEmail(cadastro.email()) != null)
            throw new UsuarioJaCadastradoException();

        Usuario novoUser = new Usuario(cadastro.email(), cadastro.senha(), cadastro.nome());

        repo.salvar(novoUser);
        repo.salvarNoDisco();

        String token = sessions.criarSessao(novoUser.getId());

        LoginResponse dados = new LoginResponse(token, novoUser.getSelfData());

        System.out.println("Cadastrado.");

        context.json(dados);
    }

    public void logout(@NotNull Context context) {
        String token = context.header("Authorization");
        String userID = sessions.validarToken(token);
        if (userID == null) throw new TokenInvalidoException();

        sessions.encerrarSessao(token);
    }

    public void cadastrarMotorista(@NotNull Context context) {
        String userID = sessions.validarToken(context.header("Authorization"));
        if (userID == null) throw new TokenInvalidoException();

        Usuario user = repo.getByID(userID);

        if (user.isMotorista()) return;

        CadastroMotoristaRequest cadastro = context.bodyAsClass(CadastroMotoristaRequest.class);
        user.tornarMotorista(cadastro.cnh(), cadastro.placaCarro(), cadastro.modeloCarro(), cadastro.corCarro());

        repo.salvar(user);
        repo.salvarNoDisco();

        context.json(user.getSelfData());
    }
}
