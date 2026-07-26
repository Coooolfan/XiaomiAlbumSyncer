# Jimmer DTO 参考

- Jimmer DTO：一个 `.dto` 文件最多只有一个 `export` 语句，用于声明当前文件 DTO 类型的基础实体；可通过 `-> package ...` 显式指定生成包
- Jimmer DTO：实体字段本身 nullable 时，input 字段不要再加 `?`
- Jimmer DTO：搜索条件优先用 `specification` DTO，生成对象可直接传给 Kotlin DSL 的 `where(specification)`
- Jimmer 生成包路径：跟随实体所在包

  实体在 `com.coooolfan.xiaomialbumsyncer.model`，则生成代码在：

  - `com.coooolfan.xiaomialbumsyncer.model.dto`（DTO）
  - `com.coooolfan.xiaomialbumsyncer.model.by`（fetcher DSL 的 `by { ... }` 扩展）

- 本项目 `.dto` 文件位于 `server/src/main/dto/<Entity>.dto`，与实体同名

- `@FetchBy`：companion fetcher 需要显式声明为 `Fetcher<T>`

## DTO 类型选择

```dto
export com.example.model.Book

BookView {
    id
    name
    store {
        id
        name
    }
    authors {
        id
        firstName
        lastName
    }
}

input BookInput {
    # 如果实体属性本身 nullable，不要再写 `?`
    name
    id(store) as storeId
    id(authors) as authorIds
}

specification BookSpecification {
    #allScalars(this)
    valueIn(id) as ids
    ge(price) as minPrice
    le(price) as maxPrice
    store {
        like/i(name) as storeName
    }
    authors {
        like/i(firstName, lastName) as authorName
    }
}
```

常用 specification 函数：

- `ge(price)` / `le(price)`：生成 `minPrice` / `maxPrice` 风格字段时用 `as` 指定别名
- `valueIn(id)`：生成集合 IN 条件
- `associatedIdIn(authors)`：按关联 id 过滤集合关联
- `like/i(firstName, lastName)`：多个字符串字段 OR 组合且忽略大小写
- `flat(store) { ... }`：把关联对象条件拍平到当前 specification

## FetchBy 投影

Controller 直接返回实体时，用 `@FetchBy` 声明返回形状。Kotlin companion fetcher 建议显式类型，尤其是要暴露给 `@FetchBy` 的常量：

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
fun detail(id: Long): @FetchBy("DETAIL_FETCHER") Book
```

`@FetchBy` 的 `value` 是 fetcher 常量名；如果 fetcher 不在当前声明类型的 companion 中，可用 `ownerType` 指定 owner。
