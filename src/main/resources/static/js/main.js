// 메인 화면의 관심종목, 차트, 상세정보, 포트폴리오 탭 렌더링을 처리한다.
let selectedWatchlistSymbol = null;
let draggedWatchlistId = null;

document.addEventListener('DOMContentLoaded', async () => {
    const authenticated = await waitAuthReady();

    if (!authenticated) {
        return;
    }

    bindDashboardTabs();
    await loadWatchlists();
});

// 메인 화면의 관심종목/내 주식 탭 전환
function bindDashboardTabs() {
    const tabButtons = document.querySelectorAll('.dashboard-tabs button');

    tabButtons.forEach((button) => {
        button.addEventListener('click', async () => {
            const target = button.dataset.dashboardTab;

            switchDashboardTab(target);

            if (target === 'portfolio' && typeof loadPortfolioDashboard === 'function') {
                await loadPortfolioDashboard();
            }
        });
    });
}

// 메인 대시보드 탭을 공통으로 전환
function switchDashboardTab(target) {
    const tabButtons = document.querySelectorAll('.dashboard-tabs button');
    const panels = document.querySelectorAll('.dashboard-panel');

    tabButtons.forEach((button) => {
        button.classList.toggle('active', button.dataset.dashboardTab === target);
    });

    panels.forEach((panel) => {
        panel.classList.toggle('active', panel.id === `dashboard-${target}`);
    });
}

// 내 주식 탭에서 보유 종목을 선택하면 해당 종목 상세페이지로 이동
function openStockFromPortfolio(symbol) {
    if (!symbol) {
        return;
    }

    location.href = `/stocks/detail?keyword=${encodeURIComponent(symbol)}`;
}

// 내 관심 종목 리스트 불러오기
async function loadWatchlists() {
    const watchlistPanel = document.querySelector('.watchlist-panel');

    if (!watchlistPanel) {
        return;
    }

    const response = await authFetch('/api/watchlists');

    if (!response || !response.ok) {
        return;
    }

    const watchlists = await response.json();
    renderWatchlists(watchlists);

    // 첫 번째 관심종목을 메인 화면 진입 시 자동 선택한다.
    if (!selectedWatchlistSymbol && watchlists.length > 0) {
        const first = watchlists[0];
        const stockName = first.stockName || first.stock_name;

        selectedWatchlistSymbol = first.symbol || null;
        await selectWatchlistStock(stockName, first.symbol);
        markSelectedWatchlist(first.id);
    }
}

// 관심종목 목록 렌더링
function renderWatchlists(watchlists) {
    const watchlistPanel = document.querySelector('.watchlist-panel');
    const previousList = document.querySelector('.watchlist-list');
    const emptyState = document.querySelector('.watchlist-panel .empty-state');

    if (!watchlistPanel) {
        return;
    }

    previousList?.remove();

    if (!watchlists || watchlists.length === 0) {
        if (emptyState) {
            emptyState.style.display = 'flex';
        }
        return;
    }

    if (emptyState) {
        emptyState.style.display = 'none';
    }

    const list = document.createElement('div');
    list.className = 'watchlist-list';

    watchlists.forEach((item) => {
        const stockName = item.stockName || item.stock_name;
        const symbol = item.symbol;

        if (!stockName) {
            return;
        }

        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'watchlist-item';
        button.textContent = stockName;
        button.draggable = true;
        button.dataset.watchlistId = item.id;
        button.dataset.symbol = symbol || '';
        button.dataset.stockName = stockName;

        if (selectedWatchlistSymbol && normalizeMainSymbol(symbol) === normalizeMainSymbol(selectedWatchlistSymbol)) {
            button.classList.add('active');
        }

        button.addEventListener('click', async () => {
            selectedWatchlistSymbol = symbol || null;
            markSelectedWatchlist(item.id);
            await selectWatchlistStock(stockName, symbol);
        });

        button.addEventListener('dragstart', () => {
            draggedWatchlistId = item.id;
            button.classList.add('dragging');
        });

        button.addEventListener('dragend', () => {
            draggedWatchlistId = null;
            button.classList.remove('dragging');
        });

        button.addEventListener('dragover', (event) => {
            event.preventDefault();

            const draggingButton = list.querySelector('.watchlist-item.dragging');

            if (!draggingButton || draggingButton === button) {
                return;
            }

            const rect = button.getBoundingClientRect();
            const shouldInsertAfter = event.clientY > rect.top + rect.height / 2;

            if (shouldInsertAfter) {
                button.after(draggingButton);
                return;
            }

            button.before(draggingButton);
        });

        button.addEventListener('drop', async (event) => {
            event.preventDefault();
            await saveWatchlistOrder();
        });

        list.appendChild(button);
    });

    watchlistPanel.appendChild(list);
}

