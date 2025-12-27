import threading
import time
import sys
import subprocess
import psutil
import requests
import getpass
from datetime import datetime
from flask import Flask, request, jsonify  # ✨ Flask 관련 import 추가
from flask_cors import CORS  # ✨ CORS import 추가

# --- 설정 부분 ---
TARGET_APPS = [
    "Code.exe", "Photoshop.exe", "chrome.exe",
    "Spotify.exe", "Discord.exe", "Steam.exe",
    "KakaoTalk.exe",
]
SERVER_URL = "http://localhost:8080/api/logs"
# COMPUTER_ID는 프론트엔드에서 입력받으므로 파이썬 코드에서는 불필요
# 대신 사용자명으로 기본 컴퓨터 ID를 생성합니다.
USERNAME = getpass.getuser()
DEFAULT_COMPUTER_ID = f"{USERNAME}-desktop"

# --- Flask 웹 서버 설정 ---
app = Flask(__name__)
CORS(app)  # ✨ 모든 도메인에서의 요청을 허용 (개발용)


# --- 실행 함수 (변경 없음) ---
# --- 실행 함수 (✨ [수정됨] UWP 앱 실행 로직 추가) ---
def run_executor(command_list):
    if not command_list:
        print("💡 [입력 오류] 실행할 명령어를 입력해주세요.")
        return "명령어가 없습니다.", False

    # command_list가 리스트가 아닌 단일 문자열일 수 있으므로 처리
    cmd_str = command_list if isinstance(command_list, str) else ' '.join(command_list)

    try:
        final_command = ""

        # ✨ [신규 로직]
        # 1. 실행 경로가 Windows 스토어 앱(UWP) 경로인지 확인
        if "Program Files\\WindowsApps" in cmd_str:
            print(f"💡 [UWP 감지] Windows 스토어 앱 경로입니다.")

            # 2. 전체 경로에서 '.exe' 파일 이름만 추출
            # (예: "C:\...long...\Spotify.exe" -> "Spotify.exe")
            app_name = cmd_str.split('\\')[-1]

            # 3. 'start' 명령어를 사용하여 앱 별명으로 실행
            # (start "" "Spotify.exe")
            final_command = f'start "" "{app_name}"'
            print(f"🚀 '{final_command}' (UWP 방식)으로 실행을 시도합니다...")

        else:
            # 4. 일반 프로그램은 기존 방식대로 전체 경로로 실행
            # (예: "C:\Program Files\Google\Chrome\Application\chrome.exe")
            final_command = f'"{cmd_str}"'
            print(f"🚀 '{final_command}' (일반 방식) 명령을 실행합니다...")

        # 5. 최종 결정된 명령어를 쉘로 실행
        subprocess.Popen(final_command, shell=True)

        print("✅ 실행 완료!")
        return f"'{cmd_str}' 실행을 시작했습니다.", True

    except FileNotFoundError:
        error_msg = f"❌ [실행 오류] '{cmd_str}' 프로그램을 찾을 수 없습니다."
        print(error_msg)
        return error_msg, False
    except Exception as e:
        error_msg = f"❌ [실행 오류] 명령 실행 중 오류가 발생했습니다: {e}"
        print(error_msg)
        return error_msg, False






# --- ✨ [신규] Flask API 엔드포인트 ---
@app.route("/execute", methods=["POST"])
def execute_command():
    data = request.get_json()
    command = data.get("command")
    if command:
        message, success = run_executor([command])
        if success:
            return jsonify({"message": message})
        else:
            return jsonify({"message": message}), 500
    return jsonify({"message": "실행할 'command'가 없습니다."}), 400


