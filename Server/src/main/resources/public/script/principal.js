exigirLogin();
const $ = id => document.getElementById(id);
let usuario = sessao.usuario(), reservaSel = null, caronaSel = null;

// ---------- Abas ----------
function abrirAba(nome) {
    document.querySelectorAll("[data-aba]").forEach(b => b.setAttribute("aria-selected", b.dataset.aba === nome));
    document.querySelectorAll(".painel").forEach(p => p.hidden = p.id !== "aba-" + nome);
}
document.querySelectorAll("[data-aba]").forEach(b => b.onclick = () => abrirAba(b.dataset.aba));

// ---------- Perfil ----------
function renderPerfil() {
    const m = usuario.perfilMotorista;
    $("nome").textContent = usuario.nome;
    $("email").textContent = usuario.email;
    $("status").textContent = m ? "Motorista" : "Passageiro";
    $("veiculo").hidden = !m;
    $("abrir-form").hidden = !!m;
    document.querySelector('[data-aba="motorista"]').hidden = !m;
    if (m) {
        $("modelo").textContent = m.modeloCarro;
        $("placa").textContent = m.placaCarro;
        $("cor").textContent = m.corCarro ?? "-";
        $("cnh").textContent = m.cnh;
    }
}

$("abrir-form").onclick = () => { $("abrir-form").hidden = true; $("form-motorista").hidden = false; };
$("cancelar-form").onclick = () => { $("form-motorista").reset(); $("form-motorista").hidden = true; $("abrir-form").hidden = false; };

$("form-motorista").onsubmit = async e => {
    e.preventDefault();
    const f = new FormData(e.target);
    const corpo = {};
    for (const campo of ["cnh", "placaCarro", "modeloCarro", "corCarro"]) corpo[campo] = f.get(campo).trim();

    try {
        const novo = await api("/api/motorista", "POST", corpo);
        if (novo) {   // sem corpo = já era motorista (SEM_ALTERACAO)
            usuario = novo;
            localStorage.setItem("usuario", JSON.stringify(novo));
        }
        $("form-motorista").reset();
        $("form-motorista").hidden = true;
        renderPerfil();
        abrirAba("motorista");
        await carregar();
        aviso("Você agora é um motorista!", "sucesso");
    } catch (err) {
        aviso(err.message || "Não foi possível completar o cadastro como motorista.", "erro");
    }
};

$("logout").onclick = async () => {
    try { await api("/api/logout", "POST"); } catch { /* sai mesmo assim */ }
    sair();
};

// ---------- Cards ----------
function cardReserva(r) {
    const passos = r.itinerario?.passos ?? [];
    const cores = coresDosPassos(passos);
    const n = nMotoristas(passos);
    return h("li", "card",
        h("div", "topo-card", h("strong", "", n <= 1 ? "Carona Direta" : `Baldeação (${n} carros)`), selo(r.status)),
        h("div", "chips", ...passos.map((p, i) => {
            const chip = h("span", "chip", `${nomeCidade(p.inicio)} → ${nomeCidade(p.fim)}`);
            chip.style.background = cores[i];
            chip.title = `Motorista: ${p.idMotorista}\nCarona: ${p.idCarona}`;
            return chip;
        })),
        h("small", "", `${passos.length} trecho(s) • Reserva #${String(r.idReserva ?? "-").slice(0, 8)}`),
        botao("Ver detalhes", "btn pequeno", () => detalhesReserva(r)));
}

const trechosOrdenados = c => (c.ocupacaoPorTrecho ?? [])
    .map(o => ({ ...o, trecho: lerTrecho(o.trecho) }))
    .sort((a, b) => a.trecho.inicio - b.trecho.inicio);


// verde (livre) → vermelho (lotado)
const corOcupacao = p => `rgb(${Math.round(46 + 185 * p)},${Math.round(204 - 128 * p)},${Math.round(113 - 53 * p)})`;

