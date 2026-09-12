package org.UEFS.vaijunto.Domain.Usuarios;

import com.fasterxml.jackson.core.type.TypeReference;
import org.UEFS.vaijunto.Domain.Interfaces.DataRepo;
import org.UEFS.vaijunto.Util.IOUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UserRepo extends DataRepo<Usuario> {
    private final Map<String, Usuario> emailIdx = new ConcurrentHashMap<>();

    public UserRepo() {
        super("data/usuarios.json", new TypeReference<>() {
        });
        for (Usuario usuario : this.dados.values()) {
            this.emailIdx.put(usuario.getEmail(), usuario);
            IOUtils.fprintf("[:yellow]Email: [:green]%s\t[:yellow]Senha: [:green]%s[::]", usuario.getEmail(), usuario.getSenha());
        }
    }

    @Override
    public void salvar(Usuario usuario) {
        dados.put(usuario.getId(), usuario);

        emailIdx.put(usuario.getEmail(), usuario);
    }

    public Usuario buscaPorEmail(String email) {
        return emailIdx.get(email);
    }

}
