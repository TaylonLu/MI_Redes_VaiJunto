function navigateTo(targetScreenId) {
    const screens = document.querySelectorAll('.screen');

    // Remove a classe 'active' de todas
    screens.forEach(screen => {
        screen.classList.remove('active');
    });

    // Adiciona a classe 'active' apenas na tela alvo
    const targetScreen = document.getElementById(targetScreenId);
    if (targetScreen) {
        targetScreen.classList.add('active');
    }
}

async function enviarJson(url, dados) {
    let resposta;
    try {
        resposta = await fetch(url, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(dados)
        });
    } catch {
        throw new Error("Não foi possível falar com o servidor.");
    }

    let corpo = null;
    try { corpo = await resposta.json(); } catch { }

    if (!resposta.ok) {
        throw new Error(corpo?.erro ?? corpo?.title ?? "Erro " + resposta.status);
    }

    return corpo;
}

async function efetuarLogin() {
    const dadosLogin = {
        "email": getData("login-email").trim(),
        "senha": getData("login-senha").trim()
    };

    try {
        const resposta = await enviarJson("/api/login", dadosLogin);
        if (!resposta?.token) throw new Error("Resposta do servidor sem token.");

        localStorage.setItem("userToken", resposta.token);
        localStorage.setItem("usuario", JSON.stringify(resposta.usuario));
        window.location.href = "/";
    } catch (erro) {
        console.log("Aconteceu um erro: " + erro.message);
    }
}

async function efetuarCadastro() {
    const senha = getData("cad-senha").trim();
    if (senha !== getData("cad-senha-conf").trim()) {
        mostrarErro("cad-erro", "As senhas não coincidem.");
        return;
    }

    const dadosCadastro = {
        "nome": getData("cad-nome").trim(),
        "email": getData("cad-email").trim(),
        "senha": senha
    }

    try {
        const resposta = await enviarJson("/api/cadastro", dadosCadastro);
        if (!resposta?.token) throw new Error("Resposta do servidor sem token.");

        localStorage.setItem("userToken", resposta.token);
        localStorage.setItem("usuario", resposta.usuario);
        window.location.href = "/";
    } catch (erro) {
        console.log("Aconteceu um erro: " + erro.message);
    }
}

function getData(id) {
    return document.getElementById(id)?.value ?? "";
}