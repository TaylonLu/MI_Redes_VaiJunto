package org.UEFS.Server.controller;

import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import org.UEFS.Server.domain.usuarios.UserRepo;
import org.UEFS.Server.domain.usuarios.Usuario;
import org.UEFS.Server.exceptions.*;
import org.UEFS.Server.http.HttpServer;
import org.UEFS.Server.http.cluster.PeerClient;
import org.UEFS.shared.JsonUtils;
import org.UEFS.shared.dto.OtherUserDTO;
import org.UEFS.shared.dto.SelfUserDTO;
import org.UEFS.shared.dto.requests.CadastroMotoristaRequest;
import org.UEFS.shared.dto.requests.CadastroRequest;
import org.UEFS.shared.dto.requests.LoginRequest;
import org.UEFS.shared.dto.responses.LoginResponse;
import org.UEFS.shared.enums.Status;
import org.UEFS.shared.model.Request;
import org.UEFS.shared.model.Response;
import org.UEFS.shared.model.ServerException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.UEFS.Server.http.Utils.tokenDe;

public class UserController {
    private static final Logger log = LoggerFactory.getLogger(UserController.class);
    private final ControllerService service = ControllerService.getInstance();
    private final SessionsController sessions = service.get(SessionsController.class);
    private final PeerClient client = service.get(PeerClient.class);
    private final UserRepo repo;

    public UserController(UserRepo repositorio) {
        this.repo = repositorio;
    }

    /**
     * Verifica se o token recebido está no banco de dados de usuários logados.
     * @param token string para verificação
     * @return id do usuário caso ele esteja logado.
     * @throws ForbiddenException
     */
    public String validarUsuarioLogado(String token) throws ForbiddenException {
        String userID = sessions.validarToken(token);
        if (userID == null || userID.isBlank()) throw new ForbiddenException("Usuário não está logado.");

        return userID;
    }

    public Usuario getByID(String userID) {
        return repo.getByID(userID);
    }

    public void getByID(@NotNull Context context) {
        var usuario = repo.getByID(context.pathParam("id"));
        if (usuario == null) throw new NotFoundResponse("Usuário não encontrado.");
        context.json(usuario.getSelfData());

        log.info("[BUSCA] ID buscado: {}", context.pathParam("id"));
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
        LoginRequest login = context.bodyAsClass(LoginRequest.class);
        LoginResponse dados = doLogin(login);
        context.json(dados);

        log.info("[LOGIN] Novo login do usuário '{}'", dados.usuario().nome());
    }

    private LoginResponse doLogin(LoginRequest login) {
        Usuario user = repo.buscaPorEmail(login.email());

        if (user == null) throw new RecursoNaoEncontradoException("Usuário não encontrado.");
        if (!login.senha().equals(user.getSenha())) throw new DadosIncorretosException("Email ou senha incorretos.");

        String token = sessions.getSession(user.getId()); // Pega a sessão caso já exista ou cria uma

        return new LoginResponse(token, user.getSelfData());
    }

    public void cadastrar(@NotNull Context context) {
        CadastroRequest cadastro = context.bodyAsClass(CadastroRequest.class);

        if (repo.buscaPorEmail(cadastro.email()) != null)
            throw new UsuarioJaCadastradoException();

        Usuario novoUser = new Usuario(cadastro.email(), cadastro.senha(), cadastro.nome());

        repo.salvar(novoUser);
        repo.salvarNoDisco();

        String token = sessions.criarSessao(novoUser.getId());

        LoginResponse dados = new LoginResponse(token, novoUser.getSelfData());

        log.info("[CADASTRO] Usuário '{}' cadastrado", novoUser.getNome());

        context.json(dados);
    }

    public void logout(@NotNull Context context) {
        String token = tokenDe(context);
        String userID = sessions.validarToken(token);
        if (userID == null) throw new TokenInvalidoException();

        sessions.encerrarSessao(token);
    }

    public void cadastrarMotorista(@NotNull Context context) {
        String userID = sessions.validarToken(tokenDe(context));
        if (userID == null) throw new TokenInvalidoException();

        Usuario user = repo.getByID(userID);

        if (user.isMotorista()) return;

        CadastroMotoristaRequest cadastro = context.bodyAsClass(CadastroMotoristaRequest.class);
        user.tornarMotorista(cadastro.cnh(), cadastro.placaCarro(), cadastro.modeloCarro(), cadastro.corCarro());

        repo.salvar(user);
        repo.salvarNoDisco();

        context.json(user.getSelfData());
    }

    /**
     * GET /api/cliente/eu
     * Devolve os dados do passageiro ou motorista autenticado.
     */
    public void eu(@NotNull Context context) {
        String token = tokenDe(context);
        String userID = validarUsuarioLogado(token);

        Usuario utilizador = repo.getByID(userID);

        if (utilizador == null) throw new NotFoundResponse("Utilizador não encontrado no sistema.");

        context.status(200).json(utilizador.getSelfData());
    }

    public OtherUserDTO getUserRemoto(String idPassageiro) {
        return client.buscarDadosUser(idPassageiro);
    }

    public void consultarUsuarioRemoto(@NotNull Context ctx) {
        String idDesejado = ctx.pathParam("id");

        Usuario usuario = getByID(idDesejado);

        if (usuario != null) {
            ctx.status(200).json(usuario.getOtherData());
            return;
        }

        log.info("[USER] Usuário '{}' não encontrado localmente, pedindo aos vizinhos...", idDesejado);

        String visitados = ctx.queryParam("visitados");
        OtherUserDTO userRemoto = client.buscarDadosUser(idDesejado, visitados);

        if (userRemoto != null) {
            ctx.status(200).json(userRemoto);
        } else {
            log.warn("[USER] Usuário '{}' não encontrado...", idDesejado);
            ctx.status(404);
        }
    }
}
