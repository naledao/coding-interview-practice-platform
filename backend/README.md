# Backend

Spring Boot API service for the coding interview practice platform.

## Run

```bash
cd backend
mvn spring-boot:run
```

The service listens on `http://127.0.0.1:8904` by default.

The backend registers in Nacos as
`coding-interview-practice-platform-service` by default so that
`gateway-service` can forward the public API from `http://127.0.0.1:8960`.
Set `NACOS_DISCOVERY_REGISTER_ENABLED=false` only for an isolated process that
must not receive Gateway traffic. Gateway exposes the auth, question bank,
practice, statistics, AI tutor, and administrator API domains. It intentionally
does not expose `/api/health`, `/api/codex-tools/**`, or `/mcp/**`.

The React administration console is built automatically during the Maven
lifecycle and is served by this service at `http://127.0.0.1:8904/admin`.
For frontend development, run `npm --prefix ../admin-web run dev` and open
`http://127.0.0.1:5174/admin/`.

Development seed users:

- `admin@example.com`, role `ADMIN`
- `user1@example.com`, role `USER`

Login uses email verification codes only. For real email delivery, keep
`APP_DEV_LOGIN_CODE_ENABLED=false` and ensure Nacos can discover a healthy
`email-service` instance. SMTP credentials belong to `email-service`; this backend
does not connect to SMTP directly. For isolated local development, set
`APP_DEV_LOGIN_CODE_ENABLED=true` and configure `APP_DEV_LOGIN_CODE` (for example,
`123456`) to skip the downstream email call.

Set `APP_JWT_SECRET` in real deployments. The default secret is only for local development.

## Test

```bash
cd backend
mvn test
```

## Native Binary

Build a GraalVM native executable:

```bash
cd backend
mvn -Pnative native:compile
```

The executable is written to:

```bash
target/coding-interview-practice-platform-backend
```
