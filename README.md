# Fichapp

App Android para controlar **fichajes, horarios y vacaciones** de una peluquería, pensada para una **tablet compartida en modo quiosco**. Todo el mundo entra con su PIN de 4 cifras y empieza en su pantalla de fichar. Hay tres roles:

| Rol | Qué puede hacer |
|---|---|
| **Dueña** (perfil maestro, solo una) | Todo: equipo, roles, corregir cualquier fichaje, horarios, vacaciones, exportar y ceder el control. Las acciones delicadas piden la **contraseña maestra**. |
| **Encargada** | Corregir fichajes, hacer horarios y aprobar vacaciones **de las demás**. Nunca lo suyo: ni sus horas, ni su horario, ni sus vacaciones. No ve *Equipo* ni puede exportar. |
| **Empleada** | Fichar, ver sus horas, pedir vacaciones y ver los avisos cuando le corrigen un fichaje. |

Stack: Kotlin · Jetpack Compose (Material 3) · MVVM · Coroutines/Flow · Hilt · Room · KSP.

**Dónde se guardan los datos**: todo vive en la **memoria interna de la tablet**, en la base de datos SQLite privada de la app (`/data/data/com.salon.fichajes/databases/fichapp.db`). No hace falta internet ni servidor, y el volumen es pequeño (unos pocos MB en años de uso). Como no hay copia en la nube, la **exportación CSV mensual** es la copia de seguridad (ver más abajo).

## Roles y seguridad

- **Nadie puede subirse de rango a sí misma.** Los roles solo los cambia la dueña desde *Equipo* (⋮ → *Ascender a encargada* / *Quitar permisos de encargada*). Además de ocultar los botones, los repositorios comprueban los permisos (`PermissionChecker` + `domain/auth/Permissions`): aunque un fallo de la UI dejara ver un botón, la operación se rechaza.
- **La encargada no puede "autoproclamarse" vacaciones** ni corregirse sus horas ni su horario. Sus solicitudes las revisa la dueña. Tampoco puede cambiar el personal mínimo por día.
- **Contraseña maestra** (mín. 8 caracteres, con letras y números, hash PBKDF2 con salt). Se pide para dar de alta o de baja, cambiar roles, restablecer PINs y exportar. Después de escribirla no se vuelve a pedir durante 5 minutos (`InactivityPolicy.ELEVATION`). Con 5 fallos seguidos se bloquea 60 s.
- **Configuración inicial en dos pasos** (para "ceder el control"): quien instala la app escribe el nombre de la dueña y sus horas, y le **pasa la tablet**. La dueña escribe su PIN y su contraseña maestra sin que nadie más los vea. No hay ningún PIN ni contraseña por defecto en el código.
- **Código de recuperación** (`XXXX-XXXX-XXXX`): se enseña **una sola vez** al crear la contraseña, para apuntarlo en papel. Si la dueña la olvida: *¿La has olvidado?* → código + contraseña nueva, y se genera un código nuevo (el usado deja de valer). Solo se guarda su hash.
- **Ceder el control**: *Equipo* → ⋮ → *Ceder el control*. La dueña actual lo autoriza con su contraseña, que se comprueba en ese momento (siempre se pide, aunque se haya escrito hace poco; con 5 fallos se bloquea un minuto), pasa la tablet y la nueva dueña crea la suya. Se hace en una transacción (nunca puede quedar la app sin dueña o con dos): la antigua dueña pasa a encargada y la nueva recibe su propio código de recuperación.
- **Al actualizar desde la v2** (rol `ADMIN`): la migración `AppDatabase.RolesMigration` convierte a las administradoras en encargadas y a la más antigua en dueña. La primera vez que entra, la app le pide crear su contraseña maestra.

## Funciones de gestión

- **Corregir fichajes**: en *Horas*, toca a una persona → semana con sus tramos → lápiz para corregir, **+** para añadir uno olvidado. Siempre hay que indicar un motivo.
  - Nunca se pierde lo fichado: en la primera corrección se guarda la entrada/salida original (`originalClockIn`/`originalClockOut`), además de quién lo cambió (`correctedBy`), cuándo (`correctedAt`) y por qué (`correctionReason`).
  - La empleada ve en su pantalla un aviso *"Se ha modificado 1 de tus fichajes"* con el antes/después y el motivo, hasta que pulsa **Entendido** (`correctionSeenAt`). En su historial los tramos tocados llevan el lápiz y la nota *"Modificado por Marta"*.
  - Reglas (`CorrectionRules`): motivo obligatorio, nada en el futuro, la salida tiene que ser posterior a la entrada (si es menor, se entiende que es del día siguiente), como mucho 16 h por tramo y sin solaparse con otro tramo de la misma persona.
