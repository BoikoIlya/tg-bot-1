package com.kamancho.bot.database

import com.kamancho.bot.model.AppUser
import com.kamancho.bot.model.PromoCode
import com.kamancho.bot.model.SubscriptionType
import com.kamancho.bot.utils.withRetry
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

// ==================== USERS TABLE ====================
object Users : Table("users") {
    val id = long("id").autoIncrement()
    val username = varchar("username", 255).nullable()
    val firstName = varchar("first_name", 255).nullable()
    val lastName = varchar("last_name", 255).nullable()
    val userSource = varchar("user_source", 255).nullable()
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

// ==================== SUBSCRIPTIONS TABLE ====================
// Only source of truth for subscription data
object Subscriptions : Table("subscriptions") {
    val id = integer("id").autoIncrement()
    val userId = long("user_id").references(Users.id)
    val type = varchar("type", 50)
    val startDate = long("start_date")
    val expiryDate = long("expiry_date")
    val paymentChargeId = varchar("payment_charge_id", 255).nullable()
    val isActive = bool("is_active").default(true)

    override val primaryKey = PrimaryKey(id)
}

// ==================== PROMO CODES TABLE ====================
object PromoCodes : Table("promo_codes") {
    val code = varchar("code", 100)
    val durationDays = integer("duration_days")
    val maxUses = integer("max_uses")
    val currentUses = integer("current_uses").default(0)
    val isActive = bool("is_active").default(true)

    override val primaryKey = PrimaryKey(code)
}

// ==================== USED PROMO CODES TABLE ====================
object UsedPromoCodes : Table("used_promo_codes") {
    val userId = long("user_id").references(Users.id)
    val promoCode = varchar("promo_code", 100).references(PromoCodes.code)
    val usedAt = long("used_at")

    override val primaryKey = PrimaryKey(userId, promoCode)
}

// ==================== HELPER FUNCTIONS ====================
fun LocalDateTime.toEpochMillis(): Long =
    this.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

fun Long.toLocalDateTime(): LocalDateTime =
    LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(this), ZoneId.systemDefault())

