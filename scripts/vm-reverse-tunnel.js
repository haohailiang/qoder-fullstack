// 把 VM 上的 127.0.0.1:<remotePort> 反向转发到本机（Windows）的 127.0.0.1:<localPort>。
// 用途：让虚机里的 dockerd 通过本机 Clash 拉镜像，无需给 Clash 开局域网监听、也无需改防火墙。
// 用法：$env:VM_PASS="<密码>"; node scripts/vm-reverse-tunnel.js [localPort] [remotePort]
const { Client } = require('ssh2');
const net = require('net');

const HOST = process.env.VM_HOST || '192.168.178.100';
const USER = process.env.VM_USER || 'root';
const PASS = process.env.VM_PASS;
const LOCAL_PORT = Number(process.argv[2] || 7897);
const REMOTE_PORT = Number(process.argv[3] || 7897);

if (!PASS) {
  console.error('缺少环境变量 VM_PASS');
  process.exit(1);
}

function connect() {
  const conn = new Client();
  const forwards = new Set();

  conn.on('ready', () => {
    console.log(`[tunnel] ssh ready, requesting remote 127.0.0.1:${REMOTE_PORT} -> local 127.0.0.1:${LOCAL_PORT}`);
    conn.forwardIn('127.0.0.1', REMOTE_PORT, (err) => {
      if (err) {
        console.error('[tunnel] forwardIn failed:', err.message);
        conn.end();
        return;
      }
      console.log('[tunnel] listening on VM 127.0.0.1:' + REMOTE_PORT);
    });
  });

  conn.on('tcp connection', (info, accept) => {
    const upstream = net.connect(LOCAL_PORT, '127.0.0.1', () => {
      const stream = accept();
      stream.pipe(upstream).pipe(stream);
      forwards.add(stream);
      stream.on('close', () => forwards.delete(stream));
    });
    upstream.on('error', (e) => {
      console.error('[tunnel] local connect error:', e.message);
      try { const s = accept(); s.close(); } catch (_) { /* ignore */ }
    });
  });

  conn.on('close', () => {
    console.error('[tunnel] ssh closed, reconnecting in 5s');
    setTimeout(connect, 5000);
  });
  conn.on('error', (e) => console.error('[tunnel] ssh error:', e.message));

  conn.connect({ host: HOST, port: 22, username: USER, password: PASS, keepaliveInterval: 10000 });
}

connect();
