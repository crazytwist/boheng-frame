# boheng 前端镜像（本地已 pnpm build 产出 web/dist，只做 Nginx 托管）
FROM nginx:1.27-alpine

COPY ./dist /usr/share/nginx/html
COPY ./nginx.conf /etc/nginx/conf.d/default.conf

ENV TZ=Asia/Shanghai

EXPOSE 80
