// 주식 상세 화면에서 관심종목, 현재가, 내 주식 정보를 처리하는 스크립트

let currentStock = null;
let stockDetailReady = null;
let detailMyStockLoaded = false;
let detailMyStockLoading = false;
let detailOrderbookLoaded = false;
let detailOrderbookLoading = false;
let detailRealtimeSocket = null;
let detailLatestOrderbook = null;
let detailChartHistories = [];
let detailActiveChartPeriod = '1D';

document.addEventListener('DOMContentLoaded', async () => {
    const authenticated = await waitAuthReady();

    if (!authenticated) {
        return;
    }

    bindStockTabs();
    bindFavoriteButton();
    bindDetailOrderButtons();
    bindDetailChartPeriodButtons();

    stockDetailReady = initStockDetail();
    await stockDetailReady;
});

// 상세페이지 매수/매도 버튼을 주문 모달과 연결
function bindDetailOrderButtons() {
    const buyButton = document.getElementById('detail-buy-btn');
    const sellButton = document.getElementById('detail-sell-btn');

    buyButton?.addEventListener('click', () => {
        openDetailOrderModal('BUY');
    });

    sellButton?.addEventListener('click', () => {
        openDetailOrderModal('SELL');
    });
}

function bindDetailChartPeriodButtons() {
    const buttons = document.querySelectorAll('.detail-chart-periods button');

    buttons.forEach((button) => {
        button.addEventListener('click', async () => {
            if (!currentStock?.symbol) {
                return;
            }

            const period = button.dataset.chartPeriod || '1D';
            // 현재 선택된 차트 기간을 저장해서 실시간 체결가 반영 여부를 판단한다.
            detailActiveChartPeriod = period;

            buttons.forEach((item) => item.classList.remove('active'));
            button.classList.add('active');

            await renderDetailChart(currentStock.symbol, period);
        });
    });
}

// 현재 상세 종목 기준으로 주문 모달 열기
function openDetailOrderModal(orderType) {
    if (!currentStock || !currentStock.symbol) {
        return;
    }

    openOrderModal(currentStock.symbol, currentStock.name, orderType);
}

// 상세페이지 초기 데이터 조회
async function initStockDetail() {
    const keyword = getTitleStockName();

    if (!keyword) {
        return;
    }

    currentStock = await fetchStockDetail(keyword);

    if (!currentStock) {
        renderDetailMyStockError('종목 정보를 찾지 못했습니다.');
        return;
    }

    // 상세페이지의 현재 종목 실시간 체결가/호가 데이터를 구독한다.
    connectDetailRealtimeSocket(currentStock.symbol);

    setStockTitle(currentStock.name);

    const quote = await fetchStockQuote(currentStock.symbol);

    if (quote) {
        renderStockQuote(quote);
    }

    renderDetailStockInfo(currentStock, quote);
    await renderDetailChart(currentStock.symbol, '1D');
    await loadFavoriteStatus(currentStock);
}

// 상세페이지에서 현재 종목의 실시간 체결가와 호가 WebSocket을 구독한다.
function connectDetailRealtimeSocket(symbol) {
    const token = localStorage.getItem('accessToken');

    if (!symbol || !token) {
        return;
    }

    if (detailRealtimeSocket && detailRealtimeSocket.readyState === WebSocket.OPEN) {
        detailRealtimeSocket.send(JSON.stringify({
            type: 'SUBSCRIBE',
            symbol,
            token
        }));
        return;
    }

    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
    detailRealtimeSocket = new WebSocket(`${protocol}://${window.location.host}/ws/stocks`);

    detailRealtimeSocket.onopen = () => {
        detailRealtimeSocket.send(JSON.stringify({
            type: 'SUBSCRIBE',
            symbol,
            token
        }));
    };

    detailRealtimeSocket.onmessage = (event) => {
        const message = JSON.parse(event.data);

        handleDetailRealtimeMessage(message);
    };

    detailRealtimeSocket.onclose = () => {
    };

    detailRealtimeSocket.onerror = () => {
    };
}

// 서버 WebSocket에서 받은 실시간 메시지를 타입별로 화면에 반영한다.
function handleDetailRealtimeMessage(message) {
    if (!message || !message.type) {
        return;
    }

    if (message.type === 'SUBSCRIBED') {
        return;
    }

    if (message.type === 'TRADE') {
        applyRealtimeTrade(message.data);
        return;
    }

    if (message.type === 'ORDERBOOK') {
        applyRealtimeOrderbook(message.data);
    }
}

// 실시간 체결가로 상세 상단 가격과 1D 차트 마지막 값을 갱신한다.
function applyRealtimeTrade(trade) {
    if (!trade || normalizeStockSymbol(trade.symbol) !== normalizeStockSymbol(currentStock?.symbol)) {
        return;
    }

    const quote = {
        currentPrice: trade.currentPrice,
        changePrice: trade.changePrice,
        changeRate: trade.changeRate,
        volume: trade.accumulatedVolume
    };

    renderStockQuote(quote);
    updateRealtimeChartPoint(trade);

    if (detailLatestOrderbook) {
        detailLatestOrderbook = {
            ...detailLatestOrderbook,
            currentPrice: trade.currentPrice,
            openPrice: trade.openPrice,
            highPrice: trade.highPrice,
            lowPrice: trade.lowPrice,
            volume: trade.accumulatedVolume
        };

        if (detailOrderbookLoaded) {
            renderDetailOrderbook(detailLatestOrderbook);
        }
    }
}

