package org.UEFS.vaijunto.Controller;

import org.UEFS.vaijunto.DTO.UserDTO;
import org.UEFS.vaijunto.Domain.Usuarios.Usuario;
import org.UEFS.vaijunto.Domain.Usuarios.UserRepo;
import org.UEFS.vaijunto.Exceptions.IncorrectDataException;
import org.UEFS.vaijunto.Exceptions.Status;
import org.UEFS.vaijunto.Server.Request;
import org.UEFS.vaijunto.Server.Response;
import org.UEFS.vaijunto.Server.ServerGrammar;

import java.util.Arrays;

public class UserController extends DataController<Usuario, UserRepo, UserDTO> {
    private final SessionsController sessions = serviceProvider.get(SessionsController.class);

    public UserController(UserRepo repositorio) {
        super(repositorio);
    }

    public Response login(Request RQ) {
        String[] partes = ServerGrammar.extrairAtributo(RQ.getDados());

        if (partes.length != 2) throw new IncorrectDataException("Dados para login incorretos");

        String email = partes[0];
        String senha = partes[1];

        Usuario user = repo.buscaPorEmail(email);

        if (user == null) return new Response(Status.USUARIO_NAO_CADASTRADO);
        if (!senha.equals(user.getSenha())) return new Response(Status.DADOS_INCORRETOS);

        String token = sessions.criarSessao(user.getID());

        return new Response(Status.SUCESSO.getCodigo(), "LOGIN_EFETUADO", token);
    }

    @Override
    public Response cadastrar(Request RQ) {
        String[] dadosSplit = ServerGrammar.extrairAtributo(RQ.getDados());
        if (dadosSplit.length != 3) return new Response(Status.DADOS_INCORRETOS, "DADOS_INCORRETOS");

        String email = dadosSplit[0];
        String senha = dadosSplit[1];
        String nome = dadosSplit[2];

        Usuario novoUser = new Usuario(email, senha, nome);

        repo.salvar(novoUser);

        String token = sessions.criarSessao(novoUser.getID());

        return new Response(Status.SUCESSO.getCodigo(), "CADASTRO_COMPLETO", token);
    }

    public Response cadastrarMotorista(Request RQ) {
        String userID = sessions.validarToken(RQ.getToken());
        if (userID == null) return new Response(Status.TOKEN_INVALIDO);

        Usuario user = repo.getByID(userID);

        if (user.isMotorista()) return new Response(Status.ACAO_INVALIDA);

        String[] dadosSplit = ServerGrammar.extrairAtributo(RQ.getDados());
        if (dadosSplit.length != 3) return new Response(Status.DADOS_INCORRETOS);

        if (Arrays.stream(dadosSplit).anyMatch(D -> (D == null || D.isBlank()))) {
            return new Response(Status.DADOS_INCORRETOS,"");
        }
        user.tornarMotorista(dadosSplit[0], dadosSplit[1], dadosSplit[2]);

        repo.salvar(user);

        return new Response(Status.SUCESSO, "CADASTRO_COMPLETO");
    }


    @Override
    public Response atualizar(Request RQ) throws Exception {
        return new Response(Status.NAO_IMPLEMENTADO);
    }

    @Override
    public UserDTO toData(Usuario entidade) {
        return new UserDTO(entidade.getID(), entidade.getEmail(), entidade.getNome(), entidade.isMotorista());
    }
}
