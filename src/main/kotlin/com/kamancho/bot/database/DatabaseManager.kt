package com.kamancho.bot.database

import com.kamancho.bot.model.AppUser
import com.kamancho.bot.model.PromoCode
import com.kamancho.bot.model.SubscriptionType
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDateTime
import java.time.ZoneId

// ==================== USERS TABLE ====================
object Users : Table("users") {
    val id = long("id").autoIncrement()
    val username = varchar("username", 255).nullable()
    val firstName = varchar("first_name", 255).nullable()
    val lastName = varchar("last_name", 255).nullable()
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
fun LocalDateTime.toEpochMillis(): Long = this.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
fun Long.toLocalDateTime(): LocalDateTime = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(this), ZoneId.systemDefault())

// ==================== DATABASE MANAGER ====================
class DatabaseManager(
    private val dbUrl: String? = null,
    private val user: String? = null,
    private val password: String? = null
) {

    fun init() {
        val databaseUrl = dbUrl ?: System.getenv("DATABASE_URL")
        ?: throw IllegalStateException("DATABASE_URL environment variable not set")

        // Сначала парсим URL для получения credentials
        val uri = java.net.URI(databaseUrl.replace("postgres://", "http://"))
        val userInfo = uri.userInfo?.split(":")

        if (userInfo == null || userInfo.size != 2) {
            throw IllegalStateException("Invalid DATABASE_URL format: missing credentials")
        }

        val username = userInfo[0]
        val password = userInfo[1]

        // Формируем JDBC URL без credentials в хосте
        val jdbcUrl = "jdbc:postgresql://${uri.host}:${uri.port}${uri.path}"

        Database.connect(
            url = jdbcUrl,
            driver = "org.postgresql.Driver",
            user = username,
            password = password
        )
        
        transaction {
            SchemaUtils.createMissingTablesAndColumns(
                Users,
                Subscriptions,
                PromoCodes,
                UsedPromoCodes
            )
            
            // Insert default promo codes
            val defaultPromoCodes = listOf(
                "FREE30" to 30,
                "WELCOME7" to 7,
                "TRIAL3" to 3
            )
            
            defaultPromoCodes.forEach { (code, days) ->
                val exists = PromoCodes.selectAll().where { PromoCodes.code eq code }.count() > 0
                if (!exists) {
                    PromoCodes.insert {
                        it[PromoCodes.code] = code
                        it[PromoCodes.durationDays] = days
                        it[PromoCodes.maxUses] = 1000
                        it[PromoCodes.isActive] = true
                    }
                }
            }
        }
    }
    
    // ==================== USER OPERATIONS ====================
    fun getUser(userId: Long): AppUser? {
        return transaction {
            Users.selectAll().where { Users.id eq userId }.firstOrNull()?.let { row ->
                AppUser(
                    id = row[Users.id],
                    username = row[Users.username],
                    firstName = row[Users.firstName],
                    lastName = row[Users.lastName],
                    createdAt = row[Users.createdAt].toLocalDateTime()
                )
            }
        }
    }
    
    fun createUser(userId: Long, username: String?, firstName: String?, lastName: String?): AppUser {
        return transaction {
            Users.insert {
                it[id] = userId
                it[this.username] = username
                it[this.firstName] = firstName
                it[this.lastName] = lastName
                it[createdAt] = LocalDateTime.now().toEpochMillis()
            }
            
            AppUser(
                id = userId,
                username = username,
                firstName = firstName,
                lastName = lastName
            )
        }
    }
    
    fun getOrCreateUser(userId: Long, username: String?, firstName: String?, lastName: String?): AppUser {
        return getUser(userId) ?: createUser(userId, username, firstName, lastName)
    }
    
    // ==================== SUBSCRIPTION OPERATIONS ====================
    fun getActiveSubscription(userId: Long): SubscriptionInfo? {
        return transaction {
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

    fun isSubscriptionActive(userId: Long): Boolean {
        return transaction {
            val sub = Subscriptions.selectAll().where {
                (Subscriptions.userId eq userId) and
                (Subscriptions.isActive eq true)
            }.firstOrNull() ?: return@transaction false

            val expiryDate = sub[Subscriptions.expiryDate].toLocalDateTime()
            val now = LocalDateTime.now()

            if (now.isAfter(expiryDate)) {
                // Mark as expired
                Subscriptions.update({ Subscriptions.userId eq userId }) {
                    it[isActive] = false
                }
                return@transaction false
            }

            true
        }
    }
    
    fun activateSubscription(
        userId: Long,
        type: SubscriptionType,
        durationDays: Int,
        paymentChargeId: String? = null
    ) {
        transaction {
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
    
    fun getSubscriptionExpiryDate(userId: Long): LocalDateTime? {
        return getActiveSubscription(userId)?.expiryDate
    }
    
    fun getSubscriptionType(userId: Long): SubscriptionType? {
        return getActiveSubscription(userId)?.type
    }
    
    // ==================== PROMO CODE OPERATIONS ====================
    fun validatePromoCode(code: String): PromoCode? {
        println("[DB] Validating promo code: $code")
        return transaction {
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
    
    fun hasUserUsedPromoCode(userId: Long, code: String): Boolean {
        println("[DB] Checking if user $userId used promo code: $code")
        return transaction {
            val result = UsedPromoCodes.selectAll().where {
                (UsedPromoCodes.userId eq userId) and
                (UsedPromoCodes.promoCode eq code)
            }.count() > 0
            println("[DB] User has used code: $result")
            result
        }
    }
    
    fun activatePromoCodeSubscription(userId: Long, promoCode: String, durationDays: Int) {
        transaction {
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

// ==================== DATA CLASSES ====================
data class SubscriptionInfo(
    val type: SubscriptionType,
    val startDate: LocalDateTime,
    val expiryDate: LocalDateTime,
    val paymentChargeId: String? = null
)
