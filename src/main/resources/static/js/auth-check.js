/**
 * 인증 체크 스크립트
 * JWT 토큰 확인 및 로그아웃 처리
 */

/**
 * 로그인 여부 확인
 * JWT 토큰이 없으면 로그인 페이지로 리다이렉트
 */
function checkAuthentication() {
    // 쿠키에서 토큰 확인
    const token = typeof getToken === 'function' ? getToken() : null;
    const currentPath = window.location.pathname;

    // 로그인/회원가입 페이지는 체크 제외
    if (currentPath === '/pages/login' || currentPath === '/pages/register') {
        return;
    }

    // 토큰이 없으면 로그인 페이지로 이동
    if (!token) {
        window.location.href = '/pages/login';
        return;
    }

    // 사용자 정보 표시
    displayUserInfo();
}

/**
 * 네비게이션 바에 사용자 정보 표시 (수정됨)
 */
function displayUserInfo() {
    const email = typeof getEmailFromToken === 'function' ? getEmailFromToken() : null;
    const userInfoContainer = document.getElementById('user-info-container');
    const userEmailDisplay = document.getElementById('user-email-display');

    if (email && userInfoContainer && userEmailDisplay) {
        // 이메일을 세팅하고 영역을 보이게 만듦
        userEmailDisplay.innerHTML = `👤 ${email}`;
        userInfoContainer.style.display = 'flex';
    } else if (userInfoContainer) {
        // 로그인이 안 되어 있으면 숨김
        userInfoContainer.style.display = 'none';
    }
}

/**
 * 로그아웃 처리
 */
function handleLogout() {
    // 쿠키에서 토큰 제거
    if (typeof removeToken === 'function') removeToken();

    // 로그인 페이지로 이동
    window.location.href = '/pages/login';
}

/**
 * OAuth2 소셜 로그인 콜백 처리
 * 성공 시 /?token={JWT} 형태로 리다이렉트되므로, URL에서 토큰을 추출하여 쿠키에 저장
 */
function handleOAuth2Callback() {
    const urlParams = new URLSearchParams(window.location.search);
    const token = urlParams.get('token');

    if (token) {
        // 토큰 저장
        if (typeof saveToken === 'function') saveToken(token);

        // URL에서 token 파라미터 제거 (히스토리 교체)
        const cleanUrl = window.location.origin + window.location.pathname;
        window.history.replaceState({}, document.title, cleanUrl);
    }
}

// 페이지 로드 시 OAuth2 콜백 처리 후 인증 체크
document.addEventListener('DOMContentLoaded', function() {
    handleOAuth2Callback();
    checkAuthentication();
});
