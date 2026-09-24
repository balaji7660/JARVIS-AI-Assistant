import fs from 'fs';
import path from 'path';
import { execSync } from 'child_process';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const certsDir = path.resolve(__dirname, '../certs');
const keyPath = path.join(certsDir, 'key.pem');
const certPath = path.join(certsDir, 'cert.pem');
const extConfigPath = path.join(certsDir, 'openssl_san.cnf');

function findOpenSSL() {
  const candidates = [
    'openssl',
    'C:\\Program Files\\Git\\usr\\bin\\openssl.exe',
    'C:\\Program Files (x86)\\Git\\usr\\bin\\openssl.exe',
    '/usr/bin/openssl',
    '/usr/local/bin/openssl'
  ];

  for (const cmd of candidates) {
    try {
      execSync(`"${cmd}" version`, { stdio: 'ignore' });
      return cmd;
    } catch (_) {}
  }
  return null;
}

const openssl = findOpenSSL();

if (!openssl) {
  console.error('[Error] OpenSSL binary not found.');
  console.error('Please install OpenSSL or Git for Windows, or provide key.pem and cert.pem manually in the backend/certs directory.');
  process.exit(1);
}

if (!fs.existsSync(certsDir)) {
  fs.mkdirSync(certsDir, { recursive: true });
}

// OpenSSL SAN configuration to ensure modern clients (browsers, Android) trust the SAN
const sanConfig = `
[req]
default_bits = 2048
prompt = no
default_md = sha256
distinguished_name = dn
x509_extensions = v3_req

[dn]
C = US
ST = State
L = City
O = JARVIS AI
OU = Development
CN = localhost

[v3_req]
subjectAltName = @alt_names

[alt_names]
DNS.1 = localhost
IP.1 = 127.0.0.1
IP.2 = 0.0.0.0
`;

fs.writeFileSync(extConfigPath, sanConfig.trim(), 'utf8');

console.log(`[JARVIS SSL] Generating local development SSL certificate in ${certsDir}...`);

try {
  execSync(
    `"${openssl}" req -x509 -nodes -days 365 -newkey rsa:2048 -keyout "${keyPath}" -out "${certPath}" -config "${extConfigPath}"`,
    { stdio: 'inherit' }
  );

  console.log('[JARVIS SSL] SSL Certificate generated successfully:');
  console.log(`  Private Key: ${keyPath}`);
  console.log(`  Certificate: ${certPath}`);
  console.log('\nTo run the server with HTTPS, add to backend/.env:');
  console.log('  USE_HTTPS=true');
  console.log('  SSL_KEY_PATH=./certs/key.pem');
  console.log('  SSL_CERT_PATH=./certs/cert.pem');
} catch (err) {
  console.error('[JARVIS SSL Error] Failed to generate certificate:', err.message);
  process.exit(1);
} finally {
  if (fs.existsSync(extConfigPath)) {
    fs.unlinkSync(extConfigPath);
  }
}