function markSelectedWatchlist(watchlistId) {
    const buttons = document.querySelectorAll('.watchlist-item');

    buttons.forEach((button) => {
        button.classList.toggle(
            'active',
            String(button.dataset.watchlistId) === String(watchlistId)
        );
    });
}

async function saveWatchlistOrder() {
    const ids = Array.from(document.querySelectorAll('.watchlist-item'))
        .map((button) => Number(button.dataset.watchlistId))
        .filter((id) => Number.isFinite(id));

    await authFetch('/api/watchlists/order', {
        method: 'PATCH',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            watchlistIds: ids
        })
    });
}

function normalizeMainSymbol(value) {
    return String(value || '')
        .replace(/\s+/g, '')
        .toUpperCase();
}

// 관심종목 클릭 시 종목 상세, 현재가, 가격 이력을 조회해서 화면 갱신
async function selectWatchlistStock(stockName, symbol = null) {
    const stock = symbol
        ? await fetchStockDetailBySymbol(symbol)
        : await fetchStockDetail(stockName);

    if (!stock) {
        renderMainChart(stockName, [], null, '1D');
        renderStockSideInfo(stockName, null, null);
        return;
    }

    const quote = await fetchStockQuote(stock.symbol);
    const priceHistories = await fetchStockPriceHistories(stock.symbol, '1D');

    renderMainChart(stock.name, priceHistories, stock.symbol, '1D', quote);
    renderStockSideInfo(stock.name, stock, quote);
}

async function fetchStockDetail(stockName) {
    if (!stockName) {
        return null;
    }

    const response = await authFetch(`/api/stocks/detail?name=${encodeURIComponent(stockName)}`);

    if (!response || !response.ok) {
        return null;
    }

    return await response.json();
}

// 종목코드로 종목 상세 정보 조회
async function fetchStockDetailBySymbol(symbol) {
    if (!symbol) {
        return null;
    }

    const response = await authFetch(`/api/stocks/symbol/${encodeURIComponent(symbol)}`);

    if (!response || !response.ok) {
        return null;
    }

    return await response.json();
}

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

