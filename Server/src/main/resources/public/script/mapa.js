exigirLogin();
const $ = id => document.getElementById(id);
const modo = new URLSearchParams(location.search).get("modo") === "criacao" ? "criacao" : "busca";
const NS = "http://www.w3.org/2000/svg";
const nos = new Map();   // id da cidade -> <g>
const usuario = JSON.parse(localStorage.getItem("usuario"));
let anterior = null, rota = [], origem = null, destino = null, itinerarios = [], itinSel = null;
let cidadesServidor = new Set();

async function carregarCidadesServidor() {
    const resposta = await api("/api/servidor/cidades");

    cidadesServidor = new Set(
        Object.keys(resposta).map(Number)
    );
}

function cidadePertenceAoServidor(id) {
    return cidadesServidor.has(Number(id));
}

$("painel-" + modo).hidden = false;

function svgEl(tag, attrs, cls) {
    const e = document.createElementNS(NS, tag);
    for (const k in attrs) e.setAttribute(k, attrs[k]);
    if (cls) e.setAttribute("class", cls);
    return e;
}

const linha = (a, b, cor, larg) =>
    $("rotas").append(svgEl("line", {x1: a.cx, y1: a.cy, x2: b.cx, y2: b.cy, stroke: cor, "stroke-width": larg}));

// ---------- Mapa ----------

function desenharMapa() {
    const estradasSvg = $("estradas");
    const cidadesSvg = $("cidades");

    const fragmentoEstradas = document.createDocumentFragment();
    const fragmentoCidades = document.createDocumentFragment();

    for (const a of cidades) {
        // ---------- Estradas ----------
        for (const idV of a.estradas) {
            const b = cidade(idV);

            if (b && a.id < b.id) {
                fragmentoEstradas.append(
                    svgEl("line", {
                        x1: a.cx,
                        y1: a.cy,
                        x2: b.cx,
                        y2: b.cy
                    }, "estrada")
                );
            }
        }

        // ---------- Cidades ----------
        const classeRegiao = "regiao-" +
            a.regiao.toLowerCase().replace(/\s+/g, "-");

        const g = svgEl("g", {}, `cidade ${classeRegiao}`);
        g.dataset.regiao = a.regiao;

        const halo = svgEl("circle", {
            cx: a.cx, cy: a.cy, r: 22
        }, "halo");

        const ponto = svgEl("circle", {
            cx: a.cx, cy: a.cy, r: 9
        }, "ponto");

        const texto = svgEl("text", {
            x: a.cx + 18,
            y: a.cy + 5
        });

        texto.textContent = a.nome;

        g.append(halo, ponto, texto);

        g.addEventListener("click", () => {
            if (!arrastou) clicou(a);
        });

        nos.set(a.id, g);

        if (modo === "criacao" && !cidadePertenceAoServidor(a.id)) {
            g.classList.add("cidade-externa");
        }

        fragmentoCidades.append(g);
    }

    // Insere todos os elementos de uma vez.
    estradasSvg.append(fragmentoEstradas);
    cidadesSvg.append(fragmentoCidades);
}


// Arrastar o mapa (sem pointer capture, para não perder o clique nas cidades)
let tx = 0, ty = 0, ox = 0, oy = 0, x0 = 0, y0 = 0, arrastando = false, arrastou = false;
$("mapa").addEventListener("pointerdown", e => {
    if (e.button !== 0) return;
    arrastando = true;
    arrastou = false;
    x0 = e.clientX;
    y0 = e.clientY;
    ox = tx;
    oy = ty;
});
window.addEventListener("pointermove", e => {
    if (!arrastando) return;
    const dx = e.clientX - x0, dy = e.clientY - y0;
    if (Math.abs(dx) + Math.abs(dy) > 4) arrastou = true;
    tx = ox + dx;
    ty = oy + dy;
    $("mundo").style.transform = `translate(${tx}px,${ty}px)`;
});
window.addEventListener("pointerup", () => {
    arrastando = false;
});

function limparMapa() {
    $("rotas").replaceChildren();
    nos.forEach(g => g.classList.remove("sel", "origem", "destino"));
}

