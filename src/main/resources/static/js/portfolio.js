// 내 주식 탭에서 포트폴리오, 미체결 주문, 거래내역을 조회하고 화면에 표시하는 스크립트

const portfolioState = {
    portfolio: null,
    openOrders: null,
    trades: null,
    loading: false,
    loaded: false,
    requestId: 0,
    inFlight: null
};

document.addEventListener('DOMContentLoaded', () => {
    const isMainDashboard = document.querySelector('.dashboard-tabs');

    if (!isMainDashboard && document.getElementById('holding-table-wrap')) {
        loadPortfolioDashboard();
    }
});

// 내 주식 탭의 포트폴리오, 미체결 주문, 거래내역을 함께 갱신한다.
async function loadPortfolioDashboard(force = false) {
    if (portfolioState.loaded && !force) {
        renderPortfolioSummary(portfolioState.portfolio);
        renderHoldings(portfolioState.portfolio?.holdings || []);
        renderOpenOrders(portfolioState.openOrders || []);
        renderTrades(portfolioState.trades || []);
        return;
    }

    if (portfolioState.inFlight && !force) {
        return await portfolioState.inFlight;
    }

    const requestId = ++portfolioState.requestId;
    portfolioState.loading = true;

    portfolioState.inFlight = (async () => {
        const [portfolio, openOrders, trades] = await Promise.all([
            loadPortfolioData(),
            loadOpenOrdersData(),
            loadTradesData()
        ]);

        if (requestId !== portfolioState.requestId) {
            return;
        }

        if (portfolio) {
            portfolioState.portfolio = portfolio;
            portfolioState.loaded = true;

            renderPortfolioSummary(portfolio);
            renderHoldings(portfolio.holdings || []);
        } else if (!portfolioState.portfolio) {
            renderPortfolioSummary(null);
            renderEmptyPortfolio();
        }

        if (openOrders) {
            portfolioState.openOrders = openOrders;
            renderOpenOrders(openOrders || []);
        } else if (!portfolioState.openOrders) {
            renderEmptyOpenOrders();
        }

        if (trades) {
            portfolioState.trades = trades;
            renderTrades(trades || []);
        } else if (!portfolioState.trades) {
            renderEmptyTrades();
        }
    })();

    try {
        await portfolioState.inFlight;
    } finally {
        if (requestId === portfolioState.requestId) {
            portfolioState.loading = false;
            portfolioState.inFlight = null;
        }
    }
}

// 내 포트폴리오 요약과 보유 종목을 조회한다.
async function loadPortfolioData() {
    const response = await authFetch('/api/portfolio');

    if (!response || !response.ok) {
        return null;
    }

    return await response.json();
}

// 아직 체결되지 않은 미체결/부분체결 주문을 조회한다.
async function loadOpenOrdersData() {
    const response = await authFetch('/api/orders/open');

    if (!response || !response.ok) {
        return null;
    }

    return await response.json();
}

// 내 전체 거래내역을 조회한다.
async function loadTradesData() {
    const response = await authFetch('/api/trades');

    if (!response || !response.ok) {
        return null;
    }

    return await response.json();
}

// 백엔드에서 계산한 포트폴리오 요약 정보를 화면에 표시한다.
function renderPortfolioSummary(portfolio) {
    const cashBalance = Number(portfolio?.cashBalance || 0);
    const availableCash = Number(portfolio?.availableCash ?? cashBalance);
    const reservedCash = Number(portfolio?.reservedCash || 0);
    const totalAsset = Number(portfolio?.totalAsset || cashBalance);
    const totalEvaluation = Number(portfolio?.totalEvaluation || 0);
    const totalProfitLoss = Number(portfolio?.totalProfitLoss || 0);
    const totalProfitRate = Number(portfolio?.totalProfitRate || 0);

    setPortfolioText('cash-balance', `${portfolioFormatNumber(cashBalance)}원`);
    setPortfolioText('available-cash', `${portfolioFormatNumber(availableCash)}원`);
    setPortfolioText('reserved-cash', `${portfolioFormatNumber(reservedCash)}원`);
    setPortfolioText('total-asset', `${portfolioFormatNumber(totalAsset)}원`);
    setPortfolioText('total-evaluation', `${portfolioFormatNumber(totalEvaluation)}원`);
    setPortfolioText('total-profit-loss', `${portfolioFormatSignedNumber(totalProfitLoss)}원`);
    setPortfolioText('total-profit-rate', `${portfolioFormatSignedNumber(totalProfitRate)}%`);

    setProfitClass('total-profit-loss', totalProfitLoss);
    setProfitClass('total-profit-rate', totalProfitRate);
}

