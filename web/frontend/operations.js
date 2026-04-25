function normalizeText(value) {
  return String(value == null ? "" : value).trim().toLocaleLowerCase("pl-PL");
}

function parseOptionalNumber(value) {
  const text = String(value == null ? "" : value).trim();
  if (!text) {
    return null;
  }
  const parsed = Number(text.replace(",", "."));
  return Number.isFinite(parsed) ? parsed : null;
}

export function filterOperations(operations, filters, helpers) {
  const {
    state,
    lookupName,
    lookupAssetLabel
  } = helpers;
  const search = normalizeText(filters.search);
  const type = String(filters.type || "");
  const portfolioId = String(filters.portfolioId || "");
  const accountId = String(filters.accountId || "");
  const dateFrom = String(filters.dateFrom || "");
  const dateTo = String(filters.dateTo || "");
  const amountMin = parseOptionalNumber(filters.amountMin);
  const amountMax = parseOptionalNumber(filters.amountMax);

  return operations.filter((operation) => {
    if (type && operation.type !== type) {
      return false;
    }
    if (portfolioId && operation.portfolioId !== portfolioId) {
      return false;
    }
    if (accountId && operation.accountId !== accountId) {
      return false;
    }
    if (dateFrom && String(operation.date || "") < dateFrom) {
      return false;
    }
    if (dateTo && String(operation.date || "") > dateTo) {
      return false;
    }
    const amount = Number(operation.amount || 0);
    if (amountMin != null && amount < amountMin) {
      return false;
    }
    if (amountMax != null && amount > amountMax) {
      return false;
    }
    if (!search) {
      return true;
    }
    const haystack = [
      operation.date,
      operation.type,
      lookupName(state.portfolios, operation.portfolioId),
      lookupName(state.accounts, operation.accountId),
      lookupAssetLabel(operation.assetId),
      lookupAssetLabel(operation.targetAssetId),
      operation.note,
      operation.currency,
      Array.isArray(operation.tags) ? operation.tags.join(", ") : ""
    ]
      .map((item) => normalizeText(item))
      .join(" ");
    return haystack.includes(search);
  });
}

