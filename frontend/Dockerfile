FROM node:20-alpine AS deps
WORKDIR /app

COPY package.json package-lock.json* ./
RUN npm ci

FROM node:20-alpine AS builder
WORKDIR /app

COPY --from=deps /app/node_modules ./node_modules
COPY . .

ENV NEXT_TELEMETRY_DISABLED=1
RUN npm run build

FROM node:20-alpine AS runner
WORKDIR /app

ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1

RUN addgroup -S appgroup && adduser -S nextjs -G appgroup

COPY --from=builder --chown=nextjs:appgroup /app/public ./public
COPY --from=builder --chown=nextjs:appgroup /app/.next ./.next
COPY --from=builder --chown=nextjs:appgroup /app/node_modules ./node_modules
COPY --from=builder --chown=nextjs:appgroup /app/package.json ./package.json
COPY --from=builder --chown=nextjs:appgroup /app/next.config.* ./

USER nextjs

EXPOSE 3000
ENV PORT=3000

HEALTHCHECK --interval=30s --timeout=10s --start-period=15s --retries=3 
  CMD wget --no-verbose --tries=1 --spider http://localhost:3000/api/health || exit 1

CMD ["npm", "start"]
