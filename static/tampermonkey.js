// ==UserScript==
// @name         快速获取小米云服务cookie
// @namespace    http://tampermonkey.net/
// @version      1.0
// @description  Show and copy cookies on i.mi.com
// @author       OpenAI ChatGPT4o
// @match        https://i.mi.com/*
// @grant        GM_setClipboard
// ==/UserScript==

(function () {
    'use strict';

    // 创建按钮
    const button = document.createElement('button');
    button.textContent = 'Show and Copy Cookies';
    button.style.position = 'fixed';
    button.style.bottom = '10px';
    button.style.left = '10px';
    button.style.zIndex = 1000;
    button.style.backgroundColor = '#007bff';
    button.style.color = '#fff';
    button.style.border = 'none';
    button.style.padding = '10px 20px';
    button.style.borderRadius = '5px';
    button.style.cursor = 'pointer';

    document.body.appendChild(button);

    // 按钮点击事件
    button.addEventListener('click', () => {
        const cookies = document.cookie;
        const jsonEscapedCookies = JSON.stringify(cookies)
            .replace(/'/g, "\'")
            .replace(/"/g, '\"') // 转义引号
            .slice(2, -1);
        alert(`Cookies: ${jsonEscapedCookies}`);
        console.log(`Cookies: ${jsonEscapedCookies}`);
        GM_setClipboard(jsonEscapedCookies); // 复制到剪贴板
    });
})();