export function renderOperations(deps) {
  const {
    dom,
    state,
    lookupName,
    lookupAssetLabel,
    escapeHtml,
    formatFloat,
    formatMoney
  } = deps;

  const filters = {
    search: dom.operationHistorySearchInput ? dom.operationHistorySearchInput.value : "",
    dateFrom: dom.operationHistoryDateFromInput ? dom.operationHistoryDateFromInput.value : "",
    dateTo: dom.operationHistoryDateToInput ? dom.operationHistoryDateToInput.value : "",
    type: dom.operationHistoryTypeSelect ? dom.operationHistoryTypeSelect.value : "",
    portfolioId: dom.operationHistoryPortfolioSelect ? dom.operationHistoryPortfolioSelect.value : "",
    accountId: dom.operationHistoryAccountSelect ? dom.operationHistoryAccountSelect.value : "",
    amountMin: dom.operationHistoryAmountMinInput ? dom.operationHistoryAmountMinInput.value : "",
    amountMax: dom.operationHistoryAmountMaxInput ? dom.operationHistoryAmountMaxInput.value : ""
  };

  const filteredOperations = filterOperations(state.operations, filters, {
    state,
    lookupName,
    lookupAssetLabel
  });

  const sortedOperations = filteredOperations
    .slice()
    .sort((a, b) => String(b.date).localeCompare(String(a.date)));

  if (dom.operationHistoryInfo) {
    const activeFilters = [
      filters.search,
      filters.dateFrom,
      filters.dateTo,
      filters.type,
      filters.portfolioId,
      filters.accountId,
      filters.amountMin,
      filters.amountMax
    ].filter(Boolean).length;
    dom.operationHistoryInfo.textContent = activeFilters
      ? `Pokazano ${filteredOperations.length} z ${state.operations.length} operacji`
      : `Łącznie operacji: ${state.operations.length}`;
  }

  if (!dom.operationList) {
    return;
  }

  if (!sortedOperations.length) {
    dom.operationList.innerHTML = `
      <div class="operation-empty-state">
        <span class="operation-empty-icon">+</span>
        <strong>Nie ma jeszcze operacji w tym widoku</strong>
        <p>Dodaj wpłatę gotówki, kupno albo sprzedaż. Jeśli używasz filtrów, możesz je wyczyścić.</p>
        <div class="operation-empty-actions">
          <button type="button" class="btn" data-quick-operation="cash">Wpłata gotówki</button>
          <button type="button" class="btn secondary" data-quick-operation="buy">Kupno waloru</button>
        </div>
      </div>
    `;
    dom.operationList.querySelectorAll("[data-quick-operation]").forEach((button) => {
      button.addEventListener("click", (event) => {
        document
          .querySelectorAll(`[data-quick-operation="${event.currentTarget.dataset.quickOperation}"]`)[0]
          ?.click();
      });
    });
    return;
  }

  dom.operationList.innerHTML = `
    <div class="operation-card-list">
      ${sortedOperations.map((operation) => renderOperationCard(operation)).join("")}
    </div>
  `;

  function renderOperationCard(operation) {
    const currency = operation.currency || state.meta.baseCurrency;
    const assetLabel = lookupAssetLabel(operation.assetId);
    const targetAssetLabel = lookupAssetLabel(operation.targetAssetId);
    const quantity = formatFloat(operation.quantity);
    const targetQuantity = formatFloat(operation.targetQuantity);
    const price = formatMoney(operation.price, currency);
    const amount = formatMoney(operation.amount, currency);
    const fee = formatMoney(operation.fee, currency);
    const title = assetLabel && assetLabel !== "-" ? assetLabel : lookupName(state.accounts, operation.accountId);
    const tags = Array.isArray(operation.tags) && operation.tags.length ? operation.tags.join(", ") : "";
    return `
      <article class="operation-card" data-action="view-operation" data-id="${escapeHtml(operation.id)}" tabindex="0">
        <div class="operation-card-main">
          <span class="operation-type-pill">${escapeHtml(operation.type || "Operacja")}</span>
          <strong>${escapeHtml(title || "Operacja")}</strong>
          <small>${escapeHtml(operation.date || "-")} · ${escapeHtml(lookupName(state.portfolios, operation.portfolioId))}</small>
        </div>
        <div class="operation-card-amount">
          <strong>${amount}</strong>
          <small>${escapeHtml(currency)}</small>
        </div>
        <div class="operation-card-grid">
          <span><small>Konto</small><strong>${escapeHtml(lookupName(state.accounts, operation.accountId))}</strong></span>
          <span><small>Ilość</small><strong>${quantity}</strong></span>
          <span><small>Cena</small><strong>${price}</strong></span>
          <span><small>Prowizja</small><strong>${fee}</strong></span>
          ${
            targetAssetLabel && targetAssetLabel !== "-"
              ? `<span><small>Walor doc.</small><strong>${escapeHtml(targetAssetLabel)}</strong></span>`
              : ""
          }
          ${
            Number(operation.targetQuantity || 0)
              ? `<span><small>Ilość doc.</small><strong>${targetQuantity}</strong></span>`
              : ""
          }
        </div>
        ${
          tags || operation.note
            ? `<p class="operation-card-note">${escapeHtml([tags, operation.note].filter(Boolean).join(" · "))}</p>`
            : ""
        }
        <div class="operation-card-actions">
          <button class="btn secondary" type="button" data-action="view-operation" data-id="${escapeHtml(operation.id)}">Szczegóły</button>
          <button class="btn secondary" type="button" data-action="edit-operation" data-id="${escapeHtml(operation.id)}">Edytuj</button>
          <button class="btn danger" type="button" data-action="delete-operation" data-id="${escapeHtml(operation.id)}">Usuń</button>
        </div>
      </article>
    `;
  }
}

export function renderRecurring(deps) {
  const { dom, state, lookupName, lookupAssetLabel, escapeHtml, formatMoney, renderTable } = deps;
  const rows = state.recurringOps.map((item) => [
    escapeHtml(item.name),
    escapeHtml(item.type),
    escapeHtml(item.frequency),
    escapeHtml(item.startDate),
    formatMoney(item.amount, item.currency || state.meta.baseCurrency),
    escapeHtml(lookupName(state.portfolios, item.portfolioId)),
    escapeHtml(lookupName(state.accounts, item.accountId)),
    escapeHtml(lookupAssetLabel(item.assetId)),
    escapeHtml(item.lastGeneratedDate || "-"),
    [
      `<button class="btn secondary" data-action="edit-recurring" data-id="${item.id}">Edytuj</button>`,
      `<button class="btn danger" data-action="delete-recurring" data-id="${item.id}">Usuń</button>`
    ].join(" ")
  ]);
  renderTable(
    dom.recurringList,
    ["Nazwa", "Typ", "Częstotliwość", "Start", "Kwota", "Portfel", "Konto", "Walor", "Ostatnio", "Akcje"],
    rows
  );
}
