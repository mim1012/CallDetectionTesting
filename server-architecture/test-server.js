// 카카오택시 콜 수락 테스트 서버 (최소 기능)
const WebSocket = require('ws');
const express = require('express');
const Jimp = require('jimp');

const app = express();
const wss = new WebSocket.Server({ port: 8081 });

let connectedDrivers = 0;

// WebSocket 연결 처리
wss.on('connection', (ws) => {
    connectedDrivers++;
    const driverId = `driver_${Date.now()}`;
    
    console.log(`\n✅ 드라이버 연결: ${driverId}`);
    console.log(`현재 연결: ${connectedDrivers}명`);
    
    // 초기화 메시지
    ws.send(JSON.stringify({
        action: 'init',
        driverId: driverId
    }));
    
    ws.on('message', async (message) => {
        try {
            const data = JSON.parse(message);
            
            if (data.type === 'screenshot') {
                console.log(`📸 스크린샷 수신 (${driverId})`);
                
                // Base64를 이미지로 변환
                const imageBuffer = Buffer.from(data.image, 'base64');
                const image = await Jimp.read(imageBuffer);
                
                // 노란 버튼 찾기 (단순 스캔)
                const button = await findYellowButton(image);
                
                if (button) {
                    console.log(`\n🎯 노란 버튼 발견!`);
                    console.log(`   위치: (${button.x}, ${button.y})`);
                    console.log(`   크기: ${button.pixelCount} 픽셀`);
                    
                    // 클릭 명령 전송
                    const clickCommand = {
                        action: 'click',
                        x: button.x,
                        y: button.y,
                        timestamp: Date.now()
                    };
                    
                    ws.send(JSON.stringify(clickCommand));
                    console.log(`\n👆 클릭 명령 전송: (${button.x}, ${button.y})`);
                }
            } else if (data.type === 'log') {
                console.log(`📝 [${driverId}] ${data.message}`);
            }
        } catch (error) {
            console.error('❌ 오류:', error.message);
        }
    });
    
    ws.on('close', () => {
        connectedDrivers--;
        console.log(`\n❌ 드라이버 연결 종료: ${driverId}`);
        console.log(`현재 연결: ${connectedDrivers}명`);
    });
});

// 노란 버튼 감지 (간단 버전)
async function findYellowButton(image) {
    const width = image.bitmap.width;
    const height = image.bitmap.height;
    
    let yellowPixels = [];
    let totalYellow = 0;
    
    // 화면 하단 30% 영역만 스캔 (버튼이 주로 있는 위치)
    const startY = Math.floor(height * 0.7);
    
    // 10픽셀 간격으로 빠르게 스캔
    for (let y = startY; y < height; y += 10) {
        for (let x = 0; x < width; x += 10) {
            const color = Jimp.intToRGBA(image.getPixelColor(x, y));
            
            // 노란색 판정 (간단한 RGB 체크)
            // R: 200-255, G: 180-255, B: 0-100
            if (color.r > 200 && color.g > 180 && color.b < 100) {
                yellowPixels.push({ x, y });
                totalYellow++;
            }
        }
    }
    
    console.log(`   노란 픽셀 수: ${totalYellow}`);
    
    // 노란 픽셀이 50개 이상이면 버튼으로 판단
    if (yellowPixels.length > 50) {
        // 중심점 계산
        const centerX = Math.round(
            yellowPixels.reduce((sum, p) => sum + p.x, 0) / yellowPixels.length
        );
        const centerY = Math.round(
            yellowPixels.reduce((sum, p) => sum + p.y, 0) / yellowPixels.length
        );
        
        return {
            x: centerX,
            y: centerY,
            pixelCount: yellowPixels.length
        };
    }
    
    return null;
}

// 간단한 상태 API
app.get('/status', (req, res) => {
    res.json({
        connected: connectedDrivers,
        server: 'running',
        time: new Date().toISOString()
    });
});

// HTTP 서버 시작
app.listen(8080, () => {
    console.log('=====================================');
    console.log('🚀 카카오택시 테스트 서버 시작');
    console.log('=====================================');
    console.log('📡 HTTP: http://localhost:8080/status');
    console.log('🔌 WebSocket: ws://localhost:8081');
    console.log('=====================================');
    console.log('테스트 순서:');
    console.log('1. 안드로이드 앱 실행');
    console.log('2. 원격 제어 시작');
    console.log('3. 카카오택시 앱에서 콜 대기');
    console.log('4. 노란 버튼 감지시 자동 클릭');
    console.log('=====================================');
    console.log('⏳ 드라이버 연결 대기중...\n');
});

console.log('Ctrl+C로 서버 종료');