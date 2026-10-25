# 💬 Real-Time Chat Application

A scalable full-stack real-time chat platform built using **Spring Boot**, **React.js**, **MySQL**, and **WebSockets**, featuring **OAuth 2.0 authentication**, **semantic room discovery powered by Sentence Transformers**, and performance-tested messaging infrastructure tested with up to 100 concurrent virtual users locally.

## Key Features

# Real-Time Group Messaging

* Built with **Spring WebSocket (STOMP)** for low-latency bidirectional communication.
* Supports instant message delivery across chat rooms.
* Topic-based message broadcasting architecture.

# OAuth 2.0 Authentication

* Secure user authentication using OAuth 2.0.
* Seamless social login experience.
* Protected endpoints with Spring Security.

# Semantic Room Discovery

* Integrated **Sentence Transformers (all-MiniLM-L6-v2)** to generate semantic embeddings.
* Prevents creation of near-duplicate chat rooms by comparing room intent rather than exact text.
* Improves room discoverability and user experience.

# Scalable Backend Architecture

* REST APIs built with Spring Boot.
* MySQL database for persistent storage.
* Layered architecture following service-repository-controller pattern.

## Tech Stack

# Frontend

* React.js
* JavaScript
* WebSocket Client (SockJS + STOMP)

# Backend

* Spring Boot
* Spring WebSocket
* Spring Security
* OAuth 2.0
* Spring Data JPA

# Database

* MySQL

# NLP & AI

* Sentence Transformers
* all-MiniLM-L6-v2

# Testing

* JUnit and Mockito
* React Testing Library
* k6 (Load Testing)

---

## Semantic Room Matching

Traditional chat applications rely on exact keyword matching when creating or searching rooms.

This project uses **Sentence Transformer embeddings** to understand the semantic meaning of room names and descriptions.

# Example

| User Input                 | Existing Room                |
| -------------------------- | ---------------------------- |
| "Java Backend Development" | "Spring Boot Developers"     |
| "AI Discussion Group"      | "Machine Learning Community" |

Instead of treating these as unrelated strings, the system identifies semantic similarity and recommends existing rooms, reducing duplication and improving community discovery.

---

## 📊 Performance Testing

k6 uses the app's JWT login and SockJS/STOMP transport. Smoke, steady-load, stress, spike and soak tests passed for one busy room and multiple rooms.

| Workload | Concurrent VUs | Messages/sec | Echo p95 | Echo p99 | Echo success |
| --- | --- | --- | --- | --- | --- |
| One busy room | 100 | 81.02 | 60 ms | 170 ms | 100% |
| Five rooms | 100 | 82.07 | 24 ms | 54 ms | 100% |

These are local MySQL-backed steady-load results with a five-minute hold. Echo latency measures a sent message returning to its sender after persistence and broadcast. It does not measure delivery to every subscriber.

See [test commands](k6/RUN_TESTS.md) and [full results](k6/RESULTS.md) for settings, the other scenarios and measurement limits.

---

## ⚙️ Local Setup

# Prerequisites

* Java 21+
* Node.js 18+
* MySQL 8+
* Maven

# Backend Setup

Copy `backend/.env.example` to `backend/.env` and fill in your MySQL settings and JWT secret. Keep this file private.

For Google login, set real `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` values. Register `http://localhost:8080/login/oauth2/code/google` as an authorized redirect URI in your Google OAuth client. Use your backend URL if the port or host differs. `FRONTEND_URL` defaults to `http://localhost:3000`.

```powershell
cd backend
./mvnw.cmd clean package
java -jar ./target/backend-0.0.1-SNAPSHOT.jar
```

The older `application-local.properties` setup is also supported with the `local` Spring profile. Its `app.jwt.*` and `app.frontend-url` settings remain compatible.

# Frontend Setup

In a second terminal:

```powershell
cd frontend
npm install
npm start
```

Set `REACT_APP_API_URL` if the backend is not running at `http://localhost:8080`.

The login and registration pages both have **Continue with Google**. Returning Google users keep their existing account; the first Google login creates a user account. Username/password and GitHub login remain available.

## 📈 Future Enhancements

AI-Powered Discussion Recommendations: Uses AI-generated rolling chat summaries to suggest discussion rooms that align with users' interests and ongoing conversations.
Community Anime Recommendations: Enables users to request anime suggestions based on descriptions, with community responses ranked through an upvote-based reputation system.
Enhanced User Profiles: Provides personalized profiles showcasing activity statistics, contributions, interests, and community reputation.
Push Notifications: Delivers real-time notifications for messages, mentions, room activity, and community interactions.

---

## 👨‍💻 Author

**Vinay Kesarwani**
