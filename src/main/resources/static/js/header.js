// 공통 헤더의 인증 확인, 검색, 내정보 수정, 토큰 연장, 주문 체결 알림을 처리한다.
let currentUserInfo = null;
let tokenTimerId = null;
let orderNotificationSocket = null;
let orderToastSequence = 0;

window.authReady = initializeAuth();

document.addEventListener('DOMContentLoaded', async () => {
    bindPrimaryNavigation();
    await window.authReady;

    bindStockSearch();
    bindMyInfoModal();
    bindTokenRefreshButton();
    connectOrderNotificationSocket();
});

// 현재 경로에 맞는 상단 주요 메뉴를 활성화한다.
function bindPrimaryNavigation() {
    const currentPath = window.location.pathname;

    document.querySelectorAll('[data-primary-nav]').forEach((link) => {
        const target = link.dataset.primaryNav;
        const active = target === 'portfolio'
                ? currentPath === '/portfolio'
                : currentPath === '/main' || currentPath === '/stocks/detail';

        link.classList.toggle('active', active);
        if (active) {
            link.setAttribute('aria-current', 'page');
        }
    });
}

// 저장된 JWT로 로그인 상태를 확인하고 헤더 사용자 정보를 초기화한다.
async function initializeAuth() {
    const accessToken = localStorage.getItem('accessToken');

    if (!accessToken) {
        redirectToLogin();
        return false;
    }

    try {
        const response = await fetch('/api/users/me', {
            method: 'GET',
            headers: {
                'Authorization': `Bearer ${accessToken}`
            }
        });

        if (isAuthError(response)) {
            redirectToLogin();
            return false;
        }

        if (!response.ok) {
            console.warn('사용자 정보 조회 실패:', response.status);
            return true;
        }

        currentUserInfo = await response.json();

        const userNickname = document.getElementById('user-nickname');

        if (userNickname) {
            userNickname.textContent = currentUserInfo.nickname || currentUserInfo.email || 'USER';
        }

        startTokenTimer();

        return true;
    } catch (error) {
        console.error('사용자 정보 조회 중 네트워크 오류:', error);
        return true;
    }
}

// 보호 API 호출 전에 공통 인증 초기화가 끝날 때까지 기다린다.
async function waitAuthReady() {
    if (!window.authReady) {
        window.authReady = initializeAuth();
    }

    return await window.authReady;
}

// 저장된 JWT를 제거하고 로그인 화면으로 이동한다.
function redirectToLogin() {
    localStorage.removeItem('accessToken');
    window.location.replace('/login');
}

// 인증 실패로 처리할 응답인지 확인한다.
function isAuthError(response) {
    return response && response.status === 401;
}

// 보호 API 요청 전에 인증 확인, Authorization 헤더 추가, 인증 실패 처리를 공통으로 수행한다.
async function authFetch(url, options = {}) {
    const authenticated = await waitAuthReady();

    if (!authenticated) {
        return null;
    }

    const accessToken = localStorage.getItem('accessToken');

    if (!accessToken) {
        redirectToLogin();
        return null;
    }

    const response = await fetch(url, {
        ...options,
        headers: {
            ...(options.headers || {}),
            'Authorization': `Bearer ${accessToken}`
        }
    });

    if (isAuthError(response)) {
        redirectToLogin();
        return null;
    }

    return response;
}

// 공통 헤더 검색창의 입력, 제출, 외부 클릭 이벤트를 연결한다.
function bindStockSearch() {
    const form = document.getElementById('stock-search-form');
    const input = document.getElementById('stock-search-input');
    const results = document.getElementById('stock-search-results');

    if (!form || !input || !results) {
        return;
    }

    let timerId;

    input.addEventListener('input', () => {
        clearTimeout(timerId);
        clearSearchResults();

        const keyword = input.value.trim();

        if (!keyword) {
            clearSearchResults();
            return;
        }

        timerId = setTimeout(() => {
            searchStocks(keyword);
        }, 250);
    });

    form.addEventListener('submit', async (event) => {
        event.preventDefault();

        const keyword = input.value.trim();

        if (!keyword) {
            clearSearchResults();
            return;
        }

        clearTimeout(timerId);

        const stocks = await searchStocks(keyword);

        if (input.value.trim() !== keyword || stocks.length === 0) {
            return;
        }

        goStockDetail(stocks[0].symbol);
    });

    document.addEventListener('click', (event) => {
        if (!form.contains(event.target)) {
            clearSearchResults();
        }
    });
}

