# ===== Stage 1: install dependencies =====
FROM node:22-alpine AS deps
WORKDIR /app
# Copy only the manifests first, so this layer is cached and re-runs only
# when dependencies change — not on every code edit.
COPY package.json package-lock.json ./
RUN npm ci

# ===== Stage 2: build the app =====
FROM node:22-alpine AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN npm run build

# ===== Stage 3: minimal runtime =====
FROM node:22-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production
ENV PORT=3000
ENV HOSTNAME="0.0.0.0"

# 'standalone' bundles a tiny server plus only the deps it traced — but it
# does NOT include public/ or .next/static, so we copy those in explicitly.
COPY --from=build /app/public ./public
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static

EXPOSE 3000
CMD ["node", "server.js"]