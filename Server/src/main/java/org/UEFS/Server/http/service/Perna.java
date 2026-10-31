package org.UEFS.Server.http.service;

import org.UEFS.Server.domain.caronas.Carona;
import org.UEFS.shared.dto.Trecho;

import java.util.List;

public record Perna(String servidor, String idPerna, Carona carona, List<Trecho> trechos) {}