// 보유 종목 목록을 테이블로 표시한다.
function renderHoldings(holdings) {
    const wrap = document.getElementById('holding-table-wrap');

    if (!wrap) {
        return;
    }

    if (!holdings || holdings.length === 0) {
        renderEmptyPortfolio();
        return;
    }

    wrap.innerHTML = `
        <table class="holding-table">
            <thead>
            <tr>
                <th>종목</th>
                <th>보유수량</th>
                <th>주문가능</th>
                <th>평균단가</th>
                <th>현재가</th>
                <th>평가금액</th>
                <th>손익</th>
                <th>수익률</th>
                <th>주문</th>
            </tr>
            </thead>
            <tbody>
            ${holdings.map((holding) => `
                <tr
                    class="holding-row"
                    data-symbol="${portfolioEscapeHtml(holding.symbol)}"
                    data-stock-name="${portfolioEscapeHtml(holding.stockName)}"
                >
                    <td>
                        <div class="holding-name">
                            <strong>${portfolioEscapeHtml(holding.stockName)}</strong>
                            <span>${portfolioEscapeHtml(holding.symbol)}</span>
                        </div>
                    </td>
                    <td>${portfolioFormatNumber(holding.quantity)}주</td>
                    <td>${portfolioFormatNumber(holding.availableQuantity ?? holding.quantity)}주</td>
                    <td>${portfolioFormatNumber(holding.averagePrice)}원</td>
                    <td>${portfolioFormatNumber(holding.currentPrice)}원</td>
                    <td>${portfolioFormatNumber(holding.evaluationAmount)}원</td>
                    <td class="${Number(holding.profitLoss) >= 0 ? 'up' : 'down'}">
                        ${portfolioFormatSignedNumber(holding.profitLoss)}원
                    </td>
                    <td class="${Number(holding.profitRate) >= 0 ? 'up' : 'down'}">
                        ${portfolioFormatSignedNumber(holding.profitRate)}%
                    </td>
                    <td>
                        <div class="holding-actions">
                            <button
                                type="button"
                                class="portfolio-order-btn buy"
                                data-symbol="${portfolioEscapeHtml(holding.symbol)}"
                                data-stock-name="${portfolioEscapeHtml(holding.stockName)}"
                                data-order-type="BUY"
                            >
                                매수
                            </button>
                            <button
                                type="button"
                                class="portfolio-order-btn sell"
                                data-symbol="${portfolioEscapeHtml(holding.symbol)}"
                                data-stock-name="${portfolioEscapeHtml(holding.stockName)}"
                                data-order-type="SELL"
                            >
                                매도
                            </button>
                        </div>
                    </td>
                </tr>
            `).join('')}
            </tbody>
        </table>
    `;

    bindPortfolioOrderButtons();
    bindHoldingRows();
}

// 미체결/부분체결 주문 목록을 테이블로 표시한다.
function renderOpenOrders(openOrders) {
    const wrap = document.getElementById('open-order-table-wrap');

    if (!wrap) {
        return;
    }

    if (!openOrders || openOrders.length === 0) {
        renderEmptyOpenOrders();
        return;
    }

    wrap.innerHTML = `
        <table class="open-order-table">
            <thead>
            <tr>
                <th>종목</th>
                <th>구분</th>
                <th>주문가</th>
                <th>주문수량</th>
                <th>미체결</th>
                <th>상태</th>
                <th>접수시간</th>
                <th>수정</th>
                <th>취소</th>
            </tr>
            </thead>
            <tbody>
            ${openOrders.map((order) => `
                <tr
                    class="open-order-row"
                    data-order-id="${portfolioEscapeHtml(order.id)}"
                    data-stock-name="${portfolioEscapeHtml(order.stockName)}"
                    data-symbol="${portfolioEscapeHtml(order.symbol)}"
                    data-order-type="${portfolioEscapeHtml(order.orderType)}"
                    data-order-price="${portfolioEscapeHtml(order.orderPrice)}"
                    data-quantity="${portfolioEscapeHtml(order.quantity)}"
                    data-executed-quantity="${portfolioEscapeHtml(order.executedQuantity)}"
                    data-remaining-quantity="${portfolioEscapeHtml(order.remainingQuantity)}"
                >
                    <td>
                        <div class="holding-name">
                            <strong>${portfolioEscapeHtml(order.stockName)}</strong>
                            <span>${portfolioEscapeHtml(order.symbol)}</span>
                        </div>
                    </td>
                    <td>
                        <span class="trade-type ${order.orderType === 'BUY' ? 'buy' : 'sell'}">
                            ${order.orderType === 'BUY' ? '매수' : '매도'}
                        </span>
                    </td>
                    <td>${portfolioFormatNumber(order.orderPrice)}원</td>
                    <td>${portfolioFormatNumber(order.quantity)}주</td>
                    <td>${portfolioFormatNumber(order.remainingQuantity)}주</td>
                    <td>${portfolioFormatOrderStatus(order.status)}</td>
                    <td>${formatTradeDate(order.orderedAt)}</td>
                    <td>
                        <button
                            type="button"
                            class="open-order-edit-btn"
                        >
                            수정
                        </button>
                    </td>
                    <td>
                        <button
                            type="button"
                            class="open-order-cancel-btn"
                            data-order-id="${portfolioEscapeHtml(order.id)}"
                        >
                            취소
                        </button>
                    </td>
                </tr>
            `).join('')}
            </tbody>
        </table>
    `;

    bindOpenOrderRows();
    bindOpenOrderEditButtons();
    bindOpenOrderCancelButtons();
}

