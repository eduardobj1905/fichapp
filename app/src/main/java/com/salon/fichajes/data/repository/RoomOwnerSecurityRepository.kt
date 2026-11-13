package com.salon.fichajes.data.repository

import androidx.room.withTransaction
import com.salon.fichajes.core.security.PasswordRules
import com.salon.fichajes.core.security.PinHasher
import com.salon.fichajes.core.security.RecoveryCode
import com.salon.fichajes.data.local.AppDatabase
import com.salon.fichajes.data.local.dao.EmployeeDao
import com.salon.fichajes.data.local.dao.OwnerCredentialDao
import com.salon.fichajes.data.local.entity.EmployeeEntity
import com.salon.fichajes.data.local.entity.OwnerCredentialEntity
import com.salon.fichajes.domain.model.Role
import com.salon.fichajes.domain.repository.OwnerSecurityRepository
import com.salon.fichajes.domain.repository.OwnerSetupState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomOwnerSecurityRepository @Inject constructor(
    private val db: AppDatabase,
    private val employeeDao: EmployeeDao,
    private val credentialDao: OwnerCredentialDao,
    private val permissions: PermissionChecker,
    private val clock: Clock,
) : OwnerSecurityRepository {

    override fun observeSetupState(): Flow<OwnerSetupState> =
        combine(employeeDao.observeHasOwner(), credentialDao.observeExists()) { hasOwner, hasPassword ->
            when {
                !hasOwner -> OwnerSetupState.NO_OWNER
                !hasPassword -> OwnerSetupState.NO_MASTER_PASSWORD
                else -> OwnerSetupState.READY
            }
        }

    override suspend fun setupOwner(name: String, weeklyContractHours: Double, pin: String, password: String): String {
        require(name.isNotBlank()) { "Falta el nombre de la dueña" }
        require(PinHasher.isValidPin(pin)) { "El PIN debe tener 4 números" }
        PasswordRules.validate(password, password)?.let { throw IllegalArgumentException(it) }
        val pinSalt = PinHasher.newSalt()
        val pinHash = hash(pin, pinSalt)
        val recoveryCode = RecoveryCode.generate()
        val credential = newCredential(password, recoveryCode)
        db.withTransaction {
            check(employeeDao.countOwners() == 0) { "La app ya está configurada" }
            employeeDao.insert(
                EmployeeEntity(
                    name = name.trim(),
                    role = Role.OWNER,
                    pinHash = pinHash,
                    pinSalt = pinSalt,
                    weeklyContractHours = weeklyContractHours,
                    annualVacationDays = 30,
                    colorArgb = OWNER_COLOR,
                )
            )
            credentialDao.upsert(credential)
        }
        return recoveryCode
    }

    override suspend fun createMasterPassword(actorId: Long, password: String): String {
        permissions.requireOwner(actorId)
        check(credentialDao.get() == null) { "La contraseña maestra ya existe" }
        PasswordRules.validate(password, password)?.let { throw IllegalArgumentException(it) }
        val recoveryCode = RecoveryCode.generate()
        credentialDao.upsert(newCredential(password, recoveryCode))
        return recoveryCode
    }

    override suspend fun verifyMasterPassword(password: String): Boolean {
        val credential = credentialDao.get() ?: return false
        return withContext(Dispatchers.Default) { PinHasher.verify(password, credential.passwordSalt, credential.passwordHash) }
    }

    override suspend fun changeMasterPassword(actorId: Long, currentPassword: String, newPassword: String) {
        permissions.requireOwner(actorId)
        require(verifyMasterPassword(currentPassword)) { "La contraseña actual no es correcta" }
        PasswordRules.validate(newPassword, newPassword)?.let { throw IllegalArgumentException(it) }
        val current = checkNotNull(credentialDao.get())
        val salt = PinHasher.newSalt()
        credentialDao.upsert(current.copy(passwordHash = hash(newPassword, salt), passwordSalt = salt, updatedAt = clock.instant()))
    }

    override suspend fun resetWithRecoveryCode(actorId: Long, recoveryCode: String, newPassword: String): String {
        permissions.requireOwner(actorId)
        val current = checkNotNull(credentialDao.get()) { "No hay contraseña maestra" }
        val validCode = withContext(Dispatchers.Default) {
            PinHasher.verify(RecoveryCode.normalize(recoveryCode), current.recoverySalt, current.recoveryHash)
        }
        require(validCode) { "El código de recuperación no es correcto" }
        PasswordRules.validate(newPassword, newPassword)?.let { throw IllegalArgumentException(it) }
        // El código usado deja de valer: se genera uno nuevo.
        val newCode = RecoveryCode.generate()
        credentialDao.upsert(newCredential(newPassword, newCode))
        return newCode
    }

    override suspend fun transferOwnership(
        actorId: Long,
        currentPassword: String,
        newOwnerId: Long,
        newOwnerPassword: String,
    ): String {
        permissions.requireOwner(actorId)
        require(verifyMasterPassword(currentPassword)) { "La contraseña actual no es correcta" }
        require(newOwnerId != actorId) { "Ya eres la dueña" }
        requireNotNull(employeeDao.getById(newOwnerId)?.takeIf { it.active }) { "Esa persona no existe" }
        PasswordRules.validate(newOwnerPassword, newOwnerPassword)?.let { throw IllegalArgumentException(it) }
        val recoveryCode = RecoveryCode.generate()
        val credential = newCredential(newOwnerPassword, recoveryCode)
        // Todo o nada: nunca puede quedar la app sin dueña o con dos.
        db.withTransaction {
            employeeDao.updateRole(actorId, Role.MANAGER)
            employeeDao.updateRole(newOwnerId, Role.OWNER)
            credentialDao.upsert(credential)
        }
        return recoveryCode
    }

    private suspend fun newCredential(password: String, recoveryCode: String): OwnerCredentialEntity {
        val passwordSalt = PinHasher.newSalt()
        val recoverySalt = PinHasher.newSalt()
        return OwnerCredentialEntity(
            passwordHash = hash(password, passwordSalt),
            passwordSalt = passwordSalt,
            recoveryHash = hash(RecoveryCode.normalize(recoveryCode), recoverySalt),
            recoverySalt = recoverySalt,
            updatedAt = clock.instant(),
        )
    }

    private suspend fun hash(secret: String, salt: String): String =
        withContext(Dispatchers.Default) { PinHasher.hash(secret, salt) }

    private companion object {
        const val OWNER_COLOR = 0xFF8E24AA
    }
}
