#!/bin/bash

# 快速启动前端开发服务器

cd web

echo "安装依赖..."
yarn install

echo ""
echo "启动前端开发服务器..."
echo "访问地址: http://localhost:5173"
echo ""

yarn dev