# --- 감시 함수 (COMPUTER_ID 동적 할당 로직 추가) ---
def run_monitor(computer_id):
    # ... (상단 5초 지연 및 print 구문은 동일) ...
    print(f"🕵️‍♂️ ({computer_id}) 사용자 활동 감시를 시작합니다...")
    already_detected = set()
    username = getpass.getuser()

    while True:
        try:
            running_procs = {p.info['name']: p for p in psutil.process_iter(['name', 'exe'])}
            running_app_names = running_procs.keys()

            # --- 1. 앱 시작 감지 (logType: "START") ---
            for app_name in TARGET_APPS:
                if app_name in running_app_names and app_name not in already_detected:
                    # ... (경로 찾기, 타임스탬프, print_log_message 등은 동일) ...
                    proc_object = running_procs[app_name]
                    path = "경로 확인 불가"
                    try:
                        path = proc_object.info['exe'] or "경로 확인 불가"
                    except (psutil.AccessDenied, TypeError):
                        pass

                    timestamp = datetime.now()
                    print_log_message(app_name, timestamp.strftime('%Y-%m-%d %H:%M:%S'), path)

                    log_data = {
                        "username": username,
                        "processName": app_name,
                        "processPath": path,
                        "logTimestamp": timestamp.isoformat(),
                        "computerId": computer_id,
                        "logType": "START"  # ✨ [수정] 로그 타입을 "START"로 명시
                    }
                    try:
                        requests.post(SERVER_URL, json=log_data, timeout=5)
                        print(f"✅ [서버] '{app_name}' (시작) 로그 전송 성공!")
                    except requests.exceptions.RequestException as e:
                        print(f"❌ [서버] 로그 전송 실패: {e}")

                    already_detected.add(app_name)

            # --- 2. ✨ [신규] 앱 종료 감지 (logType: "STOP") ---
            closed_apps = already_detected - running_app_names
            for app_name in closed_apps:
                timestamp = datetime.now()  # 종료 시간
                print_log_message(f"'{app_name}' 프로그램이 종료되었습니다.", timestamp.strftime('%H:%M:%S'), is_exit=True)
                already_detected.remove(app_name)

                # ✨ 종료 로그 데이터 구성 (경로 등은 불필요)
                log_data = {
                    "username": username,
                    "processName": app_name,
                    "processPath": None,  # 종료 시에는 경로가 의미 없음
                    "logTimestamp": timestamp.isoformat(),
                    "computerId": computer_id,
                    "logType": "STOP"  # ✨ 로그 타입을 "STOP"으로 명시
                }
                try:
                    # ✨ 종료 로그도 서버로 전송!
                    requests.post(SERVER_URL, json=log_data, timeout=5)
                    print(f"✅ [서버] '{app_name}' (종료) 로그 전송 성공!")
                except requests.exceptions.RequestException as e:
                    print(f"❌ [서버] 종료 로그 전송 실패: {e}")

            time.sleep(10)
        except (psutil.NoSuchProcess, psutil.AccessDenied, psutil.ZombieProcess):
            pass

def print_log_message(title, time_str, detail="", is_exit=False):
    """콘솔에 로그 메시지를 출력하고 입력 프롬프트를 다시 표시하는 함수"""
    tag = "🚪 종료" if is_exit else "🔎 감지"
    print("\n" + "=" * 20 + f" {tag} " + "=" * 20)
    print(f"  - 프로그램: {title}")
    print(f"  - 시간: {time_str}")
    if not is_exit:
        print(f"  - 경로: {detail}")
    print("=" * 48)
    # 현재 스레드가 메인 스레드일 때만 프롬프트 출력
    if threading.current_thread() is threading.main_thread():
        sys.stdout.write("명령어 입력 대기중... > ")
        sys.stdout.flush()


# --- 메인 프로그램 시작점 ---
def main():
    print("=" * 50)
    print("🚀 AI 비서 로거가 실행되었습니다.")
    # ✨ 컴퓨터 ID를 입력받음
    computer_id = input(f"사용할 컴퓨터 이름을 입력하세요 (미입력 시 '{DEFAULT_COMPUTER_ID}'): ").strip()
    if not computer_id:
        computer_id = DEFAULT_COMPUTER_ID
    print(f"✅ 이 컴퓨터는 '{computer_id}' 이름으로 서버와 통신합니다.")
    print("=" * 50)

    # 모니터 스레드는 입력받은 computer_id를 사용하여 실행
    monitor_thread = threading.Thread(target=run_monitor, args=(computer_id,), daemon=True)
    monitor_thread.start()

    print("   - 백그라운드에서 프로그램 실행을 감시합니다.")
    print("   - 웹 UI에서 추천 앱을 '바로 실행'할 수 있습니다.")
    print("   - 종료하려면 Ctrl+C를 누르세요.")
    print("=" * 50)

    # Flask 서버를 별도의 스레드에서 실행
    flask_thread = threading.Thread(target=lambda: app.run(port=5001), daemon=True)
    flask_thread.start()

    # 메인 스레드는 계속해서 유휴 상태로 대기 (Flask와 모니터가 백그라운드에서 동작)
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print("\n프로그램을 종료합니다.")
        sys.exit(0)


if __name__ == "__main__":
    main()