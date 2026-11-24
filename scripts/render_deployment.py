"""生成系统部署图（四区划分）PNG + SVG。

用法：python scripts/render_deployment.py
输出：docs/database/diagrams/deployment.png / .svg
"""

from __future__ import annotations

import os

from diagram_kit import (
    BG, BLUE, BORDER, GOLD, GRAY, LIGHT, MUTED, NAVY, TEAL, TEXT,
    Canvas, chip, node, title_block, zone,
)

W, H = 2600, 1320


def build() -> Canvas:
    cv = Canvas(W, H)
    title_block(
        cv,
        "目标部署架构（规划）",
        "四区划分：互联网区 / DMZ / 应用区 / 数据区；当前 v1 演示环境见 deployment-local.png",
        right="未落地",
    )

    # ---------- 区域 ----------
    zone(cv, 60, 200, 460, 620, "互联网区", NAVY, "公网 HTTPS")
    zone(cv, 580, 200, 400, 620, "DMZ", BLUE, "只转发，不落数据")
    zone(cv, 1040, 200, 520, 620, "应用区", TEAL, "无状态，可水平扩容")
    zone(cv, 1620, 200, 920, 660, "数据区", GOLD, "主从 + 缓存 + 备份")
    zone(cv, 580, 900, 980, 200, "外部系统", GRAY, "合作药房")

    # ---------- 终端 ----------
    terminals = [
        ("患者端 H5", "图文问诊 / 处方跟踪", "浏览器 + 小程序 WebView"),
        ("医生工作站", "接诊 / 病历 / 开方", "Chrome 内网或 VPN"),
        ("药师工作站", "审核 / 驳回 / 流转", "药房专线"),
        ("管理后台", "账号 / 目录 / 审计", "仅内网 + 二次确认"),
    ]
    for i, (name, detail, sub) in enumerate(terminals):
        y = 285 + i * 130
        node(cv, 90, y, 400, 105, name, detail, NAVY, "HTTPS 443")
        cv.text(106, y + 90, sub, 13, MUTED)

    # ---------- DMZ ----------
    node(cv, 610, 300, 340, 180, "Nginx / WAF", "TLS 1.3 终止、限流、", BLUE, "入口")
    cv.text(634, 430, "证书轮换、访问日志", 15, MUTED)
    node(cv, 610, 530, 340, 110, "限流与防刷", "按 IP / 账号维度", BLUE)

    # ---------- 应用 ----------
    node(cv, 1070, 285, 460, 140, "Spring Boot 实例 A", "platform-* 模块化单体", TEAL, "无状态")
    node(cv, 1070, 455, 460, 140, "Spring Boot 实例 B", "同镜像，负载均衡后", TEAL)
    node(cv, 1070, 625, 460, 120, "事务发件箱任务", "轮询 outbox_event 并投递", TEAL)

    # ---------- 数据 ----------
    node(cv, 1650, 280, 860, 130, "MySQL 8.0 主库", "utf8mb4 / InnoDB / 行格式 DYNAMIC", GOLD, "写")
    node(cv, 1650, 430, 860, 130, "MySQL 8.0 从库", "只读查询、报表与审计检索", GOLD, "读")
    node(cv, 1650, 580, 400, 120, "Redis 7", "会话 / 缓存 / 幂等键", GOLD)
    node(cv, 2110, 580, 400, 120, "对象存储", "处方影像 / 导出文件", GOLD)
    node(cv, 1650, 720, 860, 110, "备份存储（加密）", "每日全量 + binlog 增量，异地保存", GOLD)

    # ---------- 外部 ----------
    node(cv, 610, 975, 920, 110, "合作药房系统", "接方 / 核销回传，mTLS 双向认证 + 报文签名", GRAY, "mTLS")

    # ---------- 连线 ----------
    cv.arrow([(520, 390), (610, 390)], BLUE, 3)
    cv.label(565, 372, "HTTPS 443")

    cv.arrow([(950, 390), (1070, 390)], BLUE, 3)
    cv.label(1010, 372, "内网 8080")

    cv.arrow([(1530, 350), (1650, 345)], BLUE, 3)
    cv.label(1590, 327, "JDBC 3306")

    cv.arrow([(1530, 520), (1650, 495)], BLUE, 3)
    cv.label(1590, 530, "只读查询")

    cv.arrow([(1530, 600), (1650, 640)], BLUE, 3)
    cv.label(1580, 640, "缓存 6379")

    cv.arrow([(2080, 410), (2080, 430)], BLUE, 3)
    cv.label(2100, 424, "binlog 复制", anchor="ls")

    cv.arrow([(2080, 560), (2080, 720)], BLUE, 3)
    cv.label(2080, 612, "备份", anchor="ms")

    cv.arrow([(1300, 745), (1300, 975)], BLUE, 3)
    cv.label(1316, 870, "Outbox 异步投递", anchor="ls")

    # ---------- 说明 ----------
    cv.text(100, 1130, "部署要点", 20, NAVY, bold=True)
    notes = [
        "① 全站 HTTPS：TLS 在 Nginx 终止，应用区与数据区之间只走内网，端口不对外暴露。",
        "② 应用无状态：会话放 Redis，文件放对象存储，实例可水平扩容；数据库变更由 Flyway 管理。",
        "③ 跨系统调用不阻塞主事务：处方流转写入 outbox_event，由后台任务投递药房，失败指数退避重试。",
        "④ 数据区主从分离读写；备份加密后异地保存；审计日志独立归档，保留不少于 6 个月。",
    ]
    for i, line in enumerate(notes):
        cv.text(100, 1170 + i * 34, line, 17, TEXT)

    return cv


