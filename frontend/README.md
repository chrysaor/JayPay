# JayPay frontend

결제 앱의 주요 흐름(회원, 계좌, 머니 충전, 송금, 결제)을 브라우저에서 눌러 보는 예제 화면입니다. 브라우저는 같은 출처의 `/api/<서비스>/...`만 호출하고, 개발 서버나 nginx가 이 경로에서 `/api/<서비스>`를 떼고 각 서비스로 넘깁니다.

## 개발 서버

Node 24 이상이 필요합니다.

```sh
npm ci
npm run dev        # http://localhost:5173
npm run lint
npm test
npm run build
```

## 프록시 환경 변수

개발 서버(`vite.config.ts`)와 이미지(`nginx/default.conf.template`)가 같은 이름을 씁니다.

| 변수 | 경로 | 개발 서버 기본값 | 이미지 기본값 |
| --- | --- | --- | --- |
| `JAYPAY_MEMBERSHIP_URL` | `/api/membership` | `http://localhost:8081` | `http://membership-service:8080` |
| `JAYPAY_BANKING_URL` | `/api/banking` | `http://localhost:8082` | `http://banking-service:8080` |
| `JAYPAY_MONEY_URL` | `/api/money` | `http://localhost:8083` | `http://money-service:8080` |
| `JAYPAY_REMITTANCE_URL` | `/api/remittance` | `http://localhost:8084` | `http://remittance-service:8080` |
| `JAYPAY_PAYMENT_URL` | `/api/payment` | `http://localhost:8085` | `http://payment-service:8080` |

```sh
JAYPAY_MEMBERSHIP_URL=http://localhost:9081 npm run dev
```

## 이미지 빌드

```sh
docker build -t jaypay-frontend frontend
docker run --rm -p 8080:8080 jaypay-frontend   # http://localhost:8080
```

컨테이너는 nginx를 uid 101로 8080 포트에서 실행합니다. 업스트림 주소는 요청마다 DNS로 찾기 때문에, 서비스가 떠 있지 않아도 컨테이너는 뜨고 해당 `/api` 요청만 502로 응답합니다.
