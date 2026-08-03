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
const previousDirectory = args.get("--previous-dir");
if (!tag || !directory) {
  throw new Error("Usage: verify-release.mjs --tag v<version> --dir <assets-directory>");
}

const root = resolve(new URL("..", import.meta.url).pathname);
const buildFile = await readFile(resolve(root, "app/build.gradle.kts"), "utf8");
const versionName = buildFile.match(/versionName\s*=\s*"([^"]+)"/)?.[1];
const versionCode = Number(buildFile.match(/versionCode\s*=\s*(\d+)/)?.[1]);
if (!versionName || !Number.isInteger(versionCode) || tag !== `v${versionName}`) {
  throw new Error("Release tag and public source version do not match");
}

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
const shaParts = shaText.split(/\s+/);
if (shaParts[0] !== apkSha256 || shaParts.at(-1) !== apkName) {
  throw new Error("APK SHA-256 companion file does not match the APK");
}

const envelope = JSON.parse(await readFile(resolve(directory, envelopeName), "utf8"));
if (typeof envelope.payload !== "string" || typeof envelope.signature !== "string") {
  throw new Error("Invalid signed update envelope");
}
const payloadBytes = Buffer.from(envelope.payload, "base64");
const signatureBytes = Buffer.from(envelope.signature, "base64");
const payload = JSON.parse(payloadBytes.toString("utf8"));

const verifierSource = await readFile(
  resolve(root, "app/src/main/java/app/aptelly/tv/update/AptellyUpdateVerifier.java"),
  "utf8"
);
const publicKeyBase64 = verifierSource.match(
  /RELEASE_PUBLIC_KEY_BASE64\s*=\s*"([A-Za-z0-9+/=]+)"/
)?.[1];
const certificateSha256 = verifierSource.match(
  /RELEASE_CERTIFICATE_SHA256\s*=\s*\n?\s*"([0-9a-f]{64})"/
)?.[1];
if (!publicKeyBase64 || !certificateSha256) {
  throw new Error("Unable to read the pinned release identity from client source");
}
const publicKey = createPublicKey({
  key: Buffer.from(publicKeyBase64, "base64"),
  format: "der",
  type: "spki",
});
if (!verify("RSA-SHA256", payloadBytes, publicKey, signatureBytes)) {
  throw new Error("Update envelope signature is invalid");
}

const expectedUrl =
  `https://github.com/AptellyTV/aptellyTV/releases/download/${tag}/${apkName}`;
const required = {
  packageName: "app.aptelly.tv",
  versionCode,
  versionName,
  minSdk: 26,
  apkUrl: expectedUrl,
  sizeBytes: apkSize,
  sha256: apkSha256,
  signingCertificateSha256: certificateSha256,
};
for (const [key, value] of Object.entries(required)) {
  if (payload[key] !== value) {
    throw new Error(`Signed payload field ${key} does not match the release`);
  }
}

if (previousDirectory) {
  const previousEnvelopeNames = (await readdir(previousDirectory))
    .filter((name) => name.endsWith("-release.json"));
  if (previousEnvelopeNames.length !== 1) {
    throw new Error("Previous release must provide exactly one signed JSON envelope");
  }
  const previousEnvelope = JSON.parse(
    await readFile(resolve(previousDirectory, previousEnvelopeNames[0]), "utf8")
  );
  const previousPayload = JSON.parse(
    Buffer.from(previousEnvelope.payload, "base64").toString("utf8")
  );
  if (!Number.isInteger(previousPayload.versionCode) ||
      versionCode <= previousPayload.versionCode) {
    throw new Error(
      `versionCode ${versionCode} must be greater than previous ${previousPayload.versionCode}`
    );
  }
}

console.log(`Verified ${tag}: ${apkName} (${apkSize} bytes, ${apkSha256})`);
