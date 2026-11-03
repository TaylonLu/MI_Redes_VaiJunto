
(async () => {
    try {
        const resposta = await fetch("/api/servidor/cidades");

        if (!resposta.ok) return;

        const regiao = resposta.headers.get("X-Server-Region");

        const logos = ["Kalos", "Unova", "Johto", "Kanto"];

        if (!logos.includes(regiao)) return;

        document.documentElement.style.setProperty(
            "--logo-servidor",
            `url("/images/logos/${regiao}.png")`
        );

        const logo = document.getElementById("logo-servidor");

        if (logo) {
            logo.src = `/images/logos/${regiao}.png`;
            logo.alt = `Logo do servidor ${regiao}`;
        }
    } catch (erro) {
        console.error("Não foi possível identificar o servidor:", erro);
    }
})();
