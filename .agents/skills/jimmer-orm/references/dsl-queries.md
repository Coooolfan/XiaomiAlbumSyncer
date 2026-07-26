# Jimmer DSL 查询参考 (Kotlin)

## 基础查询

```kotlin
@Repository
class BookRepository(
    private val sqlClient: KSqlClient
) {

    fun findBooks(): List<Book> =
        sqlClient
            .createQuery(Book::class) {
                where(table.name eq "Learning GraphQL")
                where(table.price gt BigDecimal(30))
                orderBy(table.name.asc())
                select(table)
            }
            .execute()
}
```

## 动态谓词

动态谓词根据参数决定是否添加 SQL 条件。

```kotlin
fun findBooks(
    name: String? = null,
    minPrice: BigDecimal? = null,
    maxPrice: BigDecimal? = null
): List<Book> =
    sqlClient
        .createQuery(Book::class) {
            where(table.name `ilike?` name)  // 动态 ILIKE
            where(table.price.`between?`(minPrice, maxPrice))  // 动态 BETWEEN
            select(table)
        }
        .execute()
```

**动态谓词规则：**
- `ilike?`：参数非 null 且非空字符串时生效
- `between?`：根据参数组合生成 `BETWEEN`、`>=`、`<=` 或不生成条件
- 集合动态谓词使用 `` `valueIn?` `` / `` `valueNotIn?` ``
- 其他动态谓词：`eq?`, `ne?`, `gt?`, `ge?`, `lt?`, `le?`

```kotlin
fun findByIds(ids: Collection<Long>?): List<Book> =
    sqlClient.createQuery(Book::class) {
        where(table.id `valueIn?` ids)
        select(table)
    }.execute()
```

## 动态表连接

通过单值关联路径创建表连接，未使用的连接会被自动忽略。

```kotlin
fun findBooks(
    storeName: String? = null,
    storeWebsite: String? = null
): List<Book> =
    sqlClient
        .createQuery(Book::class) {
            // 通过 Book.store 关联到 BookStore
            where(table.store.name `ilike?` storeName)
            where(table.store.website `ilike?` storeWebsite)
            select(table)
        }
        .execute()
```

**关键特性：**
- 多次引用同一关联会自动合并为一个 JOIN
- 未使用的表连接会被自动忽略
- 非空路径默认使用 INNER JOIN；可空路径会使用可空表表达式

## 隐式子查询

基于集合关联（一对多或多对多）创建子查询。集合关联也可以通过 `asTableEx()` 做 JOIN，但 Jimmer 源码文档明确提示这种用法可能导致结果重复并破坏分页机制，通常更推荐隐式子查询。

```kotlin
fun findBooks(
    authorName: String? = null,
    authorGender: Gender? = null
): List<Book> =
    sqlClient
        .createQuery(Book::class) {
            where += table.authors {
                or(
                    firstName `ilike?` authorName,
                    lastName `ilike?` authorName
                )
            }
            where += table.authors {
                gender `eq?` authorGender
            }
            select(table)
        }
        .execute()
```

**关键特性：**
- 生成 SQL `EXISTS` 子查询
- 父子查询的关联条件自动生成
- 多个基于同一关联的子查询会自动合并
- 无效的子查询会被自动忽略

需要排除集合关联条件时，对隐式子查询结果取反：

```kotlin
sqlClient.createQuery(Book::class) {
    where(table.authors {
        lastName.asNullable().isNull()
    }?.not())
    select(table)
}
```

## 显式子查询

需要跨实体聚合、`valueIn subQuery` 或无法通过关联路径表达时，使用显式子查询：

```kotlin
val books = sqlClient.createQuery(Book::class) {
    where(
        table.storeId valueIn subQuery(BookStore::class) {
            where(table.name `ilike?` storeName)
            select(table.id)
        }
    )
    select(table)
}.execute()
```

## 对象抓取器 (Object Fetcher)

一句话查询任意形状的数据结构。

