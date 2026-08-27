# JVentas — Backend

API REST en Spring Boot 4.1.1 / Java 21 / PostgreSQL. Reemplaza a la aplicación
de escritorio Swing del proyecto original — ver el resto del repositorio para
el código legado y la documentación de la migración.

## Requisitos

- Java 21
- Docker (para PostgreSQL local; no hace falta instalar Postgres a mano)
- No hace falta tener Maven instalado — usa el wrapper (`./mvnw`)

## Levantar en local

```bash
# 1. Base de datos (una sola vez, o cuando quieras reiniciarla)
docker compose up -d

# 2. Aplicación (aplica las migraciones de Flyway automáticamente al arrancar)
./mvnw spring-boot:run
```

La API queda en `http://localhost:8081`. En el primer arranque (tabla
`usuario` vacía) se crea un administrador y su contraseña generada se
imprime **una sola vez** en el log — buscar el bloque `BootstrapAdminRunner`.

```bash
curl http://localhost:8081/actuator/health

# login (usar el login/password que imprimió el log de arranque)
curl -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"login":"admin","password":"<la-que-salió-en-el-log>"}'

# el resto de la API exige el token del paso anterior
curl http://localhost:8081/api/monedas \
  -H "Authorization: Bearer <token>"
```

## Puertos no estándar, a propósito

Este equipo de desarrollo suele tener otros proyectos corriendo en los
puertos por defecto de Postgres/Spring (5432 y 8080). Para no chocar:

| Servicio   | Puerto por defecto aquí | Variable para cambiarlo |
|------------|--------------------------|--------------------------|
| PostgreSQL | `5433`                    | `DB_PORT`                |
| API        | `8081`                    | `SERVER_PORT`             |

## Estructura

```
src/main/java/com/jventas/backend/
├── BackendApplication.java
├── common/          # manejo de errores compartido (ApiExceptionHandler, ApiError)
├── moneda/          # slice de referencia liviano: entity → repository → service → controller
├── usuario/         # Usuario, Cargo, Rol, Permiso — entidades del dominio de accesos
├── seguridad/        # JWT, SecurityConfig, AuthController, bootstrap del admin inicial
├── catalogo/         # Categoria, Marca, Modelo, UnidadMedida, Impuesto, ListaPrecio, TipoDocumento, MetodoPago
│                     # (CRUD completo desde esta sesión — antes solo lectura; permiso catalogo:administrar)
├── producto/         # CRUD completo, con @PreAuthorize por permiso — patrón de referencia
├── direccion/        # Direccion — sin controller propio, se crea junto con almacén/proveedor/cliente
├── almacen/          # CRUD de almacenes
├── proveedor/        # CRUD de proveedores
├── documento/        # SerieDocumento — numeración correlativa real (F001-00000123)
├── inventario/       # AlmacenStock (stock vigente) y Kardex (historial) — motor interno
├── compra/           # mueve stock hacia arriba, deja kardex — sin validar disponibilidad
├── cliente/          # CRUD de clientes, con la validación RUC-o-DNI que la tabla exige
├── venta/            # espejo de compra: mueve stock hacia abajo, validando disponibilidad
├── traslado/         # dos pasos: crear saca del origen, completar suma al destino
└── pago/             # pagos parciales, saldo, CANCELADO automático al llegar a 0
src/main/resources/
├── application.yml
└── db/migration/    # Flyway — V1 esquema, V2 datos semilla (34 tablas, ver artefacto de diseño)
src/test/java/com/jventas/backend/
├── BackendApplicationTests.java              # arranca el contexto completo contra Postgres real (Testcontainers)
├── producto/ProductoSecurityTests.java        # login + 401/403 reales de punta a punta
├── compra/CompraFlowTests.java                # comprar incrementa stock, anular lo revierte — contra Postgres real
├── venta/VentaFlowTests.java                  # compra real como seed + vender decrementa + stock insuficiente rechaza
├── traslado/TrasladoFlowTests.java            # crear saca del origen, completar suma al destino, anular solo si pendiente
├── pago/PagoFlowTests.java                    # pagos parciales, saldo exacto, CANCELADO automático, no permite sobrepago
└── documento/SerieDocumentoFlowTests.java      # correlativo secuencial, serie ligada a un almacén/tipo específico
```

