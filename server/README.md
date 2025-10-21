# For Developers

## development

直接使用 `./gradlew run` 启动即可

## publishing

### jvm

```bash
./gradlew clean solonJar
```

### native

> 不保证可用
> 
> 需要安装 GraalVM 并配置环境变量 `JAVA_HOME` 指向 GraalVM 的安装路径

```shell
# 使用 agent 生成配置文件（可选）
# 记得改成你的 jar 路径
java -agentlib:native-image-agent=config-merge-dir=./src/main/resources/META-INF/native-image -jar path/to/app.jar
```

```shell
./gradlew clean nativeCompile
```