// 거래내역 목록을 테이블로 표시한다.
function renderTrades(trades) {
    const wrap = document.getElementById('trade-table-wrap');

    if (!wrap) {
        return;
    }

    if (!trades || trades.length === 0) {
        renderEmptyTrades();
        return;
    }

    wrap.innerHTML = `
        <table class="trade-table">
            <thead>
            <tr>
                <th>종목</th>
                <th>구분</th>
                <th>수량</th>
                <th>체결가</th>
                <th>거래금액</th>
                <th>거래시간</th>
            </tr>
            </thead>
            <tbody>
            ${trades.map((trade) => `
                <tr>
                    <td>
                        <div class="holding-name">
                            <strong>${portfolioEscapeHtml(trade.stockName)}</strong>
                            <span>${portfolioEscapeHtml(trade.symbol)}</span>
                        </div>
                    </td>
                    <td>
                        <span class="trade-type ${trade.orderType === 'BUY' ? 'buy' : 'sell'}">
                            ${trade.orderType === 'BUY' ? '매수' : '매도'}
                        </span>
                    </td>
                    <td>${portfolioFormatNumber(trade.quantity)}주</td>
                    <td>${portfolioFormatNumber(trade.price)}원</td>
                    <td>${portfolioFormatNumber(trade.totalAmount)}원</td>
                    <td>${formatTradeDate(trade.tradedAt)}</td>
                </tr>
            `).join('')}
            </tbody>
        </table>
    `;
}

// 보유 종목이 없을 때 빈 상태를 표시한다.
function renderEmptyPortfolio() {
    const wrap = document.getElementById('holding-table-wrap');

    if (!wrap) {
        return;
    }

    wrap.innerHTML = `
        <div class="portfolio-empty">
            <p>보유 종목이 없습니다.</p>
            <span>관심종목이나 상세 화면에서 매수를 진행해보세요.</span>
        </div>
    `;
}

// 미체결 주문이 없을 때 빈 상태를 표시한다.
function renderEmptyOpenOrders() {
    const wrap = document.getElementById('open-order-table-wrap');

    if (!wrap) {
        return;
    }

    wrap.innerHTML = `
        <div class="portfolio-empty small">
            <p>미체결 주문이 없습니다.</p>
            <span>원하는 가격으로 주문하면 이곳에서 확인하고 취소할 수 있습니다.</span>
        </div>
    `;
}

// 거래내역이 없을 때 빈 상태를 표시한다.
function renderEmptyTrades() {
    const wrap = document.getElementById('trade-table-wrap');

    if (!wrap) {
        return;
    }

    wrap.innerHTML = `
        <div class="portfolio-empty">
            <p>거래내역이 없습니다.</p>
            <span>매수 또는 매도를 진행하면 거래내역이 표시됩니다.</span>
        </div>
    `;
}

// 포트폴리오 화면의 매수/매도 버튼을 주문 모달과 연결한다.
function bindPortfolioOrderButtons() {
    const buttons = document.querySelectorAll('.portfolio-order-btn');

    buttons.forEach((button) => {
        button.addEventListener('click', () => {
            const symbol = button.dataset.symbol;
            const stockName = button.dataset.stockName;
            const orderType = button.dataset.orderType;

            if (!symbol || !stockName || !orderType) {
                return;
            }

            openOrderModal(symbol, stockName, orderType);
        });
    });
}