def build_local() -> Canvas:
    """v1 实际演示环境：单机 Docker Compose + 局域网浏览器。"""
    cv = Canvas(2400, 1350)
    title_block(
        cv,
        "v1 演示环境部署（单机）",
        "一台电脑跑全部服务；客户端只访问 5173；不需要云服务器，也不需要独立数据库服务器",
        right="2026-11-24",
    )

    zone(cv, 60, 200, 1460, 760, "主机 A（Windows / macOS / Linux）", NAVY, "运行全部服务")
    zone(cv, 1620, 200, 700, 760, "局域网客户端", BLUE, "只开浏览器")

    node(cv, 100, 300, 360, 110, "浏览器 A", "患者 / 医生 / 药师", NAVY, "本机")
    node(cv, 540, 300, 900, 420, "Docker Compose 网络", "服务名互连，端口默认只绑 127.0.0.1", TEAL)
    node(cv, 580, 380, 360, 120, "Spring Boot 后端", "127.0.0.1:8080", TEAL, "单实例")
    node(cv, 1010, 380, 390, 120, "MySQL 8", "127.0.0.1:3306", GOLD, "业务库")
    node(cv, 1010, 540, 390, 120, "Redis 7", "127.0.0.1:6379", GOLD, "预留")
    node(cv, 580, 720, 860, 110, "Vite 开发服务器", "0.0.0.0:5173，/api 反向代理到 8080", BLUE, "唯一对外端口")

    node(cv, 1660, 330, 580, 120, "电脑 B", "医生 + 药师", BLUE, "浏览器")
    node(cv, 1660, 510, 580, 120, "电脑 C", "患者 / 第三人", BLUE, "浏览器")
    node(cv, 1660, 690, 580, 120, "手机 / 平板", "同一 Wi-Fi 即可", BLUE, "可选")

    cv.arrow([(280, 410), (280, 760), (570, 775)], BLUE, 3)
    cv.label(300, 700, "HTTP", anchor="ls")
    cv.arrow([(760, 720), (760, 500)], BLUE, 3)
    cv.label(780, 620, "代理 /api", anchor="ls")
    cv.arrow([(940, 440), (1010, 440)], BLUE, 3)
    cv.label(975, 420, "JDBC", anchor="ms")
    cv.arrow([(940, 600), (1010, 600)], BLUE, 3)
    cv.label(975, 580, "6379", anchor="ms")

    cv.arrow([(1660, 390), (1440, 760)], BLUE, 3)
    cv.arrow([(1660, 570), (1440, 790)], BLUE, 3)
    cv.arrow([(1660, 750), (1440, 815)], BLUE, 3)
    cv.label(1545, 470, "http://A-IP:5173", anchor="ls")

    cv.text(100, 1030, "部署要点", 20, NAVY, bold=True)
    notes = [
        "① 只有 5173 需要对局域网开放；MySQL 3306、Redis 6379、后端 8080 默认绑 127.0.0.1，不暴露。",
        "② 浏览器只访问 Vite，Vite 在主机 A 内把 /api 代理到后端，因此不需要 CORS，也不需要给前端配后端地址。",
        "③ 一台电脑就够：多开 Chrome 普通 / 无痕 / Edge 分别登录三个角色；两台或三台只是演示更直观。",
        "④ 没有云服务器：同一 Wi-Fi / 手机热点即可；跨城市用 Tailscale/ZeroTier，不需要公网 IP。",
    ]
    for i, line in enumerate(notes):
        cv.text(100, 1070 + i * 36, line, 17, TEXT)
    cv.text(100, 1240, "详细步骤见 docs/demo/local-network-test.md", 16, MUTED)
    return cv


def main() -> int:
    out_dir = os.path.join("docs", "database", "diagrams")
    os.makedirs(out_dir, exist_ok=True)
    cv = build()
    cv.save_png(os.path.join(out_dir, "deployment.png"))
    cv.save_svg(
        os.path.join(out_dir, "deployment.svg"),
        "目标部署架构（规划）",
        "四区划分，含 Nginx、Spring Boot 集群、MySQL 主从、Redis、对象存储与备份；未落地。",
    )

    local = build_local()
    local.save_png(os.path.join(out_dir, "deployment-local.png"))
    local.save_svg(
        os.path.join(out_dir, "deployment-local.svg"),
        "v1 演示环境部署（单机）",
        "单机 Docker Compose + 局域网浏览器；只有 Vite 5173 对外，无需云服务器。",
    )

    for name in ("deployment.png", "deployment.svg", "deployment-local.png", "deployment-local.svg"):
        print(f"{name}: {os.path.getsize(os.path.join(out_dir, name))} bytes")
    print("warn:", (cv.warnings + local.warnings) or "none")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
