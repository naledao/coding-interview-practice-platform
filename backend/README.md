# Backend

Spring Boot API service for the coding interview practice platform.

## Run

```bash
cd backend
mvn spring-boot:run
```

The service listens on `http://127.0.0.1:8904` by default.

Development seed users:

- `admin@example.com`, role `ADMIN`
- `user1@example.com`, role `USER`

Login uses email verification codes only. In local development,
`APP_DEV_LOGIN_CODE_ENABLED` defaults to `true` and the default code is `123456`.
Set `APP_DEV_LOGIN_CODE_ENABLED=false` and configure `MAIL_USERNAME` /
`MAIL_PASSWORD` for real email delivery.

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