// 키워드로 종목을 검색하고 결과 목록을 갱신한다.
async function searchStocks(keyword) {
    const response = await authFetch(`/api/stocks/search?keyword=${encodeURIComponent(keyword)}`);

    if (!response || !response.ok) {
        clearSearchResults();
        return [];
    }

    const responseBody = await response.json();
    const stocks = Array.isArray(responseBody) ? responseBody : [];
    const input = document.getElementById('stock-search-input');

    if (input && input.value.trim() !== keyword) {
        return [];
    }

    renderSearchResults(stocks);
    return stocks;
}

// 종목 검색 결과를 검색창 하단 목록으로 렌더링한다.
function renderSearchResults(stocks) {
    const results = document.getElementById('stock-search-results');

    if (!results) {
        return;
    }

    if (!stocks || stocks.length === 0) {
        results.innerHTML = `
            <div class="search-result-empty">검색 결과가 없습니다.</div>
        `;
        results.classList.add('active');
        return;
    }

    results.innerHTML = stocks.map((stock) => `
        <button type="button" class="search-result-item" onclick="goStockDetail('${escapeHtml(stock.symbol)}')">
            <span class="search-result-name">${escapeHtml(stock.name)}</span>
            <span class="search-result-meta">${escapeHtml(stock.symbol)} · ${escapeHtml(stock.market)}</span>
        </button>
    `).join('');

    results.classList.add('active');
}

// 종목 검색 결과 목록을 비우고 닫는다.
function clearSearchResults() {
    const results = document.getElementById('stock-search-results');

    if (!results) {
        return;
    }

    results.innerHTML = '';
    results.classList.remove('active');
}

// 선택한 종목 심볼 기준으로 상세 페이지로 이동한다.
function goStockDetail(symbol) {
    window.location.href = `/stocks/detail?keyword=${encodeURIComponent(symbol)}`;
}

// 로그아웃 시 토큰과 주문 알림 WebSocket을 정리한다.
function handleLogout() {
    localStorage.removeItem('accessToken');
    closeOrderNotificationSocket();
    window.location.href = '/login';
}

// 로그인한 사용자의 주문 체결 알림 WebSocket을 연결한다.
function connectOrderNotificationSocket() {
    const token = localStorage.getItem('accessToken');

    if (!token) {
        return;
    }

    if (orderNotificationSocket && orderNotificationSocket.readyState === WebSocket.OPEN) {
        return;
    }

    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
    orderNotificationSocket = new WebSocket(`${protocol}://${window.location.host}/ws/orders`);

    orderNotificationSocket.onopen = () => {
        orderNotificationSocket.send(JSON.stringify({
            type: 'ORDER_NOTIFICATION_SUBSCRIBE',
            token
        }));
    };

    orderNotificationSocket.onmessage = async (event) => {
        const message = JSON.parse(event.data);

        if (message.type === 'ORDER_EXECUTED') {
            await handleOrderExecutionNotification(message.data);
        }
    };

    orderNotificationSocket.onclose = () => {
        orderNotificationSocket = null;
    };

    orderNotificationSocket.onerror = () => {
        closeOrderNotificationSocket();
    };
}

// 주문 체결 알림을 사용자에게 보여주고 현재 화면 데이터를 갱신한다.
async function handleOrderExecutionNotification(notification) {
    if (!notification) {
        return;
    }

    showOrderExecutionToast(notification);

    if (typeof window.handleOrderSuccess === 'function') {
        await window.handleOrderSuccess();
    }
}

