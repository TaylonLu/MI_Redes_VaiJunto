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
import org.UEFS.Server.http.relogio.CMessage;
import org.UEFS.Server.http.relogio.EstadoLocal;
import org.UEFS.Server.exceptions.*;
import org.UEFS.Server.http.relogio.ServerConstant;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static io.javalin.apibuilder.ApiBuilder.path;
import static io.javalin.apibuilder.ApiBuilder.post;
import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.delete;

public class HttpServer {
    private static final Logger log = LoggerFactory.getLogger(HttpServer.class);
    private final ControllerService service = ControllerService.getInstance();
    private final UserController userC = service.get(UserController.class);
    private final CaronaController caronaC = service.get(CaronaController.class);
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
        ServerConstant.loadServersIps();

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
        EstadoLocal estado = service.get(EstadoLocal.class);
        ServerManager manager = service.get(ServerManager.class);

        /* ROTAS DE GET */
        config.routes.get("/", ctx -> ctx.redirect("/pages/principal.html"), Acesso.PUBLIC);
        config.routes.get("/inicio", ctx -> ctx.redirect("/pages/inicio.html"), Acesso.PUBLIC);
        config.routes.get("/usuarios/{id}", userC::getByID, Acesso.ADMIN);

        config.routes.apiBuilder(() -> path("/api", () -> {
            /* ROTAS DE POST */
            post("/logout",             userC::logout,                   Acesso.PUBLIC);
            post("/login",              userC::login,                    Acesso.PUBLIC);
            post("/cadastro",           userC::cadastrar,                Acesso.PUBLIC);
            post("/motorista",          userC::cadastrarMotorista,       Acesso.PASSAGEIRO);
            post("/itinerarios/busca",  caronaC::buscarItinerarios,      Acesso.PASSAGEIRO);

            path("/reservas", () -> {
                get("/minhas",  caronaC::consultarMinhasReservas,   Acesso.PASSAGEIRO);
                post(           caronaC::confirmarReserva,          Acesso.PASSAGEIRO);
                delete("/{id}", caronaC::cancelarReserva,           Acesso.PASSAGEIRO);
            });

            path("/caronas", () -> {
                get("/minhas",  caronaC::consultarCaronas,  Acesso.MOTORISTA);
                post(           caronaC::criarCarona,       Acesso.MOTORISTA);
                delete("/{id}", caronaC::cancelarCarona,    Acesso.MOTORISTA);
            });

            path("/server", () -> {
               post(ctx -> {
                   CMessage msg = ctx.bodyAsClass(CMessage.class);
                   estado.ReceberRequest(msg);
                   ctx.status(200);
               });

               get("/health", manager::health);

               get("/trechos", manager::trechos);
            });
        }));
    }

    private void configurarServidor(JavalinConfig config) {
        config.http.defaultContentType = "application/json; charset=utf-8";
        config.startup.showJavalinBanner = false;
    }

    private void configurarArquivosEstativos(JavalinConfig config) {
        config.staticFiles.add("/public", Location.CLASSPATH);
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
    }
}
