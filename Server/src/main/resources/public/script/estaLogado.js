(function () {
    const raiz = document.documentElement;
    const token = localStorage.getItem("userToken");

    const expulsar = () => {
        localStorage.removeItem("userToken");
        localStorage.removeItem("usuario");
        location.replace("/pages/inicio.html");
    }

    raiz.style.visibility = "hidden";
    if (!token) return expulsar();

    fetch("/api/me", { headers: { "Authorization": "Bearer " + token } })
        .then(res => { if (!res.ok) throw new Error("Sessão Inativa"); return r.json(); })
        .then(usuario => {
            localStorage.setItem("usuario", JSON.stringify(usuario));
            raiz.style.visibility = "";
        })
        .catch(expulsar);

    window.addEventListener("pageshow", e => {
        if (e.persisted && !localStorage.getItem("userToken")) expulsar();
    });
});