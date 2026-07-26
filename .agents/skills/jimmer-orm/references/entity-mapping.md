# Jimmer 实体映射参考 (Kotlin)

## 基础概念

- Jimmer 实体使用 **interface** 定义，而非 class
- 实体是**动态对象**和**不可变对象**
- 由 KSP 在编译时生成实现
- 属性未设置 ≠ 属性为 null

## 基础实体定义

```kotlin
package com.example.model

import org.babyfish.jimmer.sql.*
import java.math.BigDecimal

@Entity
interface Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long

    @Key
    val name: String

    val edition: Int

    val price: BigDecimal

    val description: String?
}
```

## 关联映射

### 多对一 (ManyToOne)

```kotlin
@Entity
interface Book {
    // ... 其他属性

    @ManyToOne
    val store: BookStore?
}
```

### 一对多 (OneToMany)

一对多必须作为多对一的镜像，使用 `mappedBy` 指定：

```kotlin
@Entity
interface BookStore {
    // ... 其他属性

    @OneToMany(mappedBy = "store")
    val books: List<Book>
}
```

### 多对多 (ManyToMany)

主动端：

```kotlin
@Entity
interface Book {
    // ... 其他属性

    @ManyToMany
    @JoinTable(
        name = "BOOK_AUTHOR_MAPPING",
        joinColumnName = "BOOK_ID",
        inverseJoinColumnName = "AUTHOR_ID"
    )
    val authors: List<Author>
}
```

镜像端：

```kotlin
@Entity
interface Author {
    // ... 其他属性

    @ManyToMany(mappedBy = "authors")
    val books: List<Book>
}
```

## 常用注解

- `@Entity` - 标记实体类型
- `@MappedSuperclass` - 标记映射父接口，复用公共字段
- `@Embeddable` - 标记嵌入值对象
- `@Id` - 标记主键
- `@GeneratedValue` - 主键生成策略
- `@Key` - 业务键（用于唯一性约束）
- `@KeyUniqueConstraint` - 让 `@Key` 组合参与数据库唯一约束语义
- `@Column` - 自定义列名（仅用于非关联字段）
- `@Table` - 自定义表名
- `@ManyToOne` / `@OneToMany` / `@ManyToMany` / `@OneToOne` - 关联映射
- `@ManyToManyView` - 通过中间实体暴露多对多视图
- `@IdView` - 关联 id 视图
- `@JoinColumn` - 自定义外键列名
- `@JoinTable` - 自定义中间表配置
- `@Version` - 乐观锁版本字段
- `@LogicalDeleted` - 逻辑删除字段
- `@Formula` / `@Transient` - 计算属性
- `@OnDissociate` - 父关联断开时的子对象处理策略
- `?` - 标记可空属性

## 复杂实体映射

### 映射父接口

公共字段用 `@MappedSuperclass`，实体通过接口继承复用：

```kotlin
@MappedSuperclass
interface BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long
}

@Entity
interface Artist : BaseEntity {
    @Key
    val name: String
}
```

### 关联 id 视图

`@IdView` 为关联生成 id 属性，适合按外键查询、DTO input 和避免只为 id 加载关联对象：

```kotlin
@Entity
interface Book {

    @ManyToOne
    val store: BookStore?

    @IdView
    val storeId: Long?

    @ManyToMany
    val authors: List<Author>

    @IdView("authors")
    val authorIds: List<Long>
}
```

### 带业务字段的中间表

中间表有 `sortOrder`、来源、时间等业务字段时，不要用纯 `@JoinTable` 把它隐藏掉；应定义中间实体，再用 `@ManyToManyView` 暴露便捷视图：

```kotlin
@Entity
@Table(name = "album_recording_mapping")
interface AlbumRecording {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long

    @Key
    @ManyToOne
    @OnDissociate(DissociateAction.DELETE)
    val album: Album

    @Key
    @ManyToOne
    @OnDissociate(DissociateAction.DELETE)
    val recording: Recording

    val sortOrder: Int
}

@Entity
interface Album {

    @OneToMany(mappedBy = "album", orderedProps = [OrderedProp("sortOrder")])
    val albumRecordings: List<AlbumRecording>

    @ManyToManyView(prop = "albumRecordings", deeperProp = "recording")
    val recordings: List<Recording>
}
```

`@ManyToManyView` 是由中间实体推导出来的视图属性，业务字段仍然在中间实体上维护。

### 计算属性

简单派生值可用 `@Formula(dependencies = [...])`；需要批量解析或外部服务时用 `@Transient(Resolver::class)`：

```kotlin
@Entity
interface Book {

    @ManyToMany
    val authors: List<Author>

    @Formula(dependencies = ["authors"])
    val authorCount: Int
        get() = authors.size
}
```

## 枚举映射

```kotlin
enum class Gender {
    @EnumItem(name = "M")
    MALE,

    @EnumItem(name = "F")
    FEMALE
}
```

## 自引用关联（树形结构）

```kotlin
@Entity
interface TreeNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long

    val name: String

    @ManyToOne
    val parent: TreeNode?

    @OneToMany(mappedBy = "parent")
    val childNodes: List<TreeNode>
}
```

## 命名约定

- 实体接口名 → 表名：`BookStore` → `BOOK_STORE`
- 属性名 → 列名：`firstName` → `FIRST_NAME`
- 多对一外键：`store` → `STORE_ID`
- 默认命名策略是 `DefaultDatabaseNamingStrategy.UPPER_CASE`；可通过 SQL Client 的命名策略配置改成小写或自定义
- 如果不匹配，使用 `@Table` 或 `@Column` 自定义
