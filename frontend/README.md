# JayPay frontend

Two sites in one npm workspace:

| Workspace | Path | What it is |
| --- | --- | --- |
| `@jaypay/web` | `apps/web` | Customer site: sign up, sign in, wallet balance and top-up, bank account, sending money, paying a merchant |
| `@jaypay/admin` | `apps/admin` | Admin console for staff accounts: sign-in, member search and edits, regional money totals, settlement queue, staff account management |
| `@jaypay/api` | `packages/api` | Typed fetch client that parses responses with zod, the schemas both sites read, and the fake server tests use |
| `@jaypay/ui` | `packages/ui` | Form reader, money and date formatting, and the card and error notice both sites render |

The browser only calls same-origin `/api/<service>/...`. The Vite dev server and the nginx image strip `/api/<service>` and forward the rest to that service.

## Development

Node 24 or newer.

```sh
npm ci
npm run dev:web      # http://localhost:5173
npm run dev:admin    # http://localhost:5174
npm run lint
npm test
npm run build
```

`npm test` and `npm run build` run in every workspace.

## Environment

Copy `.env.example` to `.env` to point the dev servers somewhere else. Both dev servers read `frontend/.env`, and variables set in the shell win over the file. The images read the same names from the container environment. Each site reads only the variables of the services it proxies.

| Variable | Path | Read by | Dev default | Image default |
| --- | --- | --- | --- | --- |
| `JAYPAY_MEMBERSHIP_URL` | `/api/membership` | web, admin | `http://localhost:8081` | `http://membership-service:8080` |
| `JAYPAY_BANKING_URL` | `/api/banking` | web | `http://localhost:8082` | `http://banking-service:8080` |
| `JAYPAY_MONEY_URL` | `/api/money` | web | `http://localhost:8083` | `http://money-service:8080` |
| `JAYPAY_REMITTANCE_URL` | `/api/remittance` | web | `http://localhost:8084` | `http://remittance-service:8080` |
| `JAYPAY_PAYMENT_URL` | `/api/payment` | web, admin | `http://localhost:8085` | `http://payment-service:8080` |
| `JAYPAY_MONEY_AGGREGATION_URL` | `/api/money-aggregation` | admin | `http://localhost:8086` | `http://money-aggregation-service:8080` |
| `JAYPAY_MONEY_QUERY_URL` | `/api/money-query` | admin | `http://localhost:8087` | `http://money-query-service:8080` |
| `JAYPAY_AUTH_URL` | `/api/auth` | admin | `http://localhost:8089` | `http://auth-service:8080` |

The dev defaults match the host ports in `infra/docker/compose.yaml`.

## Admin roles

The admin console signs staff in through `/api/auth/staff/sign-in` and sends the token as `Authorization: Bearer <token>` on every other `/api` request. A 401 or the token's `expiresAt` ends the session and returns to the sign-in screen. `apps/admin/src/permissions.ts` maps each role to what the console shows:

| Role | Members | Settlement | Staff accounts |
| --- | --- | --- | --- |
| Viewer | Read | Read | Hidden |
| Operator | Read and edit | Read and mark settled | Hidden |
| Admin | Read and edit | Read and mark settled | Manage |

Every role can change its own password under My account. The server decides every request on its own, so the console only hides what a role cannot do. In the image, nginx asks auth-service about every `/api` request before it forwards it. The Vite dev server proxies straight to the services and skips that check.

## Images

One `Dockerfile` builds either site. `APP` picks it.

```sh
docker build --build-arg APP=web --tag jaypay/frontend-web frontend
docker build --build-arg APP=admin --tag jaypay/frontend-admin frontend
docker run --rm -p 8080:8080 jaypay/frontend-web
```

`make images` builds both. Each container runs nginx as uid 101 on port 8080 and serves `index.html` for every site path. Upstream hosts are resolved per request, so a container starts without its services and answers only the affected `/api` requests with 502.
