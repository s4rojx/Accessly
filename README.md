
<div align="center">

# Assessly

**A high-concurrency, fault-tolerant online assessment platform.**

Built with Spring Boot, React, PostgreSQL, and Redis.

![Java](https://img.shields.io/badge/Java-21-orange?style=flat-square&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3.13-brightgreen?style=flat-square&logo=spring)
![React](https://img.shields.io/badge/React-18-blue?style=flat-square&logo=react)
![TypeScript](https://img.shields.io/badge/TypeScript-5.0-blue?style=flat-square&logo=typescript)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue?style=flat-square&logo=postgresql)
![Redis](https://img.shields.io/badge/Redis-7-red?style=flat-square&logo=redis)

</div>

---

##  Overview

Assessly is an enterprise-grade assessment platform designed to survive the intense load of synchronized campus exams. Traditional LMS platforms often buckle under the "thundering herd" of students starting an exam simultaneously, or when a thousand students hit "Submit" at exactly 11:00:00 AM.

Assessly solves this by utilizing a modular monolith architecture that pushes hot-path mutations to **Redis** via atomic Lua scripts, ensuring ultra-low latency and shielding the durable **PostgreSQL** source of truth from connection pool starvation.

---

##  Key Features

- **Cryptographic Shuffling:** Per-student deterministic question and option permutations using HMAC-SHA256, preventing answer sharing while allowing consistent paper reconstruction on refresh.
- **Server-Authoritative Timing:** Client timers are strictly for display. The server enforces strict cutoffs using Redis `TIME` and PostgreSQL row locks, making clock manipulation impossible.
- **Redis State Engine:** Lightning-fast `SAVE_AND_NEXT` and `MARK_FOR_REVIEW` actions backed by atomic Lua scripts.
- **Fault-Tolerant Recovery:** Redis-to-PostgreSQL asynchronous synchronization every 30 seconds bounds state loss in the event of a cache failure.
- **Secure by Design:** Double-submit CSRF protection, strict role isolation, short-lived JWTs with rotating HttpOnly refresh cookies, and zero-knowledge client payload projections (correct answers never reach the browser).
- **Admin Analytics:** Synchronous, on-demand result aggregation and score distributions computed directly from PostgreSQL.

---

##  Architecture

```text
Client (React/TS)
   │ (HTTPS REST)
   ▼
Nginx (Load Balancer)
   │
   ▼
Spring Boot App (Java 21)
   ├──▶ Redis (Active Attempt State, Rate Limiting, Locks)
   └──▶ PostgreSQL (Durable Source of Truth, Analytics)
```
