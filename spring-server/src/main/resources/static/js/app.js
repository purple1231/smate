// 💡 중요: 실제 사용 시 이 값을 사용자의 computerId로 변경해야 합니다.
// 예시에서는 하드코딩하지만, 실제로는 로그인 세션이나 로컬 저장소에서 가져와야 합니다.
const MY_COMPUTER_ID = "roy17-desktop";

document.getElementById("computerIdText").textContent = MY_COMPUTER_ID;

let weeklyChartInstance = null; // 메인 차트 인스턴스
let coUsageChartInstance = null; // 서브 차트 인스턴스

/**
 * 랜덤 색상 생성 (차트 시각화용)
 */
function getRandomColor(alpha = 0.5) {
    const r = Math.floor(Math.random() * 255);
    const g = Math.floor(Math.random() * 255);
    const b = Math.floor(Math.random() * 255);
    return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}

/**
 * 1. (메인) 주간 통계 데이터를 불러와 차트를 그립니다.
 */
async function fetchWeeklyStats() {
    try {
        const response = await fetch(`/api/stats/weekly?computerId=${MY_COMPUTER_ID}`);
        if (!response.ok) throw new Error("서버에서 주간 통계 로드 실패");

        const stats = await response.json(); // [{appName: "...", usageCount: 10}, ...]

        if (stats.length === 0) {
            console.warn("표시할 통계 데이터가 없습니다.");
            return;
        }

        // 1-1. 차트 데이터 준비
        const labels = stats.map(s => s.appName);
        const dataCounts = stats.map(s => s.usageCount);
        const backgroundColors = stats.map(() => getRandomColor(0.7));
        const borderColors = backgroundColors.map(color => color.replace('0.7', '1'));

        // 1-2. 차트 그리기
        const ctx = document.getElementById('weeklyChart').getContext('2d');

        if (weeklyChartInstance) {
            weeklyChartInstance.destroy(); // 기존 차트 파괴
        }

        weeklyChartInstance = new Chart(ctx, {
            type: 'bar', // 막대 그래프
            data: {
                labels: labels,
                datasets: [{
                    label: '앱 실행 횟수 (지난 7일)',
                    data: dataCounts,
                    backgroundColor: backgroundColors,
                    borderColor: borderColors,
                    borderWidth: 1
                }]
            },
            options: {
                scales: {
                    y: { beginAtZero: true }
                },
                // ⭐️ 중요: 클릭 이벤트 핸들러
                onClick: (event, elements) => {
                    if (elements.length > 0) {
                        const clickedElementIndex = elements[0].index;
                        const clickedAppName = labels[clickedElementIndex];

                        // 클릭된 앱의 연관 앱 통계 불러오기
                        fetchCoUsageStats(clickedAppName);
                    }
                }
            }
        });

    } catch (error) {
        console.error("fetchWeeklyStats 에러:", error);
    }
}

/**
 * 2. (서브) 특정 앱과 연관된 앱 통계를 불러와 차트를 그립니다.
 */
async function fetchCoUsageStats(baseAppName) {
    try {
        const response = await fetch(`/api/stats/co-usage?computerId=${MY_COMPUTER_ID}&appName=${encodeURIComponent(baseAppName)}`);
        if (!response.ok) throw new Error("서버에서 연관 통계 로드 실패");

        const stats = await response.json();
        const section = document.getElementById('coUsageSection');
        const title = document.getElementById('coUsageTitle');

        if (stats.length === 0) {
            title.textContent = `"${baseAppName}"(와)과 함께 사용된 다른 앱이 없습니다.`;
            section.classList.remove('hidden'); // 섹션은 보이되, 차트는 그리지 않음
            if (coUsageChartInstance) coUsageChartInstance.destroy(); // 기존 차트 파괴
            return;
        }

        // 2-1. 차트 데이터 준비
        const labels = stats.map(s => s.appName);
        const dataCounts = stats.map(s => s.usageCount);
        const backgroundColors = stats.map(() => getRandomColor(0.7));

        // 2-2. 차트 그리기
        title.textContent = `"${baseAppName}"(와)과 함께 사용된 앱 🤝`;
        section.classList.remove('hidden'); // 숨김 해제

        const ctx = document.getElementById('coUsageChart').getContext('2d');

        if (coUsageChartInstance) {
            coUsageChartInstance.destroy(); // 기존 차트 파괴
        }

        coUsageChartInstance = new Chart(ctx, {
            type: 'pie', // 파이 그래프 (혹은 'doughnut' 이나 'bar'로 변경 가능)
            data: {
                labels: labels,
                datasets: [{
                    label: '함께 사용한 횟수',
                    data: dataCounts,
                    backgroundColor: backgroundColors,
                }]
            },
            options: {
                responsive: true,
                plugins: {
                    legend: { position: 'top' }
                }
            }
        });

    } catch (error) {
        console.error("fetchCoUsageStats 에러:", error);
    }
}


// --- 페이지 로드 시 메인 차트 실행 ---
document.addEventListener('DOMContentLoaded', fetchWeeklyStats);