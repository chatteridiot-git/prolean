import { useMemo, useState } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import {
  CalendarOutlined,
  DashboardOutlined,
  DownOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  ProjectOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { Avatar, Button, Dropdown, Grid, Layout, Menu, Space, Typography } from 'antd';
import type { MenuProps } from 'antd';
import { useAuth } from '@/auth/AuthProvider';

const { Header, Sider, Content } = Layout;

function selectedKey(pathname: string): string {
  if (pathname === '/') return '/';
  if (pathname.startsWith('/projects')) return '/projects';
  if (pathname.startsWith('/users')) return '/users';
  if (pathname.startsWith('/holidays')) return '/holidays';
  if (pathname.startsWith('/profile')) return '/profile';
  return '/';
}

export function AppShell() {
  const { user, isAdmin, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const screens = Grid.useBreakpoint();
  const mobile = !screens.md;
  const [collapsed, setCollapsed] = useState(false);

  const items = useMemo<MenuProps['items']>(() => {
    const base: MenuProps['items'] = [
      { key: '/', icon: <DashboardOutlined />, label: 'Dashboard' },
      { key: '/projects', icon: <ProjectOutlined />, label: 'Projects' },
      { key: '/holidays', icon: <CalendarOutlined />, label: 'Holidays' },
      { key: '/profile', icon: <UserOutlined />, label: 'My profile' },
    ];
    if (isAdmin) base.splice(2, 0, { key: '/users', icon: <TeamOutlined />, label: 'Users' });
    return base;
  }, [isAdmin]);

  const userMenu: MenuProps['items'] = [
    {
      key: 'profile',
      icon: <UserOutlined />,
      label: 'My profile',
      onClick: () => navigate('/profile'),
    },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: 'Sign out',
      onClick: () => void logout().then(() => navigate('/login', { replace: true })),
    },
  ];

  const initials = user?.fullName
    ?.split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0])
    .join('')
    .toUpperCase();

  return (
    <Layout className="app-shell">
      <Sider
        collapsible
        collapsed={collapsed}
        onCollapse={setCollapsed}
        breakpoint="lg"
        collapsedWidth={mobile ? 0 : 64}
        width={230}
        trigger={null}
        className="app-sider"
      >
        <button
          type="button"
          className="brand"
          onClick={() => navigate('/')}
          aria-label="ProLean dashboard"
        >
          <span className="brand-mark">P</span>
          {!collapsed ? <span className="brand-text">ProLean</span> : null}
        </button>
        <Menu
          mode="inline"
          theme="dark"
          selectedKeys={[selectedKey(location.pathname)]}
          items={items}
          onClick={({ key }) => {
            navigate(key);
            if (mobile) setCollapsed(true);
          }}
          className="app-menu"
        />
        {!collapsed ? (
          <div className="sider-footer">
            <Typography.Text type="secondary" className="small-text">
              Tasks and timelines, in one place.
            </Typography.Text>
          </div>
        ) : null}
      </Sider>
      <Layout>
        <Header className="app-header">
          <Button
            type="text"
            aria-label={collapsed ? 'Open navigation' : 'Close navigation'}
            icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() => setCollapsed((value) => !value)}
          />
          <Dropdown menu={{ items: userMenu }} trigger={['click']}>
            <Button type="text" className="user-menu-button">
              <Space>
                <Avatar size="small" className="user-avatar">
                  {initials || 'U'}
                </Avatar>
                <span className="user-name">{user?.fullName}</span>
                <DownOutlined className="small-icon" />
              </Space>
            </Button>
          </Dropdown>
        </Header>
        <Content className="app-content">
          <div className="app-content-inner">
            <Outlet />
          </div>
        </Content>
      </Layout>
    </Layout>
  );
}