// 체결 알림을 화면 우측 상단 toast로 표시한다.
function showOrderExecutionToast(notification) {
    const container = getOrderToastContainer();

    if (!container) {
        return;
    }

    const toastId = `order-toast-${++orderToastSequence}`;
    const toast = document.createElement('button');
    const orderTypeText = notification.orderType === 'BUY' ? '매수' : '매도';
    const quantity = Number(notification.quantity || 0).toLocaleString('ko-KR');
    const price = Number(notification.price || 0).toLocaleString('ko-KR');
    const totalAmount = Number(notification.totalAmount || 0).toLocaleString('ko-KR');
    const stockName = notification.stockName || notification.symbol || '주문';
    const symbol = notification.symbol || '';

    toast.type = 'button';
    toast.id = toastId;
    toast.className = `order-toast ${notification.orderType === 'BUY' ? 'buy' : 'sell'}`;
    toast.innerHTML = `
        <span class="order-toast-kicker">ORDER FILLED</span>
        <strong>${escapeHtml(stockName)} ${orderTypeText} 체결</strong>
        <span>${quantity}주 · ${price}원</span>
        <em>체결금액 ${totalAmount}원</em>
    `;

    toast.addEventListener('click', () => {
        if (symbol) {
            window.location.href = `/stocks/detail?keyword=${encodeURIComponent(symbol)}`;
        }
    });

    container.prepend(toast);

    requestAnimationFrame(() => {
        toast.classList.add('visible');
    });

    setTimeout(() => {
        removeOrderToast(toast);
    }, 6500);
}

// toast 컨테이너가 없으면 생성해서 어느 화면에서도 알림을 표시할 수 있게 한다.
function getOrderToastContainer() {
    let container = document.getElementById('order-toast-container');

    if (container) {
        return container;
    }

    container = document.createElement('div');
    container.id = 'order-toast-container';
    container.className = 'order-toast-container';
    container.setAttribute('aria-live', 'polite');
    document.body.appendChild(container);

    return container;
}

// 체결 알림 toast를 부드럽게 제거한다.
function removeOrderToast(toast) {
    if (!toast) {
        return;
    }

    toast.classList.remove('visible');
    toast.classList.add('closing');

    setTimeout(() => {
        toast.remove();
    }, 220);
}

// 주문 체결 알림 WebSocket을 닫는다.
function closeOrderNotificationSocket() {
    if (orderNotificationSocket) {
        orderNotificationSocket.close();
        orderNotificationSocket = null;
    }
}

// 내정보 모달 열기, 닫기, 수정 폼 이벤트를 연결한다.
function bindMyInfoModal() {
    const profileButton = document.getElementById('profile-menu-btn');
    const myInfoOverlay = document.getElementById('my-info-modal-overlay');

    if (profileButton) {
        profileButton.addEventListener('click', showMyInfo);
    }

    if (myInfoOverlay) {
        myInfoOverlay.addEventListener('click', (event) => {
            if (event.target === myInfoOverlay) {
                closeMyInfoModal();
            }
        });
    }

    bindNicknameUpdateForm();
    bindPasswordUpdateForm();
    bindMyInfoPasswordToggles();
}

// 현재 사용자 정보를 내정보 모달에 채우고 모달을 연다.
function showMyInfo() {
    const overlay = document.getElementById('my-info-modal-overlay');

    if (!overlay || !currentUserInfo) {
        return;
    }

    setHeaderText('my-info-email', currentUserInfo.email || '-');
    setHeaderText('my-info-nickname', currentUserInfo.nickname || '-');
    setInputValue('my-info-nickname-input', currentUserInfo.nickname || '');
    setInputValue('my-info-current-password', '');
    setInputValue('my-info-new-password', '');
    resetMyInfoPasswordVisibility();
    setMyInfoMessage('');

    overlay.classList.add('active');
}

// 내정보 모달을 닫는다.
function closeMyInfoModal() {
    const overlay = document.getElementById('my-info-modal-overlay');

    if (overlay) {
        overlay.classList.remove('active');
    }
}

// 내정보 모달의 닉네임 변경 폼 submit 이벤트를 연결한다.
function bindNicknameUpdateForm() {
    const form = document.getElementById('nickname-update-form');

    if (!form) {
        return;
    }

    form.addEventListener('submit', handleNicknameUpdate);
}

