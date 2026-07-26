package com.example.model

import org.babyfish.jimmer.sql.*
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 实体模板 - 包含常见字段和关联类型
 */
@Entity
interface EntityTemplate {

    // ========== 主键 ==========
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long

    // ========== 业务键 ==========
    @Key
    val name: String

    // ========== 基础字段 ==========
    val description: String

    val optionalField: String?

    val amount: BigDecimal

    val count: Int

    val active: Boolean

    val createdTime: LocalDateTime

    val updatedTime: LocalDateTime?

    // ========== 多对一关联 ==========
    @ManyToOne
    val parent: ParentEntity?

    @IdView("parent")
    val parentId: Long?

    // ========== 一对多关联（镜像） ==========
    @OneToMany(mappedBy = "parent")
    val children: List<ChildEntity>

    // ========== 多对多关联 ==========
    @ManyToMany
    @JoinTable(
        name = "ENTITY_RELATED_MAPPING",
        joinColumnName = "ENTITY_ID",
        inverseJoinColumnName = "RELATED_ID"
    )
    val relatedEntities: List<RelatedEntity>

    // ========== 带业务字段的中间实体视图 ==========
    @OneToMany(mappedBy = "owner", orderedProps = [OrderedProp("sortOrder")])
    val relationLinks: List<EntityRelationLink>

    @ManyToManyView(prop = "relationLinks", deeperProp = "target")
    val orderedTargets: List<TargetEntity>

    // ========== 版本与逻辑删除 ==========
    @Version
    val version: Int

    @LogicalDeleted("true")
    val deleted: Boolean
}
