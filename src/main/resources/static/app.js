const API = "/api/machines";
const POLL_INTERVAL_MS = 2000;
const WAKE_TIMEOUT_MS = 120000;
const SHUTDOWN_TIMEOUT_MS = 90000;

const STATUS_LABELS = {
    OFFLINE: "Выключен",
    WAKING: "Запускается…",
    ONLINE: "Работает",
    STOPPING: "Выключается…",
    TIMEOUT: "Таймаут",
};

const machinesEl = document.getElementById("machines");
const emptyEl = document.getElementById("empty");
const errorEl = document.getElementById("error");

const modalEl = document.getElementById("modal");
const modalTextEl = document.getElementById("modal-text");
const modalCancelEl = document.getElementById("modal-cancel");
const modalConfirmEl = document.getElementById("modal-confirm");

const cards = new Map();
const pollers = new Map();

let openMenu = null;
let modalConfirmHandler = null;

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

function closeMenus() {
    if (openMenu) {
        openMenu.remove();
        openMenu = null;
    }
}

function toggleMenu(card, items) {
    const actions = card.querySelector(".actions");
    const wasOpen = openMenu && openMenu.dataset.name === card.dataset.name;
    closeMenus();
    if (wasOpen) {
        return;
    }

    const menu = document.createElement("div");
    menu.className = "menu";
    menu.dataset.name = card.dataset.name;
    menu.addEventListener("click", (e) => e.stopPropagation());

    for (const item of items) {
        const button = document.createElement("button");
        button.type = "button";
        button.className = "menu-item" + (item.danger ? " danger" : "");
        button.textContent = item.label;
        button.disabled = !!item.disabled;
        if (item.onClick && !item.disabled) {
            button.addEventListener("click", () => {
                closeMenus();
                item.onClick();
            });
        }
        menu.append(button);
    }

    actions.append(menu);
    openMenu = menu;
}

function renderActions(card, status) {
    closeMenus();

    const actions = card.querySelector(".actions");
    for (const child of [...actions.children]) {
        if (!child.classList.contains("badge")) {
            child.remove();
        }
    }

    const name = card.dataset.name;
    const isOnline = status === "ONLINE";
    const busy = status === "WAKING" || status === "STOPPING";

    if (!isOnline) {
        const wakeButton = document.createElement("button");
        wakeButton.type = "button";
        wakeButton.textContent = busy
            ? (status === "WAKING" ? "Ожидание…" : "Выключение…")
            : "Разбудить";
        wakeButton.disabled = busy;
        if (!busy) {
            wakeButton.addEventListener("click", () => wake(name));
        }
        actions.append(wakeButton);
    }

    const kebab = document.createElement("button");
    kebab.type = "button";
    kebab.className = "kebab";
    kebab.title = "Действия";
    kebab.textContent = "\u22EE";
    kebab.disabled = busy;
    kebab.addEventListener("click", (e) => {
        e.stopPropagation();
        toggleMenu(card, [
            {
                label: "Выключить",
                danger: true,
                disabled: !isOnline,
                onClick: () => confirmShutdown(name),
            },
        ]);
    });
    actions.append(kebab);
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

    actions.append(badge);
    card.append(info, actions);

    cards.set(machine.name, card);
    return card;
}

function render(machines) {
    machinesEl.innerHTML = "";
    cards.clear();
    closeMenus();

    if (!machines.length) {
        emptyEl.classList.remove("hidden");
        return;
    }
    emptyEl.classList.add("hidden");

    for (const machine of machines) {
        const card = createCard(machine);
        setBadge(card, machine.status);
        renderActions(card, machine.status);
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

async function refreshStatus(name) {
    const card = cards.get(name);
    if (!card) {
        return;
    }
    try {
        const status = await fetchJson(`${API}/${encodeURIComponent(name)}/status`);
        setBadge(card, status.status);
        renderActions(card, status.status);
    } catch (e) {
        showError("Ошибка проверки статуса: " + e.message);
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
                renderActions(card, "ONLINE");
                stopPolling(name);
                return;
            }
        } catch (e) {
            showError("Ошибка проверки статуса: " + e.message);
        }

        if (Date.now() - startedAt > WAKE_TIMEOUT_MS) {
            setBadge(card, "TIMEOUT");
            renderActions(card, "TIMEOUT");
            stopPolling(name);
        }
    }, POLL_INTERVAL_MS);

    pollers.set(name, timer);
}

function startShutdownPolling(name, startedAt) {
    stopPolling(name);

    const timer = setInterval(async () => {
        const card = cards.get(name);
        if (!card) {
            stopPolling(name);
            return;
        }

        try {
            const status = await fetchJson(`${API}/${encodeURIComponent(name)}/status`);
            if (status.status === "OFFLINE") {
                setBadge(card, "OFFLINE");
                renderActions(card, "OFFLINE");
                stopPolling(name);
                return;
            }
        } catch (e) {
            showError("Ошибка проверки статуса: " + e.message);
        }

        if (Date.now() - startedAt > SHUTDOWN_TIMEOUT_MS) {
            setBadge(card, "ONLINE");
            renderActions(card, "ONLINE");
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
    setBadge(card, "WAKING");
    renderActions(card, "WAKING");

    try {
        const result = await fetchJson(`${API}/${encodeURIComponent(name)}/wake`, { method: "POST" });
        if (result.status === "ONLINE") {
            setBadge(card, "ONLINE");
            renderActions(card, "ONLINE");
            return;
        }
        startPolling(name, Date.now());
    } catch (e) {
        showError("Не удалось отправить пакет: " + e.message);
        setBadge(card, "OFFLINE");
        renderActions(card, "OFFLINE");
    }
}

function openModal(text, onConfirm) {
    modalTextEl.textContent = text;
    modalConfirmHandler = onConfirm;
    modalEl.classList.remove("hidden");
    modalConfirmEl.focus();
}

function closeModal() {
    modalEl.classList.add("hidden");
    modalConfirmHandler = null;
}

async function shutdown(name) {
    const card = cards.get(name);
    if (!card) {
        return;
    }

    clearError();
    setBadge(card, "STOPPING");
    renderActions(card, "STOPPING");

    try {
        const result = await fetchJson(`${API}/${encodeURIComponent(name)}/shutdown`, { method: "POST" });
        if (result.status === "OFFLINE") {
            setBadge(card, "OFFLINE");
            renderActions(card, "OFFLINE");
            return;
        }
        startShutdownPolling(name, Date.now());
    } catch (e) {
        showError("Не удалось выключить сервер: " + e.message);
        await refreshStatus(name);
    }
}

function confirmShutdown(name) {
    openModal(`Сервер «${name}» будет выключен. Все процессы будут завершены.`, () => shutdown(name));
}

modalCancelEl.addEventListener("click", closeModal);
modalConfirmEl.addEventListener("click", () => {
    const handler = modalConfirmHandler;
    closeModal();
    if (handler) {
        handler();
    }
});
modalEl.addEventListener("click", (e) => {
    if (e.target === modalEl) {
        closeModal();
    }
});
document.addEventListener("click", closeMenus);
document.addEventListener("keydown", (e) => {
    if (e.key === "Escape") {
        closeMenus();
        closeModal();
    }
});

loadMachines();
