import { useEffect, useState } from 'react'
import { Icon, RouteLink } from './Common'

const NAV_ITEMS = [
  { path: '/overview', label: '总览', icon: 'overview' },
  { path: '/upload', label: '上传文档', icon: 'upload' },
  { path: '/documents', label: '知识文档', icon: 'file' },
  { path: '/jobs', label: '导入任务', icon: 'jobs' },
  { path: '/questions', label: '题库管理', icon: 'question' },
]

function isActive(route, path) {
  if (path === '/overview') return route === path
  return route === path || route.startsWith(`${path}/`)
}

export default function AdminLayout({ user, route, onLogout, children }) {
  const [menuOpen, setMenuOpen] = useState(false)

  useEffect(() => setMenuOpen(false), [route])

  return (
    <div className="admin-shell">
      {menuOpen && <button className="mobile-overlay" type="button" aria-label="关闭导航" onClick={() => setMenuOpen(false)} />}
      <aside className={`sidebar ${menuOpen ? 'sidebar-open' : ''}`}>
        <div className="brand">
          <span className="brand-mark">J</span>
          <div>
            <strong>Java Practice</strong>
            <small>ADMIN CONSOLE</small>
          </div>
          <button className="sidebar-close" type="button" aria-label="关闭导航" onClick={() => setMenuOpen(false)}>
            <Icon name="close" />
          </button>
        </div>

        <nav className="sidebar-nav" aria-label="管理导航">
          <p>工作台</p>
          {NAV_ITEMS.map((item) => (
            <RouteLink key={item.path} to={item.path} className={isActive(route, item.path) ? 'active' : ''}>
              <Icon name={item.icon} />
              <span>{item.label}</span>
              {isActive(route, item.path) && <i />}
            </RouteLink>
          ))}
        </nav>

        <div className="sidebar-footer">
          <div className="user-card">
            <span className="avatar">{(user.nickname || user.email || 'A').slice(0, 1).toUpperCase()}</span>
            <div>
              <strong>{user.nickname || '管理员'}</strong>
              <small>{user.email}</small>
            </div>
          </div>
          <button className="logout-button" type="button" onClick={onLogout}>
            <Icon name="logout" />退出登录
          </button>
        </div>
      </aside>

      <div className="shell-content">
        <div className="mobile-topbar">
          <button className="icon-button" type="button" aria-label="打开导航" onClick={() => setMenuOpen(true)}>
            <Icon name="menu" />
          </button>
          <strong>题库管理控制台</strong>
          <span className="avatar avatar-small">{(user.nickname || user.email || 'A').slice(0, 1).toUpperCase()}</span>
        </div>
        <main className="main-content">{children}</main>
      </div>
    </div>
  )
}