// 실시간 호가로 호가 탭의 매도/매수 가격과 잔량을 갱신한다.
function applyRealtimeOrderbook(orderbook) {
    if (!orderbook || normalizeStockSymbol(orderbook.symbol) !== normalizeStockSymbol(currentStock?.symbol)) {
        return;
    }

    const baseOrderbook = detailLatestOrderbook || {
        symbol: orderbook.symbol,
        currentPrice: Number(currentStock?.currentPrice || 0),
        basePrice: Number(currentStock?.currentPrice || 0),
        openPrice: 0,
        highPrice: 0,
        lowPrice: 0,
        volume: 0
    };

    const basePrice = Number(baseOrderbook.basePrice || 0);

    const realtimeOrderbook = {
        ...baseOrderbook,
        symbol: orderbook.symbol,
        totalAskQuantity: orderbook.totalAskQuantity,
        totalBidQuantity: orderbook.totalBidQuantity,
        levels: (orderbook.levels || []).map((level) => ({
            level: level.level,
            askPrice: level.askPrice,
            askQuantity: level.askQuantity,
            askRate: calculateOrderbookRate(level.askPrice, basePrice),
            bidPrice: level.bidPrice,
            bidQuantity: level.bidQuantity,
            bidRate: calculateOrderbookRate(level.bidPrice, basePrice)
        }))
    };

    detailLatestOrderbook = realtimeOrderbook;

    if (detailOrderbookLoaded) {
        renderDetailOrderbook(realtimeOrderbook);
    }
}

// 실시간 체결가를 1D 차트의 마지막 포인트에 반영한다.
function updateRealtimeChartPoint(trade) {
    if (detailActiveChartPeriod !== '1D' || detailChartHistories.length === 0) {
        return;
    }

    const chartBox = document.getElementById('detail-chart-box');

    if (!chartBox) {
        return;
    }

    const latestIndex = detailChartHistories.length - 1;
    const latest = detailChartHistories[latestIndex];
    const currentPrice = Number(trade.currentPrice || 0);

    if (currentPrice <= 0) {
        return;
    }

    detailChartHistories[latestIndex] = {
        ...latest,
        label: formatRealtimeTradeTime(trade.tradeTime) || latest.label,
        closePrice: currentPrice,
        highPrice: Math.max(Number(latest.highPrice || currentPrice), currentPrice),
        lowPrice: Math.min(Number(latest.lowPrice || currentPrice), currentPrice),
        volume: trade.accumulatedVolume || latest.volume
    };

    chartBox.innerHTML = createDetailChartMarkup(detailChartHistories);
    bindDetailChartTooltip();
}

// KIS HHmmss 체결 시간을 HH:mm:ss 형식으로 바꾼다.
function formatRealtimeTradeTime(tradeTime) {
    const value = String(tradeTime || '');

    if (value.length !== 6) {
        return '';
    }

    return `${value.substring(0, 2)}:${value.substring(2, 4)}:${value.substring(4, 6)}`;
}

// 상세페이지 내 주식 탭에 현재 종목의 보유 정보, 미체결 주문, 거래내역 표시
async function renderDetailMyStock(symbol) {
    const wrap = document.getElementById('detail-my-stock-wrap');

    if (!wrap || !symbol) {
        return false;
    }

    const [portfolio, openOrders, trades] = await Promise.all([
        fetchMyPortfolio(),
        fetchMyOpenOrders(symbol),
        fetchMyTrades(symbol)
    ]);

    if (!portfolio) {
        renderDetailMyStockError('내 주식 정보를 불러오지 못했습니다.');
        return false;
    }

    const holding = (portfolio.holdings || []).find((item) => {
        return normalizeStockSymbol(item.symbol) === normalizeStockSymbol(symbol);
    });

    wrap.innerHTML = `
        ${renderDetailHoldingSection(holding)}
        ${renderDetailOpenOrderSection(openOrders || [])}
        ${renderDetailTradeSection(trades || [])}
    `;

    bindDetailOpenOrderCancelButtons();

    return true;
}

// 내 포트폴리오 조회
async function fetchMyPortfolio() {
    const response = await authFetch('/api/portfolio');

    if (!response || !response.ok) {
        return null;
    }

    return await response.json();
}

// 현재 종목의 거래내역 조회
async function fetchMyTrades(symbol) {
    const query = symbol ? `?symbol=${encodeURIComponent(symbol)}` : '';
    const response = await authFetch(`/api/trades${query}`);

    if (!response || !response.ok) {
        return [];
    }

    return await response.json();
}

// 현재 종목의 미체결/부분체결 주문을 조회한다.
async function fetchMyOpenOrders(symbol) {
    const response = await authFetch('/api/orders/open');

    if (!response || !response.ok) {
        return [];
    }

    const orders = await response.json();
    const targetSymbol = normalizeStockSymbol(symbol);

    return (orders || []).filter((order) => {
        return normalizeStockSymbol(order.symbol) === targetSymbol;
    });
}

