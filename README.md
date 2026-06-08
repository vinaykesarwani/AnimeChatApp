# 💬 Real-Time Chat Application

A scalable full-stack real-time chat platform built using **Spring Boot**, **React.js**, **MySQL**, and **WebSockets**, featuring **OAuth 2.0 authentication**, **semantic room discovery powered by Sentence Transformers**, and performance-tested messaging infrastructure capable of handling hundreds of concurrent users.

## Live Demo

* Frontend: [Netlify Deployment]
* Backend: [Railway Deployment]

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

# Automated Deployment

* Frontend deployed on Netlify.
* Backend deployed on Railway.
* Git-based CI/CD workflow for automated builds and deployments.

---

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

# Testing & Deployment

* k6 (Load Testing)
* Railway
* Netlify
* GitHub Actions

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

Load testing was performed using **k6** to evaluate system scalability under concurrent user traffic.

# Test Results

| Metric                     | Result       |
| -------------------------- | ------------ |
| Concurrent Virtual Users   | 500          |
| Connection Success Rate    | 100%         |
| Messages Delivered         | ~5.4 Million |
| Message Loss               | 0            |
| Average Fanout Latency     | ~155 ms      |
| Average Round-Trip Latency | ~162 ms      |

---

## ⚙️ Local Setup

# Prerequisites

* Java 21+
* Node.js 18+
* MySQL 8+
* Maven

# Backend Setup

```bash
git clone https://github.com/your-username/project.git

cd backend

mvn clean install

mvn spring-boot:run
```

# Frontend Setup

```bash
cd frontend

npm install

npm start
```

## 📈 Future Enhancements

AI-Powered Discussion Recommendations: Uses AI-generated rolling chat summaries to suggest discussion rooms that align with users' interests and ongoing conversations.
Community Anime Recommendations: Enables users to request anime suggestions based on descriptions, with community responses ranked through an upvote-based reputation system.
Enhanced User Profiles: Provides personalized profiles showcasing activity statistics, contributions, interests, and community reputation.
Push Notifications: Delivers real-time notifications for messages, mentions, room activity, and community interactions.

---

## 👨‍💻 Author

**Vinay Kesarwani**
