package org.UEFS.vaijunto.Domain.Usuarios;

import org.UEFS.vaijunto.Domain.Interfaces.BaseMapper;
import org.UEFS.vaijunto.Server.ServerGrammar;

public final class UserMapper implements BaseMapper<Usuario> {
    @Override
    public String toString(Usuario usuario) {
        String userData = String.join(
                ";",
                usuario.getID(),
                usuario.getEmail(),
                usuario.getSenha(),
                usuario.getNome()
        );


        String motoData = "";
        if (usuario.isMotorista())
            motoData = String.join(";", usuario.getPerfilMotorista().getData());

        return userData + ServerGrammar.SUB_SEP + motoData;
    }

    @Override
    public Usuario fromString(String linha) {
        if (linha == null) return null;

        String[] linhaSplit = ServerGrammar.extrairSubAtributos(linha);
        if (linhaSplit.length != 2) throw new RuntimeException("atributos diferente de 2.");
        String[] dados = ServerGrammar.extrairAtributo(linhaSplit[0]);
        if (dados.length != 4) throw new RuntimeException("atributos diferente de 4.");


        String id = dados[0];
        String email = dados[1];
        String senha = dados[2];
        String nome = dados[3];

        Usuario usuario = new Usuario(id, email, senha, nome);

        if (linhaSplit[1] == null || linhaSplit[1].isBlank()) return usuario;

        String[] dadosMoto = ServerGrammar.extrairAtributo(linhaSplit[1]);
        if (dadosMoto.length != 4) return usuario;

        String cnh = dadosMoto[0];
        String placaCarro = dadosMoto[1];
        String modeloCarro = dadosMoto[2];
        double notaAvaliacao;
        try { notaAvaliacao = Double.parseDouble(dadosMoto[3]);
        } catch (NumberFormatException e) { return usuario; }

        usuario.tornarMotorista(cnh, placaCarro, modeloCarro, notaAvaliacao);

        return usuario;
    }

    @Override
    public Class<Usuario> getType() {
        return Usuario.class;
    }
}
