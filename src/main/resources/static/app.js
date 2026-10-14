const API = "/api/machines";
const POLL_INTERVAL_MS = 2000;
const WAKE_TIMEOUT_MS = 120000;

const STATUS_LABELS = {
    OFFLINE: "Выключен",
    WAKING: "Запускается…",
    ONLINE: "Работает",
    TIMEOUT: "Таймаут",
};

const machinesEl = document.getElementById("machines");
const emptyEl = document.getElementById("empty");
const errorEl = document.getElementById("error");

const cards = new Map();
const pollers = new Map();

function showError(message) {
    errorEl.textContent = message;
    errorEl.classList.remove("hidden");
}

function clearError() {
    errorEl.classList.add("hidden");
    errorEl.textContent = "";
}

function setBadge(card, status) {
    const badge = card.querySelector(".badge");
    badge.className = "badge " + status.toLowerCase();
    badge.textContent = STATUS_LABELS[status] || status;
}

function setButton(card, disabled, label) {
    const button = card.querySelector("button");
    button.disabled = disabled;
    button.textContent = label;
}

function createCard(machine) {
    const card = document.createElement("div");
    card.className = "card";
    card.dataset.name = machine.name;

    const info = document.createElement("div");
    info.className = "info";

    const name = document.createElement("div");
    name.className = "name";
    name.textContent = machine.name;

    const meta = document.createElement("div");
    meta.className = "meta";
    meta.textContent = `${machine.ip}:${machine.port} · ${machine.mac}`;

    info.append(name, meta);

    const actions = document.createElement("div");
    actions.className = "actions";

    const badge = document.createElement("span");
    badge.className = "badge offline";
    badge.textContent = STATUS_LABELS.OFFLINE;

    const button = document.createElement("button");
    button.type = "button";
    button.textContent = "Разбудить";
    button.addEventListener("click", () => wake(machine.name));

    actions.append(badge, button);
    card.append(info, actions);

    cards.set(machine.name, card);
    return card;
}

function render(machines) {
    machinesEl.innerHTML = "";
    cards.clear();

    if (!machines.length) {
        emptyEl.classList.remove("hidden");
        return;
    }
    emptyEl.classList.add("hidden");

    for (const machine of machines) {
        const card = createCard(machine);
        setBadge(card, machine.status);
        machinesEl.append(card);
    }
}

async function fetchJson(url, options) {
    const response = await fetch(url, options);
    if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
    }
    return response.json();
}

async function loadMachines() {
    try {
        clearError();
        const machines = await fetchJson(API);
        render(machines);
    } catch (e) {
        showError("Не удалось загрузить список машин: " + e.message);
    }
}

function stopPolling(name) {
    const timer = pollers.get(name);
    if (timer) {
        clearInterval(timer);
        pollers.delete(name);
    }
}

function startPolling(name, startedAt) {
    stopPolling(name);

    const timer = setInterval(async () => {
        const card = cards.get(name);
        if (!card) {
            stopPolling(name);
            return;
        }

        try {
            const status = await fetchJson(`${API}/${encodeURIComponent(name)}/status`);
            if (status.status === "ONLINE") {
                setBadge(card, "ONLINE");
                setButton(card, false, "Разбудить");
                stopPolling(name);
                return;
            }
        } catch (e) {
            showError("Ошибка проверки статуса: " + e.message);
        }

        if (Date.now() - startedAt > WAKE_TIMEOUT_MS) {
            setBadge(card, "TIMEOUT");
            setButton(card, false, "Разбудить");
            stopPolling(name);
        }
    }, POLL_INTERVAL_MS);

    pollers.set(name, timer);
}

async function wake(name) {
    const card = cards.get(name);
    if (!card) {
        return;
    }

    clearError();
    setButton(card, true, "Отправка…");
    setBadge(card, "WAKING");

    try {
        const result = await fetchJson(`${API}/${encodeURIComponent(name)}/wake`, { method: "POST" });
        if (result.status === "ONLINE") {
            setBadge(card, "ONLINE");
            setButton(card, false, "Разбудить");
            return;
        }
        setButton(card, true, "Ожидание…");
        startPolling(name, Date.now());
    } catch (e) {
        showError("Не удалось отправить пакет: " + e.message);
        setBadge(card, "OFFLINE");
        setButton(card, false, "Разбудить");
    }
}

loadMachines();
