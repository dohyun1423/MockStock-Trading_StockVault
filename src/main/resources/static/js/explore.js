// 전체 시장 순위는 REST 스냅샷으로 조회한다. 추가 WebSocket 구독은 만들지 않는다.
const RANKING_REFRESH_MS = 60_000;
let rankingItems = [];
let favoriteNames = new Map();
let favoritesReady = false;
let favoriteRevision = 0;
let rankingLoading = false;
let rankingTimer = null;
let renderedRanking = '';
const pendingFavorites = new Set();

document.addEventListener('DOMContentLoaded', async () => {
    if (!await window.authReady) return;
    document.addEventListener('visibilitychange', () => {
        clearTimeout(rankingTimer);
        if (!document.hidden) refreshExplore();
    });
    window.addEventListener('pagehide', () => clearTimeout(rankingTimer));
    window.addEventListener('pageshow', (event) => {
        if (event.persisted && !document.hidden) refreshExplore();
    });
    refreshExplore();
});

async function refreshExplore() {
    if (rankingLoading || document.hidden) return;
    clearTimeout(rankingTimer);
    rankingLoading = true;
    try {
        await Promise.all([loadVolumeRanking(), loadExploreFavorites()]);
    } finally {
        rankingLoading = false;
        document.getElementById('volume-ranking').setAttribute('aria-busy', 'false');
        renderVolumeRanking();
        if (!document.hidden) rankingTimer = setTimeout(refreshExplore, RANKING_REFRESH_MS);
    }
}

async function loadVolumeRanking() {
    try {
        const response = await authFetch('/api/stocks/rankings/volume');
        if (!response) return;
        const data = await response.json();
        if (!response.ok) throw new Error(data.message || '순위를 불러오지 못했습니다.');
        if (!Array.isArray(data.items)) throw new Error('순위 응답을 확인할 수 없습니다.');
        rankingItems = data.items.slice(0, 30);
        const updated = new Date(data.fetchedAt);
        document.getElementById('ranking-updated').textContent = Number.isNaN(updated.getTime())
            ? '' : `${updated.toLocaleString('ko-KR', { timeZone: 'Asia/Seoul', hour12: false })} 조회`;
        setRankingMessage(data.stale ? '갱신이 지연되어 마지막 조회 결과를 표시합니다. 1분 후 다시 시도합니다.' : '', data.stale);
    } catch (error) {
        setRankingMessage(rankingItems.length
            ? '연결이 원활하지 않아 마지막 조회 결과를 표시합니다. 1분 후 다시 시도합니다.'
            : `${error.message} 1분 후 다시 시도합니다.`, true);
    }
}

async function loadExploreFavorites() {
    if (pendingFavorites.size) return;
    const revision = favoriteRevision;
    try {
        const response = await authFetch('/api/watchlists');
        if (!response || !response.ok) throw new Error('관심종목 조회 실패');
        const favorites = await response.json();
        if (revision !== favoriteRevision) return;
        favoriteNames = new Map(favorites.filter((item) => item.symbol)
            .map((item) => [item.symbol, item.stockName]));
        favoritesReady = true;
    } catch (error) {
        // 상태를 확인하지 못한 경우 잘못된 추가/삭제를 막고 다음 주기에 다시 조회한다.
        favoritesReady = false;
    }
}

function setRankingMessage(message, error = false) {
    const element = document.getElementById('ranking-message');
    element.textContent = message;
    element.classList.toggle('error', !!error);
}

