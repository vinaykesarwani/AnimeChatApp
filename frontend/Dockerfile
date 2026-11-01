# Stage 1: Build the React application.
FROM node:22-bookworm-slim AS build
WORKDIR /build

COPY package.json package-lock.json ./
RUN npm ci

# This is the public backend URL used by the browser, not a secret.
ARG REACT_APP_API_URL=http://localhost:8080
ENV REACT_APP_API_URL=${REACT_APP_API_URL}

COPY . .
RUN npm run build

# Stage 2: Serve the built files. This image already uses a non-root user.
FROM nginxinc/nginx-unprivileged:stable-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/build /usr/share/nginx/html

EXPOSE 8080
CMD ["nginx", "-g", "daemon off;"]
