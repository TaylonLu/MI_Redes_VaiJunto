package org.UEFS.vaijunto.Domain.Caronas;

public enum StatusCarona {
    /** Pronta para receber passageiros */
    AGENDADA,
    /** Motorista iniciou a viagem */
    EM_ANDAMENTO,
    /** Viagem concluída */
    FINALIZADA,
    /** Motorista desistiu */
    CANCELADA
}
