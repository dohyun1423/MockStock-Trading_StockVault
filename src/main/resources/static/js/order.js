// 주문 모달의 현재가 조회, 주문가/수량 입력, 매수/매도 요청을 처리한다.
let orderState = {
    symbol: '',
    stockName: '',
    orderType: 'BUY',
    currentPrice: 0,
    orderPrice: 0,
    priceStep: 1,
    cashBalance: 0,
    holdingQuantity: 0,
    marketSession: null,
    immediateExecution: false,
    reservationAvailable: false
};

let editOrderState = {
    orderId: null,
    symbol: '',
    stockName: '',
    orderType: 'BUY',
    originalPrice: 0,
    originalRemainingQuantity: 0,
    executedQuantity: 0
};

// 주문 모달을 열고 선택한 종목의 주문 정보를 초기화한다.
async function openOrderModal(symbol, stockName, orderType = 'BUY') {
    orderState.symbol = symbol;
    orderState.stockName = stockName;
    orderState.orderType = orderType;

    setOrderMessage('');
    setOrderText('order-stock-name', stockName || symbol || '-');
    setOrderQuantity(1);
    setOrderPrice(0);

    const overlay = document.getElementById('order-modal-overlay');

    if (overlay) {
        overlay.classList.add('active');
    }

    setOrderType(orderType);
    await loadOrderData();
    updateOrderTotalAmount();
}

// 주문 모달을 닫는다.
function closeOrderModal() {
    const overlay = document.getElementById('order-modal-overlay');

    if (overlay) {
        overlay.classList.remove('active');
    }
}

// 미체결 주문 수정 모달을 열고 선택한 주문의 현재 값을 채운다.
function openOrderEditModal(order) {
    if (!order || !order.id) {
        return;
    }

    editOrderState = {
        orderId: order.id,
        symbol: order.symbol || '',
        stockName: order.stockName || order.symbol || '',
        orderType: order.orderType || 'BUY',
        originalPrice: Number(order.orderPrice || 0),
        originalRemainingQuantity: Number(order.remainingQuantity || 0),
        executedQuantity: Number(order.executedQuantity || 0)
    };

    setOrderText('edit-order-stock-name', editOrderState.stockName || '-');
    setOrderText('edit-order-type', editOrderState.orderType === 'BUY' ? '매수' : '매도');
    setOrderText('edit-order-executed-quantity', `${formatOrderNumber(editOrderState.executedQuantity)}주`);
    setOrderText('edit-order-original-price', `${formatOrderNumber(editOrderState.originalPrice)}원`);
    setOrderText('edit-order-original-quantity', `${formatOrderNumber(editOrderState.originalRemainingQuantity)}주`);
    setEditOrderPrice(editOrderState.originalPrice);
    setEditOrderQuantity(editOrderState.originalRemainingQuantity);
    setEditOrderMessage('');

    const submitButton = document.getElementById('order-edit-submit-btn');

    if (submitButton) {
        submitButton.classList.toggle('sell', editOrderState.orderType === 'SELL');
    }

    const overlay = document.getElementById('order-edit-modal-overlay');

    if (overlay) {
        overlay.classList.add('active');
    }
}

// 주문 수정 모달을 닫는다.
function closeOrderEditModal() {
    const overlay = document.getElementById('order-edit-modal-overlay');

    if (overlay) {
        overlay.classList.remove('active');
    }
}

// 매수/매도 탭 상태와 제출 버튼 색상을 변경한다.
function setOrderType(orderType) {
    orderState.orderType = orderType;

    const buyTab = document.getElementById('order-buy-tab');
    const sellTab = document.getElementById('order-sell-tab');
    const submitButton = document.getElementById('order-submit-btn');

    buyTab?.classList.toggle('active', orderType === 'BUY');
    buyTab?.classList.toggle('buy', orderType === 'BUY');

    sellTab?.classList.toggle('active', orderType === 'SELL');
    sellTab?.classList.toggle('sell', orderType === 'SELL');

    if (submitButton) {
        submitButton.textContent = orderType === 'BUY' ? '매수하기' : '매도하기';
        submitButton.classList.toggle('sell', orderType === 'SELL');
    }

    updateOrderTotalAmount();
}

