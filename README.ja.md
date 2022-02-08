# Lotus

Androidアプリ，ファームウェアの開発において，Logcatから得ることができる情報は，極めて有用です．
本ライブラリは，Logcatを端末内に残し続けるための機能を提供します．
開発中のライブラリであるため，対応するOS Versionに制限があります．

## 警告
- 本ライブラリは，開発者向けのライブラリであることを理解し，Logには一部ユーザーの情報が含まれることもあるため，取り扱いには注意ください．
- 動作を確認している OS Versionは，Android 8.1です．
- Android 12では，期待する挙動ができないことを確認しています．

# 使い方

`LogCollect` インスタンスを作成して，`start()` を実行するだけです．
ログ取得をやめるために，`stop()` を忘れないでください．

```.kt
private var logcollect: LogCollect? = null

override fun onResume() {
    super.onResume()
    logcollect = LogCollect(this, object: LogCollect.ILogCollect {
        override fun onReceivedZipFile(zipFile: File) {}
    }).apply {
        start()
    }
}

override fun onPause() {
    super.onPause()
    logcollect?.stop()
}
```

Logcatが蓄積されていることを最も簡単に確認する手段は， `run-as` を実行することです．

```
$ adb shell
$ run-as com.android.your.application.name
$ ls app_log/
1_2022-02-08-11-11-11.log index
```

# ビルド

```
$ git clone <this repo>
$ cd <this repo dir>

$ ./gradlew assembleRelease
# If wsl2, powershell.exe ./gradlew assembleRelease

$ ls ./app/lotus/build/outputs/aar/
lotus-release.aar
```

# 導入

- あなたのレポジトリに，ビルドした `aar` を追加してください．

```
# copy your repository

cp lotus-release.aar <your project>/lotus/
```

```
# create lotus build.gradle

+ configurations.maybeCreate("default")
+ artifacts.add("default", file('lotus-release.aar'))
```

```
# settings.gradle

+ include `:lotus`
```

```
# app build.gradle

+ implementation project(':lotus')
```
