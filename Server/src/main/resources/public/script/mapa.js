exigirLogin();
const $ = id => document.getElementById(id);
const modo = new URLSearchParams(location.search).get("modo") === "criacao" ? "criacao" : "busca";
const NS = "http://www.w3.org/2000/svg";
const nos = new Map();   // id da cidade -> <g>
let anterior = null, rota = [], origem = null, destino = null, itinerarios = [], itinSel = null;

$("painel-" + modo).hidden = false;

function svgEl(tag, attrs, cls) {
    const e = document.createElementNS(NS, tag);
    for (const k in attrs) e.setAttribute(k, attrs[k]);
    if (cls) e.setAttribute("class", cls);
    return e;
}

const linha = (a, b, cor, larg) =>
    $("rotas").append(svgEl("line", { x1: a.x, y1: a.y, x2: b.x, y2: b.y, stroke: cor, "stroke-width": larg }));

// ---------- Mapa ----------
function desenharMapa() {
    for (const a of cidades) {
        for (const idV of a.estradas) {
            const b = cidade(idV);
            if (b && a.id < b.id) $("estradas").append(svgEl("line", { x1: a.x, y1: a.y, x2: b.x, y2: b.y }, "estrada"));
        }
        const g = svgEl("g", {}, "cidade");
        const texto = svgEl("text", { x: a.x + 15, y: a.y + 5 });
        texto.textContent = a.nome;
        g.append(svgEl("circle", { cx: a.x, cy: a.y, r: 10 }), texto);
        g.addEventListener("click", () => { if (!arrastou) clicou(a); });
        nos.set(a.id, g);
        $("cidades").append(g);
    }
}

// Arrastar o mapa (sem pointer capture, para não perder o clique nas cidades)
let tx = 0, ty = 0, ox = 0, oy = 0, x0 = 0, y0 = 0, arrastando = false, arrastou = false;
$("mapa").addEventListener("pointerdown", e => {
    if (e.button !== 0) return;
    arrastando = true; arrastou = false;
    x0 = e.clientX; y0 = e.clientY; ox = tx; oy = ty;
});
window.addEventListener("pointermove", e => {
    if (!arrastando) return;
    const dx = e.clientX - x0, dy = e.clientY - y0;
    if (Math.abs(dx) + Math.abs(dy) > 4) arrastou = true;
    tx = ox + dx; ty = oy + dy;
    $("mundo").style.transform = `translate(${tx}px,${ty}px)`;
});
window.addEventListener("pointerup", () => { arrastando = false; });

function limparMapa() {
    $("rotas").replaceChildren();
    nos.forEach(g => g.classList.remove("sel", "origem", "destino"));
}

function clicou(c) {
    const g = nos.get(c.id);
    if (modo === "criacao") {
        if (anterior) {
            if (!(anterior.estradas.includes(c.id) || c.estradas.includes(anterior.id)))
                return aviso(`Não há estrada direta entre ${anterior.nome} e ${c.nome}`, "erro");
            linha(anterior, c, "#2980b9", 6);
            rota.push({ inicio: anterior.id, fim: c.id });
        }
        anterior = c;
        g.classList.add("sel");
        $("status-criacao").textContent = (rota.length ? "Trecho adicionado até: " : "Origem definida: ") + c.nome;
        $("percurso").append(h("li", "", c.nome));
    } else if (!origem) {
        origem = c; g.classList.add("origem");
        $("busca-origem").textContent = "Origem: " + c.nome;
    } else if (!destino && c !== origem) {
        destino = c; g.classList.add("destino");
        $("busca-destino").textContent = "Destino: " + c.nome;
    }
}

// ---------- Modo: criação ----------
function recomecar() {
    anterior = null; rota = [];
    $("percurso").replaceChildren();
    $("status-criacao").textContent = "Clique na origem da rota.";
    limparMapa();
}
$("recomecar").onclick = recomecar;

$("confirmar-rota").onclick = () => rota.length
    ? $("popup").showModal()
    : aviso("Você precisa selecionar pelo menos duas cidades conectadas!", "erro");
$("fechar-popup").onclick = () => $("popup").close();

$("form-carona").onsubmit = async e => {
    e.preventDefault();
    const f = new FormData(e.target);
    const dataHora = `${f.get("data")}T${f.get("hora")}`;
    if (new Date(dataHora) < new Date()) return aviso("A data e hora de partida não pode estar no passado.", "erro");

    // Ajuste os nomes das chaves aos componentes do seu NovaCaronaRequest
    const corpo = {
        idMotorista: sessao.usuario().id,
        vagas: Number(f.get("vagas")),
        valorPorTrecho: Number(f.get("valor")),
        dataPartida: dataHora + ":00",
        trechos: rota
    };
    try {
        await api("/api/caronas", "POST", corpo);
        aviso("Carona publicada com sucesso!", "sucesso");
        setTimeout(() => location.href = PRINCIPAL, 1000);
    } catch (err) {
        aviso(err.message, "erro");
    }
};

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
    const cores = coresDosPassos(it.passos);
    it.passos.forEach((p, i) => {
        const a = cidade(p.inicio), b = cidade(p.fim);
        if (a && b) linha(a, b, cores[i], 7);
    });
}

const cardItinerario = it => {
    const n = nMotoristas(it.passos), t = it.passos.length;
    return h("li", "card", h("strong", "", n <= 1 ? `Carona Direta (${t} trechos)` : `Baldeação: ${n} carros (${t} trechos)`));
};

$("buscar").onclick = async () => {
    if (!origem || !destino) return aviso("Selecione origem e destino no mapa antes de buscar!", "aviso");
    const vagas = parseInt($("busca-vagas").value, 10), data = $("busca-data").value;
    if (!(vagas >= 1) || !data) return aviso("Informe a quantidade de vagas e a data pretendida para a busca.", "aviso");

    itinSel = null;
    $("ver-detalhes").hidden = true;
    try {
        // Ajuste os nomes das chaves aos componentes do seu BuscaCaronaRequest
        itinerarios = await api("/api/itinerarios/busca", "POST",
            { origemDesejada: origem.id, destinoDesejado: destino.id, vagasRequeridas: vagas, data_desejada: data + "T00:00:00" });
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

$("ver-detalhes").onclick = async () => {
    if (!itinSel) return aviso("Selecione uma rota na lista primeiro.", "aviso");
    const ok = await dialogo("Resumo do itinerário", corpoPassos(itinSel.passos), "Confirmar participação");
    if (ok) reservar(itinSel);
};

async function reservar(it) {
    $("carregando").hidden = false;
    try {
        await api("/api/reservas", "POST", it);
        aviso("Reserva confirmada com sucesso!", "sucesso");
        setTimeout(() => location.href = PRINCIPAL, 1200);
    } catch (err) {
        aviso(err.status === 409 ? "Conflito: " + err.message : "Erro ao reservar: " + err.message,
            err.status === 409 ? "aviso" : "erro");
    } finally {
        $("carregando").hidden = true;
    }
}

// ---------- Inicialização ----------
(async () => {
    try { await carregarCidades(); }
    catch { return aviso("Não foi possível carregar as cidades.", "erro"); }

    const img = $("img");
    if (!img.complete) await new Promise(r => { img.onload = img.onerror = r; });
    const w = img.naturalWidth || Math.max(...cidades.map(c => c.x)) + 80;
    const alt = img.naturalHeight || Math.max(...cidades.map(c => c.y)) + 80;
    $("svg").setAttribute("width", w);
    $("svg").setAttribute("height", alt);
    desenharMapa();
})();