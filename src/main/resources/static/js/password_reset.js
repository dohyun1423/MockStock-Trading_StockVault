// 비밀번호 재설정 메일 요청과 일회용 토큰을 이용한 새 비밀번호 설정을 처리한다.
document.addEventListener('DOMContentLoaded', () => {
    bindPasswordResetRequestForm();
    bindPasswordResetConfirmForm();
});

// 비밀번호 재설정 메일 요청 form을 API 호출 함수에 연결한다.
function bindPasswordResetRequestForm() {
    const form = document.getElementById('forgot-password-form');

    if (!form) {
        return;
    }

    form.addEventListener('submit', requestPasswordReset);
}

// 새 비밀번호 설정 form을 API 호출 함수에 연결하고 URL 토큰을 확인한다.
function bindPasswordResetConfirmForm() {
    const form = document.getElementById('reset-password-form');

    if (!form) {
        return;
    }

    const token = new URLSearchParams(window.location.search).get('token');

    if (!token) {
        setResetMessage('비밀번호 재설정 링크가 올바르지 않습니다.', true);
        form.querySelector('button[type="submit"]').disabled = true;
        return;
    }

    form.dataset.token = token;
    form.addEventListener('submit', confirmPasswordReset);
}

// 입력한 이메일로 비밀번호 재설정 링크 발송을 요청한다.
async function requestPasswordReset(event) {
    event.preventDefault();

    const form = event.currentTarget;
    const email = document.getElementById('reset-email')?.value.trim();
    const button = form.querySelector('button[type="submit"]');

    setResetLoading(button, true);
    setResetMessage('');

    try {
        const response = await fetch('/api/users/password-reset/request', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({email})
        });
        const data = await readResponse(response);

        if (!response.ok) {
            setResetMessage(data.message || '재설정 요청을 처리하지 못했습니다.', true);
            return;
        }

        setResetMessage(data.message);
    } catch (error) {
        console.error(error);
        setResetMessage('서버에 연결할 수 없습니다.', true);
    } finally {
        setResetLoading(button, false);
    }
}

// URL의 일회용 토큰과 입력한 새 비밀번호로 재설정을 완료한다.
async function confirmPasswordReset(event) {
    event.preventDefault();

    const form = event.currentTarget;
    const token = form.dataset.token;
    const newPassword = document.getElementById('new-password')?.value || '';
    const confirmPassword = document.getElementById('confirm-password')?.value || '';
    const button = form.querySelector('button[type="submit"]');

    if (newPassword !== confirmPassword) {
        setResetMessage('새 비밀번호가 서로 일치하지 않습니다.', true);
        return;
    }

    setResetLoading(button, true);
    setResetMessage('');

    try {
        const response = await fetch('/api/users/password-reset/confirm', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({token, newPassword})
        });
        const data = await readResponse(response);

        if (!response.ok) {
            setResetMessage(data.message || '비밀번호를 변경하지 못했습니다.', true);
            return;
        }

        localStorage.removeItem('accessToken');
        setResetMessage(data.message);
        form.reset();
        button.dataset.completed = 'true';
        button.textContent = '변경 완료';
        button.disabled = true;
    } catch (error) {
        console.error(error);
        setResetMessage('서버에 연결할 수 없습니다.', true);
    } finally {
        if (button.dataset.completed !== 'true') {
            setResetLoading(button, false);
        }
    }
}

// 비밀번호 재설정 API의 JSON 응답을 안전하게 읽는다.
async function readResponse(response) {
    try {
        return await response.json();
    } catch (error) {
        return {};
    }
}

// 재설정 요청 중 버튼의 비활성화와 표시 문구를 전환한다.
function setResetLoading(button, loading) {
    if (!button) {
        return;
    }

    if (!button.dataset.defaultText) {
        button.dataset.defaultText = button.textContent;
    }

    button.disabled = loading;
    button.textContent = loading ? '처리 중...' : button.dataset.defaultText || button.textContent;
}

// 비밀번호 재설정 처리 결과를 성공 또는 오류 스타일로 표시한다.
function setResetMessage(message, isError = false) {
    const messageElement = document.getElementById('reset-message');

    if (!messageElement) {
        return;
    }

    messageElement.textContent = message;
    messageElement.classList.toggle('error', isError);
}

// 비밀번호 재설정 입력값의 표시 여부를 전환한다.
function toggleResetPasswordVisibility(inputId, button) {
    const input = document.getElementById(inputId);

    if (!input || !button) {
        return;
    }

    const shouldShow = input.type === 'password';
    input.type = shouldShow ? 'text' : 'password';
    button.classList.toggle('active', shouldShow);
    button.setAttribute('aria-label', shouldShow ? '비밀번호 숨기기' : '비밀번호 표시');
}