```kotlin
val books = sqlClient
    .createQuery(Book::class) {
        select(
            table.fetchBy {
                allScalarFields()
                store {
                    allScalarFields()
                }
                authors {
                    allScalarFields()
                }
            }
        )
    }
    .execute()
```

Fetcher 可以只抓标量，也可以嵌套抓关联；在 Controller 返回实体时配合 `@FetchBy` 固定 API 投影：

```kotlin
companion object {
    val SIMPLE_FETCHER: Fetcher<Book> =
        newFetcher(Book::class).by {
            allScalarFields()
            store()
            authors()
        }

    val DETAIL_FETCHER: Fetcher<Book> =
        newFetcher(Book::class).by {
            allScalarFields()
            store {
                allScalarFields()
            }
            authors {
                allScalarFields()
            }
        }
}
```

```kotlin
fun getBook(id: Long): @FetchBy("DETAIL_FETCHER") Book
```

## 多选与方向投影

只需要少量字段或混合实体与聚合值时，直接 `select` 多个表达式；需要实体形状时用 `fetchBy` 控制方向投影：

```kotlin
val rows = sqlClient.createQuery(Book::class) {
    groupBy(table.storeId)
    select(
        table.storeId,
        count(table.id),
        avg(table.price)
    )
}.execute()
```

```kotlin
val books = sqlClient.createQuery(Book::class) {
    where(table.authors {
        firstName `ilike?` authorName
    })
    select(table.fetchBy {
        name()
        store {
            name()
        }
        authors {
            firstName()
            lastName()
        }
    })
}.execute()
```

## 分页查询

```kotlin
val page = sqlClient
    .createQuery(Book::class) {
        where(table.name ilike "GraphQL")
        orderBy(table.name.asc())
        select(table)
    }
    .fetchPage(0, 10)  // 页码从 0 开始，每页 10 条
```

## 聚合查询

```kotlin
val result = sqlClient
    .createQuery(Book::class) {
        groupBy(table.storeId)
        select(
            table.storeId,
            avg(table.price)
        )
    }
    .execute()
```

## Specification DTO 查询

如果 `.dto` 定义了 `specification` 类型，KSP 会生成可直接传给 `where` 的 Specification 对象：

```kotlin
fun search(spec: BookSpecification): List<Book> =
    sqlClient.createQuery(Book::class) {
        where(spec)
        select(table)
    }.execute()
```

适合控制器搜索接口：条件解析在 DTO 层，Repository/Service 只负责组合额外权限条件、排序和投影。

## 常用谓词方法

### 比较运算符
- `eq` - 等于
- `ne` - 不等于
- `gt` - 大于
- `ge` - 大于等于
- `lt` - 小于
- `le` - 小于等于

### 字符串匹配
- `like` - LIKE 匹配
- `ilike` - 不区分大小写的 LIKE
- `like(pattern, LikeMode.START)` - 前缀匹配
- `like(pattern, LikeMode.END)` - 后缀匹配
- `like(pattern, LikeMode.ANYWHERE)` - 包含匹配（`like`/`ilike` 的默认模式）
- `like(pattern, LikeMode.EXACT)` - 精确 LIKE 模式，不自动补 `%`

### 范围和集合
- `between` - 范围匹配
- `valueIn` - IN 子句（值集合或子查询）
- `valueNotIn` - NOT IN 子句（值集合或子查询）
- `nullableValueIn` - 可包含 null 的 IN 子句
- `nullableValueNotIn` - 可包含 null 的 NOT IN 子句
- `isNull` - IS NULL
- `isNotNull` - IS NOT NULL

### 逻辑运算
- `or()` - OR 逻辑
- `and()` - AND 逻辑（默认）
- `not` - NOT 逻辑

### 动态版本
常用比较、LIKE、BETWEEN、集合谓词有动态版本，在方法名前后加反引号并加 `?`，参数为 null 时返回 null 谓词并被 `where` 忽略；字符串参数为空字符串时也通常被忽略：
- `eq?`
- `ilike?`
- `between?`
- `valueIn?`
- `valueNotIn?`
- 等等...