- **Exportar hoja de horas**: en *Horas*, elige Semana o Mes (con las flechas para ir a periodos anteriores) y pulsa **Exportar hoja (CSV)**. El sistema pregunta dónde guardarla (Descargas de la tablet, pendrive, Drive…) sin pedir permisos. El archivo (`Fichapp_horas_2026-11.csv`) se abre directamente en Excel: separador `;`, decimales con coma y UTF-8 con BOM. Incluye una fila por tramo con toda la auditoría y un resumen por persona (horas trabajadas, horas de contrato y diferencia).
- **Cierre de sesión por inactividad** (`InactivityPolicy`): 3 min para la jefa y 1 min para una empleada. Durante los últimos 15 s aparece *"¿Sigues ahí?"*. Se mide con un reloj monótono (`SystemClock.elapsedRealtime`), así que no depende de la hora del sistema. Además, tras fichar la sesión se cierra a los 3 s.

---

## Cómo ejecutarlo

Requisitos: Android Studio Narwhal (2025.1.3) o posterior y JDK 17+ (sirve el que trae Android Studio).

1. Abre la carpeta en Android Studio. Si no existe `local.properties`, Studio lo crea con la ruta del SDK.
2. Ejecuta la configuración `app` en un emulador o en una tablet (minSdk 26, Android 8.0).

Desde la terminal:

```bash
./gradlew assembleDebug
```

```bash
./gradlew test
```

**Primer arranque**: la app pide crear la cuenta de la administradora (nombre + PIN). No hay ningún PIN por defecto en el código. Después, desde **Equipo**, se da de alta al resto del personal.

Versiones: AGP 8.13.2 · Gradle 8.13 · Kotlin 2.2.21 · KSP 2.2.21-2.0.5 · Hilt 2.57.2 · Room 2.8.3 · Compose BOM 2025.10.01 · compileSdk/targetSdk 36. AGP se ha mantenido en la rama 8.13 porque es la más alta que admite Android Studio 2025.1.3. Si actualizas Studio, puedes subir a AGP 9 (ojo: AGP 9 integra Kotlin y cambia el bloque `kotlin {}`).

---

## Arquitectura

```
com.salon.fichajes
├── core/
│   ├── time/        Periods (día/semana/mes → TimeRange en Instant), WorkedTime, tickerFlow
│   ├── format/      Formateo de duraciones, horas y fechas en español (solo para mostrar)
│   └── security/    PinHasher (PBKDF2WithHmacSHA256 + salt), PasswordRules y RecoveryCode
├── domain/
│   ├── model/       Employee, ClockEntry, ClockResult, Shift, VacationRequest (Kotlin puro)
│   ├── repository/  Interfaces ClockRepository, EmployeeRepository, VacationRepository, ScheduleRepository
│   ├── auth/        Permissions: quién puede hacer qué (dueña / encargada / empleada)
│   ├── clock/       CorrectionRules (validación de las correcciones)
│   ├── staffing/    staffingByDay / daysBelowMinimumIfApproved (cobertura de personal)
│   └── vacation/    vacationBalance (días disponibles por año)
├── data/
│   ├── local/       AppDatabase, Converters, entity/ (tablas + mappers), dao/
│   ├── repository/  Implementaciones Room + PermissionChecker + RoomOwnerSecurityRepository
│   ├── export/      TimesheetCsv (genera el CSV) y TimesheetExporter (lo escribe con SAF)
│   └── settings/    SalonSettings (personal mínimo por día)
├── di/              Módulos Hilt: base de datos, Clock y binding interfaz → implementación
├── session/         SessionManager (sesión en memoria) e InactivityPolicy (cierre por inactividad)
└── ui/
    ├── theme/, components/, navigation/
    ├── setup/       Configuración inicial en dos pasos y crear contraseña maestra
    ├── security/    Diálogo de contraseña maestra ("sudo") y código de recuperación
    ├── login/       Cuadrícula de personas + teclado de PIN con bloqueo por intentos
    ├── employee/clock       Botón de fichar, cronómetro, Hoy/Semana, historial
    ├── employee/vacations   Saldo de días y solicitudes con DateRangePicker
    └── admin/       home (pestañas), hours (+ exportar), entries (corregir fichajes), schedule, vacations, team
```

