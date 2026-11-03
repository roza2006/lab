# 远程仓库重置说明（请先看完再执行）

## 当前状态

- 本地 `main` 已重置为**孤儿分支**（无父提交）；
- 原 `main` 的旧文件已从工作区清除；
- 已建立新的目录骨架，并提交 1 个基线 commit：

```text
chore: reset repository and add project skeleton
```

- **远程 GitHub 还没有被修改**。强推会重写小组仓库历史，必须由你确认后执行，且需要你的 GitHub 凭据；我没有也不会使用别人的账号。

## 先备份

原始仓库的完整备份已生成：

```text
lab-original-backup.bundle
```

需要恢复时：

```bash
git clone lab-original-backup.bundle lab-restore
```

## 方案 A：只替换 main（最常用）

适合：老师只看当前仓库内容，不追究旧 commit 是否还能通过 SHA 访问。

```bash
cd lab_repo
git push origin main --force
```

执行后，`main` 的最新提交会变成 `chore: reset repository and add project skeleton`。

如果要顺手删掉旧分支：

```bash
git push origin --delete LZY weiruanjian-patch-1
```

> 注意：这会关闭/影响相关 PR；先和组员确认。

## 方案 B：彻底清空（删库重建）

适合：要求“不能通过旧 commit/PR 找到任何旧内容”。

1. GitHub 仓库 → Settings → 最下方 **Delete this repository**；
2. 重新创建同名仓库 `roza2006/lab`（不要勾选 README）；
3. 本地执行：

```bash
cd lab_repo
git remote set-url origin https://github.com/roza2006/lab.git
git push -u origin main
```

这样远程只剩新的基线 commit，旧 PR、旧分支、旧 commit 都不在仓库里。

> 删库不可恢复；先确认组员没有未合并的分支，且备份 bundle 已保存。

## 方案 C：保留旧历史，只覆盖文件

不推荐。等于在旧历史上加一个“删除全部文件”的 commit，旧内容仍可查。不要用它冒充“全新开始”。

## 推送后检查

```bash
git log --oneline --decorate --all
git log --format='%h %an <%ae> %s' -10
git ls-tree -r --name-only HEAD | Select-Object -First 40
```

确认：

- 没有 `test.py`、`习思稿子.docx`、`《嵌入式操作系统》...docx` 等旧文件；
- 没有 `node_modules/`、`target/`、`.env`；
- 新目录骨架存在；
- 当前分支是你们要的 `main`。

## 关于“6 次提交计划”

见根目录 [COMMIT_PLAN.md](COMMIT_PLAN.md)。

- 6 个成员，每人每轮至少 1 个 commit；
- 6 轮共约 36 个 commit；
- 每个 commit 对应一个目录、一个任务、一个可解释的 message；
- 每人用自己的账号提交，不共用账号、不伪造作者。

## 重要提醒

- 强推会改写协作者的历史；先让组员确认，尤其是还有未合并分支的人。
- GitHub 的旧 commit 在强推后仍可能通过 SHA/PR 缓存访问；要彻底干净只能删库重建。
- 不要把 `.env`、密钥、真实患者数据提交进仓库。
