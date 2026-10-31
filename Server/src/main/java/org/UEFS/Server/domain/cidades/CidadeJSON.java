package org.UEFS.Server.domain.cidades;

import com.fasterxml.jackson.core.type.TypeReference;
import org.UEFS.shared.JsonUtils;
import java.io.InputStream;
import java.util.*;

public record CidadeJSON(
    int id, String nome, String regiao,
    double x, double y, double largura, double altura,
    List<String> conexoes
) {}

