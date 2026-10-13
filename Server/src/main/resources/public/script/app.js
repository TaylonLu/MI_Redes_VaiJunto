const form = document.getElementById("form-busca");
const campoId = document.getElementById("campo-id");
const botao = document.getElementById("botao-buscar");
const resultado = document.getElementById("resultado");

form.addEventListener("submit", (evento) => {
    evento.preventDefault();
    buscarUsuario(campoId.value.trim());
});

const idDaUrl = new URLSearchParams(location.search).get("id");
if (idDaUrl) {
    campoId.value = idDaUrl;
    buscarUsuario(idDaUrl);
}

async function buscarUsuario(id) {
    if (!id) return;

    botao.disabled = true;
    botao.textContent = "Buscando...";

    try {
        const resposta = await fetch("/usuarios/" + encodeURIComponent(id));
        if (resposta.status === 404) {
            mostrarErro("Nenhum usuário encontrado com esse ID.");
        } else if (!resposta.ok) {
            mostrarErro("O servidor respondeu com erro: " + resposta.status + ".\n Tente novamente.");
        } else {
            mostrarUsuario(await resposta.json());
            history.replaceState(null, "", "?id=" + encodeURIComponent(id));
        }
    } catch (erro) {
        mostrarErro("Não foi possível falar com o servidor. Verifique se ele morreu.");
    } finally {
        botao.disabled = false;
        botao.textContent = "Buscar Usuário";
    }
}

function mostrarUsuario(dados) {
    const cartao = criar("div", "cartao");
    cartao.appendChild(criar("h2", null, "Dados do usuário"));

    const lista = document.createElement("dl");
    for (const [chave, valor] of Object.entries(dados)) {
        lista.appendChild(criar("dt", null, chave));
        lista.appendChild(criar("dd", null, formatar(valor)));
    }
    cartao.appendChild(lista);

    resultado.replaceChildren(cartao);
}

function mostrarErro(mensagem) {
    const cartao = criar("div", "cartao erro");
    cartao.setAttribute("role", "alert");
    cartao.appendChild(criar("p", null, mensagem));
    resultado.replaceChildren(cartao);
}

function formatar(valor) {
    if (valor === null || valor === undefined) return "-";
    if (typeof valor === "object") return JSON.stringify(valor);
    return String(valor);
}

// textContent evita que dados do usuário sejam interpretados como HTML
function criar(tag, classe, texto) {
    const elemento = document.createElement(tag);
    if (classe) elemento.className = classe;
    if (texto !== undefined) elemento.textContent = texto;
    return elemento;
}