// 주문에 필요한 현재가, 포트폴리오, 거래 세션 정보를 함께 조회한다.
async function loadOrderData() {
    const [quote, portfolio, session] = await Promise.all([
        fetchOrderQuote(orderState.symbol),
        fetchOrderPortfolio(),
        fetchOrderSession()
    ]);

    orderState.currentPrice = Number(quote?.currentPrice || 0);
    orderState.orderPrice = orderState.currentPrice;
    orderState.priceStep = getOrderPriceStep(orderState.currentPrice);
    orderState.cashBalance = Number(portfolio?.availableCash ?? portfolio?.cashBalance ?? 0);
    orderState.marketSession = session?.marketSession || null;
    orderState.immediateExecution = !!session?.immediateExecution;
    orderState.reservationAvailable = !!session?.reservationAvailable;

    const holding = portfolio?.holdings?.find((item) => item.symbol === orderState.symbol);
    orderState.holdingQuantity = Number(holding?.availableQuantity ?? holding?.quantity ?? 0);

    setOrderText('order-current-price', `${formatOrderNumber(orderState.currentPrice)}원`);
    setOrderText('order-cash-balance', `${formatOrderNumber(orderState.cashBalance)}원`);
    setOrderText('order-holding-quantity', `${formatOrderNumber(orderState.holdingQuantity)}주`);
    setOrderText('order-market-session', session?.displayName || '-');
    setOrderText('order-market-session-message', session?.message || '-');
    setOrderPrice(orderState.orderPrice);
    updateOrderPriceStepLabel();
}

// 현재 주문 가능 세션을 조회한다.
async function fetchOrderSession() {
    const response = await authFetch('/api/orders/session');

    if (!response || !response.ok) {
        return null;
    }

    return await response.json();
}

// 주문 대상 종목의 현재가를 조회한다.
async function fetchOrderQuote(symbol) {
    if (!symbol) {
        return null;
    }

    const response = await authFetch(`/api/stocks/${encodeURIComponent(symbol)}/quote`);

    if (!response || !response.ok) {
        return null;
    }

    return await response.json();
}

// 주문 검증에 필요한 포트폴리오 정보를 조회한다.
async function fetchOrderPortfolio() {
    const response = await authFetch('/api/portfolio');

    if (!response || !response.ok) {
        return null;
    }

    return await response.json();
}

// 입력값을 검증하고 매수/매도 주문을 제출한다.
async function submitOrder() {
    const quantity = getOrderQuantity();
    const orderPrice = getOrderPrice();

    if (quantity < 1) {
        setOrderMessage('수량은 1주 이상이어야 합니다.', 'error');
        return;
    }

    if (orderPrice < 1) {
        setOrderMessage('주문가격은 1원 이상이어야 합니다.', 'error');
        return;
    }

    if (orderState.orderType === 'BUY' && orderPrice * quantity > orderState.cashBalance) {
        setOrderMessage('주문 가능 현금이 부족합니다.', 'error');
        return;
    }

    if (orderState.orderType === 'SELL' && quantity > orderState.holdingQuantity) {
        setOrderMessage('보유 수량이 부족합니다.', 'error');
        return;
    }

    const result = await requestOrder(quantity, orderPrice);

    if (!result) {
        return;
    }

    setOrderMessage(result.message || '주문이 완료되었습니다.', 'success');

    await loadOrderData();
    updateOrderTotalAmount();

    if (typeof window.handleOrderSuccess === 'function') {
        await window.handleOrderSuccess();
    }

    setTimeout(() => {
        closeOrderModal();
    }, 400);
}

// 매수 또는 매도 주문 API를 호출한다.
async function requestOrder(quantity, orderPrice) {
    const endpoint = orderState.orderType === 'BUY' ? '/api/orders/buy' : '/api/orders/sell';

    const response = await authFetch(endpoint, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            symbol: orderState.symbol,
            quantity: quantity,
            orderPrice: orderPrice
        })
    });

    if (!response) {
        return null;
    }

    if (!response.ok) {
        const error = await response.json().catch(() => null);
        setOrderMessage(error?.message || '주문 처리에 실패했습니다.', 'error');
        return null;
    }

    return await response.json();
}

