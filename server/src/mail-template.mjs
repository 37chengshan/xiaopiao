/** Transactional mail: fixed markup, no tracking or user-controlled HTML. */
export function verificationHtml(purpose, code) {
  if (!['verify', 'reset'].includes(purpose) || !/^\d{6}$/.test(code)) throw new Error('INVALID_MAIL');
  const reset = purpose === 'reset';
  return `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
<body style="margin:0;padding:32px 16px;background:#f5f5f7;color:#1d1d1f;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Arial,sans-serif">
<table role="presentation" width="100%" cellspacing="0" cellpadding="0"><tr><td align="center">
<table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="max-width:520px;background:#fff;border-radius:24px"><tr><td style="padding:36px 28px">
<p style="margin:0 0 32px;color:#6e6e73;font-size:14px">今日小票 · DAILY RECEIPT</p>
<h1 style="margin:0 0 16px;font-size:24px;line-height:1.4">${reset ? '重新找回你的日常。' : '让日常，有迹可循。'}</h1>
<p style="margin:0 0 24px;font-size:16px;line-height:1.7;color:#6e6e73">${reset ? '你正在重设今日小票密码。' : '请用下面的验证码确认你的邮箱。'}</p>
<p style="margin:0;font-size:13px;color:#6e6e73">邮箱验证码 / Verification code</p>
<p style="margin:12px 0 20px;padding:20px 12px;background:#f5f5f7;border-radius:14px;text-align:center;font-family:ui-monospace,Menlo,Consolas,monospace;font-size:34px;font-weight:700;letter-spacing:8px;color:#1d1d1f">${code}</p>
<p style="margin:0;font-size:14px;line-height:1.8;color:#6e6e73">10 分钟内有效。请勿分享给他人。<br>Valid for 10 minutes. Keep this code private.</p>
<p style="margin:28px 0 0;padding-top:24px;border-top:1px dashed #d2d2d7;font-size:12px;line-height:1.8;color:#86868b">如果不是你操作的，请忽略本邮件。<br>If you did not request this email, you can safely ignore it.</p>
</td></tr></table><p style="margin:20px 0 0;font-size:12px;color:#86868b">每天一张，会说话的小票。</p>
</td></tr></table></body></html>`;
}
