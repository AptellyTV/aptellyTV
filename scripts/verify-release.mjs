#!/usr/bin/env node

import { createHash, createPublicKey, verify } from "node:crypto";
import { readFile, readdir, stat } from "node:fs/promises";
import { resolve } from "node:path";

const args = new Map();
for (let index = 2; index < process.argv.length; index += 2) {
  args.set(process.argv[index], process.argv[index + 1]);
}
const tag = args.get("--tag");
const directory = args.get("--dir");
if (!tag || !directory || !/^v\d+\.\d+\.\d+$/.test(tag)) {
  throw new Error("Usage: verify-release.mjs --tag v<version> --dir <assets-directory>");
}

const versionName = tag.slice(1);
const apkName = `Aptelly-${versionName}-release.apk`;
const shaName = `Aptelly-${versionName}-release.sha256`;
const envelopeName = `Aptelly-${versionName}-release.json`;
const expectedNames = [apkName, envelopeName, shaName].sort();
const actualNames = (await readdir(directory)).sort();
if (JSON.stringify(actualNames) !== JSON.stringify(expectedNames)) {
  throw new Error(`Release assets must be exactly: ${expectedNames.join(", ")}`);
}

const apkPath = resolve(directory, apkName);
const apk = await readFile(apkPath);
const apkSize = (await stat(apkPath)).size;
const apkSha256 = createHash("sha256").update(apk).digest("hex");
const shaText = (await readFile(resolve(directory, shaName), "utf8")).trim();
if (shaText !== `${apkSha256}  ${apkName}`) {
  throw new Error("APK SHA-256 companion file does not match the APK");
}

const envelope = JSON.parse(await readFile(resolve(directory, envelopeName), "utf8"));
const payloadBytes = Buffer.from(envelope.payload || "", "base64");
const signatureBytes = Buffer.from(envelope.signature || "", "base64");
const publicKey = createPublicKey({
  key: Buffer.from(
    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAhaIEcL7Vpe+L3yK4CsNkPpZ8OZMjLU6JjSLgeZNvR+eA/D1NnV89EJrkaZ/xsnPYXvdRabbGilE5aR+loUpW3SGG+c9PScSQfkrMiQlgSXg2oLrQLTR2plOoI5Mvpw8g5XxhuLDBmTZpN972rJSQ/+kRIvvm8nJP+5djh3RN4OJfmvtVfhS5y/QiJyTaLniM459QJ1wP1ud5wGwClBmev94srv+VDwC/2/LCC79bH6hJK+cXmq2BkA55A4TAT6Pw64NmSEdZ8iZMUTu9k7tZ8H6SvlqD2oUsNGGTSsxHqiFlJcsNYVCf41yExbv9E8kZ6Oq4/9XstMozeG+3E1CfYBOgbMOCO+937LKW/B40En0cpGqnk0i6w+K0Akk024h6N+bjRnhP7lnEs76umDcE612mn14uANCaW6XtbU5tTIifVOhTjPiiMCo7EKuSOqhxevaMDY+ADLqx3JrXD2PS++UylwiSVhCm7vExRNy/uvRJ0LjH/6acMO5rOh1O18lf27A++yZLUx+7lTsQaJ6IEy5y3usB6ofI5uPBDI7malmICaIw4WW0bn0T2UR1FsOaiCYDsZDYKrLzu95hlLeJl7oFiT1sL3IjENx9436KktyHJ/ENxk7oLn3vLWLXsfSRARqyJk3KTQL0JgY+D/ZMYso2GDiqWn/tdYPkKnbpDhMCAwEAAQ==",
    "base64"
  ),
  format: "der",
  type: "spki",
});
if (!verify("RSA-SHA256", payloadBytes, publicKey, signatureBytes)) {
  throw new Error("Update envelope signature is invalid");
}

const payload = JSON.parse(payloadBytes.toString("utf8"));
const required = {
  packageName: "app.aptelly.tv",
  versionName,
  minSdk: 26,
  apkUrl: `https://github.com/AptellyTV/aptellyTV/releases/download/${tag}/${apkName}`,
  sizeBytes: apkSize,
  sha256: apkSha256,
  signingCertificateSha256: "e9ec43199ab7c9872328037ada9f5ce08d367df25aef8732e015b06ea28be375",
};
for (const [key, value] of Object.entries(required)) {
  if (payload[key] !== value) throw new Error(`Signed payload field ${key} is invalid`);
}
if (!Number.isInteger(payload.versionCode) || payload.versionCode <= 0) {
  throw new Error("Signed payload versionCode is invalid");
}
console.log(`Verified ${tag}: ${apkName} (${apkSize} bytes, ${apkSha256})`);
