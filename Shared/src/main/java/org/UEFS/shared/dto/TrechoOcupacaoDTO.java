package org.UEFS.shared.dto;

import java.util.Set;

public record TrechoOcupacaoDTO(
        Trecho trecho, Set<OtherUserDTO> passageiros
) {}
