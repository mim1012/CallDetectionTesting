import subprocess
import threading
import time
import cv2
import numpy as np
from flask import Flask, render_template, jsonify
import adbutils

"""
다중 기사 중앙 제어 시스템
PC 한 대로 3명의 기사 폰 동시 제어
"""

class MultiDriverController:
    def __init__(self):
        self.adb = adbutils.AdbClient(host="127.0.0.1", port=5037)
        self.devices = {}
        self.app = Flask(__name__)
        self.setup_routes()
        
    def scan_devices(self):
        """연결된 모든 디바이스 감지"""
        devices = self.adb.device_list()
        for i, device in enumerate(devices):
            serial = device.serial
            self.devices[serial] = {
                'index': i,
                'serial': serial,
                'name': f"기사{i+1}",
                'window_port': 5555 + i,  # scrcpy 포트
                'control_port': 27183 + i,  # 제어 포트
                'status': 'idle',
                'last_click': None
            }
        print(f"✅ {len(self.devices)}개 디바이스 발견")
        return self.devices
    
    def start_scrcpy_for_device(self, serial, window_title, port):
        """각 디바이스별 scrcpy 실행 (화면 미러링)"""
        # FLAG_SECURE 우회 옵션 포함
        cmd = [
            'scrcpy',
            '-s', serial,
            '--window-title', window_title,
            '--window-x', str(port % 3 * 640),  # 화면 위치
            '--window-y', '0',
            '--window-width', '360',
            '--window-height', '800',
            '--max-size', '720',
            '--bit-rate', '2M',
            '--max-fps', '15',
            '--no-audio',
            '--stay-awake',
            '--turn-screen-off',  # 폰 화면 끄기 (배터리 절약)
            '--render-driver', 'software',  # FLAG_SECURE 우회 시도
            '--port', str(port)
        ]
        
        process = subprocess.Popen(cmd)
        print(f"📱 {window_title} scrcpy 시작 (포트: {port})")
        return process
    
    def capture_screen(self, serial):
        """화면 캡처 (FLAG_SECURE 우회)"""
        device = self.adb.device(serial)
        
        # 방법 1: screencap
        try:
            png_bytes = device.shell("screencap -p", encoding=None)
            if png_bytes:
                nparr = np.frombuffer(png_bytes, np.uint8)
                img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
                return img
        except:
            pass
            
        # 방법 2: scrcpy 스크린샷
        try:
            # scrcpy --no-display로 캡처만
            result = subprocess.run(
                f"scrcpy -s {serial} --no-display --record=temp_{serial}.mp4",
                shell=True, capture_output=True, timeout=1
            )
        except:
            pass
            
        return None
    
    def detect_yellow_button(self, image):
        """노란 버튼 감지"""
        if image is None:
            return None
            
        # HSV 변환
        hsv = cv2.cvtColor(image, cv2.COLOR_BGR2HSV)
        
        # 카카오 노란색 범위
        lower_yellow = np.array([20, 100, 100])
        upper_yellow = np.array([30, 255, 255])
        
        # 마스크 생성
        mask = cv2.inRange(hsv, lower_yellow, upper_yellow)
        
        # 컨투어 찾기
        contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        
        for contour in contours:
            area = cv2.contourArea(contour)
            if area > 5000:  # 충분히 큰 영역
                x, y, w, h = cv2.boundingRect(contour)
                center_x = x + w // 2
                center_y = y + h // 2
                return (center_x, center_y)
        
        return None
    
    def click_device(self, serial, x, y):
        """특정 디바이스 클릭"""
        device = self.adb.device(serial)
        
        # 여러 방법 시도
        methods = [
            f"input tap {x} {y}",
            f"input touchscreen tap {x} {y}",
            f"sendevent /dev/input/event2 3 53 {x} && sendevent /dev/input/event2 3 54 {y} && sendevent /dev/input/event2 0 0 0"
        ]
        
        for method in methods:
            try:
                device.shell(method)
                self.devices[serial]['last_click'] = (x, y)
                self.devices[serial]['status'] = 'clicked'
                print(f"✅ {self.devices[serial]['name']}: 클릭 ({x}, {y})")
                return True
            except:
                continue
        
        return False
    
    def monitor_device(self, serial):
        """디바이스별 모니터링 스레드"""
        device_info = self.devices[serial]
        print(f"🔍 {device_info['name']} 모니터링 시작")
        
        # 학습된 좌표들 (버튼 위치)
        known_positions = [
            (540, 1800),
            (540, 1600),
            (720, 1800),
            (360, 1800)
        ]
        
        while True:
            try:
                # 화면 캡처 시도
                screen = self.capture_screen(serial)
                
                if screen is not None:
                    # 노란 버튼 감지
                    button_pos = self.detect_yellow_button(screen)
                    if button_pos:
                        self.click_device(serial, button_pos[0], button_pos[1])
                        time.sleep(2)  # 쿨다운
                        continue
                
                # 캡처 실패 시 알려진 위치 클릭
                device_info['status'] = 'blind_clicking'
                for x, y in known_positions:
                    self.click_device(serial, x, y)
                    time.sleep(0.3)
                
            except Exception as e:
                print(f"❌ {device_info['name']} 오류: {e}")
                
            time.sleep(1)
    
    def setup_routes(self):
        """웹 대시보드 라우트 설정"""
        
        @self.app.route('/')
        def dashboard():
            return render_template('dashboard.html')
        
        @self.app.route('/status')
        def status():
            return jsonify(self.devices)
        
        @self.app.route('/click/<serial>/<int:x>/<int:y>')
        def manual_click(serial, x, y):
            success = self.click_device(serial, x, y)
            return jsonify({'success': success})
    
    def start(self):
        """시스템 시작"""
        # 1. 디바이스 스캔
        self.scan_devices()
        
        # 2. 각 디바이스별 scrcpy 실행
        processes = []
        for serial, info in self.devices.items():
            p = self.start_scrcpy_for_device(
                serial, 
                info['name'],
                info['window_port']
            )
            processes.append(p)
            time.sleep(2)  # scrcpy 시작 대기
        
        # 3. 각 디바이스별 모니터링 스레드 시작
        threads = []
        for serial in self.devices:
            t = threading.Thread(target=self.monitor_device, args=(serial,))
            t.daemon = True
            t.start()
            threads.append(t)
        
        # 4. 웹 대시보드 시작
        print("🌐 웹 대시보드: http://localhost:5000")
        self.app.run(host='0.0.0.0', port=5000, debug=False)


