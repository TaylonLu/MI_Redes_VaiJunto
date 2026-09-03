package org.UEFS.vaijunto.Domain.Usuarios;

import org.UEFS.vaijunto.Domain.Interfaces.DataRepo;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UserRepo extends DataRepo<Usuario> {
    private final Map<String, Usuario> emailIdx = new ConcurrentHashMap<>();

    public UserRepo() {}
    public UserRepo(UserMapper userMapper) {
        super(userMapper, "usuarios.txt");
        for (Usuario usuario : this.dados.values()) {
            this.emailIdx.put(usuario.getEmail(), usuario);
        }
    }

    @Override
    public void salvar(Usuario usuario) {
        dados.put(usuario.getID(), usuario);

        emailIdx.put(usuario.getEmail(), usuario);
    }

    public Usuario buscaPorEmail(String email) {
        return emailIdx.get(email);
    }

}
