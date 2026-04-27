# 发布 Wiki

GitHub Wiki 是一个独立的 Git 仓库，地址通常是：

```text
https://github.com/EternalmBlue/Agent4Minecraft.wiki.git
```

本目录 `wiki/` 中的文件可以直接复制到 Wiki 仓库根目录。

## 第一次启用 Wiki

如果 `git clone` wiki 仓库失败，先在 GitHub 仓库页面：

1. 打开 `https://github.com/EternalmBlue/Agent4Minecraft`
2. 点击 `Wiki`
3. 创建一个临时 `Home` 页面并保存
4. 再克隆 `.wiki.git`

## 发布命令

在一个临时目录执行：

```powershell
git clone https://github.com/EternalmBlue/Agent4Minecraft.wiki.git Agent4Minecraft.wiki
Copy-Item F:\Agent4Minecraft\wiki\*.md .\Agent4Minecraft.wiki\ -Force
cd Agent4Minecraft.wiki
git status
git add .
git commit -m "docs: add Agent4Minecraft wiki"
git push origin HEAD
```

## 更新 Wiki

以后更新：

```powershell
cd Agent4Minecraft.wiki
git pull
Copy-Item F:\Agent4Minecraft\wiki\*.md .\ -Force
git add .
git commit -m "docs: update Agent4Minecraft wiki"
git push origin HEAD
```

## 页面命名

GitHub Wiki 通过文件名生成页面路径：

- `Home.md` 是首页。
- `_Sidebar.md` 是侧边栏。
- `Quick-Start.md` 可通过 `(Quick-Start)` 链接。

建议文件名使用英文和短横线，页面标题用中文。

## 注意事项

- Wiki 仓库和代码仓库是两个不同 Git 仓库。
- 修改主仓库 `wiki/` 不会自动更新 GitHub Wiki。
- 发布前先 `git status`，确认只包含 Wiki Markdown 文件。
- 不要把 `.env`、数据目录、构建产物复制到 Wiki 仓库。
