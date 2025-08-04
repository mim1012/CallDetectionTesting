// 카카오택시 원격 제어 서버
const WebSocket = require('ws');
const express = require('express');
const sharp = require('sharp');
const Tesseract = require('tesseract.js');
const cv = require('opencv4nodejs');

const app = express();
const PORT = 8080;

// WebSocket 서버
const wss = new WebSocket.Server({ port: 8081 });

// 연결된 드라이버들
const drivers = new Map();

// 색상 범위 (노란 버튼)
const YELLOW_HSV_LOWER = [20, 100, 100];
const YELLOW_HSV_UPPER = [30, 255, 255];

// 필터 설정
const FILTER_CONFIG = {
    minAmount: 5000,      // 최소 금액
    maxDistance: 3.0,     // 최대 거리 (km)
    preferredAreas: ['강남', '서초', '송파']  // 선호 지역
};

class DriverConnection {
    constructor(ws, id) {
        this.ws = ws;
        this.id = id;
        this.lastImage = null;
        this.stats = {
            totalCalls: 0,
            acceptedCalls: 0,
            rejectedCalls: 0,
            earnings: 0
        };
    }

    async processImage(imageBase64) {
        try {
            // Base64 디코딩
            const imageBuffer = Buffer.from(imageBase64, 'base64');
            this.lastImage = imageBuffer;

            // OpenCV로 이미지 로드
            const img = cv.imdecode(imageBuffer);
            
            // 1. 노란 버튼 감지
            const yellowButton = await this.detectYellowButton(img);
            
            if (yellowButton) {
                console.log(`[${this.id}] 노란 버튼 감지: ${yellowButton.x}, ${yellowButton.y}`);
                
                // 2. OCR로 텍스트 추출
                const text = await this.performOCR(imageBuffer);
                
                // 3. 필터링 로직
                const shouldAccept = this.filterCall(text);
                
                if (shouldAccept) {
                    // 4. 클릭 명령 전송
                    this.sendClickCommand(yellowButton.x, yellowButton.y);
                    this.stats.acceptedCalls++;
                    console.log(`[${this.id}] 콜 수락 - 위치: ${yellowButton.x}, ${yellowButton.y}`);
                } else {
                    this.stats.rejectedCalls++;
                    console.log(`[${this.id}] 콜 거절 - 필터링됨`);
                }
                
                this.stats.totalCalls++;
            }
        } catch (error) {
            console.error(`[${this.id}] 이미지 처리 오류:`, error);
        }
    }

    async detectYellowButton(img) {
        try {
            // HSV 변환
            const hsv = img.cvtColor(cv.COLOR_BGR2HSV);
            
            // 노란색 마스크
            const lower = new cv.Vec3(...YELLOW_HSV_LOWER);
            const upper = new cv.Vec3(...YELLOW_HSV_UPPER);
            const mask = hsv.inRange(lower, upper);
            
            // 컨투어 찾기
            const contours = mask.findContours(cv.RETR_EXTERNAL, cv.CHAIN_APPROX_SIMPLE);
            
            if (contours.length > 0) {
                // 가장 큰 컨투어 찾기
                let maxArea = 0;
                let maxContour = null;
                
                for (const contour of contours) {
                    const area = contour.area;
                    if (area > maxArea && area > 1000) {  // 최소 크기 필터
                        maxArea = area;
                        maxContour = contour;
                    }
                }
                
                if (maxContour) {
                    const rect = maxContour.boundingRect();
                    return {
                        x: rect.x + rect.width / 2,
                        y: rect.y + rect.height / 2,
                        width: rect.width,
                        height: rect.height
                    };
                }
            }
        } catch (error) {
            console.error('노란 버튼 감지 실패:', error);
        }
        return null;
    }

    async performOCR(imageBuffer) {
        try {
            const { data: { text } } = await Tesseract.recognize(
                imageBuffer,
                'kor',  // 한국어
                {
                    logger: m => {} // 로그 숨김
                }
            );
            return text;
        } catch (error) {
            console.error('OCR 실패:', error);
            return '';
        }
    }

