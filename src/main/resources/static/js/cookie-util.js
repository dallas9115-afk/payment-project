/**
 * 쿠키 유틸리티
 * JWT 토큰을 쿠키에 저장/조회/삭제
 */

/**
 * 쿠키 설정
 * @param {string} name - 쿠키 이름
 * @param {string} value - 쿠키 값
 * @param {number} days - 만료 일수 (기본: 1일)
 */
function setCookie(name, value, days = 1) {
    const expires = new Date();
    expires.setTime(expires.getTime() + days * 24 * 60 * 60 * 1000);

    // SameSite=Strict: CSRF 공격 방어
    // Secure는 HTTPS에서만 작동 (개발 환경에서는 제외)
    document.cookie = `${name}=${value}; expires=${expires.toUTCString()}; path=/; SameSite=Strict`;
}

/**
 * 쿠키 조회
 * @param {string} name - 쿠키 이름
 * @returns {string|null} 쿠키 값 또는 null
 */
function getCookie(name) {
    const value = `; ${document.cookie}`;
    const parts = value.split(`; ${name}=`);

    if (parts.length === 2) {
        return parts.pop().split(';').shift();
    }

    return null;
}

/**
 * 쿠키 삭제
 * @param {string} name - 쿠키 이름
 */
function deleteCookie(name) {
    document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;`;
}

/**
 * JWT 토큰 저장 (쿠키)
 * @param {string} token - JWT 토큰
 */
function saveToken(token) {
    setCookie('jwt_token', token, 1);  // 1일 유효
}

/**
 * JWT 토큰 조회 (쿠키)
 * @returns {string|null} JWT 토큰 또는 null
 */
function getToken() {
    return getCookie('jwt_token');
}

/**
 * JWT 토큰 삭제 (쿠키)
 */
function removeToken() {
    deleteCookie('jwt_token');
}

/**
 * JWT 토큰 디코딩 (Payload 추출)
 * @param {string} token - JWT 토큰
 * @returns {object|null} 디코딩된 payload 또는 null
 */
function decodeJWT(token) {
    try {
        const parts = token.split('.');
        if (parts.length !== 3) {
            return null;
        }

        // Payload 부분 (두 번째 부분)
        const payload = parts[1];

        // Base64 디코딩 (URL-safe Base64 처리)
        const decoded = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));

        return JSON.parse(decoded);
    } catch (error) {
        console.error('JWT 디코딩 실패:', error);
        return null;
    }
}

/**
 * JWT 토큰에서 이메일 추출
 * @returns {string|null} 이메일 또는 null
 */
function getEmailFromToken() {
    const token = getToken();
    if (!token) return null;

    const payload = decodeJWT(token);
    // 'sub' 클레임에 이메일이 저장되어 있음
    return payload ? payload.email : null;
}