function clicou(c) {
    const g = nos.get(c.id);
    if (modo === "criacao") {
        const cidadeRepetida = c.id === anterior?.id ||
            rota.some(passo => passo.inicio === c.id);

        if (cidadeRepetida) {
            aviso("Essa cidade já faz parte do percurso.", "aviso");
            return;
        }

        if (!anterior && !cidadePertenceAoServidor(c.id)) {
            aviso("A cidade de origem precisa pertencer ao seu servidor.", "erro");
            return;
        }
        if (anterior) {
            if (!(anterior.estradas.includes(c.id) || c.estradas.includes(anterior.id)))
                return aviso(`Não há estrada direta entre ${anterior.nome} e ${c.nome}`, "erro");
            linha(anterior, c, "#2980b9", 6);
            rota.push({inicio: anterior.id, fim: c.id});
        }
        anterior = c;
        g.classList.add("sel");
        $("status-criacao").textContent = (rota.length ? "Trecho adicionado até: " : "Origem definida: ") + c.nome;
        $("percurso").append(h("li", "", c.nome));
    } else if (!origem) {
        origem = c;
        g.classList.add("origem");
        $("busca-origem").textContent = "Origem: " + c.nome;
    } else if (!destino && c !== origem) {
        destino = c;
        g.classList.add("destino");
        $("busca-destino").textContent = "Destino: " + c.nome;
    }
}

// ---------- Modo: criação ----------
function recomecar() {
    anterior = null;
    rota = [];
    $("percurso").replaceChildren();
    $("status-criacao").textContent = "Clique na origem da rota.";
    limparMapa();
}

$("recomecar").onclick = recomecar;

function desfazerRota() {
    if (!anterior) {
        return aviso("Não há nada para desfazer.", "aviso");
    }

    if (rota.length > 0) {
        const removido = rota.pop();

        // Volta para a cidade anterior.
        anterior = cidade(removido.inicio);

        // Remove o destaque da cidade retirada.
        nos.get(removido.fim).classList.remove("sel");

        // Remove a última conexão desenhada.
        $("rotas").lastElementChild?.remove();

        // Remove a última cidade da lista do percurso.
        $("percurso").lastElementChild?.remove();

        $("status-criacao").textContent =
            "Trecho removido. Última cidade: " + anterior.nome;

    } else {
        // Ainda não existem trechos: desfaz a seleção da origem.
        nos.get(anterior.id).classList.remove("sel");

        $("percurso").lastElementChild?.remove();

        anterior = null;

        $("status-criacao").textContent =
            "Clique na origem da rota.";
    }
}

$("desfazer-rota").onclick = desfazerRota;

$("confirmar-rota").onclick = () => rota.length
    ? $("popup").showModal()
    : aviso("Você precisa selecionar pelo menos duas cidades conectadas!", "erro");
$("fechar-popup").onclick = () => $("popup").close();

$("form-carona").onsubmit = async e => {
    e.preventDefault();
    const campoData = $("form-carona").elements.data;

    const hoje = new Date();
    const dataMinima = [
        hoje.getFullYear(),
        String(hoje.getMonth() + 1).padStart(2, "0"),
        String(hoje.getDate()).padStart(2, "0")
    ].join("-");

    campoData.min = dataMinima;

    const f = new FormData(e.target);

    const data = f.get("data");
    const hora = f.get("hora");

    if (!data || !hora) {
        return aviso("Informe a data e o horário da partida.", "aviso");
    }

    const dataHora = `${data}T${hora}:00`;

    if (new Date(dataHora).getTime() <= Date.now()) {
        return aviso("A data e hora de partida precisa ser futura.", "erro");
    }

    const precoPorTrecho = Number(f.get("valor"));
    const assentosPorTrecho = Number(f.get("vagas"));

    if (!Number.isSafeInteger(assentosPorTrecho) || assentosPorTrecho < 1) {
        return aviso("A quantidade de vagas deve ser um inteiro maior que zero.", "erro");
    }

    if (!Number.isFinite(precoPorTrecho) || precoPorTrecho < 0) {
        return aviso("O valor por trecho deve ser um número válido e não negativo.", "erro");
    }

    const payload = {
        idMotorista: usuario.id,
        valorPorTrecho: precoPorTrecho,
        vagas: assentosPorTrecho,
        dataPartida: dataHora,
        trechos: rota.map(passo =>
            `${passo.inicio}-${passo.fim}`
        )
    };

    try {
        await api("/api/cliente/caronas", "POST", payload);
        aviso("Carona publicada com sucesso!", "sucesso");
        setTimeout(() => location.href = PRINCIPAL, 1000);
    } catch (err) {
        aviso(err.message, "erro");
    }
}