// 현재 종목의 보유 정보를 카드 형태로 렌더링
function renderDetailHoldingSection(holding) {
    if (!holding) {
        return `
            <section class="detail-my-section">
                <h3>보유 정보</h3>

                <div class="detail-my-empty">
                    <p>현재 보유 중인 수량이 없습니다.</p>
                    <span>매수를 진행하면 이 영역에 보유 정보가 표시됩니다.</span>
                </div>
            </section>
        `;
    }

    return `
        <section class="detail-my-section">
            <h3>보유 정보</h3>

            <div class="detail-my-cards">
                <div class="detail-my-card">
                    <span>보유수량</span>
                    <strong>${formatNumber(holding.quantity)}주</strong>
                </div>

                <div class="detail-my-card">
                    <span>평균단가</span>
                    <strong>${formatNumber(holding.averagePrice)}원</strong>
                </div>

                <div class="detail-my-card">
                    <span>현재가</span>
                    <strong>${formatNumber(holding.currentPrice)}원</strong>
                </div>

                <div class="detail-my-card">
                    <span>평가금액</span>
                    <strong>${formatNumber(holding.evaluationAmount)}원</strong>
                </div>

                <div class="detail-my-card">
                    <span>손익</span>
                    <strong class="${Number(holding.profitLoss) >= 0 ? 'up' : 'down'}">
                        ${formatSignedNumber(holding.profitLoss)}원
                    </strong>
                </div>

                <div class="detail-my-card">
                    <span>수익률</span>
                    <strong class="${Number(holding.profitRate) >= 0 ? 'up' : 'down'}">
                        ${formatSignedNumber(holding.profitRate)}%
                    </strong>
                </div>
            </div>
        </section>
    `;
}

// 현재 종목의 미체결/부분체결 주문을 표로 렌더링한다.
function renderDetailOpenOrderSection(openOrders) {
    if (!openOrders || openOrders.length === 0) {
        return `
            <section class="detail-my-section">
                <h3>주문대기</h3>

                <div class="detail-my-empty small">
                    <p>주문대기 중인 주문이 없습니다.</p>
                    <span>원하는 가격으로 주문하면 이 영역에 미체결 주문이 표시됩니다.</span>
                </div>
            </section>
        `;
    }

    return `
        <section class="detail-my-section">
            <h3>주문대기</h3>

            <table class="detail-open-order-table">
                <thead>
                <tr>
                    <th>구분</th>
                    <th>주문가</th>
                    <th>주문수량</th>
                    <th>미체결</th>
                    <th>상태</th>
                    <th>접수시간</th>
                    <th>취소</th>
                </tr>
                </thead>
                <tbody>
                ${openOrders.map((order) => `
                    <tr
                        class="detail-open-order-row"
                        data-order-id="${escapeHtml(order.id)}"
                        data-stock-name="${escapeHtml(currentStock?.name || order.symbol)}"
                        data-symbol="${escapeHtml(order.symbol)}"
                        data-order-type="${escapeHtml(order.orderType)}"
                        data-order-price="${escapeHtml(order.orderPrice)}"
                        data-quantity="${escapeHtml(order.quantity)}"
                        data-executed-quantity="${escapeHtml(order.executedQuantity)}"
                        data-remaining-quantity="${escapeHtml(order.remainingQuantity)}"
                    >
                        <td>
                            <span class="detail-trade-type ${order.orderType === 'BUY' ? 'buy' : 'sell'}">
                                ${order.orderType === 'BUY' ? '매수' : '매도'}
                            </span>
                        </td>
                        <td>${formatNumber(order.orderPrice)}원</td>
                        <td>${formatNumber(order.quantity)}주</td>
                        <td>${formatNumber(order.remainingQuantity)}주</td>
                        <td>${formatOrderStatus(order.status)}</td>
                        <td>${formatTradeDate(order.orderedAt)}</td>
                        <td>
                            <button
                                type="button"
                                class="detail-open-order-cancel-btn"
                                data-order-id="${escapeHtml(order.id)}"
                            >
                                취소
                            </button>
                        </td>
                    </tr>
                `).join('')}
                </tbody>
            </table>
        </section>
    `;
}