// 수정 모달에서 주문가격과 미체결 수량을 저장한다.
async function submitOrderEdit() {
    const orderPrice = getEditOrderPrice();
    const remainingQuantity = getEditOrderQuantity();

    if (!editOrderState.orderId) {
        setEditOrderMessage('수정할 주문을 찾지 못했습니다.', 'error');
        return;
    }

    if (orderPrice < 1) {
        setEditOrderMessage('주문가격은 1원 이상이어야 합니다.', 'error');
        return;
    }

    if (remainingQuantity < 1) {
        setEditOrderMessage('미체결 수량은 1주 이상이어야 합니다.', 'error');
        return;
    }

    const response = await authFetch(`/api/orders/${encodeURIComponent(editOrderState.orderId)}`, {
        method: 'PATCH',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            orderPrice,
            remainingQuantity
        })
    });

    if (!response) {
        return;
    }

    if (!response.ok) {
        const error = await response.json().catch(() => null);
        setEditOrderMessage(error?.message || '주문 수정에 실패했습니다.', 'error');
        return;
    }

    setEditOrderMessage('주문이 수정되었습니다.', 'success');

    if (typeof window.handleOrderSuccess === 'function') {
        await window.handleOrderSuccess();
    }

    setTimeout(() => {
        closeOrderEditModal();
    }, 300);
}

// 수정 모달에서 현재 주문을 취소한다.
async function cancelEditingOrder() {
    if (!editOrderState.orderId) {
        setEditOrderMessage('취소할 주문을 찾지 못했습니다.', 'error');
        return;
    }

    const response = await authFetch(`/api/orders/${encodeURIComponent(editOrderState.orderId)}/cancel`, {
        method: 'PATCH'
    });

    if (!response) {
        return;
    }

    if (!response.ok) {
        const error = await response.json().catch(() => null);
        setEditOrderMessage(error?.message || '주문 취소에 실패했습니다.', 'error');
        return;
    }

    setEditOrderMessage('주문이 취소되었습니다.', 'success');

    if (typeof window.handleOrderSuccess === 'function') {
        await window.handleOrderSuccess();
    }

    setTimeout(() => {
        closeOrderEditModal();
    }, 300);
}

// 주문 수량과 주문가격 변경 시 예상 금액을 다시 계산한다.
document.addEventListener('DOMContentLoaded', () => {
    const quantityInput = document.getElementById('order-quantity-input');

    if (quantityInput) {
        quantityInput.addEventListener('input', updateOrderTotalAmount);
    }

    const priceInput = document.getElementById('order-price-input');

    if (priceInput) {
        priceInput.addEventListener('input', () => {
            orderState.orderPrice = getOrderPrice();
            updateOrderTotalAmount();
            updateOrderPriceStepLabel();
        });
    }

    const editPriceInput = document.getElementById('edit-order-price-input');

    if (editPriceInput) {
        editPriceInput.addEventListener('input', updateEditOrderPriceStepLabel);
    }

    const overlay = document.getElementById('order-modal-overlay');

    if (overlay) {
        overlay.addEventListener('click', (event) => {
            if (event.target === overlay) {
                closeOrderModal();
            }
        });
    }

    const editOverlay = document.getElementById('order-edit-modal-overlay');

    if (editOverlay) {
        editOverlay.addEventListener('click', (event) => {
            if (event.target === editOverlay) {
                closeOrderEditModal();
            }
        });
    }
});

// 주문가격과 수량을 기준으로 예상 주문금액을 표시한다.
function updateOrderTotalAmount() {
    const quantity = getOrderQuantity();
    const orderPrice = getOrderPrice();
    const totalAmount = orderPrice * quantity;

    setOrderText('order-total-amount', `${formatOrderNumber(totalAmount)}원`);
}

// 주문 수량 입력값을 숫자로 읽는다.
function getOrderQuantity() {
    const quantityInput = document.getElementById('order-quantity-input');
    return Number(quantityInput?.value || 0);
}

// 주문가격 입력값을 숫자로 읽는다.
function getOrderPrice() {
    const priceInput = document.getElementById('order-price-input');
    return Number(priceInput?.value || 0);
}

// 수정 주문가격 입력값을 숫자로 읽는다.
function getEditOrderPrice() {
    const priceInput = document.getElementById('edit-order-price-input');
    return Number(priceInput?.value || 0);
}

// 수정 미체결 수량 입력값을 숫자로 읽는다.
function getEditOrderQuantity() {
    const quantityInput = document.getElementById('edit-order-quantity-input');
    return Number(quantityInput?.value || 0);
}

// 주문 수량 입력값을 설정한다.
function setOrderQuantity(quantity) {
    const quantityInput = document.getElementById('order-quantity-input');

    if (quantityInput) {
        quantityInput.value = quantity;
    }
}

// 주문가격 입력값과 상태값을 함께 설정한다.
function setOrderPrice(price) {
    const priceInput = document.getElementById('order-price-input');

    if (priceInput) {
        priceInput.value = price || '';
    }

    orderState.orderPrice = Number(price || 0);
}

