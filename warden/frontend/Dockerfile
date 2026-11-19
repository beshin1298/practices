# Frontend image: build the React app, serve the static files with nginx (which also proxies /api).
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# Off by default: the demo-account shortcuts must not ship in a real build.
ARG VITE_SHOW_DEMO_ACCOUNTS=false
ENV VITE_SHOW_DEMO_ACCOUNTS=$VITE_SHOW_DEMO_ACCOUNTS
RUN npm run build

# Runs as the unprivileged "nginx" user (uid 101) and listens on 8080, like the backend's non-root user.
FROM nginxinc/nginx-unprivileged:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
