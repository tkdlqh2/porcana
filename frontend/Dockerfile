FROM node:18-alpine
WORKDIR /app
COPY package.json ./
COPY webpack.config.js ./
COPY .babelrc ./
COPY public ./public
COPY src ./src
RUN npm install
RUN npm run build
CMD ["npx", "serve", "-s", "dist"]
