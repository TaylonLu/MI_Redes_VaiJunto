package org.UEFS.shared.dto.requests;

public record CadastroRequest(
        String email, String senha,
        String nome
) {}


/*
CADASTRO|{ "email": "mail@com", "senha": "senhaT", "nome": "testecad" }|null|52

*/
