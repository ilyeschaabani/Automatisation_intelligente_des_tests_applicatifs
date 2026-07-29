FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm install --legacy-peer-deps
COPY . .
RUN mkdir -p public
RUN npm run build

FROM node:20-alpine
RUN apk add --no-cache curl
WORKDIR /app
COPY --from=builder /app/.next/standalone ./
COPY --from=builder /app/.next/static ./.next/static
COPY --from=builder /app/public ./public
EXPOSE 3000
# Next.js standalone server.js binds to process.env.HOSTNAME. Docker sets HOSTNAME
# to the container id, so without this Next listens only on the container's eth0 IP
# and an internal healthcheck on localhost:3000 is refused. Force all interfaces.
ENV HOSTNAME=0.0.0.0
ENV PORT=3000
CMD ["node", "server.js"]
