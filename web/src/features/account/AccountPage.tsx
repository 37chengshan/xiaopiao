import { motion, useReducedMotion } from "framer-motion";
import { useEffect, useState, type FormEvent } from "react";
import { ArrowRight, ArrowLeft, Check, Loader2 } from "lucide-react";
import { useAccount, accountRequest, refreshAccount } from "@/services/account";
import { useLocale } from "@/lib/strings";
import { Link } from "@tanstack/react-router";

type Mode = "login" | "register" | "verify" | "forgot" | "reset";
/** Signal reference: split composition, quiet field fills, rounded pane, one clear submit action. */
export function AccountPage() {
  const reduce = useReducedMotion();
  const locale = useLocale();
  const en = locale === "en";
  const c = (zh: string, english: string) => en ? english : zh;
  const user = useAccount();
  const [mode, setMode] = useState<Mode>("login");
  const [identifier, setIdentifier] = useState("");
  const [email, setEmail] = useState("");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [code, setCode] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  useEffect(() => { void refreshAccount(); }, []);
  useEffect(() => { if (user && !user.verified) setMode("verify"); }, [user]);
  const change = (next: Mode) => { setMode(next); setError(""); setNotice(""); setPassword(""); };
  async function submit(event?: FormEvent, override?: "resend" | "logout") {
    event?.preventDefault(); if (busy) return;
    setBusy(true); setError(""); setNotice("");
    const action = override ?? mode;
    try {
      const fields: Record<string, string> = action === "login" ? { identifier, password } : action === "register" ? { email, username, password } : action === "verify" ? { code } : action === "forgot" ? { email } : action === "reset" ? { email, code, password } : {};
      await accountRequest(action, fields); setPassword("");
      if (action === "register") { setMode("verify"); setNotice(c("验证码已发送，10 分钟内有效。", "Code sent. It expires in 10 minutes.")); }
      if (action === "forgot") { setMode("reset"); setNotice(c("如果邮箱已注册，我们会发送验证码。", "If registered, a code will arrive by email.")); }
      if (action === "reset") { setMode("login"); setNotice(c("密码已更新，请重新登录。", "Password updated. Please sign in.")); }
      if (action === "resend") setNotice(c("新的验证码已发送。", "A new code has been sent."));
      if (action === "logout") setMode("login");
    } catch (e) { setError(e instanceof Error ? e.message : c("连接失败，请稍后重试。", "Connection failed. Please try again.")); }
    finally { setBusy(false); }
  }
  const inputClass = "mt-2 h-12 w-full rounded-xl border border-d-hairline bg-d-fill/40 px-4 text-[15px] outline-none transition-shadow focus:border-d-ink-2 focus:ring-2 focus:ring-d-fill disabled:opacity-60";
  const labelClass = "block text-[13px] font-medium text-d-ink-2";
  return <main className="min-h-dvh bg-d-canvas text-d-ink lg:grid lg:grid-cols-[1.08fr_1fr]">
    <section className="relative hidden overflow-hidden bg-d-fill p-12 lg:flex lg:min-h-dvh lg:flex-col lg:justify-between" aria-label={c("今日小票", "Daily Receipt")}>
      <Link to="/" className="w-fit text-sm font-semibold">今日小票 / DAILY RECEIPT</Link>
      <div className="relative mx-auto my-12 w-[min(80%,360px)] rotate-[-6deg] rounded-t-xl bg-d-surface px-9 py-10 shadow-xl" aria-hidden="true">
        <div className="mb-8 text-center text-xs tracking-[0.18em] text-d-ink-3">TODAY, WELL KEPT.</div>
        <p className="text-3xl font-semibold leading-relaxed">{c("日子不必算计，\n也可以清清楚楚。", "Small moments.\nA clearer picture.")}</p>
        <div className="my-8 border-t border-d-hairline" />
        {["一杯咖啡 / A coffee", "一次出发 / A little journey", "一点日常 / Everyday life"].map(line => <p key={line} className="my-3 text-sm text-d-ink-2">{line}</p>)}
        <div className="mt-10 text-right text-xs text-d-ink-3">KEEP THE LITTLE THINGS.</div>
      </div>
      <div><p className="text-xs text-d-ink-2">{c("原始账单留在本机 · 数据贡献由你决定", "Original receipts stay local · Sharing is your choice")}</p><h1 className="mt-4 text-[clamp(30px,3.1vw,48px)] font-medium leading-tight">{c("把每一天，\n收进一张小票。", "A little receipt.\nA day well kept.")}</h1></div>
    </section>
    <section className="flex min-h-dvh items-center justify-center p-6 sm:p-12 lg:m-3 lg:min-h-0 lg:rounded-[28px] lg:border lg:border-d-hairline">
      <div className="w-full max-w-[390px]">
        <Link to="/app" className="mb-12 inline-flex items-center gap-2 text-xs text-d-ink-2"><ArrowLeft size={14}/>{c("继续使用小票", "Back to your receipt")}</Link>
        {user?.verified ? <><span className="mb-5 grid size-12 place-items-center rounded-full bg-d-fill"><Check size={24}/></span><h2 className="text-3xl font-medium">{c(`你好，${user.username}`, `Hello, ${user.username}`)}</h2><p className="mt-4 text-d-ink-2">{user.email}</p><p className="mt-2 text-sm text-d-ink-2">{c("邮箱已验证，账号已就绪。", "Email verified. Your account is ready.")}</p><Link to="/app/ai" className="mt-8 flex h-12 items-center justify-center rounded-full bg-d-ink text-d-canvas">{c("开始对话", "Start a conversation")}</Link><button onClick={() => void submit(undefined, "logout")} disabled={busy} className="mt-4 h-10 w-full text-sm text-d-ink-2">{c("退出登录", "Sign out")}</button></> : <>
          <h2 className="text-[32px] font-medium tracking-[-0.02em]">{({ login: c("欢迎回来", "Welcome back"), register: c("让日常，有迹可循。", "Keep the little things."), verify: c("验证你的邮箱", "Check your inbox"), forgot: c("找回你的账号", "Recover your account"), reset: c("设置新密码", "Set a new password") })[mode]}</h2>
          <p className="mb-8 mt-3 text-sm leading-relaxed text-d-ink-2">{mode === "verify" ? c(`验证码已发往 ${user?.email ?? email}，请在 10 分钟内填写。`, `Enter the code sent to ${user?.email ?? email} within 10 minutes.`) : c("同一个账号，手机和网页都能登录。", "Use the same account on your phone and the web.")}</p>
          <motion.form key={mode} onSubmit={submit} className="space-y-5" initial={reduce ? false : { opacity: 0.7, transform: "translateY(4px)" }} animate={{ opacity: 1, transform: "translateY(0px)" }} transition={{ duration: reduce ? 0 : 0.18, ease: [0.23, 1, 0.32, 1] }}>
            {mode === "login" && <label className={labelClass}>{c("邮箱或用户名", "Email or username")}<input className={inputClass} value={identifier} onChange={e => setIdentifier(e.target.value)} required autoComplete="username" maxLength={254} disabled={busy}/></label>}
            {["register", "forgot", "reset"].includes(mode) && <label className={labelClass}>{c("邮箱", "Email")}<input type="email" className={inputClass} value={email} onChange={e => setEmail(e.target.value)} required autoComplete="email" maxLength={254} disabled={busy}/></label>}
            {mode === "register" && <label className={labelClass}>{c("用户名", "Username")}<input className={inputClass} value={username} onChange={e => setUsername(e.target.value.toLowerCase())} pattern="[a-z][a-z0-9_]{2,29}" required autoComplete="username" disabled={busy}/><span className="mt-1.5 block text-xs font-normal text-d-ink-3">{c("3–30 位字母、数字或下划线，以字母开头", "3–30 letters, digits or underscores; start with a letter")}</span></label>}
            {["login", "register", "reset"].includes(mode) && <label className={labelClass}>{c("密码", "Password")}<input type="password" className={inputClass} value={password} onChange={e => setPassword(e.target.value)} minLength={15} maxLength={128} autoComplete={mode === "login" ? "current-password" : "new-password"} required disabled={busy}/><span className="mt-1.5 block text-xs font-normal text-d-ink-3">{c("至少 15 个字符，可使用空格和中文", "At least 15 characters. Spaces and Unicode welcome.")}</span></label>}
            {["verify", "reset"].includes(mode) && <label className={labelClass}>{c("邮箱验证码", "Email code")}<input className={inputClass} value={code} onChange={e => setCode(e.target.value.replace(/\D/g, "").slice(0, 6))} inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{6}" required disabled={busy}/></label>}
            {error && <p role="alert" className="text-sm text-red-600">{error}</p>}{notice && <p role="status" className="text-sm text-d-ink-2">{notice}</p>}
            <button type="submit" disabled={busy} className="flex h-12 w-full items-center justify-center gap-3 rounded-full bg-d-ink text-sm font-medium text-d-canvas transition-opacity disabled:opacity-50">{busy ? <Loader2 size={18} className="animate-spin motion-reduce:animate-none"/> : <>{({ login: c("登录", "Sign in"), register: c("创建账号", "Create account"), verify: c("验证邮箱", "Verify email"), forgot: c("发送验证码", "Send code"), reset: c("重设密码", "Reset password") })[mode]}<ArrowRight size={16}/></>}</button>
          </motion.form>
          <div className="mt-6 flex justify-between gap-4 text-xs text-d-ink-2"><button disabled={busy} onClick={() => change(mode === "login" ? "register" : "login")}>{mode === "login" ? c("还没有账号？创建一个", "New here? Create an account") : c("返回登录", "Back to sign in")}</button><button disabled={busy} onClick={() => mode === "verify" ? void submit(undefined, "resend") : change("forgot")}>{mode === "verify" ? c("重新发送", "Resend code") : c("忘记密码？", "Forgot password?")}</button></div>
          <p className="mt-12 text-xs leading-relaxed text-d-ink-3">{c("不登录也能继续本机记账。登录不会自动上传账单原文或开启数据贡献。", "You can keep a local ledger without signing in. Signing in does not upload original receipts or enable sharing.")}</p>
        </>}
        {user?.verified && error && <p role="alert" className="mt-4 text-sm text-red-600">{error}</p>}
      </div>
    </section>
  </main>;
}
