package com.salon.fichajes.domain.auth

import com.salon.fichajes.domain.model.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionsTest {

    private val ownerId = 1L
    private val managerId = 2L
    private val employeeId = 3L

    @Test
    fun `la duena puede gestionar a cualquiera, incluida ella misma`() {
        assertTrue(Permissions.canManageStaffMember(Role.OWNER, ownerId, employeeId))
        assertTrue(Permissions.canManageStaffMember(Role.OWNER, ownerId, managerId))
        assertTrue(Permissions.canManageStaffMember(Role.OWNER, ownerId, ownerId))
    }

    @Test
    fun `la encargada gestiona a las demas pero nunca lo suyo`() {
        assertTrue(Permissions.canManageStaffMember(Role.MANAGER, managerId, employeeId))
        assertTrue(Permissions.canManageStaffMember(Role.MANAGER, managerId, ownerId))
        assertFalse(Permissions.canManageStaffMember(Role.MANAGER, managerId, managerId))
    }

    @Test
    fun `una empleada no puede gestionar a nadie, ni a si misma`() {
        assertFalse(Permissions.canOpenManagement(Role.EMPLOYEE))
        assertFalse(Permissions.canManageStaffMember(Role.EMPLOYEE, employeeId, employeeId))
        assertFalse(Permissions.canManageStaffMember(Role.EMPLOYEE, employeeId, managerId))
    }

    @Test
    fun `solo la duena administra el equipo y no se puede asignar el rol de duena`() {
        assertTrue(Permissions.canAdministerTeam(Role.OWNER))
        assertFalse(Permissions.canAdministerTeam(Role.MANAGER))
        assertFalse(Permissions.canAdministerTeam(Role.EMPLOYEE))
        assertEquals(listOf(Role.EMPLOYEE, Role.MANAGER), Permissions.ASSIGNABLE_ROLES)
    }
}
