# Raccoon UI

智能巡检管理系统前端

## 技术栈

- Vue 3.4
- TypeScript
- Vite 5.x
- Element Plus
- Vue Router 4
- Pinia
- Axios

## 安装依赖

```bash
npm install
```

## 运行项目

```bash
npm run dev
```

## 构建项目

```bash
npm run build
```

## 项目结构

```
src/
├── api/              # API 接口
│   ├── auth.ts       # 认证接口
│   ├── user.ts       # 用户管理接口
│   └── role.ts       # 角色管理接口
├── assets/           # 静态资源
├── components/       # 公共组件
├── layouts/         # 布局组件
│   ├── MainLayout.vue    # 主布局
│   ├── Sidebar.vue       # 侧边栏
│   └── Header.vue        # 头部
├── router/           # 路由配置
│   └── index.ts      # 路由守卫
├── stores/           # Pinia 状态管理
│   ├── index.ts      # Store 入口
│   └── user.ts       # 用户状态
├── utils/            # 工具类
│   └── request.ts    # Axios 封装
├── views/            # 页面组件
│   ├── auth/         # 认证页面
│   │   └── Login.vue
│   ├── dashboard/    # 首页
│   │   └── index.vue
│   ├── role/         # 角色管理
│   │   └── index.vue
│   └── user/         # 用户管理
│       └── index.vue
├── App.vue           # 根组件
├── env.d.ts          # 环境变量类型
└── main.ts           # 入口文件
```

## 接口配置

- 后端接口基础路径：`/api`
- Gateway 代理配置：`http://localhost:8080`
- 统一返回格式：`{ code: number, data: any, message: string }`

## 登录账号

系统默认提供一个测试账号：

- 用户名：admin
- 密码：123456

## 功能模块

1. **认证模块** - 登录、退出、Token 管理
2. **用户管理** - 用户列表、新增、编辑、删除、状态变更、密码重置
3. **角色管理** - 角色列表、新增、编辑