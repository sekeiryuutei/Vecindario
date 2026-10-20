import { access, readFile, writeFile } from 'node:fs/promises';
import { join } from 'node:path';

const publicUrl = process.env['APP_PUBLIC_URL']?.trim();
const appName = process.env['APP_NAME']?.trim();
if (!publicUrl) {
  console.error('APP_PUBLIC_URL is required to configure the native API base.');
  process.exit(1);
}
if (!appName) {
  console.error('APP_NAME is required to configure the native application name.');
  process.exit(1);
}

let url;
try {
  url = new URL(publicUrl);
} catch {
  console.error(`APP_PUBLIC_URL is not a valid URL: ${publicUrl}`);
  process.exit(1);
}
if (url.protocol !== 'https:') {
  console.error('APP_PUBLIC_URL must use HTTPS for native builds.');
  process.exit(1);
}

const escapeXml = (value) => value
  .replace(/&/g, '&amp;')
  .replace(/</g, '&lt;')
  .replace(/>/g, '&gt;')
  .replace(/"/g, '&quot;')
  .replace(/'/g, '&apos;');

url.pathname = `${url.pathname.replace(/\/$/, '')}/`;
const apiBase = new URL('api/v1', url).toString().replace(/\/$/, '');
const runtimeTargets = [
  join(process.cwd(), 'android', 'app', 'src', 'main', 'assets', 'public', 'runtime-config.json'),
  join(process.cwd(), 'ios', 'App', 'App', 'public', 'runtime-config.json')
];
for (const target of runtimeTargets) {
  await access(target);
  await writeFile(target, `${JSON.stringify({ apiBase }, null, 2)}\n`, 'utf8');
}

const androidStrings = join(process.cwd(), 'android', 'app', 'src', 'main', 'res', 'values', 'strings.xml');
const androidXml = await readFile(androidStrings, 'utf8');
if (!/<string name="app_name">[^<]*<\/string>/.test(androidXml)) {
  throw new Error('Android app_name resource was not found.');
}
const androidUpdated = androidXml
  .replace(/(<string name="app_name">)[^<]*(<\/string>)/, `$1${escapeXml(appName)}$2`)
  .replace(/(<string name="title_activity_main">)[^<]*(<\/string>)/, `$1${escapeXml(appName)}$2`);
await writeFile(androidStrings, androidUpdated, 'utf8');

const iosPlist = join(process.cwd(), 'ios', 'App', 'App', 'Info.plist');
const plist = await readFile(iosPlist, 'utf8');
if (!/<key>CFBundleDisplayName<\/key>\s*<string>[^<]*<\/string>/.test(plist)) {
  throw new Error('iOS CFBundleDisplayName was not found.');
}
const plistUpdated = plist.replace(
  /(<key>CFBundleDisplayName<\/key>\s*<string>)[^<]*(<\/string>)/,
  `$1${escapeXml(appName)}$2`
);
await writeFile(iosPlist, plistUpdated, 'utf8');

console.log(`Native API base configured: ${apiBase}`);
console.log(`Native application name configured: ${appName}`);
