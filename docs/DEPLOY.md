# 今日小票 API 生产部署

部署日期：2026-10-29（America/Los_Angeles；服务器已是 2026-10-30）。分支 `deploy/huawei`，基于 `ui/app-v10` 的 `8ca7ddf`，只改 `server/` 与 `docs/`。

生产 base URL：**https://xiaopiao.citygenius.top**。该域名提供 API，Web 比赛提交物仍走既有 QMuse 流程。

## 实例与网络

| 项目 | 实际配置 |
|---|---|
| 云主机 | 华为云 Flexus ECS `hcss_ecs_a2ff` |
| 地域 / AZ | `cn-south-1` / `cn-south-1g` |
| 实例 ID | `ae26e406-d31a-4fc9-a2f0-7ad8febef668` |
| 公网 IPv4 | `110.41.84.190` |
| 系统 | Ubuntu 24.04.4 LTS x86_64 |
| API | `127.0.0.1:8787`，`xiaopiao-api.service`，用户 `xiaopiao` |
| 运行时 | `/opt/xiaopiao/runtime/bin/node`，24.14.0；保留系统 Node 22 |
| 网关 | Ubuntu nginx 1.24.0-2ubuntu7.18，TLS `0.0.0.0:8443` |
| 对外 HTTPS | 浏览器 / App → Cloudflare 443 → 原站 8443 → loopback 8787 |
| Cloudflare DNS | A `xiaopiao.citygenius.top` → 上述 IPv4，proxied=true，TTL=auto |
| Origin Rule | 只匹配 `http.host eq "xiaopiao.citygenius.top"`，destination port=8443 |
| 证书 | 仅该主机名的 Cloudflare Origin CA ECC，2041-10-26 到期 |
| 全区 SSL | 保持原有 `full`；未调整为 `strict`，未改全区设置 |

未使用源站 80/443，未在这些端口测试备案拦截。8443 回源已从 Mac 通过公网 HTTPS 实测；这不能保证大陆接入政策永远允许，也不能代替备案。源站只监听公网 8443，API 8787 不对公网开放。

安全组 `sg-default-smb` / `bb898958-b18c-4391-a1c1-1aa3c7ca0820` 仅新增 15 条 TCP/8443 ingress，来源为 Cloudflare 官方 IPv4 CIDR；逐条读回匹配，既有 80/443、SSH、Tailscale 等规则未改。nginx 再校验原始 TCP peer 属于 Cloudflare；使用该连接提供的 `CF-Connecting-IP`，覆盖 `X-Xiaopiao-Client-IP` 和 `X-Forwarded-For`。API 只有在 `TRUST_PROXY=1` 时接受这个内部头；客户端直接提交同名头无效。CIDR 快照在 `server/deploy/cloudflare-realip.conf`，更新时同时核对 Cloudflare `/ips` 与安全组。

Cloudflare 对象：DNS record `286ee016236ffe6abc0c2d4090e323e4`；Origin Rules ruleset `618e63c35422428ca8f074595464644e`；该规则 `41e08313c00c4acc806d7de4dfa44a96`。只在撤销这次部署时删除对应对象；如 ruleset 后来已有其它规则，保留 ruleset 和其它规则。

## 目录与私密配置

| 路径 | 用途 / 权限 |
|---|---|
| `/opt/xiaopiao/releases/<release>` | 只读代码 release；当前 `f84e8fc06f26`，旧 `6f41d712f246` 保留 |
| `/opt/xiaopiao/current` | 原子切换的 release symlink |
| `/opt/xiaopiao/node-runtime/node-v24.14.0-linux-x64` | 隔离 Node 运行时 |
| `/etc/xiaopiao/server.env` | root:root 600，由 systemd 读取后传给服务进程 |
| `/etc/xiaopiao/tls/origin.key` | root:root 600，只在原站生成，从未导出 |
| `/etc/xiaopiao/tls/origin.pem` | Origin CA 证书 |
| `/etc/xiaopiao/cloudflare-realip.conf` | 网关信任链与 Cloudflare peer 白名单 |
| `/etc/xiaopiao/resolv.conf` | 仅 API mount namespace 使用的 resolver |
| `/etc/nginx/conf.d/xiaopiao.conf` | 专用 8443 网关 |
| `/var/lib/xiaopiao/api.sqlite` | 持久化 DB，目录 700、数据库 600，owner=xiaopiao |
| `/var/lib/xiaopiao/backups/` | 在线 DB 备份；目录 700、文件 600 |
| `/var/backups/xiaopiao/` | 完整代码/运行时归档与更新前 service config，root 700/600 |

本地密钥来源只记录路径：`~/.config/xiaopiao/model.env` 与 `~/.config/agentmail/env`。生产环境包含 `NODE_ENV=production`、唯一的 `APP_ORIGIN=https://xiaopiao.citygenius.top`、持久化 `DB_PATH`、`TRUST_PROXY=1`，没有 `XP_DEV_MAIL`。不将 env、私钥、会话、邮件内容或验证码放入仓库、archive 或日志。

原机 DNS `100.125.1.250` 的回包落入现有 Tailscale CGNAT drop 段，查询超时。没有修改原机 DNS、iptables 或 Tailscale；systemd 的 `BindReadOnlyPaths` 仅给今日小票挂载专用 resolver（223.5.5.5 / 119.29.29.29），已在该 namespace 内验证解析和模型 API 出网。

