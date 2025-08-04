// 카카오택시 원격 제어 서버 (간소화 버전)
const WebSocket = require('ws');
const express = require('express');
const Jimp = require('jimp');
const Tesseract = require('tesseract.js');
const cors = require('cors');

const app = express();
const PORT = 8080;

// WebSocket 서버
const wss = new WebSocket.Server({ port: 8081 });

// 연결된 드라이버들
const drivers = new Map();

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
        this.lastImageBase64 = null;
        this.stats = {
            totalCalls: 0,
            acceptedCalls: 0,
            rejectedCalls: 0,
            earnings: 0,
            lastDetection: null
        };
        // 드라이버별 개별 필터 설정
        this.filterSettings = {
            minAmount: 5000,
            maxDistance: 3.0,
            preferredAreas: ['강남구', '서초구', '송파구'],
            autoAccept: true,
            priorityHigh: false,
            avoidTraffic: false
        };
    }

    async processImage(imageBase64) {
        try {
            console.log(`[${this.id}] 이미지 처리 시작`);
            
            // Base64 저장 (나중에 조회용)
            this.lastImageBase64 = imageBase64;
            
            // Base64를 버퍼로 변환
            const imageBuffer = Buffer.from(imageBase64, 'base64');
            this.lastImage = imageBuffer;
            
            // Jimp로 이미지 로드
            const image = await Jimp.read(imageBuffer);
            
            // 1. 노란 버튼 감지
            const yellowButton = await this.detectYellowButton(image);
            
            if (yellowButton) {
                console.log(`[${this.id}] 노란 버튼 감지: ${yellowButton.x}, ${yellowButton.y}`);
                
                this.stats.lastDetection = {
                    time: new Date().toISOString(),
                    position: yellowButton
                };
                
                // 2. OCR로 텍스트 추출
                const text = await this.performOCR(imageBuffer);
                console.log(`[${this.id}] OCR 결과: ${text.substring(0, 100)}...`);
                
                // 3. 필터링 로직
                const shouldAccept = this.filterCall(text);
                
                if (shouldAccept) {
                    // 4. 클릭 명령 전송
                    this.sendClickCommand(yellowButton.x, yellowButton.y);
                    this.stats.acceptedCalls++;
                    console.log(`[${this.id}] ✅ 콜 수락 - 위치: ${yellowButton.x}, ${yellowButton.y}`);
                } else {
                    this.stats.rejectedCalls++;
                    console.log(`[${this.id}] ❌ 콜 거절 - 필터링됨`);
                }
                
                this.stats.totalCalls++;
            }
        } catch (error) {
            console.error(`[${this.id}] 이미지 처리 오류:`, error.message);
        }
    }

    async detectYellowButton(image) {
        try {
            const width = image.bitmap.width;
            const height = image.bitmap.height;
            
            let yellowPixels = [];
            
            // 5픽셀 간격으로 스캔 (성능 최적화)
            for (let y = height * 0.3; y < height * 0.8; y += 5) {
                for (let x = 0; x < width; x += 5) {
                    const color = Jimp.intToRGBA(image.getPixelColor(x, y));
                    
                    // HSV로 변환
                    const hsv = rgbToHsv(color.r, color.g, color.b);
                    
                    // 노란색 범위 체크 (Hue: 45-65, Saturation: 40-100%, Value: 60-100%)
                    if (hsv.h >= 45 && hsv.h <= 65 && 
                        hsv.s >= 40 && hsv.s <= 100 && 
                        hsv.v >= 60 && hsv.v <= 100) {
                        yellowPixels.push({ x, y });
                    }
                }
            }
            
            // 노란 픽셀들의 중심점 계산
            if (yellowPixels.length > 100) {  // 최소 100개 픽셀
                const centerX = Math.round(yellowPixels.reduce((sum, p) => sum + p.x, 0) / yellowPixels.length);
                const centerY = Math.round(yellowPixels.reduce((sum, p) => sum + p.y, 0) / yellowPixels.length);
                
                return {
                    x: centerX,
                    y: centerY,
                    pixelCount: yellowPixels.length
                };
            }
        } catch (error) {
            console.error('노란 버튼 감지 실패:', error);
        }
        return null;
    }

    async performOCR(imageBuffer) {
        try {
            const worker = await Tesseract.createWorker('kor');
            const { data: { text } } = await worker.recognize(imageBuffer);
            await worker.terminate();
            return text;
        } catch (error) {
            console.error('OCR 실패:', error);
            return '';
        }
    }

    filterCall(text) {
        // 드라이버별 개별 필터 사용
        const filter = this.filterSettings;
        
        // 금액 추출 (예: "5,500원" 또는 "12,000원")
        const amountMatch = text.match(/(\d{1,2},?\d{3})원/);
        if (amountMatch) {
            const amount = parseInt(amountMatch[1].replace(',', ''));
            if (amount < filter.minAmount) {
                console.log(`[${this.id}] 금액 필터: ${amount}원 < ${filter.minAmount}원`);
                return false;
            }
            
            // 고액 우선 모드
            if (filter.priorityHigh && amount < 10000) {
                console.log(`[${this.id}] 고액 우선: ${amount}원 < 10,000원`);
                return false;
            }
            
            this.stats.earnings += amount;
        }

        // 거리 추출 (예: "2.5km")
        const distanceMatch = text.match(/(\d+\.?\d*)km/);
        if (distanceMatch) {
            const distance = parseFloat(distanceMatch[1]);
            if (distance > filter.maxDistance) {
                console.log(`[${this.id}] 거리 필터: ${distance}km > ${filter.maxDistance}km`);
                return false;
            }
        }

        // 선호 지역 체크
        const hasPreferredArea = filter.preferredAreas.some(area => 
            text.includes(area)
        );
        
        if (!hasPreferredArea && filter.preferredAreas.length > 0) {
            console.log(`[${this.id}] 지역 필터: 선호 지역 아님`);
            // 교통 체증 회피 모드가 켜져있으면 더 엄격하게
            if (filter.avoidTraffic) {
                return false;
            }
        }
        
        // 교통 체증 지역 체크
        if (filter.avoidTraffic) {
            const trafficAreas = ['강남역', '서울역', '고속터미널', '잠실역'];
            const hasTrafficArea = trafficAreas.some(area => text.includes(area));
            if (hasTrafficArea) {
                console.log(`[${this.id}] 교통 체증 지역 회피`);
                return false;
            }
        }

        // 자동 수락 설정 확인
        if (!filter.autoAccept) {
            console.log(`[${this.id}] 자동 수락 비활성화됨`);
            return false;
        }

        return true;  // 필터 통과
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

// RGB to HSV 변환 함수
function rgbToHsv(r, g, b) {
    r /= 255;
    g /= 255;
    b /= 255;
    
    const max = Math.max(r, g, b);
    const min = Math.min(r, g, b);
    const diff = max - min;
    
    let h = 0;
    let s = max === 0 ? 0 : diff / max;
    let v = max;
    
    if (diff !== 0) {
        if (max === r) {
            h = ((g - b) / diff + (g < b ? 6 : 0)) / 6;
        } else if (max === g) {
            h = ((b - r) / diff + 2) / 6;
        } else {
            h = ((r - g) / diff + 4) / 6;
        }
    }
    
    return {
        h: Math.round(h * 360),
        s: Math.round(s * 100),
        v: Math.round(v * 100)
    };
}

// WebSocket 연결 처리
wss.on('connection', (ws, req) => {
    const driverId = `driver_${Date.now()}`;
    const driver = new DriverConnection(ws, driverId);
    drivers.set(driverId, driver);
    
    console.log(`✅ [${driverId}] 드라이버 연결됨`);
    console.log(`현재 연결된 드라이버: ${drivers.size}명`);
    
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
                    
                case 'filter_settings':
                    // 드라이버별 개별 필터 설정 업데이트
                    if (data.settings) {
                        driver.filterSettings = {
                            minAmount: data.settings.minAmount || 5000,
                            maxDistance: data.settings.maxDistance || 3.0,
                            preferredAreas: data.settings.preferredAreas || [],
                            autoAccept: data.settings.autoAccept !== false,
                            priorityHigh: data.settings.priorityHigh || false,
                            avoidTraffic: data.settings.avoidTraffic || false
                        };
                        console.log(`[${driverId}] 필터 설정 업데이트:`, driver.filterSettings);
                    }
                    break;
                    
                case 'status':
                    console.log(`[${driverId}] 상태:`, data.status);
                    break;
                    
                case 'log':
                    console.log(`[${driverId}] 로그:`, data.message);
                    break;
                    
                case 'ping':
                    ws.send(JSON.stringify({ type: 'pong' }));
                    break;
            }
        } catch (error) {
            console.error(`[${driverId}] 메시지 처리 오류:`, error.message);
        }
    });
    
    // 연결 종료
    ws.on('close', () => {
        console.log(`❌ [${driverId}] 드라이버 연결 종료`);
        drivers.delete(driverId);
        console.log(`현재 연결된 드라이버: ${drivers.size}명`);
    });
    
    // 에러 처리
    ws.on('error', (error) => {
        console.error(`[${driverId}] WebSocket 오류:`, error.message);
    });
});