// 내정보 모달의 비밀번호 변경 폼 submit 이벤트를 연결한다.
function bindPasswordUpdateForm() {
    const form = document.getElementById('password-update-form');

    if (!form) {
        return;
    }

    form.addEventListener('submit', handlePasswordUpdate);
}

// 내정보 모달의 비밀번호 표시 버튼을 각 입력칸과 연결한다.
function bindMyInfoPasswordToggles() {
    const buttons = document.querySelectorAll('.my-info-password-toggle');

    buttons.forEach((button) => {
        button.addEventListener('click', () => {
            const input = document.getElementById(button.dataset.passwordTarget);

            if (!input) {
                return;
            }

            const shouldShow = input.type === 'password';
            input.type = shouldShow ? 'text' : 'password';
            button.classList.toggle('active', shouldShow);
            button.setAttribute('aria-label', shouldShow ? '비밀번호 숨기기' : '비밀번호 표시');
        });
    });
}

// 내정보 모달을 열 때 비밀번호 입력칸을 다시 숨김 상태로 되돌린다.
function resetMyInfoPasswordVisibility() {
    document.querySelectorAll('.my-info-password-toggle').forEach((button) => {
        const input = document.getElementById(button.dataset.passwordTarget);

        if (input) {
            input.type = 'password';
        }

        button.classList.remove('active');
        button.setAttribute('aria-label', '비밀번호 표시');
    });
}

// 닉네임 변경 API를 호출하고 성공 시 화면의 사용자 닉네임을 갱신한다.
async function handleNicknameUpdate(event) {
    event.preventDefault();

    const input = document.getElementById('my-info-nickname-input');
    const nickname = input?.value?.trim();

    if (!nickname || nickname.length < 2) {
        setMyInfoMessage('닉네임은 2자 이상 입력해 주세요.', true);
        return;
    }

    const response = await authFetch('/api/users/me/nickname', {
        method: 'PATCH',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ nickname })
    });

    if (!response) {
        return;
    }

    if (!response.ok) {
        setMyInfoMessage(await readErrorMessage(response, '닉네임 변경에 실패했습니다.'), true);
        return;
    }

    currentUserInfo = await response.json();
    setHeaderText('user-nickname', currentUserInfo.nickname || currentUserInfo.email || 'USER');
    setHeaderText('my-info-nickname', currentUserInfo.nickname || '-');
    setInputValue('my-info-nickname-input', currentUserInfo.nickname || '');
    setMyInfoMessage('닉네임이 변경되었습니다.');
}

// 비밀번호 변경 API를 호출하고 기존 JWT가 무효화되면 로그인 화면으로 이동한다.
async function handlePasswordUpdate(event) {
    event.preventDefault();

    const currentPassword = document.getElementById('my-info-current-password')?.value || '';
    const newPassword = document.getElementById('my-info-new-password')?.value || '';

    if (!currentPassword) {
        setMyInfoMessage('현재 비밀번호를 입력해 주세요.', true);
        return;
    }

    if (!newPassword || newPassword.length < 8) {
        setMyInfoMessage('새 비밀번호는 8자 이상 입력해 주세요.', true);
        return;
    }

    const response = await authFetch('/api/users/me/password', {
        method: 'PATCH',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            currentPassword,
            newPassword
        })
    });

    if (!response) {
        return;
    }

    if (!response.ok) {
        setMyInfoMessage(await readErrorMessage(response, '비밀번호 변경에 실패했습니다.'), true);
        return;
    }

    setInputValue('my-info-current-password', '');
    setInputValue('my-info-new-password', '');
    setMyInfoMessage('비밀번호가 변경되었습니다. 새 비밀번호로 다시 로그인해 주세요.');
    localStorage.removeItem('accessToken');

    window.setTimeout(() => {
        window.location.replace('/login');
    }, 1200);
}

// 공통 에러 응답에서 사용자에게 보여줄 메시지를 추출한다.
async function readErrorMessage(response, fallbackMessage) {
    try {
        const data = await response.json();
        return data.message || fallbackMessage;
    } catch (error) {
        return fallbackMessage;
    }
}

