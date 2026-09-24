# Production Dockerfile for JARVIS AI Assistant Backend
FROM node:20-alpine AS runner

WORKDIR /app

# Set production environment
ENV NODE_ENV=production
ENV PORT=3000
ENV AI_PROVIDER=puter
ENV PUTER_MODEL=gpt-4o

# Install production dependencies
COPY package*.json ./
RUN npm ci --only=production

# Copy application source
COPY src ./src

# Expose application port
EXPOSE 3000

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:${PORT}/health || exit 1

# Run server
CMD ["npm", "start"]