# HTML 대시보드 템플릿
dashboard_html = """
<!DOCTYPE html>
<html>
<head>
    <title>카카오 택시 다중 제어</title>
    <style>
        body { font-family: Arial; background: #1a1a1a; color: white; }
        .container { display: flex; gap: 20px; padding: 20px; }
        .device { 
            background: #2a2a2a; 
            padding: 20px; 
            border-radius: 10px;
            width: 300px;
        }
        .device.active { border: 2px solid #FEE500; }
        .status { 
            padding: 5px 10px; 
            border-radius: 5px; 
            display: inline-block;
        }
        .status.clicked { background: #4CAF50; }
        .status.idle { background: #666; }
        .status.blind_clicking { background: #FF9800; }
        button {
            background: #FEE500;
            color: black;
            border: none;
            padding: 10px 20px;
            border-radius: 5px;
            cursor: pointer;
            font-weight: bold;
            width: 100%;
            margin-top: 10px;
        }
        h1 { text-align: center; color: #FEE500; }
        .stats { margin-top: 10px; }
        .last-click { color: #888; font-size: 12px; }
    </style>
</head>
<body>
    <h1>🚕 카카오 택시 다중 기사 제어 시스템</h1>
    <div class="container" id="devices"></div>
    
    <script>
        function updateStatus() {
            fetch('/status')
                .then(res => res.json())
                .then(data => {
                    const container = document.getElementById('devices');
                    container.innerHTML = '';
                    
                    Object.values(data).forEach(device => {
                        const div = document.createElement('div');
                        div.className = `device ${device.status === 'clicked' ? 'active' : ''}`;
                        div.innerHTML = `
                            <h2>${device.name}</h2>
                            <p>시리얼: ${device.serial}</p>
                            <p>상태: <span class="status ${device.status}">${device.status}</span></p>
                            ${device.last_click ? 
                                `<p class="last-click">마지막 클릭: ${device.last_click[0]}, ${device.last_click[1]}</p>` 
                                : ''}
                            <div class="stats">
                                <p>포트: ${device.window_port}</p>
                            </div>
                            <button onclick="manualClick('${device.serial}', 540, 1800)">
                                수동 클릭 (중앙)
                            </button>
                            <button onclick="manualClick('${device.serial}', 540, 1600)">
                                수동 클릭 (상단)
                            </button>
                        `;
                        container.appendChild(div);
                    });
                });
        }
        
        function manualClick(serial, x, y) {
            fetch(`/click/${serial}/${x}/${y}`)
                .then(res => res.json())
                .then(data => {
                    if (data.success) {
                        console.log('클릭 성공');
                    }
                });
        }
        
        setInterval(updateStatus, 1000);
        updateStatus();
    </script>
</body>
</html>
"""

if __name__ == "__main__":
    controller = MultiDriverController()
    controller.start()