## 备份、更新与回滚

每日 `xiaopiao-backup.timer` 在 Asia/Shanghai 03:20 加最多 10 分钟随机延迟运行，`Persistent=true`，保留 30 天日备份。`backup.sh` 调用既有 `backup.mjs`，SQLite online backup API 包含 WAL，不直接拷贝活动数据库主文件。手动执行：

```bash
sudo systemctl start xiaopiao-backup.service
sudo systemctl status xiaopiao-backup.service --no-pager
systemctl list-timers xiaopiao-backup.timer
```

已做一次备份副本重新打开及 `PRAGMA quick_check=ok`，并确认 ledger 表存在。备份与实例同盘，**没有异机灾备**；完整 archive 是 `/var/backups/xiaopiao/opt-xiaopiao-20261030.tgz`，包含保留的旧 release、新 release 和隔离运行时；第一轮更新前 DB 是 `/var/lib/xiaopiao/backups/pre-1e9574723f61.sqlite`。

重新部署先在本机 `server/` 跑 `npm test && npm run check`。只打包 runtime 所需文件，不包含 env、data、node_modules：

```bash
mkdir -p /tmp/xiaopiao-release
# 在仓库根目录执行
COPYFILE_DISABLE=1 tar -czf /tmp/xiaopiao-release/source.tar.gz -C server src deploy package.json package-lock.json
(
  cd /tmp/xiaopiao-release
  shasum -a 256 source.tar.gz > SHA256SUMS
  cut -c1-12 SHA256SUMS > release-id
)
release=$(cat /tmp/xiaopiao-release/release-id)
scp -i ~/.ssh/flexus_nodejs_ed25519 -r /tmp/xiaopiao-release root@110.41.84.190:/tmp/
# 脚本必须来自这次已 review 的代码。
ssh -i ~/.ssh/flexus_nodejs_ed25519 root@110.41.84.190 bash -s -- /tmp/xiaopiao-release "$release" < server/deploy/update-private.sh
```

`update-private.sh` 校验 SHA256，先做在线 DB 备份并保存 service/resolver，再解包至新目录、检查语法、原子切换 symlink、restart 和 health。失败自动恢复旧代码、unit 与 resolver；不会自动倒退数据库。网关改动另用 `nginx -t && systemctl reload nginx`，备份 unit/timer 改动另做 `systemctl daemon-reload`。更新代码不会隐式改 Cloudflare 或安全组。

手动代码回滚（先核对旧代码与当前 schema 的兼容性）：

```bash
sudo ln -s /opt/xiaopiao/releases/6f41d712f246 /opt/xiaopiao/current.next
sudo mv -Tf /opt/xiaopiao/current.next /opt/xiaopiao/current
sudo systemctl restart xiaopiao-api
curl --fail http://127.0.0.1:8787/healthz
```

恢复数据库需停 API，先另存当前 DB 和 `-wal` / `-shm`，再把对应在线备份放回 `/var/lib/xiaopiao/api.sqlite`、owner=xiaopiao、mode=600，确保旧 WAL/SHM 不和恢复后的主库混用后重启。这会丢失该备份之后的写入，不能作为普通代码回滚步骤自动执行。停止公开访问可停 nginx，再撤销上表 DNS 和该 Origin Rule、以及本次新增的 15 条 8443 安全组规则；不影响其它域名或已有服务。

## 验收进度

| 项目 | 结果 |
|---|---|
| 本机测试 | 68/68，通过 `npm run check`；含可选 relay 的安全测试 |
| a 公网 healthz | HTTPS 200，`{"ok":true}` |
| b self-inbox 原生账号 / ledger | 当前阻塞：华为云到 AgentMail 返回 CloudFront 403；注册安全失败为 MAIL_UNAVAILABLE，未留下账号 |
| c restart / enable | API restart 后公网 200；API/nginx/timer 均 enabled、active |
| d Origin | HTTP 精确生产源 200、外源 403；ego-browser 同源 preflight=204 且 ACAO 正确，example.com 跨源请求在网络面返回 403 并被浏览器拒绝 |
| 真实 IP / 伪造头 | 与 Cloudflare trace 的客户端 IP 匹配；伪造内部 IP / XFF 未写入限流 key |
| 备份恢复检查 | 副本可打开、quick_check=ok、ledger schema 存在 |
| 模型 | 从服务 namespace 认证 GET /models=200；尚未验收真实模型对话 |

AgentMail 相同 key 在 Mac 可正常读自己的 inbox；从 ECS 使用两种 User-Agent 均收到 CloudFront `Request blocked`。因此不能把配置好 key 等同于注册可用。已向协调者申请新增免费且只路由该主机名特定路径的邮件 relay Worker；未经追加授权不会创建该资源。Cloudflare `full` 仅保证加密，当前全区配置没有开启 origin 证书严格校验；未为此改动全区 SSL。

配置依据：[Cloudflare Origin Rule API](https://developers.cloudflare.com/rules/origin-rules/create-api/)、[Origin CA certificate API](https://developers.cloudflare.com/api/resources/origin_ca_certificates/methods/create/)、[Cloudflare IP ranges API](https://developers.cloudflare.com/api/resources/ips/methods/list/)。线上结果以本次实际验收为准。
