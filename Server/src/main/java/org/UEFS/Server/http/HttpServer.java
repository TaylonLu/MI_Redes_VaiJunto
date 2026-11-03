package org.UEFS.Server.http;

import io.javalin.Javalin;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.UnauthorizedResponse;
import io.javalin.http.staticfiles.Location;
import io.javalin.security.RouteRole;
import org.UEFS.Server.controller.CaronaController;
import org.UEFS.Server.controller.ControllerService;
import org.UEFS.Server.controller.UserController;
import org.UEFS.Server.exceptions.*;
import org.UEFS.Server.http.service.ServerManager;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static io.javalin.apibuilder.ApiBuilder.*;

public class HttpServer {
    private static final Logger log = LoggerFactory.getLogger(HttpServer.class);
    private final ControllerService service = ControllerService.getInstance();
    private final UserController userC = service.get(UserController.class);
    private final CaronaController caronaC = service.get(CaronaController.class);
    private final ServerManager manager = service.get(ServerManager.class);

    private final int PORT;

    public enum Acesso implements RouteRole {
        PUBLIC, PASSAGEIRO, MOTORISTA(PASSAGEIRO), ADMIN(MOTORISTA);

        private final Set<Acesso> herdados;

        Acesso(Acesso... herdados) {
            this.herdados = Set.of(herdados);
        }

        Set<Acesso> efetivos() {
            Set<Acesso> todos = new HashSet<>();
            todos.add(this);
            for (Acesso A : herdados) todos.addAll(A.efetivos());

            return todos;
        }
    }

    public HttpServer(int port) {
        this.PORT = port;
    }

    public void Start() {
        Javalin server = Javalin.create(config -> {
            configurarServidor(config);
            configurarArquivosEstativos(config);
            configurarAcesso(config);
            configurarRotas(config);
            configurarErros(config);
        }).start(PORT);

        log.info("Iniciando servidor em {}:{}", server.javalinServlet().getServletConfig().getServletName(), PORT);

        new Thread(new MenuServidores()::executar, "menu").start();
    }

    private void configurarErros(JavalinConfig config) {
        config.routes.exception(RecursoNaoEncontradoException.class, (e, ctx) -> erro(ctx, 404, e));
        config.routes.exception(UsuarioJaCadastradoException.class,  (e, ctx) -> erro(ctx, 409, e));
        config.routes.exception(DadosIncorretosException.class,      (e, ctx) -> erro(ctx, 401, e));
        config.routes.exception(TokenInvalidoException.class,        (e, ctx) -> erro(ctx, 401, e));
        config.routes.exception(ForbiddenException.class,            (e, ctx) -> erro(ctx, 403, e));
    }

    private void erro(@NotNull Context ctx, int status, Exception e) {
        log.error("{} {} -> {} {}", ctx.method(), ctx.path(), status, e.getMessage());
        ctx.status(status).json(Map.of("erro", Objects.requireNonNullElse(e.getMessage(), "Erro")));
    }

    private void configurarRotas(JavalinConfig config) {
        /* ROTAS DO FRONTEND WEB (HTML) */
        config.routes.get("/", ctx -> ctx.redirect("/pages/principal.html"), Acesso.PUBLIC);
        config.routes.get("/origem", ctx -> ctx.redirect("/pages/origem.html"), Acesso.PUBLIC);

        // Administração interna
        config.routes.get("/usuarios/{id}", userC::getByID, Acesso.ADMIN);

        config.routes.apiBuilder(() -> path("/api", () -> {
            get("/servidor/cidades", manager::getCidades, Acesso.PUBLIC);
            /* =======================================================
               ROTAS DO CONSÓRCIO P2P (Comunicação entre Servidores)
               ======================================================= */
            path("/consorcio", () -> {
                get("/health", manager::health, Acesso.PUBLIC);
                get("/vivo", manager::health, Acesso.PUBLIC);
                get("/trechos", manager::trechos, Acesso.PUBLIC);
                get("/usuarios/{id}", userC::consultarUsuarioRemoto, Acesso.PUBLIC);

                path("/reservas", () -> {
                    post("/prepare", manager::prepare, Acesso.PUBLIC);
                    delete("/{id}", manager::rollback, Acesso.PUBLIC);
                    put("/{id}/commit", manager::commit, Acesso.PUBLIC);
                    put("/{id}/cancel", manager::cancel, Acesso.PUBLIC);
                    put("/{id}/cancel-motorista", manager::cancelMotorista, Acesso.PUBLIC);
                    get("/{id}/status", manager::status, Acesso.PUBLIC);
                });
            });

            /* =======================================================
               ROTAS DO CLIENTE (Passageiros e Motoristas)
               ======================================================= */
            // | TODO: Adicionar método para apagar transação cancelada e carona cancelada do banco de dados.
            path("/cliente", () -> {

                // --- Autenticação e Perfil ---
                post("/cadastro", userC::cadastrar, Acesso.PUBLIC);
                post("/cadastro/motorista", userC::cadastrarMotorista, Acesso.PASSAGEIRO);
                post("/login", userC::login, Acesso.PUBLIC);
                post("/logout", userC::logout, Acesso.PUBLIC);
                get("/eu", userC::eu, Acesso.PUBLIC);

                // --- Busca de Itinerários ---
                get("/itinerarios", manager::itinerarios, Acesso.PASSAGEIRO);

                // --- Caronas (Motorista) ---
                path("/caronas", () -> {
                    get(caronaC::consultarCaronas, Acesso.MOTORISTA);
                    post(caronaC::criarCarona, Acesso.MOTORISTA);
                    delete("/{id}", caronaC::cancelarCarona, Acesso.MOTORISTA);
                });

                // Equivalente a /caronas para manter compatibilidade com o front-end
                path("/rotas", () -> {
                    post(caronaC::criarCarona, Acesso.MOTORISTA);
                });

                // --- Reservas (Passageiro) ---
                path("/reservas", () -> {
                    get(caronaC::consultarReservas, Acesso.PASSAGEIRO);
                    post(manager::reservaCliente, Acesso.PASSAGEIRO); // 2PC Coordenador
                    delete("/{id}", caronaC::cancelarReserva, Acesso.PASSAGEIRO);
                });
            });
        }));
    }

    private void configurarServidor(JavalinConfig config) {
        config.http.defaultContentType = "application/json; charset=utf-8";
        config.startup.showJavalinBanner = false;
    }

    private void configurarArquivosEstativos(JavalinConfig config) {
        config.staticFiles.add("Server/src/main/resources/public", Location.EXTERNAL);
    }

    private void configurarAcesso(JavalinConfig config) {
        config.routes.beforeMatched(ctx -> {
            Set<RouteRole> exigidos = ctx.routeRoles();

            if (exigidos.isEmpty() || exigidos.contains(Acesso.PUBLIC)) return;

            Acesso perfil = userC.getAcesso(Utils.tokenDe(ctx));
            if (perfil == null) throw new UnauthorizedResponse("Faça login.");

            boolean permitido = perfil.efetivos().stream().anyMatch(exigidos::contains);

            if (!permitido) {
                throw new ForbiddenResponse("Acesso não autorizado para o seu perfil.");
            }
        });

        config.routes.before("/api*", manager::aoReceber);
        config.routes.after("/api*", manager::aoResponder);
    }
}