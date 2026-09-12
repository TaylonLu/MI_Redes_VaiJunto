package org.UEFS.vaijunto.DTO;

import org.UEFS.vaijunto.Domain.Interfaces.Trecho;

import java.util.Set;

public record TrechoOcupacaoDTO(
        Trecho trecho, Set<String> passageiros
) {}
