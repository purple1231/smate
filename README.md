
# smate
## 사용자의 화면을 이해하고 대화를 제안하는 지능형 데스크탑 비서

smate는 사용자의 PC 환경을 실시간으로 분석하여 자동 채팅 추천, 앱 실행 제어, 일정 관리를 도와주는 멀티모달 AI 비서 솔루션입니다.

<br/>
<br/>

# 0. Getting Started (시작하기)

 - Backend Setup (백엔드 설정 및 실행)
   - ### 🐍 AI 서버 (Python Flask)
   1. 가상환경 설정 및 필수 패키지를 설치합니다.
   ```bash
    cd flask-server
    python -m venv venv
    source venv/bin/activate  # Windows: venv\Scripts\activate
    pip install -r requirements.txt
   ```
   2. 서버를 실행합니다.
   ```bash
   python main.py
   ```
   
   - ### ☕ 메인 서버 (Java Spring Boot)
   1. spring-server 폴더로 이동합니다.
   2. src/main/resources/application.yml 파일에서 Gemini API Key 및 DB 연결 정보를 설정합니다.
   3. Gradle을 사용하여 프로젝트를 빌드하고 실행합니다.
   ```bash
   cd spring-server
   ./gradlew bootRun
   ```

   - 🎮 Unity 프로젝트 로드
   1. [유니티 클라이언트 레포지토리] ([링크](https://github.com/neuanwi/smate.git))


   
https://github.com/neuanwi/smate.git 이거 들어가서 클라이언트 받으셈 ㅋ
백엔드 하는법
스프링부트,플라스크 준비 및 이거 클론한담 알아서 ㅋ

<br/>
<br/>

# 1. Project Overview (프로젝트 개요)
- 프로젝트 이름: smate
- 프로젝트 설명: smate는 사용자의 PC 환경을 실시간으로 분석하여 자동 채팅 추천, 앱 실행 제어, 일정 관리를 도와주는 멀티모달 AI 비서 솔루션입니다.

<br/>
<br/>

# 2. Team Members (팀원 및 팀 소개)
| 김진근 | 김한결 | 김호정 | 김근우 |
|:------:|:------:|:------:|:------:|
| backend | ux/ui design | client | client |

<br/>
<br/>

# 3.💡 Key Features (주요 기능)
- **실시간 화면 인식 및 대화 제안 (Vision-based Chat)**:
  - 단순히 묻는 말에 대답하는 수동적인 비서를 넘, 사용자의 PC 환경을 스스로 관찰하고 먼저 말을 거는 **'능동형 멀티모달 시스템'**입니다.
    - 멀티모달 분석: 사용자가 보내는 텍스트뿐만 아니라 스크린샷을 함께 분석하여 문맥에 맞는 답변을 제공합니다.
    - 시스템 리마크: 사용자가 직접 묻지 않아도 화면 상황([SYSTEM_SCREENSHOT])을 인지하여 먼저 대화를 건넵니다.

- **3단계 대답 로직**:
  - 서버 유입 시 사용자 의도를 세 가지 단계로 즉시 판별합니다.
    - 앱 실행 제어 (1순위): "크롬 켜줘"와 같은 명령을 인식하여 즉시 실행 응답을 보냅니다.
    - 일정/알람 추출 (2순위): 대화 속에서 시간과 할 일을 추출하여 스케줄러에 등록합니다.
    - 능형 일반 대화 (3순위): 캐릭터 설정에 맞춰 자연스러운 대화를 수행합니다.

- **자동 채팅 추천 시스템**:
  - 최신성 유지: 5분 이내의 가장 신선한 추천 데이터만 사용자에게 전달합니다.
  - 중복 방지: 추천이 성공적으로 전달되면 기존 기록을 즉시 초기화하여 실시간성을 보장합니다.

<br/>
<br/>



# 4. Technology Stack (기술 스택)
## 4.1 Client
|  |  |
|-----------------|-----------------|
| Unity (C#)    |<img src="https://yt3.googleusercontent.com/2GbJoy1rf88ByUwmy1Kc05BcnxH33wbjAxRdqg2n6_VSoZsKTbVKrvPs3zivavdHbuTIC5iV=s160-c-k-c0x00ffffff-no-rj" alt="unity" width="100">| 
<br/>



## 4.2 Main Server
|  |  |  |
|-----------------|-----------------|-----------------|
| Java Spring Boot    |  <img src="https://www.coderscampus.com/wp-content/uploads/2016/06/spring-boot-project-logo.png" alt="spring" width="100">    | 비즈니스 로직, 데이터 관리, Gemini API 연동    |
| Python Flask    |  <img src="https://img1.daumcdn.net/thumb/R800x0/?scode=mtistory2&fname=https%3A%2F%2Fblog.kakaocdn.net%2Fdna%2FYM56a%2FbtsJWPzbyh2%2FAAAAAAAAAAAAAAAAAAAAANkWotqZkXRoGmzL70hEDD9S18KyWqRmbzxBtkuUscQ1%2Fimg.png%3Fcredential%3DyqXZFxpELC7KVnFOS48ylbz2pIh7yKj8%26expires%3D1767193199%26allow_ip%3D%26allow_referer%3D%26signature%3DaVy8vWINgapqtV%252BjBe96fJgDvoo%253D" alt="flask" width="100">    | 로그 분석 및 특정 데이터 처리 로직    |

<br/>

## 4.3 Cooperation
|  |  |
|-----------------|-----------------|
| Git    |  <img src="https://github.com/user-attachments/assets/483abc38-ed4d-487c-b43a-3963b33430e6" alt="git" width="100">    |
| Gemini    |  <img src="https://i.namu.wiki/i/G741c49m7_spX7OwibKiO999-vQ74huCB4J5URGQxJawRpTSdOwWYALtjUunV_7jttM-ZdHYQlPbcHvXQlo7Mg.webp" alt="gemini" width="100">    |
| Notion    |  <img src="https://github.com/user-attachments/assets/34141eb9-deca-416a-a83f-ff9543cc2f9a" alt="Notion" width="100">    |

<br/>

# 5. Project Structure (프로젝트 구조)
```plaintext
Smate/
├── flask-server/         # Python 기반 AI 보조 서버
└── spring-server/        # 메인 서버 (Spring Boot)
    ├── domain/           # 엔티티 (Persona, Recommendation 등)
    ├── dto/              # 데이터 전송 객체 (ChatResponse, TaskDto)
    ├── service/          # 비즈니스 로직 (GeminiService, Cache)
    └── controller/       # API 엔드포인트 (Recommendation, Gemini)
```

<br/>
<br/>