// 미체결 주문 취소 버튼을 취소 API와 연결한다.
function bindOpenOrderCancelButtons() {
    const buttons = document.querySelectorAll('.open-order-cancel-btn');

    buttons.forEach((button) => {
        button.addEventListener('click', async (event) => {
            event.stopPropagation();

            const orderId = button.dataset.orderId;

            if (!orderId) {
                return;
            }

            button.disabled = true;
            await cancelOpenOrder(orderId);
        });
    });
}

// 미체결 주문 수정 버튼을 선택한 주문의 수정 모달과 연결한다.
function bindOpenOrderEditButtons() {
    const buttons = document.querySelectorAll('.open-order-edit-btn');

    buttons.forEach((button) => {
        button.addEventListener('click', (event) => {
            event.stopPropagation();

            const row = button.closest('.open-order-row');

            if (!row || typeof openOrderEditModal !== 'function') {
                return;
            }

            openOrderEditModal({
                id: row.dataset.orderId,
                stockName: row.dataset.stockName,
                symbol: row.dataset.symbol,
                orderType: row.dataset.orderType,
                orderPrice: Number(row.dataset.orderPrice || 0),
                quantity: Number(row.dataset.quantity || 0),
                executedQuantity: Number(row.dataset.executedQuantity || 0),
                remainingQuantity: Number(row.dataset.remainingQuantity || 0)
            });
        });
    });
}

// 미체결 주문 행 클릭 시 해당 종목의 상세화면으로 이동한다.
function bindOpenOrderRows() {
    const rows = document.querySelectorAll('.open-order-row');

    rows.forEach((row) => {
        row.addEventListener('click', () => {
            const symbol = row.dataset.symbol;

            if (!symbol) {
                return;
            }

            window.location.href = `/stocks/detail?keyword=${encodeURIComponent(symbol)}`;
        });
    });
}

// 사용자가 선택한 미체결 주문을 취소하고 화면을 다시 조회한다.
async function cancelOpenOrder(orderId) {
    const response = await authFetch(`/api/orders/${encodeURIComponent(orderId)}/cancel`, {
        method: 'PATCH'
    });

    if (!response || !response.ok) {
        await loadPortfolioDashboard(true);
        return;
    }

    await loadPortfolioDashboard(true);
}

// 보유 종목 행 클릭 시 종목 상세화면으로 이동한다.
function bindHoldingRows() {
    const rows = document.querySelectorAll('.holding-row');

    rows.forEach((row) => {
        row.addEventListener('click', (event) => {
            if (event.target.closest('button')) {
                return;
            }

            const symbol = row.dataset.symbol;
            const stockName = row.dataset.stockName;

            if (!symbol) {
                return;
            }

            if (typeof openStockFromPortfolio === 'function') {
                openStockFromPortfolio(symbol, stockName);
                return;
            }

            window.location.href = `/stocks/detail?keyword=${encodeURIComponent(symbol)}`;
        });
    });
}

// 주문 성공 후 현재 화면의 포트폴리오, 미체결 주문, 거래내역을 다시 조회한다.
window.handleOrderSuccess = async function () {
    portfolioState.loaded = false;
    portfolioState.inFlight = null;
    await loadPortfolioDashboard(true);
};

function formatTradeDate(value) {
    if (!value) {
        return '-';
    }

    return String(value).replace('T', ' ').substring(0, 16);
}

function portfolioFormatOrderStatus(status) {
    if (status === 'PARTIALLY_FILLED') {
        return '부분체결';
    }

    if (status === 'PENDING') {
        return '미체결';
    }

    return status || '-';
}

function setPortfolioText(id, value) {
    const element = document.getElementById(id);

    if (element) {
        element.textContent = value;
    }
}

function setProfitClass(id, value) {
    const element = document.getElementById(id);

    if (!element) {
        return;
    }

    element.classList.remove('up', 'down');
    element.classList.add(Number(value) >= 0 ? 'up' : 'down');
}

function portfolioFormatNumber(value) {
    return Number(value || 0).toLocaleString('ko-KR');
}

function portfolioFormatSignedNumber(value) {
    const number = Number(value || 0);
    const sign = number > 0 ? '+' : '';

    return `${sign}${number.toLocaleString('ko-KR')}`;
}

function portfolioEscapeHtml(value) {
    return String(value || '')
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#039;');
}
