FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .

# Baked in at build time (Vite requirement) - these point the browser at
# the host-published ports of the other services, since the browser runs
# outside the Docker network and can't resolve container-only hostnames
# like "core-reactor". Server-to-server calls (astromech -> core-reactor,
# astromech -> ollama) use internal service names instead - see docker-compose.yml.
ARG VITE_API_BASE=http://localhost:8080/api
ARG VITE_WS_BASE=http://localhost:8080/ws
ARG VITE_DROID_BASE=http://localhost:5001/api
ENV VITE_API_BASE=$VITE_API_BASE
ENV VITE_WS_BASE=$VITE_WS_BASE
ENV VITE_DROID_BASE=$VITE_DROID_BASE

RUN npm run build

FROM nginx:alpine
RUN apk add --no-cache curl
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