// ---------- Modo: busca ----------
function limparBusca() {
    origem = destino = itinSel = null;
    $("busca-origem").textContent = "Origem: (não selecionada)";
    $("busca-destino").textContent = "Destino: (não selecionado)";
    $("busca-vagas").value = "";
    $("busca-data").value = "";
    $("resultados").replaceChildren();
    $("ver-detalhes").hidden = true;
    limparMapa();
}

$("limpar-busca").onclick = limparBusca;

function desenharItinerario(it) {
    $("rotas").replaceChildren();

    const trechos = it.trechos || [];
    const cores = coresDosPassos(trechos);

    trechos.forEach((p, i) => {
        const percurso = [
            p.origem,
            ...(p.paradas_intermediarias ?? []),
            p.destino
        ];

        for (let j = 0; j < percurso.length - 1; j++) {
            const cOrigem = cidadesPorNome.get(percurso[j]);
            const cDestino = cidadesPorNome.get(percurso[j + 1]);

            if (cOrigem && cDestino) {
                linha(cOrigem, cDestino, cores[i], 7);
            }
        }
    });
}

const cardItinerario = it => {
    const trechos = it.trechos || [];
    const n = nCaronas(trechos), t = trechos.length; // alterado de passos para trechos
    const preco = ` - ${Number(it.preco_total ?? 0).toLocaleString(
        "pt-BR",
        {style: "currency", currency: "BRL"}
    )}`;
    return h("li", "card", h("strong", "", n <= 1 ? `Carona Direta (${t} trechos)${preco}` : `Baldeação: ${n} carros (${t} trechos)${preco}`));
};

$("buscar").onclick = async () => {
    if (!origem || !destino) return aviso("Selecione origem e destino no mapa antes de buscar!", "aviso");
    const vagas = parseInt($("busca-vagas").value, 10), data = $("busca-data").value;
    if (!(vagas >= 1) || !data) return aviso("Informe a quantidade de vagas e a data pretendida para a busca.", "aviso");

    itinSel = null;
    $("ver-detalhes").hidden = true;
    try {
        const params = new URLSearchParams({
            origem: origem.nome,
            destino: destino.nome,
            data,
            vagas: String(vagas)
        });

        const resultadoBusca = await api(`/api/cliente/itinerarios?${params}`, "GET"
        );
        // Ajuste os nomes das chaves aos componentes do seu BuscaCaronaRequest
        itinerarios = resultadoBusca?.itinerarios || [];
    } catch (err) {
        return aviso("Erro na busca: " + err.message, "erro");
    }

    $("rotas").replaceChildren();
    renderLista($("resultados"), itinerarios, cardItinerario, "", it => {
        itinSel = it;
        desenharItinerario(it);
        $("ver-detalhes").hidden = false;
    });
    itinerarios.length
        ? aviso(`${itinerarios.length} rotas encontradas!`, "sucesso")
        : aviso("Nenhuma carona encontrada para esse trajeto.", "aviso");
};

function corpoTrechosBusca(trechos) {
    const n = nCaronas(trechos);

    return h("div", "corpo",
        h("p", "",
            n <= 1
                ? "Percurso realizado por uma única carona."
                : `Percurso com ${n} caronas e ${n - 1} baldeação(ões).`
        ),

        h("h3", "", "Trechos do percurso"),

        ...trechos.map((t, i) => {
            const paradas = t.paradas_intermediarias ?? [];

            return h("div", "caixa",
                h("strong", "",
                    `${i + 1}. ${t.origem} → ${t.destino}`
                ),

                ...(paradas.length
                    ? [h("small", "",
                        `Paradas intermediárias: ${paradas.join(" → ")}`
                    )]
                    : []),

                h("small", "", `Horário: ${fmtData(t.horario)}`),

                h("small", "", `Preço: ${Number(t.preco).toLocaleString(
                    "pt-BR", {style: "currency", currency: "BRL"}
                )}`),

                h("small", "",
                    `${t.assentos_livres} vaga(s) disponível(is)`
                ),

                h("small", "", `Servidor: ${t.servidor}`)
            );
        })
    );
}

