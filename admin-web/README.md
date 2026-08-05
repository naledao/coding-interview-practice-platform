# React Admin Web

浏览器管理控制台，复用 Spring Boot 的认证、文档、导入任务和题库 API。

## Development

```bash
npm ci
npm run dev
```

默认访问地址为 `http://127.0.0.1:5174/admin/`，`/api` 请求代理到 `http://127.0.0.1:8904`。可以通过 `VITE_API_TARGET` 修改开发代理地址。

## Production build

```bash
npm run build
```

通常不需要手动执行生产构建。后端 Maven 生命周期会自动执行 `npm ci` 和 `npm run build`，再将 `dist/` 复制到 `target/classes/static/admin`。生产入口为后端的 `/admin`。
