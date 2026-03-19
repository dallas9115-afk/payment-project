/**
 * API 핸들러 - API 요청을 위한 공통 함수
 * API 패널에 요청/응답을 표시합니다
 */

/**
 * API 요청을 수행하고 패널에 표시
 * @param {string} endpointKey - 설정의 엔드포인트 키
 * @param {Object} options - Fetch 옵션 (method, body, params, returnHeaders)
 *   - method: HTTP 메서드 (생략 시 YAML 설정에서 자동으로 가져옴)
 * @returns {Promise<Object>} 응답 데이터 (returnHeaders가 true면 { data, headers })
 */
async function makeApiRequest(endpointKey, options = {}) {
    const {
        body = null,
        params = {},
        pathParams = {},
        returnHeaders = false
    } = options;

    try {
        // 설정에서 엔드포인트 계약 가져오기
        const config = await getConfig();
        const endpointContract = config.api.endpoints[endpointKey];

        if (!endpointContract) {
            throw new Error(`엔드포인트 '${endpointKey}'를 설정에서 찾을 수 없습니다`);
        }

        // method를 YAML 설정에서 자동으로 가져옴 (options에서 override 가능)
        const method = options.method || endpointContract.method || 'GET';

        // URL 생성
        const url = await buildApiUrl(endpointKey, pathParams);

        // 엔드포인트 표시 업데이트
        updateEndpointDisplay(method, url);

        // 요청 본문 표시 업데이트
        if (body) {
            updateRequestDisplay(body);
        }

        // 로딩 표시
        showLoading();

        // Make request
        const fetchOptions = {
            method,
            headers: {
                'Content-Type': 'application/json'
            }
        };

        // JWT 토큰이 있으면 Authorization 헤더 추가
        const token = typeof getToken === 'function' ? getToken() : null;
        if (token) {
            fetchOptions.headers['Authorization'] = `Bearer ${token}`;
        }

        if (body && method !== 'GET') {
            fetchOptions.body = JSON.stringify(body);
        }

        const response = await fetch(url, fetchOptions);

        // 401 Unauthorized 응답 시 로그인 페이지로 이동 (쿠키 삭제)
        if (response.status === 401) {
            if (!window.location.pathname.includes('/pages/login')) {
                if (typeof removeToken === 'function') removeToken();
                window.location.href = '/pages/login';
                return;
            }
        }
        const text = await response.text();
        const data = text ? JSON.parse(text) : {
            error: '응답 본문 없음',
            status: response.status
        };

        // 응답 표시 및 에러 처리
        if (!response.ok) {
            displayError(data);
            // HTTP 에러 발생 시 예외 throw
            const errorMessage = data.message || data.error || `HTTP ${response.status}: ${response.statusText}`;
            throw new Error(errorMessage);
        }

        displaySuccess(data);

        // returnHeaders 옵션이 true면 헤더도 함께 반환
        if (returnHeaders) {
            return {
                data,
                headers: Object.fromEntries(response.headers.entries())
            };
        }

        return data;
    } catch (error) {
        displayError({
            error: error.message,
            stack: error.stack
        });
        throw error;
    }
}

/**
 * API 패널의 엔드포인트 표시 업데이트
 */
function updateEndpointDisplay(method, url) {
    const endpointBadge = document.getElementById('current-endpoint');
    if (endpointBadge) {
        endpointBadge.innerHTML = `
            <strong>${method}</strong> ${url}
        `;
    }
}

/**
 * 요청 본문 표시 업데이트
 */
function updateRequestDisplay(body) {
    const requestTextarea = document.getElementById('request-body');
    if (requestTextarea) {
        requestTextarea.value = JSON.stringify(body, null, 2);
    }
}

/**
 * 로딩 상태 표시
 */
function showLoading() {
    const responseBox = document.getElementById('response-output');
    if (responseBox) {
        responseBox.className = 'response-box';
        responseBox.textContent = '⏳ 로딩 중...';
    }
}

/**
 * 성공 응답 표시
 */
function displaySuccess(data) {
    const responseBox = document.getElementById('response-output');
    if (responseBox) {
        responseBox.className = 'response-box response-success';
        responseBox.textContent = JSON.stringify(data, null, 2);
    }
}

/**
 * 오류 응답 표시
 */
function displayError(error) {
    const responseBox = document.getElementById('response-output');
    if (responseBox) {
        responseBox.className = 'response-box response-error';
        responseBox.textContent = JSON.stringify(error, null, 2);
    }
}

/**
 * 텍스트 영역에서 커스텀 요청 전송
 */
async function sendCustomRequest() {
    const endpointInput = document.getElementById('custom-endpoint');
    const methodSelect = document.getElementById('request-method');
    const bodyTextarea = document.getElementById('request-body');

    if (!endpointInput || !bodyTextarea) {
        console.error('Required elements not found');
        return;
    }

    const url = endpointInput.value.trim();
    const method = methodSelect ? methodSelect.value : 'POST';
    let body = null;

    try {
        const bodyText = bodyTextarea.value.trim();
        if (bodyText && method !== 'GET') {
            body = JSON.parse(bodyText);
        }
    } catch (e) {
        displayError({ error: 'Invalid JSON in request body', details: e.message });
        return;
    }

    try {
        updateEndpointDisplay(method, url);
        showLoading();

        const fetchOptions = {
            method,
            headers: {
                'Content-Type': 'application/json'
            }
        };

        if (body && method !== 'GET') {
            fetchOptions.body = JSON.stringify(body);
        }

        const response = await fetch(url, fetchOptions);
        const data = await response.json();

        if (response.ok) {
            displaySuccess(data);
        } else {
            displayError(data);
        }
    } catch (error) {
        displayError({
            error: error.message,
            stack: error.stack
        });
    }
}

/**
 * 응답 패널 초기화
 */
function clearResponse() {
    const responseBox = document.getElementById('response-output');
    if (responseBox) {
        responseBox.className = 'response-box';
        responseBox.textContent = '';
    }
}

/**
 * 헬퍼: 통화 형식 지정
 */
function formatCurrency(amount, currency = 'KRW') {
    return new Intl.NumberFormat('ko-KR', {
        style: 'currency',
        currency: currency
    }).format(amount);
}

/**
 * 헬퍼: 알림 표시
 */
function showNotification(message, type = 'info') {
    const notification = document.createElement('div');
    notification.className = `alert alert-${type}`;
    notification.textContent = message;
    notification.style.position = 'fixed';
    notification.style.top = '100px';
    notification.style.right = '20px';
    notification.style.zIndex = '9999';
    notification.style.minWidth = '300px';

    document.body.appendChild(notification);

    setTimeout(() => {
        notification.remove();
    }, 3000);
}