// ==================== DATABASE MANAGER ====================
class DatabaseManager(
    private val dbUrl: String? = null,
    private val user: String? = null,
    private val password: String? = null
) {

    fun init() {
        val databaseUrl = dbUrl ?: System.getenv("DATABASE_URL")
        ?: throw IllegalStateException("DATABASE_URL environment variable not set")

        val isLocalLaunch = System.getenv("DB_USER").equals("admin")

        val (username, password, jdbcUrl) = if (isLocalLaunch) {
            Triple(
                System.getenv("DB_USER"),
                System.getenv("DB_PASSWORD"),
                databaseUrl
            )
        } else {
            val uri = java.net.URI(databaseUrl.replace("postgres://", "http://"))
            val userInfo = uri.userInfo?.split(":")
                ?: throw IllegalStateException("Invalid DATABASE_URL format")

            Triple(
                userInfo[0],
                userInfo[1],
                "jdbc:postgresql://${uri.host}:${uri.port}${uri.path}?sslmode=require"
            )
        }

        val config = HikariConfig().apply {
            setJdbcUrl(jdbcUrl)
            this.username = username
            this.password = password
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 5
            maxLifetime = TimeUnit.MINUTES.toMillis(29)
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            addDataSourceProperty("prepareThreshold", "0")
        }
        config.validate()

        val dataSource = HikariDataSource(config)

        Database.connect(dataSource)

        transaction {
            SchemaUtils.create(
                Users,
                Subscriptions,
                PromoCodes,
                UsedPromoCodes
            )
        }
    }

    // ==================== USER OPERATIONS ====================
    suspend fun getUser(userId: Long): AppUser? {
        return withRetry {
            suspendTransaction {
                Users.selectAll().where { Users.id eq userId }.firstOrNull()?.let { row ->
                    AppUser(
                        id = row[Users.id],
                        username = row[Users.username],
                        firstName = row[Users.firstName],
                        lastName = row[Users.lastName],
                        userSource = row[Users.userSource],
                        createdAt = row[Users.createdAt].toLocalDateTime()
                    )
                }
            }
        }
    }

    suspend fun createUser(
        userId: Long,
        username: String?,
        firstName: String?,
        lastName: String?,
        userSource: String? = null
    ): AppUser {
        return withRetry {
            suspendTransaction {
                Users.insert {
                    it[id] = userId
                    it[this.username] = username
                    it[this.firstName] = firstName
                    it[this.lastName] = lastName
                    it[Users.userSource] = userSource
                    it[createdAt] = LocalDateTime.now().toEpochMillis()
                }

                AppUser(
                    id = userId,
                    username = username,
                    firstName = firstName,
                    lastName = lastName,
                    userSource = userSource
                )
            }
        }
    }

    suspend fun getOrCreateUser(
        userId: Long,
        username: String?,
        firstName: String?,
        lastName: String?,
        userSource: String? = null
    ): AppUser {
        return withRetry {
            getUser(userId) ?: createUser(
                userId,
                username,
                firstName,
                lastName,
                userSource
            )
        }
    }

    // ==================== SUBSCRIPTION OPERATIONS ====================
    suspend fun getActiveSubscription(userId: Long): SubscriptionInfo? {
        return withRetry {
            suspendTransaction {
                Subscriptions.selectAll().where {
                    (Subscriptions.userId eq userId) and
                            (Subscriptions.isActive eq true)
                }.firstOrNull()?.let { row ->
                    SubscriptionInfo(
                        type = SubscriptionType.valueOf(row[Subscriptions.type]),
                        startDate = row[Subscriptions.startDate].toLocalDateTime(),
                        expiryDate = row[Subscriptions.expiryDate].toLocalDateTime(),
                        paymentChargeId = row[Subscriptions.paymentChargeId]
                    )
                }
            }
        }
    }

    suspend fun isSubscriptionActive(userId: Long): Boolean {
        return withRetry {
            suspendTransaction {
                val sub = Subscriptions.selectAll().where {
                    (Subscriptions.userId eq userId) and
                            (Subscriptions.isActive eq true)
                }.firstOrNull() ?: return@suspendTransaction false

                val expiryDate = sub[Subscriptions.expiryDate].toLocalDateTime()
                val now = LocalDateTime.now()

                if (now.isAfter(expiryDate)) {
                    // Mark as expired
                    Subscriptions.update({ Subscriptions.userId eq userId }) {
                        it[isActive] = false
                    }
                    return@suspendTransaction false
                }

                true
            }
        }
    }

    suspend fun activateSubscription(
        userId: Long,
        type: SubscriptionType,
        durationDays: Int,
        paymentChargeId: String? = null
    ) {
        withRetry {
            suspendTransaction {
                val now = LocalDateTime.now()
                val expiryDate = now.toLocalDate().plusDays(durationDays.toLong()).atStartOfDay()

                // Deactivate old subscriptions
                Subscriptions.update({ Subscriptions.userId eq userId }) {
                    it[isActive] = false
                }

                // Create new active subscription
                Subscriptions.insert {
                    it[Subscriptions.userId] = userId
                    it[Subscriptions.type] = type.name
                    it[Subscriptions.startDate] = now.toEpochMillis()
                    it[Subscriptions.expiryDate] = expiryDate.toEpochMillis()
                    it[Subscriptions.paymentChargeId] = paymentChargeId
                    it[Subscriptions.isActive] = true
                }
            }
        }
    }

    suspend fun getSubscriptionExpiryDate(userId: Long): LocalDateTime? {
        return getActiveSubscription(userId)?.expiryDate
    }

    suspend fun getSubscriptionType(userId: Long): SubscriptionType? {
        return getActiveSubscription(userId)?.type
    }

    // ==================== PROMO CODE OPERATIONS ====================
    suspend fun validatePromoCode(code: String): PromoCode? {
        println("[DB] Validating promo code: $code")
        return withRetry {
            suspendTransaction {
                val result = PromoCodes.selectAll().where {
                    (PromoCodes.code eq code) and
                            (PromoCodes.isActive eq true) and
                            (PromoCodes.currentUses less PromoCodes.maxUses)
                }.firstOrNull()?.let { row ->
                    PromoCode(
                        code = row[PromoCodes.code],
                        durationDays = row[PromoCodes.durationDays],
                        maxUses = row[PromoCodes.maxUses],
                        currentUses = row[PromoCodes.currentUses],
                        isActive = row[PromoCodes.isActive]
                    )
                }
                println("[DB] Promo code validation result: $result")
                result
            }
        }
    }

    suspend fun hasUserUsedPromoCode(userId: Long, code: String): Boolean {
        println("[DB] Checking if user $userId used promo code: $code")
        return withRetry {
            suspendTransaction {
                val result = UsedPromoCodes.selectAll().where {
                    (UsedPromoCodes.userId eq userId) and
                            (UsedPromoCodes.promoCode eq code)
                }.count() > 0
                println("[DB] User has used code: $result")
                result
            }
        }
    }

    suspend fun activatePromoCodeSubscription(userId: Long, promoCode: String, durationDays: Int) {
        withRetry {
            suspendTransaction {
                val now = LocalDateTime.now()
                val expiryDate = now.toLocalDate().plusDays(durationDays.toLong()).atStartOfDay()

                // Deactivate old subscriptions
                Subscriptions.update({ Subscriptions.userId eq userId }) {
                    it[isActive] = false
                }

                // Create new active subscription
                Subscriptions.insert {
                    it[Subscriptions.userId] = userId
                    it[Subscriptions.type] = SubscriptionType.PROMO.name
                    it[Subscriptions.startDate] = now.toEpochMillis()
                    it[Subscriptions.expiryDate] = expiryDate.toEpochMillis()
                    it[Subscriptions.isActive] = true
                }

                // Update promo code usage count
                exec(
                    "UPDATE promo_codes SET current_uses = current_uses + 1 WHERE code = '$promoCode'"
                )

                // Record usage
                val alreadyUsed = UsedPromoCodes.selectAll().where {
                    (UsedPromoCodes.userId eq userId) and (UsedPromoCodes.promoCode eq promoCode)
                }.count() > 0

                if (!alreadyUsed) {
                    UsedPromoCodes.insert {
                        it[UsedPromoCodes.userId] = userId
                        it[UsedPromoCodes.promoCode] = promoCode
                        it[usedAt] = LocalDateTime.now().toEpochMillis()
                    }
                }
            }
        }
    }
}

// ==================== DATA CLASSES ====================
data class SubscriptionInfo(
    val type: SubscriptionType,
    val startDate: LocalDateTime,
    val expiryDate: LocalDateTime,
    val paymentChargeId: String? = null
)