// 내정보 모달 메시지를 성공/실패 상태에 맞춰 표시한다.
function setMyInfoMessage(message, isError = false) {
    const element = document.getElementById('my-info-message');

    if (!element) {
        return;
    }

    element.textContent = message || '';
    element.classList.toggle('error', Boolean(isError));
}

// input 값을 안전하게 변경한다.
function setInputValue(id, value) {
    const element = document.getElementById(id);

    if (element) {
        element.value = value;
    }
}

// 지정한 id의 텍스트 콘텐츠를 안전하게 변경한다.
function setHeaderText(id, value) {
    const element = document.getElementById(id);

    if (element) {
        element.textContent = value;
    }
}

// 사용자 입력 또는 API 값을 HTML 문자열에 넣기 전에 이스케이프한다.
function escapeHtml(value) {
    return String(value || '')
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#039;');
}

// 서버 거래 세션 코드를 메인과 상세 화면에서 공통으로 사용할 표시 이름으로 변환한다.
function resolveMarketSessionDisplayName(marketSession) {
    const displayNames = {
        PRE_MARKET: '장전 주문',
        OPENING_AUCTION: '장 시작 동시호가',
        REGULAR: '정규장',
        CLOSING_AUCTION: '장 마감 동시호가',
        AFTER_MARKET_WAIT: '장후 대기',
        AFTER_MARKET_CLOSING_PRICE: '장후 시간외',
        AFTER_HOURS_SINGLE_PRICE: '시간외 단일가',
        NXT_AFTER_MARKET: 'NXT 애프터마켓',
        RESERVATION: '예약 주문',
        CLOSED: '거래 종료'
    };

    return displayNames[marketSession] || '시장';
}

// 토큰 수동 연장 버튼에 클릭 이벤트를 연결한다.
function bindTokenRefreshButton() {
    const button = document.getElementById('token-refresh-btn');

    if (!button) {
        return;
    }

    button.addEventListener('click', refreshAccessTokenManually);
}

// 사용자가 연장 버튼을 눌렀을 때만 새 JWT를 발급받아 로그인 시간을 60분으로 되돌린다.
async function refreshAccessTokenManually() {
    const accessToken = localStorage.getItem('accessToken');

    if (!accessToken) {
        redirectToLogin();
        return;
    }

    const response = await fetch('/api/users/refresh', {
        method: 'POST',
        headers: {
            'Authorization': `Bearer ${accessToken}`
        }
    });

    if (isAuthError(response)) {
        redirectToLogin();
        return;
    }

    if (!response.ok) {
        return;
    }

    const data = await response.json();

    if (data.token) {
        localStorage.setItem('accessToken', data.token);
        startTokenTimer();
    }
}

// JWT payload의 exp 값을 읽어 남은 로그인 시간을 계산한다.
function getTokenRemainingMs(token) {
    try {
        const payload = JSON.parse(atob(token.split('.')[1]));
        return payload.exp * 1000 - Date.now();
    } catch (error) {
        return 0;
    }
}

// 남은 로그인 시간을 mm:ss 형식으로 변환한다.
function formatRemainingTime(milliseconds) {
    const totalSeconds = Math.max(0, Math.floor(milliseconds / 1000));
    const minutes = String(Math.floor(totalSeconds / 60)).padStart(2, '0');
    const seconds = String(totalSeconds % 60).padStart(2, '0');

    return `${minutes}:${seconds}`;
}

// 헤더에 JWT 남은 시간을 표시하고 만료되면 로그인 화면으로 이동한다.
function startTokenTimer() {
    const remainTime = document.getElementById('token-remain-time');

    if (!remainTime) {
        return;
    }

    clearInterval(tokenTimerId);

    tokenTimerId = setInterval(() => {
        const token = localStorage.getItem('accessToken');
        const remainingMs = getTokenRemainingMs(token);

        remainTime.textContent = formatRemainingTime(remainingMs);

        if (remainingMs <= 0) {
            clearInterval(tokenTimerId);
            redirectToLogin();
        }
    }, 1000);
}
