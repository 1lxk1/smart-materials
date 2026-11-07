import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

/**
 * 开发服务器注意点（实测踩坑）：
 * 项目根目录同时也是 Maven/IDE/编辑器写临时文件的地方，Vite 默认会监听整棵树，
 * 一旦扫到被锁定的临时文件（例如 Windows 上编辑器生成的
 * `.README.md.<uuid>.tmpdir/README.md.tmp`）就会抛 EBUSY 并让 dev server 直接退出。
 * 这里显式忽略后端、构建产物、上传目录、工具链目录与所有临时文件。
 */
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': '/src',
    },
  },
  server: {
    port: 5173,
    // host: true → 监听 0.0.0.0，手机在同一 Wi-Fi 下可直接访问 http://<局域网IP>:5173
    host: true,
    open: true,
    watch: {
      ignored: [
        '**/*.tmp',
        '**/*.tmpdir/**',
        '**/.*.tmpdir/**',
        '**/backend/**',
        '**/target/**',
        '**/dist/**',
        '**/uploads/**',
        '**/.tooling/**',
        '**/preview/**',
        '**/.git/**',
      ],
    },
  },
  build: {
    // antd 组件库本身 gzip 后约 389KB，属于该技术栈的合理体积；
    // 这里按三方库拆包（react / antd / charts），并把提示阈值调到 1.4MB，
    // 避免每次构建都刷一条无意义的「chunk 过大」告警。
    chunkSizeWarningLimit: 1400,
    rollupOptions: {
      output: {
        manualChunks: {
          react: ['react', 'react-dom', 'react-router-dom'],
          antd: ['antd', '@ant-design/icons'],
          charts: ['recharts'],
        },
      },
    },
  },
});
