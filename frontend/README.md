# AnimeChat Frontend

A React frontend for the AnimeChat Spring Boot backend.

---

## 🚀 How to Run Locally

### Prerequisites
- **Node.js** v16+ ([nodejs.org](https://nodejs.org))
- **Spring Boot backend** running on `http://localhost:8080`
- **MySQL** running with the `Anime` database (as configured in `application.properties`)

---

### Step 1 — Start the Backend

```bash
# Navigate to backend directory
cd path/to/backend

# Run Spring Boot
./mvnw spring-boot:run
# or on Windows:
mvnw.cmd spring-boot:run
```

The backend should start on **http://localhost:8080**

---

### Step 2 — Install Frontend Dependencies

```bash
# Navigate to this frontend folder
cd animechat-frontend

# Install packages
npm install
```

---

### Step 3 — Start the Frontend

```bash
npm start
```

The app opens at **http://localhost:3000**

---

## ⚙️ Configuration

The frontend connects to the backend via `REACT_APP_API_URL` in `.env`:

```
REACT_APP_API_URL=http://localhost:8080
```

Change this if your backend runs on a different port.

---

## 🗺️ Pages & Features

| Route | Access | Description |
|-------|--------|-------------|
| `/` | Public | Homepage — browse all anime communities |
| `/login` | Public | Sign in |
| `/register` | Public | Create account |
| `/anime/:id` | Login required | View & create discussion rooms |
| `/chat/:id` | Login required | Live WebSocket chat room |

---

## 🔌 API Endpoints Used

| Method | Endpoint | Auth |
|--------|----------|------|
| GET | `/api/anime-rooms` | Public |
| POST | `/api/anime-rooms?name=...` | Basic Auth |
| GET | `/api/discussion-rooms` | Basic Auth |
| POST | `/api/discussion-rooms` | Basic Auth |
| GET | `/api/messages/discussion/:id` | Basic Auth |
| GET | `/api/users/self` | Basic Auth |
| POST | `/api/users` | Public |
| WS | `/ws` (SockJS/STOMP) | Basic Auth headers |

**Auth method:** HTTP Basic Authentication (username + password sent with every request)

---

## 💬 WebSocket

- Connects to `/ws` via SockJS + STOMP
- Subscribes to `/topic/chat/{roomId}` for live messages
- Publishes to:
  - `/app/chat.send/{roomId}` — send message
  - `/app/chat.edit` — edit message
  - `/app/chat.delete` — delete message

---

## 🛠️ Tech Stack

- React 18 with React Router v6
- Axios for REST API calls
- @stomp/stompjs + sockjs-client for WebSocket
- Pure CSS (no UI library) with CSS variables
- Google Fonts: Bebas Neue + Syne + JetBrains Mono