// Express 미들웨어
app.use(cors());
app.use(express.json({ limit: '50mb' }));
app.use(express.static('public'));

// 모니터링 API
app.get('/api/drivers', (req, res) => {
    const driverList = Array.from(drivers.values()).map(driver => ({
        id: driver.id,
        stats: driver.stats,
        filterSettings: driver.filterSettings,  // 개별 필터 설정 포함
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

// 설정 업데이트
app.post('/api/config', (req, res) => {
    const { minAmount, maxDistance, preferredAreas } = req.body;
    
    if (minAmount) FILTER_CONFIG.minAmount = minAmount;
    if (maxDistance) FILTER_CONFIG.maxDistance = maxDistance;
    if (preferredAreas) FILTER_CONFIG.preferredAreas = preferredAreas;
    
    // 모든 드라이버에게 새 설정 전송
    drivers.forEach(driver => {
        driver.sendCommand({
            action: 'config',
            config: FILTER_CONFIG
        });
    });
    
    res.json({ success: true, config: FILTER_CONFIG });
});

// 통계 API
app.get('/api/stats', (req, res) => {
    let totalStats = {
        totalDrivers: drivers.size,
        totalCalls: 0,
        acceptedCalls: 0,
        rejectedCalls: 0,
        totalEarnings: 0
    };
    
    drivers.forEach(driver => {
        totalStats.totalCalls += driver.stats.totalCalls;
        totalStats.acceptedCalls += driver.stats.acceptedCalls;
        totalStats.rejectedCalls += driver.stats.rejectedCalls;
        totalStats.totalEarnings += driver.stats.earnings;
    });
    
    res.json(totalStats);
});

// 서버 시작
app.listen(PORT, () => {
    console.log('===================================');
    console.log('🚀 카카오택시 원격 제어 서버 시작');
    console.log('===================================');
    console.log(`📡 HTTP 서버: http://localhost:${PORT}`);
    console.log(`🔌 WebSocket: ws://localhost:8081`);
    console.log('===================================');
    console.log('📋 현재 설정:');
    console.log(`  - 최소 금액: ${FILTER_CONFIG.minAmount}원`);
    console.log(`  - 최대 거리: ${FILTER_CONFIG.maxDistance}km`);
    console.log(`  - 선호 지역: ${FILTER_CONFIG.preferredAreas.join(', ')}`);
    console.log('===================================');
    console.log('⏳ 드라이버 연결 대기중...');
});

// Graceful shutdown
process.on('SIGINT', () => {
    console.log('\n🛑 서버 종료중...');
    
    // 모든 연결 종료
    drivers.forEach((driver) => {
        driver.ws.close();
    });
    
    wss.close(() => {
        console.log('✅ WebSocket 서버 종료됨');
        process.exit(0);
    });
});