$("ver-detalhes").onclick = async () => {
    if (!itinSel) return aviso("Selecione uma rota na lista primeiro.", "aviso");

    const ok = await dialogo(
        "Resumo do itinerário",
        corpoTrechosBusca(itinSel.trechos ?? []),
        "Confirmar participação"
    );

    if (ok) reservar(itinSel);
};

async function reservar(it) {
    $("carregando").hidden = false;
    try {
        const trechosSolicitados = (it.trechos || []).map(t => ({
            servidor: t.servidor,
            id_carona: t.id_carona,
            origem: cidadesPorNome.get(t.origem)?.id,
            destino: cidadesPorNome.get(t.destino)?.id
        }));

        if (trechosSolicitados.some(t =>
            t.origem === undefined || t.destino === undefined
        )) {
            throw new Error("Não foi possível identificar as cidades do itinerário.");
        }
        // 2. Montar o payload final com o ID da perna consolidado
        const corpo = {
            trechos: trechosSolicitados
        };

        const resposta = await api("/api/cliente/reservas", "POST", corpo);

        if (resposta?.propagacao_pendente) {
            aviso("Reserva confirmada, mas a sincronização com os servidores ainda está pendente.", "aviso");
        } else {
            aviso("Reserva confirmada com sucesso!", "sucesso");
        }
        setTimeout(() => location.href = PRINCIPAL, 2000);
    } catch (err) {
        aviso(err.status === 409 ? "Conflito: " + err.message : "Erro ao reservar: " + err.message,
            err.status === 409 ? "aviso" : "erro");
    } finally {
        $("carregando").hidden = true;
    }
}

// ==========================================
// LÓGICA DE ZOOM DO MAPA
// ==========================================
let zoomAtual = 1;
const ZOOM_MIN = 0.4; // Limite máximo de afastamento (zoom out)
const ZOOM_MAX = 3.5; // Limite máximo de aproximação (zoom in)
const ZOOM_PASSO = 0.15; // Velocidade do zoom

function aplicarZoom(novoZoom) {
    zoomAtual = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, novoZoom));

    const mundo = $("mundo");

    if (mundo) {
        mundo.style.scale = zoomAtual;
    }

    mundo.style.setProperty("--zoom", zoomAtual);

    $("mapa").classList.toggle("zoom-detalhado", zoomAtual >= 1.2);
}

// 1. Zoom usando a roda do rato (Scroll)
const areaMapa = $("mapa");
if (areaMapa) {
    areaMapa.addEventListener("wheel", (e) => {
        e.preventDefault(); // Impede que a página role para baixo

        // Se o deltaY for negativo (scroll para cima), aproxima. Caso contrário, afasta.
        const direcao = e.deltaY < 0 ? 1 : -1;
        aplicarZoom(zoomAtual + (ZOOM_PASSO * direcao));
    }, {passive: false}); // 'passive: false' é obrigatório para usar preventDefault no wheel
}

// 2. Zoom usando os botões visuais
const btnZoomIn = $("btn-zoom-in");
const btnZoomOut = $("btn-zoom-out");

if (btnZoomIn && btnZoomOut) {
    btnZoomIn.addEventListener("click", () => aplicarZoom(zoomAtual + ZOOM_PASSO));
    btnZoomOut.addEventListener("click", () => aplicarZoom(zoomAtual - ZOOM_PASSO));
}

// ---------- Inicialização ----------
(async () => {
    if (!$("estradas") || !$("cidades")) return;
    try {
        await carregarCidades();
        if (modo === "criacao") await carregarCidadesServidor();
    } catch {
        return aviso("Não foi possível carregar as cidades.", "erro");
    }

    const img = $("img");
    if (!img.complete) await new Promise(r => {
        img.onload = img.onerror = r;
    });
    const w = img.naturalWidth || Math.max(...cidades.map(c => c.x + c.largura));
    const alt = img.naturalHeight || Math.max(...cidades.map(c => c.y + c.altura));
    $("svg").setAttribute("width", w);
    $("svg").setAttribute("height", alt);
    desenharMapa();
})();