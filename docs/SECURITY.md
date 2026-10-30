# 今日小票后端安全检查与防御

2026-10-30，工作分支 `deploy/huawei`。本轮候选代码与配置已完成测试和目标机配置校验；**尚未上线**，生产仍为 `f84e8fc06f26`。上一轮邮件 relay 已上线且保持原状态。本报告不是第三方渗透测试，不表示所有平台权限已经验收。

## 服务与审计范围

| 服务 | 已确认状态 | 本轮处理 |
|---|---|---|
| ECS nginx 8443 | Cloudflare 回源、专用主机名、Origin CA TLS；公网 health=200 | 按真实客户端 IP 的频率/连接预算，上传/发送超时 |
| Node API 127.0.0.1:8787 | Cookie/native Bearer、精确 Origin、账本/圈子用户归属、Agent | 统一资源防御与解析、上游边界修复 |
| SQLite | 参数化业务查询、WAL、owner 作用域、在线备份 | 限流键容量、过期索引、会话容量 |
| 邮件 relay Worker | 精确 route、IP+secret、固定 AgentMail 目的地；日志/preview/workers.dev 关闭 | 保留 Worker，API 新增邮件预算和发送互斥 |
| QMuse 云服务 | 此 checkout 声明三张表，无自定义云函数；Runtime facade 设置 owner ACL；产物校验通过 | 实际表权限无法从仓库证明，列为高优先级未决项 |
| SSH/Tailscale/华为云管理 agent | 监听和运行单元已只读盘点 | 保留现状，未改其它服务 |

源码审查覆盖 `server/src`、`server/deploy`、`server/test` 以及 Web 云服务声明和访问 facade。本 checkout 无服务端第三方 npm 依赖。没有扫描其它域名、读取用户账单或在生产执行攻击流量。

## 候选策略

| 控制 | 阈值和行为 |
|---|---|
| API 活跃请求 | 全实例 64、同 IP 8；超出 503 + Retry-After=5 |
| 认证任务 | 全实例 3；读 body 前占位；断开连接仍需等实际任务结束才释放 |
| 频率 | 同 IP 300/min、全实例 3000/min；保留认证、账号、账本、圈子专用限流 |
| 限流状态 | 最多 20,000 keys；耗尽时失败关闭，health 可读；增加过期索引 |
| JSON | 普通 96,000 bytes、ledger 512,000 bytes；最多读取 15s；深度 24、节点 10,000；拒绝错误 MIME、压缩编码、非法 UTF-8 |
| 未登录 ledger/chat | 读 body 前拒绝 |
| 邮件 | 单账号/地址 4/h、全实例 100/h；同账号同目的互斥；上游失败仅删除对应 code digest |
| 会话 | 单账号最多 10；超出淘汰最早到期者 |
| 模型 | 全实例 4 个活跃请求、200 次 chat 请求/h（含工具续轮）；保留每用户 40/h 和单活跃请求；整次最多 120s |
| 模型上游 | 禁止重定向；discovery 512,000 bytes、错误体 4096 bytes；SSE 单帧 256,000 chars、总流 2,000,000 bytes；工具 ID/名称有界 |
| 流式输出 | 开始输出后仍执行截止时间；慢客户端写缓冲满即中止；整次超时发送明确错误事件 |
| HTTP | Node 128 sockets、16KiB headers、64 headers、每 socket 最多 100 requests；nginx 10 req/s，burst=60，同 IP 12 连接 |
| 进程 | 保留非 root、CPU/内存/文件系统隔离；新增私有设备、内核/控制组保护、空 capability set、TasksMax=64、NOFILE=1024、禁 core dump |
| 观测 | 生产每分钟最多一条累计 security_summary：拒绝类型计数、活跃数量；无 IP、账号、URL、正文、会话、验证码、密钥 |

资源预算触发后主动拒绝请求，不能保证分布式攻击下仍可用。阈值针对小规格单实例；共用 NAT 可能触发同 IP 预算，尚未经过线上负载标定。聚合摘要使用原有 systemd journal，无新增管理入口、外部埋点或通知。网关错误日志关闭，诊断使用应用聚合摘要；不要开启正文/debug 日志。

body 按 bytes 累计，收齐后统一 UTF-8 解码，修复中文跨 chunk 被破坏的问题；URL 解析纳入异常边界，畸形路径返回 400。既有跨账号账本、圈子隔离、验证码单次消费、会话失效、可信代理和敏感字段拒绝用例继续运行。

## 未决项

1. **QMuse 实际表权限：高优先级。** `transactions`、`merchant_revenue`、`receipt_prefs` 含个人数据。需在 QMuse 云服务「数据表权限设置」核对 READ/EDIT/DELETE 仅创建者，SUBMIT 仅业务允许的身份（包括明确允许的访客）。平台文档的新表默认 READ=ALL；不能假定线上仍为默认值，也不能用 ownerId 查询证明隔离。随后用两个独立身份验证：各自读写自己的行，交叉读/改/删失败，篡改 ownerId 不能获得他人行。本轮没有修改平台权限，未宣称隔离通过。
2. **备份仍与 ECS 同盘。** 备份重开和 quick_check 通过，不构成异机灾备。只读盘点磁盘使用约 13%。异机/对象存储资源需另行选择和批准。
3. **Cloudflare 全区 SSL 仍为 full。** 原站证书存在，前轮已查证书链；full 不等同严格验证原站证书。未改全区或别的域名，主机名级 strict 可另行准备。
4. 应用配额不能替代网络层 DDoS 防护；没有购买 WAF、扩大云权限、改安全组、安装新 daemon 或改变 SSH/Tailscale。主机与 Node/nginx 补丁/CVE 核验不属于本轮已完成结论。

## 验证与上线

增加 13 项防御回归，与既有测试合计 81 项：UTF-8 分块、chunked 限额、恶意 URL、无鉴权大上传、慢 body 超时、并发拒绝和恢复、断开不能绕过认证配额、邮件总量、限流状态容量、模型并发/超时、上游恶意流、圈子默认 body 限额和会话上限。最终执行结果见 deployment-work/progress.md。QMuse 产物校验通过。

候选 nginx 经目标机同版本 `nginx -t` 校验通过，候选 unit 经 `systemd-analyze verify` 通过。临时文件已清理，没有 reload/restart 或替换生产配置。第一次 stdin 传 nginx 配置因二次读取而失败，改用受限临时文件后完整校验通过。

经 tt 批准后，仅对今日小票执行：

1. 按 DEPLOY.md 准备 source-only archive、SHA256 和 release ID，备份 DB 与专用 nginx/unit。
2. `server/deploy/update-private.sh` 更新 API/unit：在线备份、原子 symlink、restart，health 失败恢复旧代码/unit；保留私密 env 和 resolver。
3. 单独安装候选 nginx，先 `nginx -t` 再 reload；失败恢复专用配置。代码更新脚本不会自动安装 nginx 配置。
4. 顺序验收公网 health、Origin、native 登录/邮件/ledger/logout、错误 JSON 和无凭据拒绝；检查聚合摘要与服务状态。重启不与账号验收重叠，生产不做洪泛测试，仅向自有 AgentMail inbox 发信。
5. 观察误限流与延迟，必要时恢复前一 release 和旧 unit/nginx。新增过期索引向后兼容，普通回滚不恢复旧 DB、不删除新账目。

实现依据：[Node HTTP 超时与写缓冲](https://nodejs.org/docs/latest-v24.x/api/http.html)、[nginx request limiting](https://nginx.org/en/docs/http/ngx_http_limit_req_module.html)。阈值是本项目的工程选择，不是官方安全保证。
