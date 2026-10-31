const PRINCIPAL = "/login-efetuado";   // rota que serve a tela principal
const CORES = ["#27ae60", "#e74c3c", "#9b59b6", "#f39c12", "#3498db"];
const sessao = {
    token: () => localStorage.getItem("userToken"),
    usuario: () => JSON.parse(localStorage.getItem("usuario") || "null")
};
let cidades = [];
let cidadesPorID = new Map();

// Cria elementos; strings viram texto (nunca HTML)
function h(tag, cls, ...filhos) {
    const e = document.createElement(tag);
    if (cls) e.className = cls;
    for (const f of filhos) if (f != null) e.append(f);
    return e;
}

function botao(texto, cls, fn) {
    const b = h("button", cls, texto);
    b.type = "button";
    b.onclick = fn;
    return b;
}

function sair() {
    localStorage.removeItem("userToken");
    localStorage.removeItem("usuario");
    location.href = "/pages/inicio.html";
}

function exigirLogin() {
    if (!sessao.token() || !sessao.usuario()) {
        console.log("Problema na sessão.")
        sair();
    }
}

async function api(url, metodo = "GET", corpo) {
    let r;
    try {
        r = await fetch(url, {
            method: metodo,
            headers: { "Content-Type": "application/json", "Authorization": "Bearer " + sessao.token() },
            body: corpo === undefined ? undefined : JSON.stringify(corpo)
        });
    } catch {
        throw new Error("Não foi possível falar com o servidor.");
    }
    let dados = null;
    try { dados = await r.json(); } catch { /* sem corpo */ }

    if (r.status === 401) {
        console.log("Erro 401");
        sair(); throw new Error("Sessão expirada.");
    }
    if (!r.ok) throw Object.assign(new Error(dados?.erro ?? dados?.title ?? "Erro " + r.status), { status: r.status });
    return dados;
}

function aviso(msg, tipo = "info") {
    const t = h("div", "toast " + tipo, msg);
    document.getElementById("toasts").append(t);
    setTimeout(() => t.remove(), 4000);
}


async function carregarCidades() {
    if (cidades.length > 0) return;

    const cache = sessionStorage.getItem("cidades_cache");

    if (cache) {
        cidades = JSON.parse(cache);
        console.log("Cidades carregadas do cache local.");
    } else {
        const resposta = await fetch("/data/cidades.json");

        if (!resposta.ok)
            throw new Error(`Erro HTTP: ${resposta.status}`);

        const bruto = await resposta.json();

        cidades = bruto.map(c => {
            const x = Number(c.x || 0);
            const y = Number(c.y || 0);
            const largura = Number(c.largura || 0);
            const altura = Number(c.altura || 0);

            return {
                id: Number(c.id),
                nome: c.nome,
                x, y, largura, altura,
                cx: x + largura / 2,
                cy: y + altura / 2,
                estradas: (c.conexoes ?? []).map(Number),
                regiao: c.regiao ?? "Desconhecida"
            };
        });

        sessionStorage.setItem("cidades_cache", JSON.stringify(cidades));
        console.log("Cidades baixadas do servidor.");
    }

    // Normaliza os IDs e reconstrói o índice, inclusive com cache.
    cidadesPorID.clear();

    for (const c of cidades) {
        c.id = Number(c.id);
        c.estradas = (c.estradas ?? []).map(Number);
        cidadesPorID.set(c.id, c);
    }
}

const cidade = id => cidadesPorID.get(Number(id));
const nomeCidade = id => cidade(id)?.nome ?? "#" + id;



const fmtData = iso => new Date(iso).toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short" });
const nMotoristas = trechos => new Set(trechos.map(t => t.servidor)).size;
const corStatus = s => ({ CONFIRMADA: "#2ecc71", EM_ANDAMENTO: "#2ecc71", CANCELADA: "#e74c3c", FINALIZADA: "#95a5a6" })[String(s).toUpperCase()] ?? "#f39c12";

function lerTrecho(t) {
    if (typeof t === "string") {
        const [inicio, fim] = t.split("-").map(Number);
        return { inicio, fim };
    }
    return t;
}

function selo(status) {
    const e = h("span", "selo", String(status));
    e.style.background = corStatus(status);
    return e;
}

// Uma cor por passo; muda sempre que o motorista muda
function coresDosPassos(trechos) { // alterado de passos para trechos
    let i = 0, atual = trechos[0]?.servidor; // alterado de idMotorista para servidor
    return trechos.map(t => {
        if (t.servidor !== atual) { atual = t.servidor; i = (i + 1) % CORES.length; }
        return CORES[i];
    });
}

// Lista selecionável (clique ou Enter/Espaço)
function renderLista(ul, itens, fazerCard, vazio, aoSelecionar) {
    ul.replaceChildren();
    if (!itens.length) { ul.append(h("li", "vazio", vazio)); return; }
    for (const item of itens) {
        const li = fazerCard(item);
        li.tabIndex = 0;
        const marcar = () => {
            ul.querySelectorAll(".card").forEach(c => c.setAttribute("aria-selected", c === li));
            aoSelecionar(item);
        };
        li.onclick = marcar;
        li.onkeydown = e => { if (e.target === li && (e.key === "Enter" || e.key === " ")) { e.preventDefault(); marcar(); } };
        ul.append(li);
    }
}


function nCaronas(trechos) {
    return new Set(
        trechos.map(t => `${t.servidor}/${t.id_carona}`)
    ).size;
}

function corpoPassos(trechos, ...extra) {
    const n = nCaronas(trechos);

    return h("div", "corpo", ...extra,
        h("p", "", n <= 1
            ? "Percurso realizado por uma única carona."
            : `Percurso com ${n} caronas e ${n - 1} baldeação(ões).`
        ),

        h("h3", "", "Trechos do percurso"),

        ...(trechos.length
                ? trechos.map((t, i) => h("div", "caixa",
                    h("strong", "", `${i + 1}. ${[
                        t.origem,
                        ...(t.paradas_intermediarias ?? []),
                        t.destino
                    ].join(" → ")}`),

                    h("small", "", `Partida: ${fmtData(t.horario)}`),

                    h("small", "", `Preço: R$ ${Number(t.preco).toFixed(2)}`),

                    h("small", "", `Assentos livres: ${t.assentos_livres}`),

                    h("small", "", `Servidor: ${t.servidor}`),

                    h("small", "", `ID da carona: ${t.id_carona}`)
                ))
                : [h("p", "vazio", "Nenhum trecho cadastrado.")]
        )
    );
}


// Diálogo modal; resolve true se o usuário clicar no botão de confirmação
function dialogo(titulo, corpo, confirmar) {
    return new Promise(resolve => {
        const d = document.getElementById("dialogo");
        d.returnValue = "";
        d.replaceChildren(h("h2", "", titulo), corpo, h("div", "acoes",
            ...(confirmar ? [botao(confirmar, "btn verde", () => d.close("ok"))] : []),
            botao("Fechar", "btn", () => d.close(""))));
        d.onclose = () => resolve(d.returnValue === "ok");
        d.showModal();
    });
}