// 종목코드와 기간으로 차트용 가격 이력 조회
async function fetchStockPriceHistories(symbol, period) {
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

// 현재가 quote의 등락값을 기준으로 메인 차트 상승/하락 색상을 결정한다.
function resolveMainChartClass(quote, hasChartData, firstPrice, latestPrice) {
    const quoteChangeValue = quote?.changePrice ?? quote?.changeRate;
    const quoteChangeNumber = Number(quoteChangeValue);

    if (quoteChangeValue !== null && quoteChangeValue !== undefined && !Number.isNaN(quoteChangeNumber)) {
        return quoteChangeNumber < 0 ? 'down' : 'up';
    }

    return hasChartData && latestPrice < firstPrice ? 'down' : 'up';
}

// 가격 이력 데이터로 메인 차트 영역 갱신
function renderMainChart(stockName, priceHistories = [], symbol = null, activePeriod = '1D', quote = null) {
    const chartPanel = document.querySelector('.chart-panel');

    if (!chartPanel) {
        return;
    }

    // 차트 데이터가 있을 때만 상승/하락 색상을 계산한다.
    const hasChartData = Array.isArray(priceHistories) && priceHistories.length > 0;
    const firstPrice = hasChartData ? Number(priceHistories[0]?.closePrice || 0) : 0;
    const latestPrice = hasChartData ? Number(priceHistories[priceHistories.length - 1]?.closePrice || 0) : 0;
    // 메인 화면의 정보 카드와 차트 색상이 같은 등락 기준을 쓰도록 quote를 우선 사용한다.
    const chartClass = resolveMainChartClass(quote, hasChartData, firstPrice, latestPrice);

    chartPanel.innerHTML = `
        <div class="panel-header chart-main-header">
            <div class="chart-title-row">
                <div>
                    <h1>${escapeHtml(stockName)}</h1>
                </div>

                ${
                    symbol
                        ? `
                            <div class="chart-action-buttons">
                                <button
                                    type="button"
                                    class="chart-trade-btn buy"
                                    data-symbol="${escapeHtml(symbol)}"
                                    data-stock-name="${escapeHtml(stockName)}"
                                    data-order-type="BUY"
                                >
                                    매수
                                </button>

                                <button
                                    type="button"
                                    class="chart-trade-btn sell"
                                    data-symbol="${escapeHtml(symbol)}"
                                    data-stock-name="${escapeHtml(stockName)}"
                                    data-order-type="SELL"
                                >
                                    매도
                                </button>

                                <button
                                    type="button"
                                    class="chart-watchlist-btn active"
                                    data-symbol="${escapeHtml(symbol)}"
                                    data-stock-name="${escapeHtml(stockName)}"
                                    aria-label="관심종목"
                                >
                                    ♥
                                </button>
                            </div>
                        `
                        : ''
                }
            </div>

            <div class="chart-periods">
                <button type="button" data-period="1D" class="${activePeriod === '1D' ? 'active' : ''}">1D</button>
                <button type="button" data-period="1W" class="${activePeriod === '1W' ? 'active' : ''}">1W</button>
                <button type="button" data-period="1M" class="${activePeriod === '1M' ? 'active' : ''}">1M</button>
                <button type="button" data-period="1Y" class="${activePeriod === '1Y' ? 'active' : ''}">1Y</button>
            </div>
        </div>

        <div class="main-chart-box">
            <div class="chart-grid"></div>

            ${
                hasChartData
                    ? createMainChartMarkup(priceHistories, chartClass)
                    : `
                        <div class="chart-placeholder">
                            <p>가격 이력이 없습니다.</p>
                            <span>차트에 표시할 데이터를 찾을 수 없습니다.</span>
                        </div>
                    `
            }
        </div>
    `;

    bindChartPeriodButtons(stockName, symbol, quote);
    bindMainChartTooltip();
    bindChartActionButtons();
}

// 차트 상단의 매수, 매도, 관심 버튼 연결
// 상세페이지 차트와 같은 좌표/라벨/포인트 구조로 메인 차트 SVG를 만든다.
function createMainChartMarkup(priceHistories, chartClass) {
    const width = 900;
    const height = 360;
    const paddingTop = 24;
    const paddingRight = 72;
    const paddingBottom = 34;
    const paddingLeft = 34;
    const chartColor = chartClass === 'down' ? '#3b82f6' : '#ff4560';

    const prices = priceHistories.flatMap((item) => [
        Number(item.highPrice || item.closePrice || 0),
        Number(item.lowPrice || item.closePrice || 0),
        Number(item.closePrice || 0)
    ]);

    const minPrice = Math.min(...prices);
    const maxPrice = Math.max(...prices);
    const priceRange = maxPrice - minPrice || 1;
    const first = priceHistories[0];
    const latest = priceHistories[priceHistories.length - 1];

    const points = priceHistories.map((item, index) => {
        const price = Number(item.closePrice || 0);
        const x = paddingLeft + index * ((width - paddingLeft - paddingRight) / Math.max(priceHistories.length - 1, 1));
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

    const yLabels = createMainChartPriceLabels(minPrice, maxPrice, 5).map((price) => {
        const y = height - paddingBottom - ((price - minPrice) / priceRange) * (height - paddingTop - paddingBottom);

        return {
            price,
            y
        };
    });

    return `
        <svg class="mock-chart" viewBox="0 0 ${width} ${height}" preserveAspectRatio="none">
            ${yLabels.map((label) => `
                <line
                    class="main-chart-guide"
                    x1="${paddingLeft}"
                    y1="${label.y.toFixed(1)}"
                    x2="${width - paddingRight}"
                    y2="${label.y.toFixed(1)}"
                ></line>
                <text
                    class="main-chart-price-label"
                    x="${width - paddingRight + 12}"
                    y="${label.y.toFixed(1)}"
                    dominant-baseline="middle"
                >${formatNumber(Math.round(label.price))}</text>
            `).join('')}

            <path
                class="chart-line ${chartClass}"
                d="${linePath}"
                style="stroke: ${chartColor};"
            ></path>
            ${points.slice(1).map((point, index) => {
                const prevPoint = points[index];
        
                return `
                    <path
                        class="main-chart-hover-line"
                        d="M${prevPoint.x.toFixed(1)},${prevPoint.y.toFixed(1)} L${point.x.toFixed(1)},${point.y.toFixed(1)}"
                        data-chart-label="${escapeHtml(point.label)}"
                        data-chart-price="${formatNumber(point.price)}원"
                    ></path>
                `;
            }).join('')}
            ${points.map((point, index) => {
        const step = Math.ceil(points.length / 6);

        if (index !== 0 && index !== points.length - 1 && index % step !== 0) {
            return '';
        }

        return `
                <circle
                    class="main-chart-point ${chartClass}"
                    cx="${point.x.toFixed(1)}"
                    cy="${point.y.toFixed(1)}"
                    r="3"
                    style="fill: ${chartColor};"
                >
                    <title>${escapeHtml(point.label)} / ${formatNumber(point.price)}원</title>
                </circle>
            `;
    }).join('')}
        </svg>

        <div class="main-chart-axis">
            <span>${escapeHtml(first.label)}</span>
            <span>${escapeHtml(latest.label)}</span>
        </div>

        <div class="chart-watermark">PRICE HISTORY</div>
    `;
}

// 상세페이지 차트처럼 오른쪽 가격 라벨 값을 균등하게 만든다.
// 메인 차트 선 위에 마우스를 올리면 상세 차트와 같은 커스텀 툴팁을 표시한다.
function bindMainChartTooltip() {
    const chartBox = document.querySelector('.main-chart-box');

    if (!chartBox) {
        return;
    }

    let tooltip = document.getElementById('main-chart-tooltip');

    if (!tooltip) {
        tooltip = document.createElement('div');
        tooltip.id = 'main-chart-tooltip';
        tooltip.className = 'main-chart-tooltip';
        document.body.appendChild(tooltip);
    }

    chartBox.querySelectorAll('.main-chart-hover-line').forEach((line) => {
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

function createMainChartPriceLabels(minPrice, maxPrice, count) {
    if (count <= 1 || minPrice === maxPrice) {
        return [maxPrice];
    }

    const step = (maxPrice - minPrice) / (count - 1);

    return Array.from({ length: count }, (_, index) => {
        return maxPrice - step * index;
    });
}

function bindChartActionButtons() {
    const tradeButtons = document.querySelectorAll('.chart-trade-btn');

    tradeButtons.forEach((button) => {
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

    const watchlistButton = document.querySelector('.chart-watchlist-btn');

    if (watchlistButton) {
        watchlistButton.addEventListener('click', async () => {
            const stockName = watchlistButton.dataset.stockName;

            if (!stockName) {
                return;
            }

            const success = await addMainWatchlist(stockName);

            if (success) {
                watchlistButton.classList.add('active');
                await loadWatchlists();
            }
        });
    }
}

// 메인 차트에서 관심종목 추가
async function addMainWatchlist(stockName) {
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

// 차트 기간 버튼 클릭 시 같은 quote 기준으로 상승/하락 색상을 유지한다.
function bindChartPeriodButtons(stockName, symbol, quote = null) {
    const buttons = document.querySelectorAll('.chart-periods button');

    buttons.forEach((button) => {
        button.addEventListener('click', async () => {
            const period = button.dataset.period;

            if (!symbol || !period) {
                return;
            }

            const priceHistories = await fetchStockPriceHistories(symbol, period);
            renderMainChart(stockName, priceHistories, symbol, period, quote);
        });
    });
}

function renderStockSideInfo(stockName, stock, quote) {
    const sidePanel = document.querySelector('.side-panel');

    if (!sidePanel) {
        return;
    }

    if (!stock) {
        sidePanel.innerHTML = `
            <div class="panel-header">
                <div>
                    <h2>${escapeHtml(stockName || 'DETAILS')}</h2>
                </div>
            </div>

            <section class="stock-info-section">
                <h3>정보 없음</h3>
                <p class="stock-info-message">등록된 종목 정보를 찾을 수 없습니다.</p>
            </section>
        `;
        return;
    }

    const price = quote || stock;

    sidePanel.innerHTML = `
        <div class="panel-header">
            <div>
                <h2>${escapeHtml(stock.name)}</h2>
            </div>
        </div>

        <div class="stock-info-grid">
            <div class="stock-info-card">
                <span class="info-label">현재가</span>
                <strong>${formatNumber(price.currentPrice)}원</strong>
            </div>
            <div class="stock-info-card">
                <span class="info-label">어제대비</span>
                <strong class="${price.changePrice >= 0 ? 'up' : 'down'}">${formatSignedNumber(price.changePrice)}원</strong>
            </div>
            <div class="stock-info-card">
                <span class="info-label">등락률</span>
                <strong class="${price.changeRate >= 0 ? 'up' : 'down'}">${formatSignedNumber(price.changeRate)}%</strong>
            </div>
            <div class="stock-info-card">
                <span class="info-label">거래량</span>
                <strong>${formatNumber(price.volume)}</strong>
            </div>
        </div>

        <section class="stock-info-section">
            <h3>종목정보</h3>
            <dl>
                <div><dt>종목코드</dt><dd>${escapeHtml(stock.symbol)}</dd></div>
                <div><dt>시장</dt><dd>${escapeHtml(stock.market)}</dd></div>
                <div><dt>업종</dt><dd>${escapeHtml(stock.sector)}</dd></div>
                <div><dt>시가총액</dt><dd>${formatNumber(stock.marketCap)}원</dd></div>
                <div><dt>상장주식수</dt><dd>${formatNumber(stock.listedShares)}</dd></div>
            </dl>
        </section>

        <section class="stock-info-section">
            <h3>실시간 참고</h3>
            <dl>
                <div><dt>시가</dt><dd>${formatNumber(quote?.openPrice)}원</dd></div>
                <div><dt>고가</dt><dd>${formatNumber(quote?.highPrice)}원</dd></div>
                <div><dt>저가</dt><dd>${formatNumber(quote?.lowPrice)}원</dd></div>
                <div><dt>거래대금</dt><dd>${formatNumber(quote?.tradingValue)}원</dd></div>
            </dl>
        </section>
    `;
}

function formatNumber(value) {
    if (value === null || value === undefined) {
        return '-';
    }

    return Number(value).toLocaleString('ko-KR');
}

function formatSignedNumber(value) {
    if (value === null || value === undefined) {
        return '-';
    }

    const number = Number(value);
    const sign = number > 0 ? '+' : '';

    return `${sign}${number.toLocaleString('ko-KR')}`;
}