// 현재 종목의 거래내역을 표로 렌더링
function renderDetailTradeSection(trades) {
    if (!trades || trades.length === 0) {
        return `
            <section class="detail-my-section">
                <h3>거래내역</h3>

                <div class="detail-my-empty">
                    <p>거래내역이 없습니다.</p>
                    <span>이 종목을 매수하거나 매도하면 거래내역이 표시됩니다.</span>
                </div>
            </section>
        `;
    }

    return `
        <section class="detail-my-section">
            <h3>거래내역</h3>

            <table class="detail-trade-table">
                <thead>
                <tr>
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
                            <span class="detail-trade-type ${trade.orderType === 'BUY' ? 'buy' : 'sell'}">
                                ${trade.orderType === 'BUY' ? '매수' : '매도'}
                            </span>
                        </td>
                        <td>${formatNumber(trade.quantity)}주</td>
                        <td>${formatNumber(trade.price)}원</td>
                        <td>${formatNumber(trade.totalAmount)}원</td>
                        <td>${formatTradeDate(trade.tradedAt)}</td>
                    </tr>
                `).join('')}
                </tbody>
            </table>
        </section>
    `;
}

// 상세페이지 주문대기 취소 버튼을 취소 API와 연결한다.
function bindDetailOpenOrderCancelButtons() {
    const buttons = document.querySelectorAll('.detail-open-order-cancel-btn');

    buttons.forEach((button) => {
        button.addEventListener('click', async (event) => {
            event.stopPropagation();

            const orderId = button.dataset.orderId;

            if (!orderId) {
                return;
            }

            button.disabled = true;
            await cancelDetailOpenOrder(orderId);
        });
    });

    bindDetailOpenOrderRows();
}

// 상세페이지 주문대기 행 클릭 시 주문 수정 모달을 연다.
function bindDetailOpenOrderRows() {
    const rows = document.querySelectorAll('.detail-open-order-row');

    rows.forEach((row) => {
        row.addEventListener('click', () => {
            if (typeof openOrderEditModal !== 'function') {
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

// 상세페이지에서 선택한 미체결 주문을 취소하고 내 주식 탭을 다시 조회한다.
async function cancelDetailOpenOrder(orderId) {
    const response = await authFetch(`/api/orders/${encodeURIComponent(orderId)}/cancel`, {
        method: 'PATCH'
    });

    if (!response || !response.ok) {
        await loadDetailMyStockTab(true);
        return;
    }

    await loadDetailMyStockTab(true);
}

// 내 주식 조회 실패 시 표시
function renderDetailMyStockError(message = '내 주식 정보를 불러오지 못했습니다.') {
    const wrap = document.getElementById('detail-my-stock-wrap');

    if (!wrap) {
        return;
    }

    wrap.innerHTML = `
        <div class="detail-my-empty">
            <p>${escapeHtml(message)}</p>
            <span>잠시 후 다시 시도해 주세요.</span>
        </div>
    `;
}

// 종목정보 탭에 현재 종목의 상세 정보 표시
function renderDetailStockInfo(stock, quote) {
    const infoGrid = document.getElementById('detail-info-grid');

    if (!infoGrid || !stock) {
        return;
    }

    const price = quote || stock;

    infoGrid.innerHTML = `
        <section class="detail-info-section">
            <h3>가격 정보</h3>

            <div class="detail-info-cards">
                <div class="detail-info-card">
                    <span>현재가</span>
                    <strong>${formatNumber(price.currentPrice)}원</strong>
                </div>

                <div class="detail-info-card">
                    <span>어제대비</span>
                    <strong class="${Number(price.changePrice) >= 0 ? 'up' : 'down'}">
                        ${formatSignedNumber(price.changePrice)}원
                    </strong>
                </div>

                <div class="detail-info-card">
                    <span>등락률</span>
                    <strong class="${Number(price.changeRate) >= 0 ? 'up' : 'down'}">
                        ${formatSignedNumber(price.changeRate)}%
                    </strong>
                </div>

                <div class="detail-info-card">
                    <span>거래량</span>
                    <strong>${formatNumber(price.volume)}</strong>
                </div>
            </div>
        </section>

        <section class="detail-info-section">
            <h3>종목정보</h3>

            <dl class="detail-info-list">
                <div>
                    <dt>종목명</dt>
                    <dd>${escapeHtml(stock.name)}</dd>
                </div>
                <div>
                    <dt>종목코드</dt>
                    <dd>${escapeHtml(stock.symbol)}</dd>
                </div>
                <div>
                    <dt>시장</dt>
                    <dd>${escapeHtml(stock.market)}</dd>
                </div>
                <div>
                    <dt>업종</dt>
                    <dd>${escapeHtml(stock.sector)}</dd>
                </div>
                <div>
                    <dt>시가총액</dt>
                    <dd>${formatNumber(stock.marketCap)}원</dd>
                </div>
                <div>
                    <dt>상장주식수</dt>
                    <dd>${formatNumber(stock.listedShares)}</dd>
                </div>
                <div>
                    <dt>PER</dt>
                    <dd>${formatNumber(stock.per)}</dd>
                </div>
                <div>
                    <dt>EPS</dt>
                    <dd>${formatNumber(stock.eps)}원</dd>
                </div>
                <div>
                    <dt>배당수익률</dt>
                    <dd>${formatNumber(stock.dividendYield)}%</dd>
                </div>
            </dl>
        </section>

        <section class="detail-info-section">
            <h3>실시간 참고</h3>

            <dl class="detail-info-list">
                <div>
                    <dt>시가</dt>
                    <dd>${formatNumber(quote?.openPrice)}원</dd>
                </div>
                <div>
                    <dt>고가</dt>
                    <dd>${formatNumber(quote?.highPrice)}원</dd>
                </div>
                <div>
                    <dt>저가</dt>
                    <dd>${formatNumber(quote?.lowPrice)}원</dd>
                </div>
                <div>
                    <dt>기준가</dt>
                    <dd>${formatNumber(quote?.basePrice)}원</dd>
                </div>
                <div>
                    <dt>거래대금</dt>
                    <dd>${formatNumber(quote?.tradingValue)}원</dd>
                </div>
            </dl>
        </section>
    `;
}

// 상세 화면 탭 전환 처리
function bindStockTabs() {
    const tabButtons = document.querySelectorAll('.stock-tabs button');
    const tabPanels = document.querySelectorAll('.tab-panel');

    tabButtons.forEach((button) => {
        button.addEventListener('click', async () => {
            const target = button.dataset.tab;

            tabButtons.forEach((item) => item.classList.remove('active'));
            tabPanels.forEach((panel) => panel.classList.remove('active'));

            button.classList.add('active');

            const targetPanel = document.getElementById(`tab-${target}`);

            if (targetPanel) {
                targetPanel.classList.add('active');
            }

            if (target === 'orderbook') {
                await loadDetailOrderbookTab();
            }

            if (target === 'my-stock') {
                await loadDetailMyStockTab();
            }
        });
    });
}

// 호가 탭 최초 진입 시 현재 종목의 호가 데이터를 조회한다.
async function loadDetailOrderbookTab(force = false) {
    if (detailOrderbookLoaded && !force) {
        return;
    }

    if (detailOrderbookLoading) {
        return;
    }

    detailOrderbookLoading = true;

    try {
        if (stockDetailReady) {
            await stockDetailReady;
        }

        if (!currentStock?.symbol) {
            renderDetailOrderbookError('종목 정보가 아직 준비되지 않았습니다.');
            return;
        }

        const orderbook = await fetchStockOrderbook(currentStock.symbol);

        if (!orderbook) {
            renderDetailOrderbookError();
            return;
        }

        renderDetailOrderbook(orderbook);
        detailOrderbookLoaded = true;
    } finally {
        detailOrderbookLoading = false;
    }
}

// 종목코드 기준 호가 API를 호출한다.
async function fetchStockOrderbook(symbol) {
    if (!symbol) {
        return null;
    }

    const response = await authFetch(`/api/stocks/${encodeURIComponent(symbol)}/orderbook`);

    if (!response || !response.ok) {
        return null;
    }

    return await response.json();
}

// 호가 데이터를 가격 중심 ladder 형태로 렌더링한다.
function renderDetailOrderbook(orderbook) {
    const wrap = document.getElementById('detail-orderbook-wrap');

    if (!wrap || !orderbook) {
        return;
    }

    // 실시간 호가 수신 시 REST로 가져온 기준가/현재가 정보를 유지하기 위해 최신 호가 상태를 보관한다.
    detailLatestOrderbook = orderbook;

    const levels = orderbook.levels || [];

    if (levels.length === 0) {
        renderDetailOrderbookError('호가 데이터가 없습니다.');
        return;
    }

    const askLevels = [...levels].reverse();
    const bidLevels = [...levels];

    wrap.innerHTML = `
        <section class="detail-orderbook-ladder">
            <div class="orderbook-main">
                <div class="orderbook-side-label sell">판매 대기 ${formatNumber(orderbook.totalAskQuantity)}</div>

                <div class="orderbook-rows">
                    ${askLevels.map((level) => renderOrderbookAskRow(level)).join('')}

                    <div class="orderbook-current-row">
                        <strong>${formatNumber(orderbook.currentPrice)}원</strong>
                        <span>${formatSignedNumber(calculateOrderbookRate(orderbook.currentPrice, orderbook.basePrice))}%</span>
                    </div>

                    ${bidLevels.map((level) => renderOrderbookBidRow(level)).join('')}
                </div>

                <div class="orderbook-side-label buy">구매 대기 ${formatNumber(orderbook.totalBidQuantity)}</div>
            </div>

            <aside class="orderbook-info-panel">
                <dl>
                    <div><dt>종목코드</dt><dd>${escapeHtml(orderbook.symbol)}</dd></div>
                    <div><dt>기준가</dt><dd>${formatNumber(orderbook.basePrice)}원</dd></div>
                    <div><dt>시가</dt><dd>${formatNumber(orderbook.openPrice)}원</dd></div>
                    <div><dt>고가</dt><dd class="up">${formatNumber(orderbook.highPrice)}원</dd></div>
                    <div><dt>저가</dt><dd class="down">${formatNumber(orderbook.lowPrice)}원</dd></div>
                    <div><dt>거래량</dt><dd>${formatNumber(orderbook.volume)}</dd></div>
                </dl>
            </aside>
        </section>
    `;
}

function renderOrderbookAskRow(level) {
    return `
        <div class="orderbook-row ask">
            <div class="orderbook-quantity left">
                <span style="width: ${getOrderbookBarWidth(level.askQuantity)}%"></span>
                <strong>${formatNumber(level.askQuantity)}</strong>
            </div>
            <div class="orderbook-price sell">
                <strong>${formatNumber(level.askPrice)}</strong>
                <em>${formatSignedNumber(level.askRate)}%</em>
            </div>
            <div class="orderbook-quantity right"></div>
        </div>
    `;
}

function renderOrderbookBidRow(level) {
    return `
        <div class="orderbook-row bid">
            <div class="orderbook-quantity left"></div>
            <div class="orderbook-price buy">
                <strong>${formatNumber(level.bidPrice)}</strong>
                <em>${formatSignedNumber(level.bidRate)}%</em>
            </div>
            <div class="orderbook-quantity right">
                <span style="width: ${getOrderbookBarWidth(level.bidQuantity)}%"></span>
                <strong>${formatNumber(level.bidQuantity)}</strong>
            </div>
        </div>
    `;
}

function getOrderbookBarWidth(quantity) {
    const value = Number(quantity || 0);
    return Math.min(100, Math.max(8, value / 1000));
}

function getOrderbookPriceClass(rate) {
    const value = Number(rate || 0);

    if (value > 0) {
        return 'up';
    }

    if (value < 0) {
        return 'down';
    }

    return '';
}

function calculateOrderbookRate(price, basePrice) {
    const current = Number(price || 0);
    const base = Number(basePrice || 0);

    if (current === 0 || base === 0) {
        return 0;
    }

    return Math.round(((current - base) * 10000 / base)) / 100;
}

// 호가 조회 실패 또는 빈 데이터 상태를 표시한다.
function renderDetailOrderbookError(message = '호가 정보를 불러오지 못했습니다.') {
    const wrap = document.getElementById('detail-orderbook-wrap');

    if (!wrap) {
        return;
    }

    wrap.innerHTML = `
        <div class="detail-my-empty">
            <p>${escapeHtml(message)}</p>
            <span>잠시 후 다시 시도해 주세요.</span>
        </div>
    `;
}

async function loadDetailMyStockTab(force = false) {
    if (detailMyStockLoaded && !force) {
        return;
    }

    if (detailMyStockLoading) {
        return;
    }

    detailMyStockLoading = true;

    try {
        if (stockDetailReady) {
            await stockDetailReady;
        }

        if (!currentStock?.symbol) {
            renderDetailMyStockError('종목 정보가 아직 준비되지 않았습니다.');
            return;
        }

        const success = await renderDetailMyStock(currentStock.symbol);

        if (success) {
            detailMyStockLoaded = true;
        }
    } finally {
        detailMyStockLoading = false;
    }
}

// 관심종목 하트 클릭 시 추가 또는 삭제 처리
function bindFavoriteButton() {
    const favoriteButton = document.getElementById('favorite-btn');

    if (!favoriteButton) {
        return;
    }

    favoriteButton.addEventListener('click', async () => {
        const stockName = getCurrentStockName();

        if (!stockName) {
            return;
        }

        if (favoriteButton.classList.contains('active')) {
            const success = await removeWatchlist(stockName);

            if (success) {
                favoriteButton.classList.remove('active');
            }

            return;
        }

        const success = await addWatchlist(stockName);

        if (success) {
            favoriteButton.classList.add('active');
        }
    });
}

// DB 관심종목 목록을 조회해서 현재 종목의 하트 상태 반영
async function loadFavoriteStatus(stock) {
    const favoriteButton = document.getElementById('favorite-btn');

    if (!favoriteButton || !stock) {
        return;
    }

    const targetSymbol = normalizeStockSymbol(stock.symbol);
    const targetNames = [stock.name, stock.symbol, getTitleStockName()]
        .map(normalizeStockName)
        .filter((name) => name.length > 0);

    const response = await authFetch('/api/watchlists');

    if (!response || !response.ok) {
        return;
    }

    const watchlists = await response.json();

    const exists = watchlists.some((item) => {
        const itemSymbol = normalizeStockSymbol(item.symbol);
        const itemStockName = normalizeStockName(item.stockName || item.stock_name);

        return (targetSymbol && itemSymbol === targetSymbol)
            || targetNames.includes(itemStockName);
    });

    favoriteButton.classList.toggle('active', exists);
}

// 관심종목 DB 저장
async function addWatchlist(stockName) {
    if (!stockName) {
        return false;
    }

    const response = await authFetch('/api/watchlists', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            stockName: stockName
        })
    });

    return !!response && response.ok;
}

// 관심종목 DB 삭제
async function removeWatchlist(stockName) {
    if (!stockName) {
        return false;
    }

    const response = await authFetch(`/api/watchlists?stockName=${encodeURIComponent(stockName)}`, {
        method: 'DELETE'
    });

    return !!response && response.ok;
}

// 종목명 또는 종목코드로 DB 상세 정보 조회
async function fetchStockDetail(keyword) {
    if (!keyword) {
        return null;
    }

    const normalizedKeyword = String(keyword || '').trim();
    const isSymbol = /^[0-9A-Z]+$/.test(normalizedKeyword);

    const url = isSymbol
        ? `/api/stocks/symbol/${encodeURIComponent(normalizedKeyword)}`
        : `/api/stocks/detail?name=${encodeURIComponent(normalizedKeyword)}`;

    const response = await authFetch(url);

    if (!response || !response.ok) {
        return null;
    }

    return await response.json();
}
// 종목코드로 현재가 조회
async function fetchStockQuote(symbol) {
    if (!symbol) {
        return null;
    }

    const response = await authFetch(`/api/stocks/${encodeURIComponent(symbol)}/quote`);

    if (!response || !response.ok) {
        return null;
    }

    return await response.json();
}

async function fetchStockPriceHistories(symbol, period = '1D') {
    if (!symbol) {
        return [];
    }

    const response = await authFetch(`/api/stocks/${encodeURIComponent(symbol)}/prices?period=${encodeURIComponent(period)}`, {
        cache: 'no-store'
    });

    if (!response || !response.ok) {
        return [];
    }

    return await response.json();
}

async function renderDetailChart(symbol, period = '1D') {
    // 현재 렌더링 중인 차트 기간을 저장한다.
    detailActiveChartPeriod = period;

    const chartBox = document.getElementById('detail-chart-box');
    const chartTitle = document.getElementById('detail-chart-title');

    if (!chartBox || !symbol) {
        return;
    }

    if (chartTitle) {
        chartTitle.textContent = currentStock?.name || 'CHART';
    }

    chartBox.innerHTML = `
        <div class="chart-grid"></div>
        <div class="tab-empty">
            <p>차트 정보를 불러오는 중입니다.</p>
        </div>
    `;

    const histories = await fetchStockPriceHistories(symbol, period);

    if (!histories || histories.length === 0) {
        chartBox.innerHTML = `
            <div class="chart-grid"></div>
            <div class="tab-empty">
                <p>차트 데이터가 없습니다.</p>
                <span>가격 이력을 불러오지 못했습니다.</span>
            </div>
        `;
        return;
    }

    // 실시간 체결가 수신 시 마지막 차트 포인트를 갱신하기 위해 최신 차트 데이터를 보관한다.
    detailChartHistories = histories;
    chartBox.innerHTML = createDetailChartMarkup(histories);
    bindDetailChartTooltip();
}

function createDetailChartMarkup(histories) {
    const width = 900;
    const height = 360;
    const paddingTop = 24;
    const paddingRight = 72;
    const paddingBottom = 34;
    const paddingLeft = 34;

    const prices = histories.flatMap((item) => [
        Number(item.highPrice || item.closePrice || 0),
        Number(item.lowPrice || item.closePrice || 0),
        Number(item.closePrice || 0)
    ]);

    const minPrice = Math.min(...prices);
    const maxPrice = Math.max(...prices);
    const priceRange = maxPrice - minPrice || 1;

    const latest = histories[histories.length - 1];
    const first = histories[0];

    const firstPrice = Number(first.closePrice || 0);
    const latestPrice = Number(latest.closePrice || 0);
    const change = latestPrice - firstPrice;
    const changeRate = firstPrice === 0 ? 0 : (change / firstPrice) * 100;
    const isUp = change >= 0;

    const chartClass = isUp ? 'up' : 'down';
    const chartColor = chartClass === 'down' ? '#3b82f6' : '#ff4560';

    const points = histories.map((item, index) => {
        const price = Number(item.closePrice || 0);
        const x = paddingLeft + index * ((width - paddingLeft - paddingRight) / Math.max(histories.length - 1, 1));
        const y = height - paddingBottom - ((price - minPrice) / priceRange) * (height - paddingTop - paddingBottom);

        return {
            x,
            y,
            price,
            label: item.label
        };
    });

    const linePath = points.map((point, index) => {
        const command = index === 0 ? 'M' : 'L';
        return `${command}${point.x.toFixed(1)},${point.y.toFixed(1)}`;
    }).join(' ');

    const highPrice = Math.max(...histories.map((item) => Number(item.highPrice || item.closePrice || 0)));
    const lowPrice = Math.min(...histories.map((item) => Number(item.lowPrice || item.closePrice || 0)));
    const openPrice = Number(first.openPrice || first.closePrice || 0);

    const yLabels = createChartPriceLabels(minPrice, maxPrice, 5).map((price) => {
        const y = height - paddingBottom - ((price - minPrice) / priceRange) * (height - paddingTop - paddingBottom);

        return {
            price,
            y
        };
    });

    return `
        <div class="detail-chart-visual">
            <div class="chart-grid"></div>

            <svg class="detail-price-chart" viewBox="0 0 ${width} ${height}" preserveAspectRatio="none">
                ${yLabels.map((label) => `
                    <line
                        class="detail-chart-guide"
                        x1="${paddingLeft}"
                        y1="${label.y.toFixed(1)}"
                        x2="${width - paddingRight}"
                        y2="${label.y.toFixed(1)}"
                    ></line>
                    <text
                        class="detail-chart-price-label"
                        x="${width - paddingRight + 12}"
                        y="${label.y.toFixed(1)}"
                        dominant-baseline="middle"
                    >${formatNumber(Math.round(label.price))}</text>
                `).join('')}

                <path
                    class="detail-chart-line ${chartClass}"
                    d="${linePath}"
                    style="stroke: ${chartColor};"
                ></path>

                ${points.slice(1).map((point, index) => {
                    const prevPoint = points[index];

                    return `
                        <path
                            class="detail-chart-hover-line"
                            d="M${prevPoint.x.toFixed(1)},${prevPoint.y.toFixed(1)} L${point.x.toFixed(1)},${point.y.toFixed(1)}"
                            data-chart-label="${escapeHtml(point.label)}"
                            data-chart-price="${formatNumber(point.price)}원"
                        ></path>
                    `;
                }).join('')}
            </svg>

            <div class="detail-chart-axis">
                <span>${escapeHtml(first.label)}</span>
                <span>${escapeHtml(latest.label)}</span>
            </div>
        </div>

        <div class="detail-chart-stats">
            <div>
                <span>현재</span>
                <strong class="${chartClass}">${formatNumber(latestPrice)}원</strong>
            </div>
            <div>
                <span>기간 등락</span>
                <strong class="${chartClass}">${formatSignedNumber(change)}원</strong>
            </div>
            <div>
                <span>기간 등락률</span>
                <strong class="${chartClass}">${formatSignedNumber(changeRate.toFixed(2))}%</strong>
            </div>
            <div>
                <span>고가 / 저가</span>
                <strong>${formatNumber(highPrice)} / ${formatNumber(lowPrice)}원</strong>
            </div>
            <div>
                <span>시작가</span>
                <strong>${formatNumber(openPrice)}원</strong>
            </div>
            <div>
                <span>거래량</span>
                <strong>${formatNumber(latest.volume)}</strong>
            </div>
        </div>
    `;
}

function bindDetailChartTooltip() {
    const chartBox = document.getElementById('detail-chart-box');

    if (!chartBox) {
        return;
    }

    let tooltip = document.getElementById('detail-chart-tooltip');

    if (!tooltip) {
        tooltip = document.createElement('div');
        tooltip.id = 'detail-chart-tooltip';
        tooltip.className = 'detail-chart-tooltip';
        document.body.appendChild(tooltip);
    }

    chartBox.querySelectorAll('.detail-chart-hover-line').forEach((line) => {
        line.addEventListener('mouseenter', () => {
            const label = line.dataset.chartLabel || '-';
            const price = line.dataset.chartPrice || '-';

            tooltip.innerHTML = `
                <span>${escapeHtml(label)}</span>
                <strong>${escapeHtml(price)}</strong>
            `;
            tooltip.style.display = 'block';
        });

        line.addEventListener('mousemove', (event) => {
            const offset = 14;
            const maxLeft = window.innerWidth - tooltip.offsetWidth - offset;
            const maxTop = window.innerHeight - tooltip.offsetHeight - offset;

            tooltip.style.left = `${Math.max(offset, Math.min(event.clientX + offset, maxLeft))}px`;
            tooltip.style.top = `${Math.max(offset, Math.min(event.clientY + offset, maxTop))}px`;
        });

        line.addEventListener('mouseleave', () => {
            tooltip.style.display = 'none';
        });
    });
}

// 차트 오른쪽 가격축에 표시할 가격 라벨을 만든다.
function createChartPriceLabels(minPrice, maxPrice, count) {
    if (count <= 1 || minPrice === maxPrice) {
        return [maxPrice];
    }

    const step = (maxPrice - minPrice) / (count - 1);

    return Array.from({ length: count }, (_, index) => {
        return maxPrice - step * index;
    });
}

// quote 응답을 상세 화면 가격 카드에 표시
function renderStockQuote(quote) {
    setText('detail-current-price', `${formatNumber(quote.currentPrice)}원`);
    setText('detail-change-price', `${formatSignedNumber(quote.changePrice)}원`);
    setText('detail-change-rate', `${formatSignedNumber(quote.changeRate)}%`);
    setText('detail-volume', formatNumber(quote.volume));

    setPriceColor('detail-change-price', quote.changePrice);
    setPriceColor('detail-change-rate', quote.changeRate);
}

// 상세 화면 제목의 현재 종목명 조회
function getTitleStockName() {
    const stockTitle = document.querySelector('.stock-title');

    if (!stockTitle) {
        return '';
    }

    return stockTitle.textContent.trim();
}

// 현재 페이지에서 사용할 기준 종목명 조회
function getCurrentStockName() {
    if (currentStock && currentStock.name) {
        return currentStock.name;
    }

    return getTitleStockName();
}

// 상세 화면 제목을 DB 기준 종목명으로 보정
function setStockTitle(stockName) {
    const stockTitle = document.querySelector('.stock-title');

    if (stockTitle) {
        stockTitle.textContent = stockName;
    }
}

function setText(id, value) {
    const element = document.getElementById(id);

    if (element) {
        element.textContent = value;
    }
}

// 등락 값에 따라 상승/하락 색상 적용
function setPriceColor(id, value) {
    const element = document.getElementById(id);

    if (!element) {
        return;
    }

    element.classList.remove('up', 'down');

    if (Number(value) > 0) {
        element.classList.add('up');
        return;
    }

    if (Number(value) < 0) {
        element.classList.add('down');
    }
}

// 관심종목 비교를 위해 공백 제거 후 대문자로 통일
function normalizeStockName(value) {
    return String(value || '')
        .replace(/\s+/g, '')
        .toUpperCase();
}

// 종목코드 비교를 위해 공백 제거 후 대문자로 통일
function normalizeStockSymbol(value) {
    return String(value || '')
        .replace(/\s+/g, '')
        .toUpperCase();
}

function escapeHtml(value) {
    return String(value || '')
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#039;');
}

function formatNumber(value) {
    if (value === null || value === undefined || value === '') {
        return '-';
    }

    return Number(value).toLocaleString('ko-KR');
}

function formatSignedNumber(value) {
    if (value === null || value === undefined || value === '') {
        return '-';
    }

    const number = Number(value);
    const sign = number > 0 ? '+' : '';

    return `${sign}${number.toLocaleString('ko-KR')}`;
}

function formatTradeDate(value) {
    if (!value) {
        return '-';
    }

    return String(value).replace('T', ' ').slice(0, 16);
}

function formatOrderStatus(status) {
    if (status === 'PARTIALLY_FILLED') {
        return '부분체결';
    }

    if (status === 'PENDING') {
        return '미체결';
    }

    return status || '-';
}

// 상세페이지에서 주문 성공 시 현재 종목의 가격, 종목정보, 보유 정보, 거래내역을 다시 조회
window.handleOrderSuccess = async function () {
    if (!currentStock || !currentStock.symbol) {
        return;
    }

    const quote = await fetchStockQuote(currentStock.symbol);

    if (quote) {
        renderStockQuote(quote);
        renderDetailStockInfo(currentStock, quote);
    }

    detailMyStockLoaded = false;
    await loadDetailMyStockTab(true);
    detailOrderbookLoaded = false;
    await loadDetailOrderbookTab(true);
};
