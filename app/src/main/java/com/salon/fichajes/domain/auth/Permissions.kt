package com.salon.fichajes.domain.auth

import com.salon.fichajes.domain.model.Role

/**
 * Quién puede hacer qué. Son funciones puras: las usan la UI (para ocultar botones) y los
 * repositorios (para rechazar la operación aunque alguien se salte la UI).
 *
 * Regla de oro: una encargada NO puede tocar nada suyo (sus fichajes, su horario, sus vacaciones)
 * para que no pueda "autoproclamarse" vacaciones ni inflarse horas. Eso solo lo hace la dueña.
 */
object Permissions {

    fun canOpenManagement(role: Role): Boolean = role.canManage

    /** Corregir/añadir fichajes, editar turnos o revisar vacaciones de [targetEmployeeId]. */
    fun canManageStaffMember(actorRole: Role, actorId: Long, targetEmployeeId: Long): Boolean = when (actorRole) {
        Role.OWNER -> true
        Role.MANAGER -> actorId != targetEmployeeId
        Role.EMPLOYEE -> false
    }

    /** Aprobar o rechazar vacaciones: solo la dueña (la encargada solo ve el calendario). */
    fun canReviewVacations(actorRole: Role): Boolean = actorRole == Role.OWNER

    /** Alta, baja, cambio de rol, restablecer PIN, exportar y ceder el control: solo la dueña. */
    fun canAdministerTeam(actorRole: Role): Boolean = actorRole == Role.OWNER

    /** Roles que la dueña puede asignar desde Equipo (dueña solo hay una: se cede, no se asigna). */
    val ASSIGNABLE_ROLES: List<Role> = listOf(Role.EMPLOYEE, Role.MANAGER)
}

/** Error de permisos con mensaje en español para mostrar tal cual. */
class PermissionDeniedException(message: String) : IllegalStateException(message)