Cada módulo nuevo sigue el patrón de `producto` (el de referencia completo,
no `moneda` que es deliberadamente el más simple posible): una entidad JPA,
un repositorio, un servicio con la lógica de negocio y las validaciones, un
controller que solo traduce HTTP y declara `@PreAuthorize` por permiso, y
DTOs de request/respuesta (`record`) separados de la entidad. Los catálogos
de apoyo (`categoria`, `marca`, `modelo`, `unidad_medida`, `impuesto`,
`lista_precio`, `tipo_documento`, `metodo_pago`) tienen `POST`/`PUT` además
de `GET`, detrás del permiso `catalogo:administrar` (ver
"Catálogos administrables" más abajo).

`spring.jpa.hibernate.ddl-auto=validate` — el esquema lo gobierna Flyway,
Hibernate solo verifica que las entidades coincidan con las tablas. Si algo
no calza, la app falla al arrancar en vez de alterar el esquema en
silencio.

## Seguridad

JWT stateless (HS256, vía [jjwt](https://github.com/jwtk/jjwt)). El token
lleva las autoridades del usuario embebidas (`ROLE_<rol>` + un código de
permiso por cada uno, p.ej. `producto:crear`) — las requests autenticadas
**no** vuelven a consultar la base de datos, solo validan la firma
(`JwtAuthenticationFilter`). `UsuarioDetailsService` solo se usa una vez,
en `POST /api/auth/login`, para verificar la contraseña contra el hash
BCrypt.

- `POST /api/auth/login` — público, devuelve el access token (30 min) más un
  refresh token opaco (7 días, se guarda hasheado con SHA-256 en
  `refresh_token`, nunca en texto plano). 5 intentos fallidos seguidos
  bloquean el login 15 minutos (`LoginRateLimiter`, en memoria — válido para
  una sola instancia, no para varias detrás de un balanceador).
- `POST /api/auth/refresh` — público, cambia un refresh token válido por un
  access token nuevo sin pedir contraseña de nuevo
- `POST /api/auth/logout` — público, revoca el refresh token (el access
  token ya emitido sigue vivo hasta que expire solo)
- `GET /api/auth/me` — perfil del usuario autenticado
- `GET /api/auditoria-sesion?usuarioId=` (`usuario:administrar`) — historial
  de logins (IP, user-agent, fecha) de un usuario
- Todo lo demás exige `Authorization: Bearer <token>`; sin token responde
  `401` (ver `RestAuthenticationEntryPoint`, cubre las reglas de
  `authorizeHttpRequests`)
- Los endpoints de escritura además exigen el permiso puntual vía
  `@PreAuthorize("hasAuthority('producto:crear')")` — sin él responde `403`
  (ver `ApiExceptionHandler.handleAccessDenied`; **no** es lo mismo que
  `RestAccessDeniedHandler`, que solo cubre las reglas de
  `authorizeHttpRequests` — `@PreAuthorize` es AOP a nivel de método y su
  `AccessDeniedException` llega a Spring MVC como cualquier otra excepción.
  Sin este handler, una denegación de `@PreAuthorize` respondía `500` en
  vez de `403` — bug real que encontramos probando con un usuario de rol
  VENTAS de verdad, no algo hipotético; quedó cubierto en
  `ProductoSecurityTests`)

`JWT_SECRET` vacío en local: `JwtService` genera una clave aleatoria en
cada arranque (los tokens no sobreviven un reinicio — suficiente para
desarrollar, insuficiente para cualquier entorno compartido, donde hay que
definirla).

`BootstrapAdminRunner` crea el primer usuario ADMINISTRADOR únicamente si
la tabla `usuario` está vacía. Nunca hay una contraseña hardcodeada en el
repositorio: si `ADMIN_PASSWORD` no está definida, se genera una al azar y
se imprime una sola vez en el log de arranque.

**RBAC administrable** — los roles y permisos no son fijos en la semilla:
- `GET /api/roles`, `GET /api/roles/{id}`, `POST /api/roles`,
  `PUT /api/roles/{id}` (`usuario:administrar`) — crear un rol nuevo o
  reasignarle permisos, sin tocar la base a mano. `PUT` reemplaza el set
  completo de permisos, no lo suma.
- `GET /api/permisos` (`usuario:administrar`) — solo lectura a propósito:
  un permiso nuevo por API no haría cumplir nada, porque cada
  `@PreAuthorize` está hardcodeado en su controller. Esto existe para que
  la UI de edición de roles pueda listar el catálogo completo como
  checkboxes.
- `GET /api/cargos` (abierto a cualquier autenticado) y
  `POST`/`PUT /api/cargos` (`usuario:administrar`) — antes no había forma
  de listarlos, y el formulario de "crear usuario" los necesita.

## Nota sobre Spring Boot 4

Varias piezas de este scaffold chocaron con la modularización de Spring
Boot 4 — quedan documentadas acá porque no son evidentes por el mensaje de
error:

- **Flyway**: su autoconfiguración vive en un módulo propio
  (`spring-boot-flyway`), que solo se activa con el starter
  `spring-boot-starter-flyway`. Tener únicamente `flyway-core` en el
  classpath falla en silencio — la app arranca, pero Hibernate valida
  contra un esquema vacío porque las migraciones nunca corrieron.
- **Jackson**: el starter web trae Jackson 3 (`tools.jackson.databind`), no
  el clásico `com.fasterxml.jackson.databind`. Cualquier código propio que
  use `ObjectMapper` directamente (como los manejadores de error de
  seguridad) tiene que importar del paquete nuevo.
- **Testcontainers**: sus artefactos se renombraron
  (`org.testcontainers:junit-jupiter` → `testcontainers-junit-jupiter`,
  igual con `postgresql`) — el BOM de Boot 4.1.1 gestiona los nombres
  nuevos, no los viejos.
- **`TestRestTemplate`** se movió de `spring-boot-test` a su propio módulo
  y en Boot 4 se prefiere `RestTestClient` (necesita el starter
  `spring-boot-resttestclient` + `@AutoConfigureRestTestClient`) — API
  fluida al estilo `WebTestClient`, ver `ProductoSecurityTests`.
- **`@PreAuthorize` denegado ≠ regla de `authorizeHttpRequests` denegada**:
  ambos lanzan una subclase de `AccessDeniedException`, pero solo la
  segunda pasa por el `AccessDeniedHandler` de la cadena de filtros. La
  primera es AOP a nivel de método y llega a Spring MVC como una excepción
  común — sin un `@ExceptionHandler(AccessDeniedException.class)` en el
  `@RestControllerAdvice`, cae en el handler genérico y responde `500`.
  No es específico de Boot 4, pero es fácil no toparse con esto hasta
  probar con un usuario de permisos reducidos de verdad.

El `pom.xml` y el código ya reflejan las versiones correctas; queda acá
para no perder tiempo redescubriéndolo.

## El flujo de Compra

Es el primer módulo transaccional real, y el patrón que van a seguir
Venta y Traslado:

1. `POST /api/compras` valida proveedor/almacén/moneda/tipo de documento y
   cada producto de las líneas, calcula subtotal/IGV/total por línea y en
   total (en el servicio, no con triggers de base de datos — la regla de
   negocio queda visible y testeable en Java).
2. Por cada línea: incrementa (o crea) la fila de `almacen_stock`
   correspondiente, y registra una fila de `kardex` (tipo `COMPRA`,
   entrada = cantidad, `stock_resultante` = el nuevo total).
3. `POST /api/compras/{id}/anular` hace exactamente lo inverso: decrementa
   el stock y registra kardex de reversa (tipo `PRODUCTO_ELIMINADO_COMPRA`).
   Nunca se borra la compra ni el kardex — todo el rastro queda, solo
   cambia `estado` a `ANULADO`. Anular dos veces la misma compra se
   rechaza con `400`.
4. El usuario que registra la compra sale del JWT (`Authentication`), no
   del body — no se puede falsear quién hizo la operación.

`CompraFlowTests` prueba las tres cosas de punta a punta contra Postgres
real: el stock sube, el kardex queda, anular lo revierte exacto.

### Costeo por promedio ponderado

Decisión de negocio confirmada (no PEPS ni UEPS). `producto.costo` es el
costo promedio vigente, **global** entre todos los almacenes — no hay un
costo distinto por almacén.

- Solo **comprar** cambia el promedio:
  `nuevoCosto = (stockActual × costoActual + cantidadComprada × precioCompra) ÷ (stockActual + cantidadComprada)`,
  calculado con el stock de **antes** de esa compra
  (`CosteoPromedioPonderadoService.registrarCompra`). Vender y trasladar
  solo lo **leen** — no lo modifican.
- `ProductoRepository.findByIdParaActualizarCosto()` bloquea la fila
  (`SELECT ... FOR UPDATE`) antes de recalcular — mismo motivo que el lock
  de `AlmacenStock`: dos compras concurrentes del mismo producto (aunque
  sean en almacenes distintos, el costo es global) pueden pisarse la
  actualización.
- Cada fila de `kardex` guarda `costoUnitario`/`costoTotal` -- distinto de
  `precio`/`valor`, que es lo que se pagó o cobró en esa transacción, no lo
  que salió costando. En una venta, por ejemplo, `precio` es lo que pagó
  el cliente y `costoUnitario` es el costo promedio vigente — la resta de
  los dos es el margen real de esa línea.
- Anular una compra revierte el promedio con la fórmula inversa
  (`revertirCompra`) — matemáticamente exacto solo si nada más movió el
  costo entre medio. Anular una compra vieja después de que hubo compras
  más nuevas a otro precio puede dejar un promedio ligeramente distinto al
  que había antes de esa compra: es la misma limitación que acepta
  cualquier costeo por promedio (a diferencia de PEPS, no hay un
  historial de lotes que permita reconstruirlo con precisión).
  `CosteoPromedioPonderadoFlowTests` prueba el caso limpio (sin nada de
  por medio), que es exacto.

## El flujo de Venta

Mismo servicio que Compra, calcado — con la única diferencia real que le
corresponde: valida stock disponible antes de confirmar, cosa que una
compra nunca necesita.

- `decrementarStockValidando()` rechaza con `400` si `cantidad_actual <
  cantidad_solicitada` — la venta completa se rechaza (rollback de la
  transacción), no solo la línea que falló.
- Vender registra kardex tipo `VENTA` (salida); anular registra
  `PRODUCTO_ELIMINADO_VENTA` (entrada) y devuelve el stock.
- No existe `venta:editar` — mismo argumento que compra: corregir una
  venta es anularla y volver a registrarla, no mutar un historial que ya
  movió inventario.
- `EstadoTransaccion` vive en el paquete `compra` pero lo usan ambos —
  es el mismo enum nativo de Postgres (`estado_transaccion`) compartido
  por las dos tablas en el esquema; no se duplicó.

`VentaFlowTests` compra stock de verdad primero (mismo pipeline que un
usuario real usaría), confirma que vender más de lo disponible se
rechaza sin tocar el stock, y que vender-y-anular deja todo exactamente
donde empezó.

### Concurrencia en el stock

`AlmacenStockRepository.findByAlmacenIdAndProductoIdParaActualizar()`
bloquea la fila (`SELECT ... FOR UPDATE`, mismo patrón que
`SerieDocumentoRepository`) antes de sumar o restar `cantidad_actual`.
Compra, Venta y Traslado los tres pasan por acá. Bug real, reproducido a
propósito antes del fix (`AlmacenStockConcurrenciaFlowTests` dispara
ventas concurrentes de verdad, con hilos, contra el mismo producto/
almacén): sin el lock, dos ventas simultáneas podían leer el mismo
`cantidad_actual`, y una de las dos actualizaciones se perdía —
15 ventas de 1 unidad concurrentes contra un stock de 50 dejaban el
stock en 48 en vez de 35. El `CHECK (cantidad_actual >= 0)` del esquema
no lo evita: cada `UPDATE` valida contra su propia lectura, nunca contra
la del otro.

`GET /api/kardex` pagina (`Page<KardexResponse>`, ya no una lista sin
límite) — un producto de alta rotación con meses de historial puede
acumular miles de filas.

### Idempotencia en crear compra/venta

`POST /api/ventas` y `POST /api/compras` aceptan un header opcional
`Idempotency-Key`. Si viene, la primera respuesta exitosa se cachea (en
memoria, 10 minutos, mismo enfoque que `LoginRateLimiter` — solo válido
para una instancia) y una segunda request con la misma clave la recibe
de vuelta tal cual, sin crear un documento nuevo (`IdempotencyFilter`,
después del filtro JWT en la cadena de seguridad). Sin el header, el
comportamiento es el de siempre — es opt-in del cliente.

### N+1 en los listados

`GET /api/productos`, `/api/ventas`, `/api/compras`, `/api/traslados` y
`/api/usuarios` traen `join fetch` (o `left join fetch` cuando la
relación es opcional) de las asociaciones que su respuesta "resumen"
necesita (categoría/marca, cliente, proveedor, almacén origen/destino,
cargo). Sin eso, cada fila de la página disparaba una query aparte por
cada asociación leída al armar el DTO — comprobado a mano antes del fix
con `logging.level.org.hibernate.SQL=DEBUG`: 3 productos con categoría y
marca generaban 7 queries (1 + 3×2), y con el fix quedó en 1.

`usuario.roles` es la excepción: es una colección (`@ManyToMany`), y
mezclar `join fetch` de una colección con `LIMIT`/`OFFSET` hace que
Hibernate pagine en memoria en vez de en la base — trae todo antes de
cortar la página, silenciosamente. Para eso se usa
`@BatchSize(size = 25)` en la entidad en vez de `join fetch` en el
repositorio: cada usuario de la página sigue cargando sus roles de forma
lazy, pero Hibernate agrupa esas cargas de varios usuarios en un solo
`... where usuario_id in (...)` en vez de una por usuario.

Todas las queries con `join fetch` en una `Page` llevan `countQuery`
explícito -- `join fetch` no es válido dentro de un `count(...)`, y
dejar que Spring Data lo derive solo es una apuesta a que la versión de
turno lo maneje bien.

## El flujo de Traslado

Es el único de los tres flujos de movimiento de stock que tiene **dos
pasos**, porque en la vida real un traslado no es instantáneo:

1. `POST /api/traslados` saca el stock del almacén origen de inmediato
   (kardex `TRASLADO_SALIDA`) — ya no está disponible ahí, está "en
   tránsito". El destino todavía no recibe nada. Queda `estado = PENDIENTE`.
2. `POST /api/traslados/{id}/completar` recién ahí suma el stock al
   destino (kardex `TRASLADO_ENTRADA`) y pasa a `COMPLETADO`.
3. `POST /api/traslados/{id}/anular` solo funciona mientras está
   `PENDIENTE` — devuelve el stock al origen. Uno ya `COMPLETADO` no se
   puede anular (necesitaría un traslado nuevo en sentido contrario, no
   hay una operación de "deshacer" sobre algo que el destino ya recibió).

## Trazabilidad por número de serie

Decisión de negocio confirmada: aplica por **categoría** de producto
(`categoria.requiere_serie`), no por producto individual — todo lo que
esté en una categoría marcada exige serie, sin excepción por producto.

- **Comprar**: `DetalleCompraRequest.numerosSerie` es obligatorio y debe
  traer exactamente `cantidad` números, sin repetidos, si la categoría del
  producto lo exige (`400` si no). Cada número se guarda como una fila de
  `serie_producto`, ligada a esa línea de compra y al almacén de la
  compra.
- **Vender**: el vendedor elige manualmente cuáles series salen
  (`DetalleVentaRequest.numerosSerie`) — sin asignación automática, a
  propósito. Se valida que cada serie exista, no esté ya vendida, y esté
  físicamente en el almacén de esa venta (`400` si no). `GET
  /api/series-producto?productoId=&almacenId=` lista las disponibles para
  elegir.
- **Anular** una compra desactiva sus series (nunca debieron existir);
  anular una venta las libera de vuelta a disponibles.
- **Traslado es la excepción**: mover series entre almacenes NO pasa por
  selección manual — al completar el traslado se reasignan automáticamente
  (las de `numeroSerie` más chico primero, orden determinístico). La
  decisión de negocio fue específicamente sobre la venta a un cliente, un
  movimiento interno de stock no necesita el mismo cuidado.
- Mismo patrón de lock que el resto de la sesión:
  `SerieProductoRepository.findParaVender()` (al vender) y
  `findDisponiblesParaActualizar()` (al mover por traslado) bloquean fila
  (`SELECT ... FOR UPDATE`) para que dos operaciones concurrentes no
  elijan la misma serie.

`SerieProductoFlowTests` cubre el ciclo completo: comprar sin números de
serie se rechaza, comprar-vender-anular venta-anular compra deja todo
donde corresponde, vender una serie que está en otro almacén se rechaza,
y completar un traslado mueve las series al destino.

## El flujo de Pago

Pagos parciales contra una compra o una venta (nunca ambas, nunca
ninguna — mismo `CHECK` que en el esquema, validado antes en el
servicio). Cuando la suma de pagos activos llega al total, el estado de
la compra/venta pasa solo a `CANCELADO` (= pagado, no anulado — término
heredado del sistema original). Un pago que excede el saldo pendiente se
rechaza con `400`, no se acepta parcialmente.

`GET /api/pagos/saldo?compraId=` o `?ventaId=` expone total, pagado y
saldo — para que el frontend sepa cuánto falta sin tener que sumar los
pagos él mismo.

## Devoluciones parciales

Tercera y última decisión de negocio confirmada. `POST
/api/ventas/{ventaId}/devoluciones` — documento propio (`Devolucion` +
`DetalleDevolucion`), no edita la venta original. `POST
/api/compras/{compraId}/devoluciones` es su espejo del lado de compra
(`DevolucionCompra` + `DetalleDevolucionCompra`) — devolver mercadería
defectuosa o de más al proveedor, mismo plazo y mismo diseño.

- **Plazo**: 10 días desde `venta.fecha`/`compra.fecha` — pasado eso, `400`.
- Se puede devolver **parte** de una línea (2 de 5 unidades, por
  ejemplo), y en más de una devolución sucesiva — cada línea valida contra
  lo que *queda* por devolver (`cantidad - ya devuelta`), no contra el
  total original. `DetalleVentaRepository`/`DetalleCompraRepository`
  `.findByIdParaActualizar()` bloquean la fila (mismo patrón de lock que
  el resto de la sesión) para que dos devoluciones concurrentes de la
  misma línea no acepten ambas más de lo que realmente queda.
- **Venta**: el stock devuelto vuelve automático a disponible (sin
  revisión previa), kardex `DEVOLUCION_VENTA`. **Compra**: el stock sale
  hacia el proveedor, kardex `DEVOLUCION_COMPRA` — y a diferencia de
  venta, sí hay que validar que ese stock siga disponible (si ya se
  vendió a un cliente, no se puede devolver al proveedor); también
  revierte el costo promedio ponderado con esas unidades, mismo criterio
  que anular una compra.
- Si el producto exige serie, hay que indicar cuáles números concretos
  vuelven (`SerieProductoService.revertirParcialPorDevolucion[Compra]`
  valida que esa serie de verdad pertenezca a esa línea puntual, no solo
  al mismo producto — y del lado de compra, que no esté ya vendida).
- El monto se descuenta **directo** del total (`subtotal/igv/total` bajan
  proporcional a lo devuelto) — mismo criterio que un pago, en sentido
  contrario. Si eso deja el saldo (`total - pagos`) en 0, la venta/compra
  pasa sola a `CANCELADO` — mismo criterio que `PagoService`. Bug real
  encontrado revisando la interacción entre ambas features: antes de
  esto, `PagoService` era el único lugar que hacía esa transición, así
  que una venta que quedaba saldada solo por una devolución (sin pago de
  por medio en ese momento) se quedaba con `estado = PENDIENTE` para
  siempre, y el reporte de cuentas por cobrar/pagar la seguía listando
  como pendiente aunque ya no se le debiera nada.

`DevolucionFlowTests`/`DevolucionCompraFlowTests` cubren devolver dentro
del plazo, devolver más de lo disponible entre dos devoluciones
sucesivas (rechazado), devolver fuera del plazo de 10 días (rechazado —
retrasando la fecha directo en la base, ya que la API no la expone para
backdatear), devolver lo suficiente para saldar y pasar a `CANCELADO`, y
del lado de compra, devolver más de lo que queda físicamente en stock
por haberse vendido ya (rechazado).

## Series de documento

`POST /api/compras` y `POST /api/ventas` aceptan `serieDocumentoId`
opcional. Si viene, el correlativo se genera solo (`F001-00000123`) y
pisa `numeroDocumento`; si no viene, `numeroDocumento` sigue siendo
texto libre como antes — retrocompatible con todo lo que ya existía.

`SerieDocumentoService.consumirSiguiente()` corre dentro de la misma
transacción que la compra/venta (propagación `REQUIRED`) y toma un lock
pesimista (`SELECT ... FOR UPDATE`) sobre la fila de la serie antes de
incrementar — sin eso, dos compras concurrentes con la misma serie
podrían leer el mismo número. Una serie está ligada a un almacén y un
tipo de documento específicos; usarla desde otro almacén se rechaza con
`400`.

## Catálogos administrables

Los 8 catálogos (`categoria`, `marca`, `modelo`, `unidad_medida`,
`impuesto`, `lista_precio`, `tipo_documento`, `metodo_pago`) ya tienen
`POST`/`PUT` además de `GET`, todos detrás de `catalogo:administrar`
(hoy solo lo tiene `ADMINISTRADOR`).

`Impuesto` es el único con lógica real: solo puede haber un impuesto
`esDefault = true` a la vez (índice único parcial en la tabla). Al
crear o editar uno como predeterminado, el servicio le quita el flag al
anterior — con `saveAndFlush()` explícito, porque Hibernate agrupa
todos los `INSERT` antes que los `UPDATE` al hacer flush sin importar
el orden en que se llamó a `save()`: sin el flush, el `INSERT` del
nuevo impuesto (`es_default = true`) se ejecutaba antes que el `UPDATE`
que le quitaba el default al anterior, violando el índice. Bug real,
reproducido a mano antes del fix.

## Reportes y archivos

- `GET /api/reportes/cuentas-por-cobrar` (`venta:ver`) y
  `GET /api/reportes/cuentas-por-pagar` (`compra:ver`) — ventas/compras
  `PENDIENTE` con total, pagado, saldo y si están vencidas. El saldo de
  todas las filas se calcula en un solo query agrupado contra `pago`, no
  uno por documento.
- `GET /api/ventas` y `GET /api/compras` aceptan `fechaDesde`/`fechaHasta`
  (ISO-8601) además de `clienteId`/`proveedorId`.
- `POST /api/archivos` (multipart, cualquier autenticado) sube una imagen
  (JPEG/PNG/WEBP/GIF, máx. 5 MB) a disco local
  (`jventas.storage.upload-dir`) y devuelve su URL; `GET /api/archivos/**`
  la sirve, sin autenticación. El cliente asigna esa URL al `imagenUrl` de
  producto (u otra entidad) por su cuenta.

## Cliente y Proveedor

Igual que producto/usuario, se pueden dar de baja sin borrarlos:
`POST /api/clientes/{id}/desactivar` / `/reactivar` (`cliente:editar`) y
lo mismo bajo `/api/proveedores` (`proveedor:editar`). Uno inactivo sigue
siendo consultable por `GET /{id}` pero desaparece de `GET /api/clientes`
y `GET /api/proveedores` (ambos filtran `activo = true` por defecto).

## Producción

`SPRING_PROFILES_ACTIVE=prod` (`application-prod.yml`) exige `JWT_SECRET`
y `CORS_ALLOWED_ORIGINS` sin default — si falta cualquiera de las dos, la
app falla al arrancar con un mensaje explícito en vez de arrancar "bien"
con una configuración insegura o rota (`JwtService` lo valida para el
secreto, `ProdConfiguracionValidator` para CORS). También reduce
`management.endpoints.web.exposure` a solo `health` y el logging de la app
de `DEBUG` a `INFO`.

## Pendiente para las siguientes sesiones

Las 3 decisiones de negocio que quedaban pendientes ya se implementaron:
costeo por promedio ponderado, trazabilidad por número de serie, y
devoluciones parciales (ver sus secciones arriba). No queda ninguna
decisión de negocio abierta.

Fuera de alcance por pedido explícito, no evaluado en este repo:

- Documentación de API (Swagger/OpenAPI)
- Infraestructura de despliegue: `Dockerfile` del backend (solo existe
  `docker-compose.yml` para levantar Postgres en local), CI, estrategia de
  backup/restore

Con el núcleo transaccional (producto, compra, venta, traslado, pago,
devoluciones), la numeración de comprobantes, la administración de
catálogos y RBAC, el costeo, la trazabilidad por serie y los reportes
básicos ya cerrados, el frontend tiene superficie completa para
construirse.
