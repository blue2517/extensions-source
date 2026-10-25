# 个人源构建

此 fork 在 `DIY` 分支维护少量自用源，使用独立的 **Personal source build** 工作流。
原作者的构建、发布工作流保留原样；本工作流只上传构建产物，不发布扩展索引。

## 首次配置

1. 在自己的 fork 中进入 **Actions**，启用 GitHub Actions。
2. 配置下面四个 **Repository secrets**：
   **Settings → Secrets and variables → Actions → New repository secret**。
3. 提交并推送 `DIY` 分支。相关源或构建文件变更会自动触发工作流。
4. 若需要 **Run workflow** 手动入口，将 `DIY` 设置为默认分支；也可以让默认分支包含此工作流。
   手动运行时选择 `DIY`，工作流仅在该分支执行。

### 固定签名

| Secret | 内容 |
| --- | --- |
| `SIGNING_KEY` | 自己的 JKS 文件编码成的 Base64 文本 |
| `ALIAS` | 密钥别名，例如 `personal-extensions` |
| `KEY_STORE_PASSWORD` | 密钥库密码 |
| `KEY_PASSWORD` | 对应别名的私钥密码 |

四项均为必填。缺失或签名失败会终止构建，不会发布临时 debug 签名的 APK。
密钥、密码和 Base64 文本均不放入 Git，不上传为构建产物。

如果已有自己的扩展签名密钥，继续使用原密钥。如果首次创建，可在安装有 JDK 的可信电脑上执行：

```powershell
New-Item -ItemType Directory -Path "$HOME\extension-signing" -Force
keytool -genkeypair -keystore "$HOME\extension-signing\personal-extensions.jks" -storetype JKS -alias personal-extensions -keyalg RSA -keysize 3072 -validity 10000
```

按提示输入密码和证书信息。未单独设置私钥密码时，`KEY_PASSWORD` 与密钥库密码相同。
用下面的 PowerShell 命令将 Base64 文本复制到剪贴板，粘贴至 `SIGNING_KEY` 后清空剪贴板：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$HOME\extension-signing\personal-extensions.jks")) | Set-Clipboard
# 在 GitHub 保存 SIGNING_KEY 后执行
Set-Clipboard -Value ""
```

请离线备份 JKS 文件、别名和两个密码；后续版本始终使用同一份密钥。
本机仅编辑源码、由 Actions 构建时，日常不需要本地 JDK 或 Android SDK。
首次创建密钥时需要一次性使用 `keytool`，生成后保留备份即可。

## 构建和安装

- 自动构建：推送 `DIY` 分支，且变更匹配工作流中的 `push.paths`。
- 手动构建：**Actions → Personal source build → Run workflow → DIY**。
- 构建顺序：配置检查、release lint、单元测试、签名 release 构建、APK 签名验证。
- 在成功运行的 **Artifacts** 下载 `personal-extensions-运行编号`，解压后安装 `.apk`。
  同一产物中的 `.jar` 和元数据用于后续仓库发布，当前手动安装只需要 APK。
- APK 产物保留 14 天，lint 和测试报告保留 7 天；需要长期保存时另行备份。
- 在日志中可查看 APK 证书指纹，确认后续版本持续使用自己的签名。

首次从原作者签名的扩展切换时，先做 Mihon 备份，卸载原来的**对应源扩展**，再安装个人版本，
在 Mihon 中信任新扩展。Mihon 主程序保持原样。后续沿用相同签名并递增源的 `versionCode`。
保持源名称、语言和 source ID 稳定，以延续已有书架关联。

## 添加其他维护源

1. 在 `.github/personal-sources.txt` 增加一行，例如 `en/example`。
2. 在 `.github/workflows/personal_build.yml` 的 `push.paths` 增加 `src/en/example/**`。
3. 提交源码修改并推送到 `DIY`。每次构建会测试并打包维护列表中的全部源。

工作流会在临时 checkout 中将 `settings.gradle.kts` 的全量源加载替换为维护列表，
共享模块仍保持加载；本地文件和仓库中的原始设置保持原样。
合并上游共享代码或构建依赖变更也会触发个人源重建，不会打包未维护的其他源。

## 本地检查工作流辅助脚本

```powershell
python -B -m unittest discover -s .github/scripts/tests -v
```

## 参考

- [GitHub：手动运行工作流](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow)
- [GitHub：Actions secrets](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets)
- [Android：应用签名](https://developer.android.com/studio/publish/app-signing)
