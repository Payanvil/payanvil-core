# Payanvil Core

**Batch USDT (TRC-20) payouts from your desktop. Non-custodial: your private key never leaves your machine.**

This repository contains the source code of every Payanvil component that touches your private key or talks to the network. It exists for one reason: **so you don't have to trust us — you can read the code.**

> 🔨 Payanvil is a desktop application for sending USDT to many recipients in one run — built for teams paying contractors, e-commerce paying suppliers, OTC desks and anyone who doesn't want to hand their keys or their payment data to a web service.

---

## Why this repository exists

Payanvil asks for your private key. In crypto, that is — and should be — a red flag. Wallet drainers are everywhere, and "just trust me" is not an argument.

So instead of asking for trust, we publish the code that matters:

- **every line that touches the private key**
- **every line that signs or sends a transaction**
- **every line that makes a network request**

The GUI and convenience features (reports, Telegram intake, history) remain closed — they are the product we sell. They interact with the blockchain **only through the open code in this repository** and have no access to your key.

## Security architecture

### The private key touches exactly two classes

| Class | What it does with the key |
|---|---|
| `TronClientFactory` | Validates the key, derives your address, creates the trident `ApiWrapper` client. The `char[]` key buffer is **zero-filled immediately after use** (`Arrays.fill(privateKey, '\0')`). |
| `TronClientHolder` | Holds the initialized client for the session. The raw key is not retained. |

That's it. No other class in the entire application ever sees the key.

### Key policy

- The key is entered at runtime and kept **in memory only** — never written to disk, never logged, never sent anywhere except into the official signing library.
- Key buffers use `char[]` (not `String`) and are explicitly zeroed after the client is created.
- Transaction signing happens **locally**, inside [trident](https://github.com/tronprotocol/trident) — the official Tron SDK maintained by the Tron protocol team. Payanvil does not implement its own cryptography.
- Application data (payment queue, transfer history) is stored in a **locally encrypted H2 database**; the encryption password is yours and is never transmitted.

### Complete map of network calls

The application talks to exactly these endpoints — nothing else:

| Endpoint | Purpose | When |
|---|---|---|
| Tron node via trident (TronGrid: mainnet / Nile / Shasta) | Balances, transaction broadcast, confirmations, USDT blacklist check (`isBlackListed` view call) | Every send |
| `api.binance.com` | TRX→USDT rate (single ticker request) | Only when "fee from recipient" mode is used |
| CoinGecko API | Fallback rate source if Binance is unavailable | Fallback only |
| `api.gopluslabs.io` | Recipient address reputation check (GoPlus Malicious Address API) | Pre-flight check before batch confirmation |
| `api.telegram.org` | Telegram bot for payment intake | **Only if you enable the bot** with your own token |

No telemetry. No analytics. No "phone home". If it's not in this table, the application doesn't call it — and you can verify that claim in this repository.

### What the closed part can and cannot do

The closed-source part of Payanvil (GUI, reports, history screen, licensing) is architecturally **downstream** of this core: it prepares payment lists and displays results. All key handling, signing and network I/O goes through the open classes above. The closed code has no independent channel to your key or to the network for transaction purposes.

## What's in this repository

```
tron/       — client factory & holder (key handling), send/batch/confirm services,
              fee estimation, balance checks, USDT blacklist check, network retry
security/   — GoPlus address reputation check (with graceful degradation)
rate/       — TRX/USDT rate providers (Binance + CoinGecko fallback)
chain/      — supported chain model
domain/     — core domain model (WalletTransfer, FeePayer)
```

> **Note:** this repository is currently a *source-available witness* — the code is published for reading and audit, and is compiled as part of the full (closed) application build. Extracting it into a standalone, independently buildable core module with reproducible builds is on the [roadmap](#roadmap).

## Verifying releases

Official builds are distributed **signed**: macOS builds are notarized by Apple, Windows builds are code-signed. Download only from [payanvil.io](https://payanvil.io) or this GitHub organization.

## Roadmap

- [ ] Standalone buildable `payanvil-core` module (this code as a compilable library)
- [ ] Reproducible builds — byte-for-byte verification that the shipped binary contains exactly this code
- [ ] Multi-chain support (v2)

## License

This code is published under the **Business Source License 1.1** — see [LICENSE](LICENSE).

In plain words: you may read, copy, modify and use this code for **any non-production purpose** (audit, research, education). Production/commercial use requires a license from us. On the Change Date the code automatically becomes available under Apache 2.0.

This is not an OSI-approved open source license — and we're upfront about that. The goal of this repository is **transparency and auditability**, not free-to-run software. If that trade-off bothers you, we understand; the code is still here for you to read.

---

**Payanvil** — forge your payouts. 🔨
[payanvil.io](https://payanvil.io) · [@Payanvil](https://x.com/Payanvil) · [t.me/payanvil](https://t.me/payanvil) · hello@payanvil.io