Flujo de datos: **Room → DAO (Flow) → Repositorio (mapea a dominio) → ViewModel (`combine`/`flatMapLatest` → `StateFlow<UiState>`) → Composable (`collectAsStateWithLifecycle`)**. Los eventos de un solo uso (snackbar, vibración) van por un `Channel`.

### Decisiones clave

- **El fichaje es un tramo** (`clockIn`, `clockOut?`), no dos eventos sueltos. Un tramo abierto significa "trabajando". Es fácil sumar horas y es imposible tener dos salidas seguidas.
- **`ClockEntryDao.toggle()` es una `@Transaction`**: lee el tramo abierto y decide entrada o salida de forma atómica. Si el tramo lleva más de 14 h abierto (o la hora del dispositivo ha ido hacia atrás), devuelve `NeedsAdminReview` y no lo cierra: lo corrige la jefa desde **Horas**, y queda marcado como `ADMIN_CORRECTION` con fecha, autora y motivo, guardando las horas originales.
- **Fechas en `Instant` (UTC)**, guardadas como epochMilli. Solo se convierten a hora local al mostrarlas. Los días/semanas/meses se calculan con `atStartOfDay(zone)`, así que los cambios de hora (días de 23 h o 25 h) salen bien.
- **`java.time.Clock` inyectado**: nadie llama a `Instant.now()`. En tests se usa `Clock.fixed`, y al migrar a Firestore la hora la pondrá el servidor.
- **`WorkedTime.between(entries, range, now)`** suma el solape de cada tramo con el rango. Un tramo abierto cuenta hasta `now`, y uno que cruza medianoche se reparte entre los dos días.
- **Nunca se borran fichajes ni empleadas**. El registro de jornada (art. 34.9 ET, RD-ley 8/2019) debe conservarse **4 años**. Las FKs son `RESTRICT` y la baja de una empleada es lógica (`active = false`).
- **Modo quiosco**: la sesión vive solo en memoria. Tras fichar, la sesión se cierra sola a los 3 s. La raíz de navegación decide la pantalla por estado (sin sesión → PIN), así que no queda ninguna pantalla de otra persona en la pila.
- **PIN**: hash PBKDF2 con salt por persona. Tras 5 fallos seguidos, el teclado se bloquea 30 s.
- **Migraciones de Room**: los esquemas se exportan en `app/schemas` (plugin `androidx.room`) y hay que subirlos a git. La v2 añadió las columnas de auditoría con una `AutoMigration(1 → 2)`, así que actualizar la app no borra ningún fichaje.

---

## Room vs Firestore

### Por qué empezamos con Room

- **Funciona sin conexión**: la peluquería puede quedarse sin wifi y fichar tiene que funcionar siempre.
- **Cero coste y cero configuración**: sin cuenta de Firebase, sin facturación y sin reglas de seguridad que mantener mientras se valida la app con el salón.
- **Consultas por rango baratas** (`clockIn < :to AND (clockOut IS NULL OR clockOut > :from)`) con índices.
- **La migración está preparada**: los ViewModels solo conocen las interfaces de `domain/repository` y los modelos de `domain/model`. Para cambiar a Firestore hay que escribir `FirestoreClockRepository`, etc., y cambiar una línea por repositorio en `di/Modules.kt` (`RepositoryModule`). La UI no se toca.

### Riesgos de guardar solo en la tablet

1. **Si la tablet se rompe, se pierde o se resetea, se pierde el registro de jornada**, que es obligatorio conservar 4 años y enseñar en una inspección de trabajo. `allowBackup` está desactivado a propósito, para que los datos no acaben en copias sin control.
   **Hecho**: la **exportación CSV** desde *Horas*. **Pendiente**: un recordatorio automático el día 1 de cada mes. La idea original era una exportación CSV mensual (una fila por tramo: empleada, entrada, salida, horas, origen, motivo de corrección) que se comparta por correo o Drive con el Storage Access Framework, más un recordatorio el día 1 de cada mes con WorkManager. Eso además sirve como informe firmable para la inspección.
