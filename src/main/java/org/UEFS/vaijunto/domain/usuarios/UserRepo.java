package org.UEFS.vaijunto.domain.usuarios;

import com.fasterxml.jackson.core.type.TypeReference;
import org.UEFS.vaijunto.domain.interfaces.DataRepo;
import org.UEFS.vaijunto.util.IOUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UserRepo extends DataRepo<Usuario> {
    private final Map<String, Usuario> emailIdx = new ConcurrentHashMap<>();

    public UserRepo() {
        super("data/usuarios.json", new TypeReference<Map<String, Usuario>>() {});
        for (Usuario usuario : this.dados.values()) {
            this.emailIdx.put(usuario.getEmail(), usuario);
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
