const { Client } = require('ssh2');
const fs = require('fs');
const path = require('path');

// SSH 凭据一律从环境变量读取，禁止明文入库。
// PowerShell: $env:VM_PASS="..."   Linux/macOS: export VM_PASS="..."
const VM_HOST = process.env.VM_HOST || '192.168.178.100';
const VM_USER = process.env.VM_USER || 'root';
const VM_PASS = process.env.VM_PASS;

if (!VM_PASS) {
  console.error('缺少环境变量 VM_PASS，请先设置目标主机的 SSH 口令后再执行（例：$env:VM_PASS="..."）');
  process.exit(1);
}

function sshExec(command) {
  return new Promise((resolve, reject) => {
    const conn = new Client();
    conn.on('ready', () => {
      conn.exec(command, (err, stream) => {
        if (err) { conn.end(); reject(err); return; }
        let stdout = '', stderr = '';
        stream.on('data', d => stdout += d.toString());
        stream.stderr.on('data', d => stderr += d.toString());
        stream.on('close', (code) => {
          conn.end();
          resolve({ stdout: stdout.trim(), stderr: stderr.trim(), code });
        });
      });
    }).on('error', reject).connect({ host: VM_HOST, port: 22, username: VM_USER, password: VM_PASS });
  });
}

function sshUpload(localPath, remotePath) {
  return new Promise((resolve, reject) => {
    const conn = new Client();
    conn.on('ready', () => {
      conn.sftp((err, sftp) => {
        if (err) { conn.end(); reject(err); return; }
        sftp.fastPut(localPath, remotePath, (err2) => {
          conn.end();
          if (err2) reject(err2); else resolve();
        });
      });
    }).on('error', reject).connect({ host: VM_HOST, port: 22, username: VM_USER, password: VM_PASS });
  });
}

async function main() {
  const args = process.argv.slice(2);
  if (args.length === 0) { console.error('Usage: node vm-ssh.js <command|upload local remote>'); process.exit(1); }

  try {
    if (args[0] === 'upload' && args.length >= 3) {
      const localFile = path.resolve(args[1]);
      const remoteFile = args[2];
      await sshUpload(localFile, remoteFile);
      console.log(`Uploaded ${localFile} -> ${remoteFile}`);
    } else {
      const cmd = args.join(' ');
      const result = await sshExec(cmd);
      if (result.stdout) console.log(result.stdout);
      if (result.stderr) console.error(result.stderr);
      process.exitCode = result.code || 0;
    }
  } catch (e) {
    console.error('SSH Error:', e.message);
    process.exit(1);
  }
}

main();
