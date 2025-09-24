# For Developers

## development

直接使用 `./gradlew run` 启动即可，倒入 IDE 时**选中作为 Gradle 项目导入**！

## publishing

> 这不是最佳实践，这只是程序实现

1. 确保 build.gradle.kts 和 pom.xml 中的版本号与依赖一致
2. 执行 `./gradlew clean preCompile` 执行 KSP 生成与 Flyway 迁移文件索引文件生成
3. 执行 `./mvnw clean install -DskipTests` 使用 maven 打包为 Jar
4. 执行 `java -agentlib:native-image-agent=config-output-dir=./src/main/resources/META-INF/native-image -jar target/xiaomi-album-syncer.jar` 使用 GraalVM 运行 Jar 包，生成 native-image 所需的配置文件（主要是反射配置）
5. 执行 `./mvnw clean native:compile -P native -DskipTests` 使用 solon-aot 插件完成 native-image 的编译
6. 文件 `./target/xiaomi-album-syncer` 即为最终产物