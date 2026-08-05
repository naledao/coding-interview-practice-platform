import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'

const ICON_PATHS = {
  overview: '<rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/>',
  upload: '<path d="M12 16V4m0 0L7 9m5-5 5 5"/><path d="M5 15v4a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-4"/>',
  file: '<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><path d="M14 2v6h6M8 13h8M8 17h6"/>',
  jobs: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
  question: '<circle cx="12" cy="12" r="9"/><path d="M9.8 9a2.4 2.4 0 1 1 3.5 2.1c-.9.5-1.3 1-1.3 2.1M12 17h.01"/>',
  menu: '<path d="M4 7h16M4 12h16M4 17h16"/>',
  close: '<path d="m6 6 12 12M18 6 6 18"/>',
  logout: '<path d="M10 17l5-5-5-5M15 12H3"/><path d="M14 3h5a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-5"/>',
  refresh: '<path d="M20 6v5h-5"/><path d="M4 18v-5h5"/><path d="M18.5 9a7 7 0 0 0-12-2.5L4 11M5.5 15a7 7 0 0 0 12 2.5L20 13"/>',
  arrowLeft: '<path d="m15 18-6-6 6-6"/>',
  arrowRight: '<path d="m9 18 6-6-6-6"/>',
  play: '<path d="m8 5 11 7-11 7z"/>',
  retry: '<path d="M3 12a9 9 0 1 0 3-6.7L3 8"/><path d="M3 3v5h5"/>',
  eye: '<path d="M2 12s3.5-6 10-6 10 6 10 6-3.5 6-10 6S2 12 2 12z"/><circle cx="12" cy="12" r="2.5"/>',
  check: '<path d="m5 12 4 4L19 6"/>',
  shield: '<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/><path d="m9 12 2 2 4-4"/>',
  chevron: '<path d="m9 18 6-6-6-6"/>',
  download: '<path d="M12 3v12m0 0 5-5m-5 5-5-5"/><path d="M5 21h14"/>',
}

export function Icon({ name, size = 18, className = '' }) {
  return (
    <svg
      aria-hidden="true"
      className={className}
      fill="none"
      height={size}
      viewBox="0 0 24 24"
      width={size}
      stroke="currentColor"
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth="1.8"
      dangerouslySetInnerHTML={{ __html: ICON_PATHS[name] || ICON_PATHS.question }}
    />
  )
}

export function RouteLink({ to, className = '', children, title }) {
  return <a className={className} href={`#${to}`} title={title}>{children}</a>
}

export function StatusBadge({ status, label }) {
  return <span className={`status-badge status-${String(status || 'unknown').toLowerCase()}`}>{label || status || '-'}</span>
}

export function Alert({ type = 'error', children }) {
  if (!children) return null
  return <div className={`alert alert-${type}`} role={type === 'error' ? 'alert' : 'status'}>{children}</div>
}

export function PageHeader({ eyebrow = '管理控制台', title, description, actions, backTo }) {
  return (
    <header className="page-header">
      <div className="page-heading-row">
        {backTo && (
          <RouteLink to={backTo} className="icon-button back-button" title="返回">
            <Icon name="arrowLeft" />
          </RouteLink>
        )}
        <div>
          <p className="eyebrow">{eyebrow}</p>
          <h1>{title}</h1>
          {description && <p className="page-description">{description}</p>}
        </div>
      </div>
      {actions && <div className="page-actions">{actions}</div>}
    </header>
  )
}

export function Panel({ title, meta, actions, children, className = '' }) {
  return (
    <section className={`panel ${className}`}>
      {(title || actions) && (
        <div className="panel-header">
          <div>
            {title && <h2>{title}</h2>}
            {meta && <p>{meta}</p>}
          </div>
          {actions && <div className="panel-actions">{actions}</div>}
        </div>
      )}
      {children}
    </section>
  )
}

export function LoadingBlock({ label = '正在加载…' }) {
  return (
    <div className="loading-block">
      <span className="spinner" />
      <span>{label}</span>
    </div>
  )
}

export function EmptyState({ title = '暂无数据', description }) {
  return (
    <div className="empty-state">
      <span>◇</span>
      <strong>{title}</strong>
      {description && <p>{description}</p>}
    </div>
  )
}

export function Pagination({ page, pageSize, total, loading, onChange }) {
  const totalPages = Math.max(1, Math.ceil(total / pageSize))
  return (
    <div className="pagination">
      <button className="button button-ghost" type="button" disabled={loading || page <= 1} onClick={() => onChange(page - 1)}>
        <Icon name="arrowLeft" size={16} />上一页
      </button>
      <span>第 <strong>{page}</strong> / {totalPages} 页 · 共 {total} 条</span>
      <button className="button button-ghost" type="button" disabled={loading || page >= totalPages} onClick={() => onChange(page + 1)}>
        下一页<Icon name="arrowRight" size={16} />
      </button>
    </div>
  )
}

export function Markdown({ children, className = '' }) {
  if (!children) return <span className="muted">-</span>
  return (
    <div className={`markdown ${className}`}>
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          a: ({ children: linkChildren, ...props }) => <a {...props} target="_blank" rel="noreferrer">{linkChildren}</a>,
        }}
      >
        {children}
      </ReactMarkdown>
    </div>
  )
}

export function DetailGrid({ items }) {
  return (
    <dl className="detail-grid">
      {items.filter((item) => !item.hidden).map((item) => (
        <div className={item.wide ? 'detail-wide' : ''} key={item.label}>
          <dt>{item.label}</dt>
          <dd>{item.value ?? '-'}</dd>
        </div>
      ))}
    </dl>
  )
}