// 수정 주문가격 입력값과 호가 단위 안내를 함께 갱신한다.
function setEditOrderPrice(price) {
    const priceInput = document.getElementById('edit-order-price-input');

    if (priceInput) {
        priceInput.value = price || '';
    }

    updateEditOrderPriceStepLabel();
}

// 수정 미체결 수량 입력값을 설정한다.
function setEditOrderQuantity(quantity) {
    const quantityInput = document.getElementById('edit-order-quantity-input');

    if (quantityInput) {
        quantityInput.value = quantity || 1;
    }
}

// 주문가격을 현재가로 되돌린다.
function setOrderPriceToCurrent() {
    setOrderPrice(orderState.currentPrice);
    updateOrderTotalAmount();
}

// 현재 종목 가격대에 맞는 호가 단위만큼 주문가격을 내린다.
function decreaseOrderPrice() {
    stepOrderPrice(-getOrderPriceStep(getOrderPrice()));
}

// 현재 종목 가격대에 맞는 호가 단위만큼 주문가격을 올린다.
function increaseOrderPrice() {
    stepOrderPrice(getOrderPriceStep(getOrderPrice()));
}

// 수정 주문가격을 가격대별 호가 단위만큼 내린다.
function decreaseEditOrderPrice() {
    stepEditOrderPrice(-getOrderPriceStep(getEditOrderPrice()));
}

// 수정 주문가격을 가격대별 호가 단위만큼 올린다.
function increaseEditOrderPrice() {
    stepEditOrderPrice(getOrderPriceStep(getEditOrderPrice()));
}

// 주문가격을 지정한 단위만큼 증감한다.
function stepOrderPrice(step) {
    const currentPrice = getOrderPrice();
    const nextPrice = Math.max(1, currentPrice + step);

    setOrderPrice(nextPrice);
    updateOrderTotalAmount();
    updateOrderPriceStepLabel();
}

// 수정 주문가격을 지정한 단위만큼 증감한다.
function stepEditOrderPrice(step) {
    const currentPrice = getEditOrderPrice();
    const nextPrice = Math.max(1, currentPrice + step);

    setEditOrderPrice(nextPrice);
}

// 한국 주식 가격대별 호가 단위를 계산한다.
function getOrderPriceStep(price) {
    const value = Number(price || orderState.currentPrice || 0);

    if (value < 2000) {
        return 1;
    }

    if (value < 5000) {
        return 5;
    }

    if (value < 20000) {
        return 10;
    }

    if (value < 50000) {
        return 50;
    }

    if (value < 200000) {
        return 100;
    }

    if (value < 500000) {
        return 500;
    }

    return 1000;
}

// 현재 주문가격 기준의 호가 단위를 숨김 텍스트로 갱신한다.
function updateOrderPriceStepLabel() {
    const stepLabel = document.getElementById('order-price-step-label');

    if (stepLabel) {
        stepLabel.textContent = `${formatOrderNumber(getOrderPriceStep(getOrderPrice()))}원 단위`;
    }
}

// 수정 주문가격 기준의 호가 단위 안내 문구를 갱신한다.
function updateEditOrderPriceStepLabel() {
    const stepLabel = document.getElementById('edit-order-price-step-label');

    if (stepLabel) {
        stepLabel.textContent = `${formatOrderNumber(getOrderPriceStep(getEditOrderPrice()))}원 단위`;
    }
}

// 특정 요소의 텍스트를 안전하게 변경한다.
function setOrderText(id, value) {
    const element = document.getElementById(id);

    if (element) {
        element.textContent = value;
    }
}

// 주문 모달 하단 메시지를 상태별로 표시한다.
function setOrderMessage(message, type = '') {
    const messageElement = document.getElementById('order-message');

    if (!messageElement) {
        return;
    }

    messageElement.textContent = message;
    messageElement.classList.remove('error', 'success');

    if (type) {
        messageElement.classList.add(type);
    }
}

// 주문 수정 모달 하단 메시지를 상태별로 표시한다.
function setEditOrderMessage(message, type = '') {
    const messageElement = document.getElementById('edit-order-message');

    if (!messageElement) {
        return;
    }

    messageElement.textContent = message;
    messageElement.classList.remove('error', 'success');

    if (type) {
        messageElement.classList.add(type);
    }
}

// 숫자를 한국어 천 단위 형식으로 변환한다.
function formatOrderNumber(value) {
    return Number(value || 0).toLocaleString('ko-KR');
}