    filterCall(text) {
        // 금액 추출 (예: "5,500원")
        const amountMatch = text.match(/(\d{1,2},?\d{3})원/);
        if (amountMatch) {
            const amount = parseInt(amountMatch[1].replace(',', ''));
            if (amount < FILTER_CONFIG.minAmount) {
                console.log(`금액 필터: ${amount}원 < ${FILTER_CONFIG.minAmount}원`);
                return false;
            }
        }

        // 거리 추출 (예: "2.5km")
        const distanceMatch = text.match(/(\d+\.?\d*)km/);
        if (distanceMatch) {
            const distance = parseFloat(distanceMatch[1]);
            if (distance > FILTER_CONFIG.maxDistance) {
                console.log(`거리 필터: ${distance}km > ${FILTER_CONFIG.maxDistance}km`);
                return false;
            }
        }

        // 선호 지역 체크
        const hasPreferredArea = FILTER_CONFIG.preferredAreas.some(area => 
            text.includes(area)
        );

        return true;  // 기본적으로 수락
    }

    sendClickCommand(x, y) {
        const command = {
            action: 'click',
            x: Math.round(x),
            y: Math.round(y),
            timestamp: Date.now()
        };
        
        this.ws.send(JSON.stringify(command));
    }

    sendCommand(command) {
        this.ws.send(JSON.stringify(command));
    }
}

// WebSocket 연결 처리
wss.on('connection', (ws, req) => {
    const driverId = `driver_${Date.now()}`;
    const driver = new DriverConnection(ws, driverId);
    drivers.set(driverId, driver);
    
    console.log(`[${driverId}] 드라이버 연결됨`);
    
    // 초기 설정 전송
    ws.send(JSON.stringify({
        action: 'init',
        driverId: driverId,
        config: FILTER_CONFIG
    }));
    
    // 메시지 수신
    ws.on('message', async (message) => {
        try {
            const data = JSON.parse(message);
            
            switch(data.type) {
                case 'screenshot':
                    await driver.processImage(data.image);
                    break;
                    
                case 'status':
                    console.log(`[${driverId}] 상태:`, data.status);
                    break;
                    
                case 'log':
                    console.log(`[${driverId}] 로그:`, data.message);
                    break;
            }
        } catch (error) {
            console.error(`[${driverId}] 메시지 처리 오류:`, error);
        }
    });
    
    // 연결 종료
    ws.on('close', () => {
        console.log(`[${driverId}] 드라이버 연결 종료`);
        drivers.delete(driverId);
    });
    
    // 에러 처리
    ws.on('error', (error) => {
        console.error(`[${driverId}] WebSocket 오류:`, error);
    });
});

// 모니터링 API
app.use(express.static('public'));
app.use(express.json());

app.get('/api/drivers', (req, res) => {
    const driverList = Array.from(drivers.values()).map(driver => ({
        id: driver.id,
        stats: driver.stats,
        connected: driver.ws.readyState === WebSocket.OPEN
    }));
    res.json(driverList);
});

app.post('/api/command', (req, res) => {
    const { driverId, command } = req.body;
    const driver = drivers.get(driverId);
    
    if (driver) {
        driver.sendCommand(command);
        res.json({ success: true });
    } else {
        res.status(404).json({ error: 'Driver not found' });
    }
});

app.get('/api/screenshot/:driverId', (req, res) => {
    const driver = drivers.get(req.params.driverId);
    
    if (driver && driver.lastImage) {
        res.type('image/jpeg');
        res.send(driver.lastImage);
    } else {
        res.status(404).send('No screenshot available');
    }
});

// 서버 시작
app.listen(PORT, () => {
    console.log(`HTTP 서버 시작: http://localhost:${PORT}`);
    console.log(`WebSocket 서버 시작: ws://localhost:8081`);
    console.log('드라이버 연결 대기중...');
});

// Graceful shutdown
process.on('SIGINT', () => {
    console.log('\n서버 종료중...');
    
    // 모든 연결 종료
    drivers.forEach((driver) => {
        driver.ws.close();
    });
    
    wss.close(() => {
        console.log('WebSocket 서버 종료됨');
        process.exit(0);
    });
});