function renderVolumeRanking() {
    if (pendingFavorites.size) return;
    const signature = JSON.stringify([rankingItems, [...favoriteNames], favoritesReady]);
    if (signature === renderedRanking) return;
    renderedRanking = signature;
    const list = document.getElementById('volume-ranking');
    const activeElement = document.activeElement;
    const focusedSymbol = activeElement && activeElement.dataset ? activeElement.dataset.rankingSymbol : null;
    const rows = rankingItems.map((item) => {
        const row = document.createElement('li');
        row.className = 'ranking-row';
        const link = document.createElement('a');
        link.className = 'ranking-link';
        link.href = `/stocks/detail?keyword=${encodeURIComponent(item.symbol)}${item.available ? '' : '&source=explore'}`;

        const rank = exploreText('span', 'ranking-position', item.rank);
        const info = document.createElement('div');
        info.className = 'ranking-info';
        const identity = document.createElement('div');
        identity.className = 'ranking-identity';
        identity.append(exploreText('span', 'ranking-name', item.name),
            exploreText('span', 'ranking-symbol', item.symbol));
        const values = document.createElement('div');
        values.className = 'ranking-values';
        const rate = Number(item.changeRate);
        values.append(exploreText('span', 'ranking-price', `${Number(item.currentPrice).toLocaleString('ko-KR')}원`),
            exploreText('span', `ranking-change ${rate > 0 ? 'up' : rate < 0 ? 'down' : ''}`,
                `${rate > 0 ? '+' : ''}${rate.toFixed(2)}%`));
        info.append(identity, values);
        link.append(rank, info);

        const heart = document.createElement('button');
        heart.type = 'button';
        heart.className = 'ranking-heart';
        heart.dataset.rankingSymbol = item.symbol;
        heart.innerHTML = "<svg width=\"22\" height=\"22\" viewBox=\"0 0 24 24\" fill=\"currentColor\">\r\n                    <path d=\"M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.08C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z\"/>\r\n                </svg>";
        updateExploreHeart(heart, item);
        heart.addEventListener('click', () => toggleExploreFavorite(item, heart));
        row.append(link, exploreText('span', 'ranking-volume', `${Number(item.volume).toLocaleString('ko-KR')}주`), heart);
        return row;
    });
    list.replaceChildren(...rows);
    if (focusedSymbol) {
        const heart = [...list.querySelectorAll('.ranking-heart')]
            .find((button) => button.dataset.rankingSymbol === focusedSymbol);
        if (heart && !heart.disabled) heart.focus();
    }
}

function exploreText(tag, className, text) {
    const element = document.createElement(tag);
    element.className = className;
    element.textContent = String(text);
    return element;
}

function updateExploreHeart(button, item) {
    const active = favoriteNames.has(item.symbol);
    button.classList.toggle('active', active);
    button.setAttribute('aria-pressed', String(active));
    const label = !favoritesReady ? '관심종목 상태 확인 중 · 잠시 후 다시 시도'
        : `${item.name} 관심종목 ${active ? '해제' : '추가'}`;
    button.setAttribute('aria-label', label);
    button.title = label;
    button.disabled = !favoritesReady || pendingFavorites.has(item.symbol);
}

async function prepareExploreStock(item) {
    if (item.available) return;
    const response = await authFetch(`/api/stocks/rankings/volume/${encodeURIComponent(item.symbol)}/resolve`, { method: 'POST' });
    if (!response) throw new Error('로그인 상태를 확인해 주세요.');
    const stock = await response.json();
    if (!response.ok) throw new Error(stock.message || '종목 정보를 확인하지 못했습니다.');
    if (stock.symbol !== item.symbol || !stock.name) throw new Error('종목 정보를 확인하지 못했습니다.');
    item.name = stock.name;
    item.available = true;
    rankingItems = rankingItems.map((entry) => entry.symbol === item.symbol
        ? { ...entry, name: stock.name, available: true } : entry);
}

async function toggleExploreFavorite(item, button) {
    if (!favoritesReady || pendingFavorites.has(item.symbol)) return;
    pendingFavorites.add(item.symbol);
    favoriteRevision++;
    updateExploreHeart(button, item);
    const active = favoriteNames.has(item.symbol);
    try {
        if (!active) await prepareExploreStock(item);
        const response = await authFetch(active
            ? `/api/watchlists?stockName=${encodeURIComponent(favoriteNames.get(item.symbol))}`
            : '/api/watchlists', active ? { method: 'DELETE' } : {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ stockName: item.name })
            });
        if (!response) return;
        if (!response.ok) {
            const error = await response.json().catch(() => ({}));
            throw new Error(error.message || '관심종목을 변경하지 못했습니다.');
        }
        if (active) favoriteNames.delete(item.symbol);
        else favoriteNames.set(item.symbol, item.name);
        setRankingMessage(`${item.name} ${active ? '관심종목을 해제했습니다.' : '관심종목에 추가했습니다.'}`);
    } catch (error) {
        setRankingMessage(error.message || '관심종목을 변경하지 못했습니다.', true);
    } finally {
        favoriteRevision++;
        pendingFavorites.delete(item.symbol);
        updateExploreHeart(button, item);
        renderVolumeRanking();
    }
}
