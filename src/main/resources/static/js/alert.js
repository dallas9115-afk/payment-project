/**
 * 사용법: showAlert('메시지', 'success' | 'error' | 'info', '타이틀 직접 지정') <- 타이틀 작성은 선택 사항입니다.
 */

const ALERT_CONFIG = {
    success: {
        title: '완료', // 성공
        icon: `<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                 <polyline points="20 6 9 17 4 12"/>
               </svg>`
    },
    error: {
        title: '오류', // 오류
        icon: `<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                 <line x1="18" y1="6" x2="6" y2="18"/>
                 <line x1="6" y1="6" x2="18" y2="18"/>
               </svg>`
    },
    info: {
        title: 'INFO', // 정보
        icon: `<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                 <circle cx="12" cy="12" r="10"/>
                 <line x1="12" y1="8" x2="12" y2="12"/>
                 <line x1="12" y1="16" x2="12.01" y2="16"/>
               </svg>`
    }
};

function showAlert(message, type = 'info', customTitle = null) {
    let container = document.getElementById('custom-alert-container');
    if (!container) {
        container = document.createElement('div');
        container.id = 'custom-alert-container';
        container.className = 'custom-alert-container';
        document.body.appendChild(container);
    }

    const config = ALERT_CONFIG[type] || ALERT_CONFIG.info;
    const title = customTitle || config.title;

    // Alert 박스 생성
    const alertEl = document.createElement('div');
    alertEl.className = `custom-alert ${type}`;
    alertEl.innerHTML = `
        <div class="custom-alert-icon">
            ${config.icon}
        </div>
        <div class="custom-alert-content">
            <div class="custom-alert-title">${title}</div>
            <div class="custom-alert-message">${message}</div>
        </div>
    `;

    container.appendChild(alertEl);

    // 페이드 아웃
    setTimeout(() => {
        alertEl.classList.add('fade-out');
        setTimeout(() => {
            alertEl.remove();
        }, 500);
    }, 2000);
}