2. **Manipulación de la hora del dispositivo**: con Room, la hora del fichaje es la del reloj de la tablet. Para mitigarlo:
   - Activar **hora automática de la red** y bloquear los Ajustes con **Lock Task Mode** (modo quiosco de Android: `startLockTask()` con la app como *device owner*, o "Fijar pantalla" como mínimo). Así nadie puede salir de la app para cambiar la hora.
   - La app ya detecta saltos hacia atrás: si la salida sería anterior a la entrada, se manda a revisión (`NeedsAdminReview`) en vez de guardar una duración negativa.
   - La solución definitiva es la hora del servidor (Firestore, abajo).

### Pasos para migrar a Firestore

1. **Modelo**: colecciones `employees/{id}`, `clockEntries/{id}` (con `employeeId`, `clockIn`, `clockOut`, `source`, …), `shifts`, `vacationRequests`. Usar IDs `String` (habrá que cambiar `Long` → `String` en los modelos de dominio).
2. **Hora autoritativa**: escribir `clockIn`/`clockOut` con `FieldValue.serverTimestamp()` en lugar de la hora del dispositivo. Así manipular el reloj de la tablet no sirve de nada.
3. **Transacción de fichaje**: `toggle()` pasa a ser una `runTransaction` que lee el tramo abierto de la empleada y lo cierra o crea uno nuevo. Mismo contrato `ClockResult`.
4. **Autenticación y roles**: Firebase Auth (por ejemplo, una cuenta por tablet más una para la jefa) y **custom claims** (`role: "admin"`) asignados desde una Cloud Function o el Admin SDK. Reglas:
   - Una empleada solo puede crear o cerrar **sus** tramos y nunca editarlos después.
   - Nadie puede borrar `clockEntries` (`allow delete: if false`).
   - Solo `request.auth.token.role == "admin"` puede leer todas las horas, aprobar vacaciones y hacer correcciones (que deben incluir `correctionReason`).
5. **Caché offline**: activar la persistencia local de Firestore (activada por defecto en Android) para seguir fichando sin red. Las escrituras se sincronizan al volver la conexión, y `serverTimestamp()` se resuelve en el servidor.
6. **Migrar los datos existentes**: un exportador puntual que lea Room y suba los registros en lotes (`WriteBatch`), manteniendo los `Instant` originales.
7. **Flows**: `addSnapshotListener` envuelto en `callbackFlow` para que los repositorios sigan devolviendo `Flow<List<…>>`.

---

## Tests

`./gradlew test` ejecuta los tests unitarios de JVM:

- `WorkedTimeTest`: tramo que cruza medianoche, tramo abierto, fuera de rango, borde exacto, reloj atrasado y jornada partida.
- `PeriodsTest`: la semana empieza en lunes, semana con cambio de hora (167 h), febrero bisiesto, rango semiabierto.
- `TimesheetCsvTest`: filas de detalle, auditoría de correcciones, tramo nocturno (+1) y abierto, resumen con diferencias y escape de `;` y comillas.
- `CorrectionRulesTest`: motivo obligatorio, salida anterior, futuro, más de 16 h, interpretación del día siguiente.
- `InactivityPolicyTest`: cuenta atrás y tiempos por rol.
- `PermissionsTest`: la dueña gestiona a todas; la encargada a las demás pero nunca a sí misma; la empleada a nadie.
- `MasterPasswordTest`: reglas de la contraseña, formato del código de recuperación y hash con salt.
- `StaffingTest`: `staffingByDay` (solo cuentan las aprobadas, fin inclusive, cruce de mes, personal inactivo, solapes), aviso de personal mínimo y saldo de vacaciones entre años.

## Pendiente / siguientes pasos

- Recordatorio mensual (WorkManager) para exportar la hoja de horas.
- Tests instrumentados de `ClockEntryDao.toggle()` con una base de datos en memoria (`Room.inMemoryDatabaseBuilder`) y tests de ViewModels con `Clock.fixed` y `kotlinx-coroutines-test`.
- Configurar Lock Task Mode / *device owner* en la tablet real.
- Editar los datos de una empleada (horas de contrato, días de vacaciones, cambio de PIN).
- Tests instrumentados de las migraciones v1→v2→v3 con `MigrationTestHelper`.
- Anular un tramo creado por error (sin borrarlo: marcarlo como anulado para que no sume).
- Festivos y días de cierre del salón en el calendario de vacaciones (ahora se cuentan días naturales).
- Icono de la app y pantalla de bienvenida.