function cardCarona(c) {
    const trechos = trechosOrdenados(c);
    const unicos = new Set(trechos.flatMap(t => t.passageiros.map(p => p.id))).size;
    const barra = h("div", "barra", ...trechos.map(t => {
        const seg = h("span");
        const ocup = t.passageiros.length;
        seg.style.flex = Math.max(1, t.trecho.fim - t.trecho.inicio);
        seg.style.background = corOcupacao(c.vagasTotais ? Math.min(1, ocup / c.vagasTotais) : 0);
        seg.title = `Trecho ${nomeCidade(t.trecho.inicio)}–${nomeCidade(t.trecho.fim)}: ${ocup}/${c.vagasTotais} vaga(s)`
            + (ocup ? "\n" + t.passageiros.map(p => p.nome || p.email).join(", ") : "");
        return seg;
    }));
    return h("li", "card",
        h("div", "topo-card", h("strong", "", fmtData(c.data)), selo(c.status)),
        barra,
        h("small", "", `${unicos} passageiro(s) únicos • ${c.vagasTotais} vaga(s) totais`),
        botao("Ver detalhes", "btn pequeno", () => detalhesCarona(c)));
}

// ---------- Diálogos de detalhes ----------
function detalhesReserva(r) {
    return dialogo("Detalhes da reserva",
        corpoPassos(r.itinerario?.passos ?? [], h("strong", "", `Reserva #${r.idReserva}`), h("p", "", `Status: ${r.status}`)));
}

function detalhesCarona(c) {
    const trechos = trechosOrdenados(c);
    return dialogo("Detalhes da carona", h("div", "corpo",
        h("strong", "", fmtData(c.data)),
        h("p", "", `Status: ${c.status} • Vagas totais: ${c.vagasTotais}`),
        h("small", "", `Carona ${c.id} • Motorista ${c.idMotorista}`),
        h("h3", "", "Trechos e passageiros"),
        ...(trechos.length ? trechos.map(t => h("div", "caixa",
                h("strong", "", `Trecho ${nomeCidade(t.trecho.inicio)} – ${nomeCidade(t.trecho.fim)} (${t.passageiros.length}/${c.vagasTotais} vagas)`),
                ...(t.passageiros.length
                    ? t.passageiros.map(p => h("span", "", `• ${p.nome || p.email} (${p.email})`))
                    : [h("small", "", "Nenhum passageiro neste trecho.")])))
            : [h("p", "vazio", "Nenhum trecho cadastrado.")])));
}

// ---------- Cancelamentos ----------
async function cancelar(selecionado, url, msgVazio, msgConfirma, msgOk) {
    if (!selecionado) return aviso(msgVazio, "aviso");
    if (!confirm(msgConfirma)) return;
    try {
        await api(url, "DELETE");
        aviso(msgOk, "sucesso");
        reservaSel = caronaSel = null;
        await carregar();
    } catch (e) {
        aviso(e.message, "erro");
    }
}

$("cancelar-reserva").onclick = () => cancelar(reservaSel,
    "/api/reservas/" + encodeURIComponent(reservaSel?.idReserva),
    "Selecione uma reserva na lista para cancelar sua inscrição.",
    "Cancelar esta inscrição?", "Inscrição cancelada com sucesso!");

$("cancelar-carona").onclick = () => cancelar(caronaSel,
    "/api/caronas/" + encodeURIComponent(caronaSel?.id),
    "Selecione uma viagem na lista para cancelar.",
    "Cancelar esta viagem? Os passageiros serão notificados.", "Viagem cancelada.");

// ---------- Carregamento ----------
async function carregar() {
    try {
        const reservas = await api("/api/reservas/minhas");
        renderLista($("lista-reservas"), reservas, cardReserva, "Você ainda não tem inscrições.", r => reservaSel = r);
        if (usuario.perfilMotorista) {
            const caronas = await api("/api/caronas/minhas");
            renderLista($("lista-caronas"), caronas, cardCarona, "Você ainda não criou nenhuma carona.", c => caronaSel = c);
        }
    } catch (e) {
        aviso(e.message, "erro");
    }
}

(async () => {
    try { await carregarCidades(); } catch { aviso("Não foi possível carregar as cidades.", "erro"); }
    renderPerfil();
    await carregar();
})();