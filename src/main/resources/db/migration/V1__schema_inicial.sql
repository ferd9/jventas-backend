-- =====================================================================
-- JVentas — Esquema PostgreSQL (V1)
-- =====================================================================
-- Reemplaza a basededatos/bd.sql (MySQL/gventas).
-- Convenciones: snake_case, PK numérica GENERATED ALWAYS AS IDENTITY,
-- baja lógica vía columna `activo` (se preserva el patrón del sistema
-- original), created_at/updated_at en tablas transaccionales.
--
-- Catálogos editables (unidad_medida, tipo_documento, cargo, metodo_pago,
-- lista_precio, impuesto) en vez de enums fijos donde el negocio puede
-- necesitar agregar valores sin migrar el esquema. Se mantienen como enum
-- los conceptos verdaderamente estructurales (sexo, tipo de cliente,
-- estado de una transacción, tipo de movimiento interno de kardex).
--
-- Nombrado pensado para caer directo en
-- src/main/resources/db/migration/ (Flyway) al crear el proyecto Spring.
-- =====================================================================


-- =====================================================================
-- 1. TIPOS ENUMERADOS (conceptos estructurales, no catálogos de negocio)
-- =====================================================================

CREATE TYPE sexo_persona            AS ENUM ('H','M');
CREATE TYPE tipo_cliente            AS ENUM ('NATURAL','JURIDICA');
CREATE TYPE tipo_cargo_almacen      AS ENUM ('LIDER','EMPLEADO');
CREATE TYPE tipo_producto           AS ENUM ('PRODUCTO_TERMINADO','COMPONENTE','INSUMO');
-- CANCELADO = pagado/liquidado (uso original en español de negocio, no "cancelado" en el sentido de anular)
CREATE TYPE estado_transaccion      AS ENUM ('PENDIENTE','CANCELADO','ANULADO');
CREATE TYPE estado_traslado         AS ENUM ('PENDIENTE','COMPLETADO','ANULADO');
-- tipo_documento_kardex es interno del sistema (lo genera el código, no lo edita el usuario) — se mantiene como enum
CREATE TYPE tipo_documento_kardex   AS ENUM (
    'APERTURA',
    'COMPRA', 'COMPRA_ACTUALIZACION', 'PRODUCTO_ELIMINADO_COMPRA',
    'VENTA',  'VENTA_ACTUALIZACION',  'PRODUCTO_ELIMINADO_VENTA',
    'TRASLADO_SALIDA', 'TRASLADO_ENTRADA',
    'DEVOLUCION_VENTA', 'DEVOLUCION_COMPRA'
);


-- =====================================================================
-- 2. CATÁLOGOS EDITABLES
-- =====================================================================
-- Antes eran enums fijos (unidad_medida, tipo_documento_compra/venta,
-- tipo_cargo_usuario). Como tabla, se agregan valores nuevos con un
-- INSERT en vez de una migración de esquema.

CREATE TABLE moneda (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre              VARCHAR(50)  NOT NULL,
    simbolo             VARCHAR(3)   NOT NULL,
    codigo_iso          VARCHAR(3)   UNIQUE,              -- p.ej. PEN, USD — no existía en el original, se agrega para integraciones futuras
    es_predeterminada   BOOLEAN      NOT NULL DEFAULT FALSE,
    activo              BOOLEAN      NOT NULL DEFAULT TRUE
);
CREATE UNIQUE INDEX uq_moneda_predeterminada ON moneda (es_predeterminada) WHERE es_predeterminada;

CREATE TABLE categoria (   -- antes: gv_clase
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    categoria_padre_id  BIGINT REFERENCES categoria(id),   -- árbol de categorías; el original era una lista plana
    nombre              VARCHAR(125) NOT NULL,
    -- decisión de negocio: los productos de esta categoría exigen número de serie
    -- individual por unidad (electrodomésticos, equipos con garantía, etc.) -- no se
    -- hereda de una categoría padre a sus hijas, cada fila lleva su propio flag.
    requiere_serie      BOOLEAN      NOT NULL DEFAULT FALSE,
    activo              BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_categoria_no_auto_padre CHECK (categoria_padre_id IS DISTINCT FROM id),
    UNIQUE (categoria_padre_id, nombre)
);

CREATE TABLE marca (
    id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre  VARCHAR(125) NOT NULL UNIQUE,
    activo  BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE modelo (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    marca_id  BIGINT       NOT NULL REFERENCES marca(id),   -- el original no vinculaba modelo con marca
    nombre    VARCHAR(125) NOT NULL,
    activo    BOOLEAN      NOT NULL DEFAULT TRUE,
    UNIQUE (marca_id, nombre)
);

CREATE TABLE unidad_medida (   -- antes: enum unidad_medida en gv_producto
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre        VARCHAR(40) NOT NULL UNIQUE,
    abreviatura   VARCHAR(10),
    activo        BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE cargo (   -- antes: enum tipo_cargo en gv_usuario
    id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre  VARCHAR(40) NOT NULL UNIQUE,
    activo  BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE metodo_pago (   -- no existía en el original
    id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre  VARCHAR(40) NOT NULL UNIQUE,     -- Efectivo, Tarjeta, Transferencia, Crédito...
    activo  BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE lista_precio (   -- reemplaza a las columnas fijas precio_mayor/precio_menor de producto
    id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre  VARCHAR(60) NOT NULL UNIQUE,     -- Mayorista, Minorista, Promocional...
    activo  BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE impuesto (   -- reemplaza al booleano aplica_igv (fijo al 18% peruano)
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre       VARCHAR(60)  NOT NULL,       -- IGV, Exonerado, ISC...
    tasa         NUMERIC(5,2) NOT NULL,       -- porcentaje, ej. 18.00
    es_default   BOOLEAN NOT NULL DEFAULT FALSE,
    activo       BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE UNIQUE INDEX uq_impuesto_predeterminado ON impuesto (es_default) WHERE es_default;

CREATE TABLE tipo_documento (   -- antes: enum tipo_documento_compra + enum tipo_documento_venta, separados
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre          VARCHAR(40) NOT NULL UNIQUE,   -- FACTURA, BOLETA, GUIA, NOTA_DE_PEDIDO, DUA...
    aplica_compra   BOOLEAN NOT NULL DEFAULT TRUE,
    aplica_venta    BOOLEAN NOT NULL DEFAULT TRUE,
    activo          BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE direccion (   -- reemplaza al varchar suelto repetido en almacen/proveedor y se suma a cliente (que no tenía)
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pais              VARCHAR(60)  NOT NULL DEFAULT 'Perú',
    departamento      VARCHAR(80),
    provincia         VARCHAR(80),
    distrito          VARCHAR(80),
    direccion_linea   VARCHAR(200) NOT NULL,
    referencia        VARCHAR(200)
);


-- =====================================================================
-- 3. ORGANIZACIÓN, USUARIOS Y PERMISOS
-- =====================================================================

CREATE TABLE almacen (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre        VARCHAR(125) NOT NULL,
    direccion_id  BIGINT NOT NULL REFERENCES direccion(id),
    activo        BOOLEAN NOT NULL DEFAULT TRUE
    -- la columna `encargado` del original se elimina: es redundante con encargado_almacen,
    -- que ya modela usuario<->almacen con tipo_cargo.
);

CREATE TABLE usuario (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dni               VARCHAR(30)  NOT NULL UNIQUE,
    codigo            VARCHAR(30)  NOT NULL UNIQUE,
    login             VARCHAR(125) NOT NULL UNIQUE,
    nombre            VARCHAR(185) NOT NULL,
    apellidos         VARCHAR(185) NOT NULL,
    foto_url          TEXT,                                -- antes: longblob `foto`
    password_hash     VARCHAR(255) NOT NULL,                -- BCrypt (el hash ya incluye su propio salt)
    fecha_nacimiento  DATE,                                 -- antes: bigint epoch
    telefono          VARCHAR(15)  NOT NULL,
    telefono2         VARCHAR(15),
    celular           VARCHAR(15),
    email             VARCHAR(125),
    sexo              sexo_persona NOT NULL,
    cargo_id          BIGINT NOT NULL REFERENCES cargo(id),
    descripcion       TEXT,
    activo            BOOLEAN      NOT NULL DEFAULT TRUE,
    fecha_registro    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    fecha_baja        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
    -- la PK compuesta (idu, dni, codigo, login) del original se reemplaza por PK simple + UNIQUE en cada campo natural.
    -- clave + salt manuales se reemplazan por un solo password_hash (BCrypt) — ver hallazgo crítico #2 del plan de migración.
);

CREATE TABLE encargado_almacen (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id  BIGINT NOT NULL REFERENCES usuario(id),
    almacen_id  BIGINT NOT NULL REFERENCES almacen(id),
    tipo_cargo  tipo_cargo_almacen NOT NULL,
    activo      BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (usuario_id, almacen_id)
);

-- RBAC plano (rol / permiso), reemplaza al motor jerárquico estilo Yii1
-- (gv_authitem / gv_authitemchild / gv_asignarauth) — ver decisión abierta §7
-- del plan de migración; este es el modelo por defecto propuesto, idiomático
-- para Spring Security.
CREATE TABLE rol (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre       VARCHAR(64) NOT NULL UNIQUE,
    descripcion  TEXT
);

CREATE TABLE permiso (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    codigo       VARCHAR(64) NOT NULL UNIQUE,   -- ej: 'producto:crear', 'venta:anular'
    descripcion  TEXT
);

CREATE TABLE rol_permiso (
    rol_id      BIGINT NOT NULL REFERENCES rol(id)     ON DELETE CASCADE,
    permiso_id  BIGINT NOT NULL REFERENCES permiso(id) ON DELETE CASCADE,
    PRIMARY KEY (rol_id, permiso_id)
);

CREATE TABLE usuario_rol (
    usuario_id  BIGINT NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    rol_id      BIGINT NOT NULL REFERENCES rol(id)     ON DELETE CASCADE,
    PRIMARY KEY (usuario_id, rol_id)
);

CREATE TABLE auditoria_sesion (   -- antes: gv_bitacora
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id          BIGINT NOT NULL REFERENCES usuario(id),
    sistema_operativo   VARCHAR(80),            -- campos del cliente de escritorio original; sin poblar desde el login web
    arquitectura        VARCHAR(80),
    version_app         VARCHAR(50),
    cuenta_so           VARCHAR(200),           -- nombre de cuenta del SO del equipo cliente
    ip_address          VARCHAR(45),            -- IPv4 o IPv6; se llena desde el login web
    user_agent          VARCHAR(255),
    fecha_actividad     TIMESTAMPTZ NOT NULL DEFAULT now(),   -- antes: bigint epoch
    ultima_actividad    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Sesiones de refresco: un access token de vida corta (JWT) + este token
-- opaco de vida larga para renovarlo sin pedir contraseña de nuevo. Se
-- guarda el hash, nunca el valor real -- igual que una contraseña.
CREATE TABLE refresh_token (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id   BIGINT NOT NULL REFERENCES usuario(id),
    token_hash   VARCHAR(64) NOT NULL UNIQUE,   -- SHA-256 en hexadecimal
    expira_en    TIMESTAMPTZ NOT NULL,
    revocado     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_token_usuario ON refresh_token (usuario_id);


-- =====================================================================
-- 4. TERCEROS
-- =====================================================================

CREATE TABLE proveedor (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ruc                   VARCHAR(50)  NOT NULL UNIQUE,
    razon_social          VARCHAR(150) NOT NULL,
    direccion_id          BIGINT NOT NULL REFERENCES direccion(id),
    telefono              VARCHAR(15),
    telefono_alternativo  VARCHAR(15),           -- reemplaza a `nextel`/`fax`, obsoletos
    cuenta_bancaria       VARCHAR(30),
    nombre_contacto       VARCHAR(125),
    email                 VARCHAR(125),
    rubro                 VARCHAR(125),
    activo                BOOLEAN NOT NULL DEFAULT TRUE
    -- el campo `productos` (texto libre) del original se elimina: es denormalizado;
    -- la relación real proveedor<->producto vive en el historial de compras.
);

CREATE TABLE cliente (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ruc              VARCHAR(35),
    dni              VARCHAR(30),
    nombre           VARCHAR(125) NOT NULL,
    apellidos        VARCHAR(125) NOT NULL,
    tipo             tipo_cliente NOT NULL,
    direccion_id     BIGINT REFERENCES direccion(id),   -- nuevo: el original no tenía dirección de cliente
    email            VARCHAR(225),
    telefono         VARCHAR(15),
    celular          VARCHAR(15),
    sexo             sexo_persona,               -- solo aplica si tipo = NATURAL
    fecha_registro   TIMESTAMPTZ NOT NULL DEFAULT now(),
    activo           BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_cliente_identificacion CHECK (ruc IS NOT NULL OR dni IS NOT NULL)
    -- `movil`/`nextel`/`fax` del original se consolidan en telefono/celular.
);


-- =====================================================================
-- 5. SERIES Y CORRELATIVOS DE COMPROBANTES
-- =====================================================================
-- No existía en el original: numeración real tipo F001-00000123 por
-- almacén y tipo de documento, en vez de un campo de texto libre.

CREATE TABLE serie_documento (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    almacen_id           BIGINT NOT NULL REFERENCES almacen(id),
    tipo_documento_id    BIGINT NOT NULL REFERENCES tipo_documento(id),
    serie                VARCHAR(10) NOT NULL,       -- ej. 'F001', 'B001'
    correlativo_actual   INTEGER NOT NULL DEFAULT 0,
    activo               BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (almacen_id, tipo_documento_id, serie)
);


-- =====================================================================
-- 6. PRODUCTOS E INVENTARIO
-- =====================================================================

CREATE TABLE producto (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    codigo_barras       VARCHAR(50)  NOT NULL UNIQUE,
    codigo              VARCHAR(50)  NOT NULL UNIQUE,
    codigo_fabricante   VARCHAR(30),
    nombre              VARCHAR(125) NOT NULL,
    costo               NUMERIC(12,3) NOT NULL,
    stock_minimo        INTEGER NOT NULL DEFAULT 0,
    tipo                tipo_producto NOT NULL,
    moneda_id           BIGINT NOT NULL REFERENCES moneda(id),
    impuesto_id         BIGINT REFERENCES impuesto(id),       -- antes: booleano aplica_igv; null = no aplica impuesto
    imagen_url          TEXT,                                  -- antes: longblob `imagen`
    categoria_id        BIGINT REFERENCES categoria(id),
    marca_id            BIGINT REFERENCES marca(id),
    modelo_id           BIGINT REFERENCES modelo(id),
    unidad_medida_id    BIGINT REFERENCES unidad_medida(id),   -- antes: enum
    ubicacion           VARCHAR(10),
    peso                NUMERIC(8,3) DEFAULT 0,
    activo              BOOLEAN NOT NULL DEFAULT TRUE
    -- precio_mayor/precio_menor del original pasan a producto_precio (tabla lista_precio),
    -- ya no son columnas fijas: admite más de 2 niveles o precios por cliente.
);

-- Reemplaza a las columnas precio_mayor/precio_menor: un producto puede
-- tener un precio distinto por cada lista (Mayorista, Minorista, Promocional...).
CREATE TABLE producto_precio (
    producto_id      BIGINT NOT NULL REFERENCES producto(id) ON DELETE CASCADE,
    lista_precio_id  BIGINT NOT NULL REFERENCES lista_precio(id),
    precio           NUMERIC(12,3) NOT NULL,
    PRIMARY KEY (producto_id, lista_precio_id)
);

-- Stock actual por almacén: una fila = la verdad vigente de cuánto hay.
-- Reemplaza a gv_almaceproduct, que mezclaba "cantidad actual" con un log
-- de movimientos (tipo_manipulacion) — ese rol pasa por completo a `kardex`.
CREATE TABLE almacen_stock (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    almacen_id        BIGINT  NOT NULL REFERENCES almacen(id),
    producto_id       BIGINT  NOT NULL REFERENCES producto(id),
    cantidad_actual   INTEGER NOT NULL DEFAULT 0,
    activo            BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (almacen_id, producto_id),
    CONSTRAINT chk_almacen_stock_no_negativo CHECK (cantidad_actual >= 0)
);


-- =====================================================================
-- 7. COMPRAS
-- =====================================================================

CREATE TABLE compra (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tipo_documento_id   BIGINT NOT NULL REFERENCES tipo_documento(id),
    serie_documento_id  BIGINT REFERENCES serie_documento(id),   -- nulo si el documento no usa numeración correlativa propia
    numero_documento    VARCHAR(20),                              -- número resuelto/mostrado, aunque cambie la config de la serie
    proveedor_id        BIGINT NOT NULL REFERENCES proveedor(id),
    usuario_id          BIGINT NOT NULL REFERENCES usuario(id),
    almacen_id          BIGINT NOT NULL REFERENCES almacen(id),   -- no existía en el original: sin él no se sabe a qué almacén entra el stock
    moneda_id           BIGINT NOT NULL REFERENCES moneda(id),
    estado              estado_transaccion NOT NULL DEFAULT 'PENDIENTE',
    fecha_vencimiento   DATE,                                     -- compras a crédito
    num_items           INTEGER,
    observaciones       TEXT,
    subtotal            NUMERIC(12,2),
    igv                 NUMERIC(12,2),
    total                NUMERIC(12,2),
    fecha               DATE NOT NULL DEFAULT CURRENT_DATE,
    activo              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE detalle_compra (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    compra_id         BIGINT  NOT NULL REFERENCES compra(id) ON DELETE CASCADE,
    producto_id       BIGINT  NOT NULL REFERENCES producto(id),
    cantidad          INTEGER NOT NULL CHECK (cantidad > 0),
    precio_unitario   NUMERIC(12,2) NOT NULL,   -- antes solo existía `importe` (total de línea); se recupera el precio unitario
    descuento_pct     NUMERIC(5,2) DEFAULT 0,
    impuesto_id       BIGINT REFERENCES impuesto(id),
    monto_impuesto    NUMERIC(12,2) DEFAULT 0,   -- congelado al momento de la compra, aunque luego cambie la tasa del catálogo
    subtotal          NUMERIC(12,2) NOT NULL,    -- cantidad * precio_unitario * (1 - descuento_pct/100), calculado en servicio
    activo            BOOLEAN NOT NULL DEFAULT TRUE
);

-- Decisión de negocio confirmada: devolución parcial de una compra a su
-- proveedor (mercadería defectuosa o de más), mismo plazo de 10 días desde
-- la fecha de compra y mismo diseño que devolucion/detalle_devolucion del
-- lado de venta -- documento propio, no edita la compra original.
CREATE TABLE devolucion_compra (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    compra_id    BIGINT NOT NULL REFERENCES compra(id),
    usuario_id   BIGINT NOT NULL REFERENCES usuario(id),
    fecha        TIMESTAMPTZ NOT NULL DEFAULT now(),
    motivo       TEXT,
    monto_total  NUMERIC(12,2) NOT NULL,
    activo       BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE detalle_devolucion_compra (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    devolucion_compra_id   BIGINT  NOT NULL REFERENCES devolucion_compra(id) ON DELETE CASCADE,
    detalle_compra_id      BIGINT  NOT NULL REFERENCES detalle_compra(id),
    cantidad               INTEGER NOT NULL CHECK (cantidad > 0),
    monto                  NUMERIC(12,2) NOT NULL,
    monto_impuesto         NUMERIC(12,2) NOT NULL DEFAULT 0
);

CREATE INDEX idx_detalle_devolucion_compra_detalle ON detalle_devolucion_compra (detalle_compra_id);


-- =====================================================================
-- 8. VENTAS — módulo nuevo, espejo de Compras
-- =====================================================================

CREATE TABLE venta (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tipo_documento_id   BIGINT NOT NULL REFERENCES tipo_documento(id),
    serie_documento_id  BIGINT REFERENCES serie_documento(id),
    numero_documento    VARCHAR(20),
    cliente_id          BIGINT NOT NULL REFERENCES cliente(id),
    usuario_id          BIGINT NOT NULL REFERENCES usuario(id),   -- vendedor
    almacen_id          BIGINT NOT NULL REFERENCES almacen(id),   -- de dónde sale el stock
    moneda_id           BIGINT NOT NULL REFERENCES moneda(id),
    estado              estado_transaccion NOT NULL DEFAULT 'PENDIENTE',
    fecha_vencimiento   DATE,                                     -- ventas a crédito
    num_items           INTEGER,
    observaciones       TEXT,
    subtotal            NUMERIC(12,2),
    igv                 NUMERIC(12,2),
    total                NUMERIC(12,2),
    fecha               DATE NOT NULL DEFAULT CURRENT_DATE,
    activo              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE detalle_venta (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    venta_id          BIGINT  NOT NULL REFERENCES venta(id) ON DELETE CASCADE,
    producto_id       BIGINT  NOT NULL REFERENCES producto(id),
    cantidad          INTEGER NOT NULL CHECK (cantidad > 0),
    precio_unitario   NUMERIC(12,2) NOT NULL,
    descuento_pct     NUMERIC(5,2) DEFAULT 0,
    impuesto_id       BIGINT REFERENCES impuesto(id),
    monto_impuesto    NUMERIC(12,2) DEFAULT 0,
    subtotal          NUMERIC(12,2) NOT NULL,
    activo            BOOLEAN NOT NULL DEFAULT TRUE
);

-- Decisión de negocio confirmada: devoluciones parciales de una venta, con
-- plazo de 10 días desde la fecha de venta. Documento propio (no edita la
-- venta original) -- el stock devuelto vuelve automático a disponible sin
-- revisión previa, y el reembolso se descuenta directo del total de la venta.
CREATE TABLE devolucion (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    venta_id     BIGINT NOT NULL REFERENCES venta(id),
    usuario_id   BIGINT NOT NULL REFERENCES usuario(id),
    fecha        TIMESTAMPTZ NOT NULL DEFAULT now(),
    motivo       TEXT,
    monto_total  NUMERIC(12,2) NOT NULL,
    activo       BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE detalle_devolucion (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    devolucion_id      BIGINT  NOT NULL REFERENCES devolucion(id) ON DELETE CASCADE,
    detalle_venta_id   BIGINT  NOT NULL REFERENCES detalle_venta(id),
    cantidad           INTEGER NOT NULL CHECK (cantidad > 0),
    monto              NUMERIC(12,2) NOT NULL,
    monto_impuesto     NUMERIC(12,2) NOT NULL DEFAULT 0
);

CREATE INDEX idx_detalle_devolucion_detalle_venta ON detalle_devolucion (detalle_venta_id);


-- =====================================================================
-- 9. TRASLADOS ENTRE ALMACENES
-- =====================================================================
-- Existía como valor 'TRANSLADO' en gv_almaceproduct.tipo_manipulacion,
-- sin ninguna tabla propia ni pantalla. Se construye como módulo completo,
-- espejo de compra/venta pero moviendo stock entre dos almacenes propios.

CREATE TABLE traslado_almacen (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    almacen_origen_id   BIGINT NOT NULL REFERENCES almacen(id),
    almacen_destino_id  BIGINT NOT NULL REFERENCES almacen(id),
    usuario_id          BIGINT NOT NULL REFERENCES usuario(id),
    estado              estado_traslado NOT NULL DEFAULT 'PENDIENTE',
    fecha               DATE NOT NULL DEFAULT CURRENT_DATE,
    observaciones       TEXT,
    activo              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_traslado_almacenes_distintos CHECK (almacen_origen_id <> almacen_destino_id)
);

CREATE TABLE detalle_traslado (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    traslado_id   BIGINT  NOT NULL REFERENCES traslado_almacen(id) ON DELETE CASCADE,
    producto_id   BIGINT  NOT NULL REFERENCES producto(id),
    cantidad      INTEGER NOT NULL CHECK (cantidad > 0),
    activo        BOOLEAN NOT NULL DEFAULT TRUE
);


-- =====================================================================
-- 10. KARDEX — historial de movimientos de inventario
-- =====================================================================
-- Ahora referencia almacén (el original no lo hacía, incoherente con tener
-- múltiples almacenes) y enlaza directo a la compra/venta/traslado que lo
-- originó en vez de solo un `numero_documento` de texto suelto.

CREATE TABLE kardex (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    almacen_id         BIGINT NOT NULL REFERENCES almacen(id),
    producto_id        BIGINT NOT NULL REFERENCES producto(id),
    fecha              DATE NOT NULL DEFAULT CURRENT_DATE,
    tipo_documento     tipo_documento_kardex NOT NULL,
    numero_documento   VARCHAR(30),                    -- libre: usado en ajustes manuales / APERTURA sin compra ni venta asociada
    usuario_id         BIGINT REFERENCES usuario(id),  -- quién hizo el movimiento; en compra/venta/traslado es redundante con esa cabecera, en APERTURA es la única referencia
    compra_id          BIGINT REFERENCES compra(id),
    venta_id           BIGINT REFERENCES venta(id),
    traslado_id        BIGINT REFERENCES traslado_almacen(id),
    entrada            INTEGER DEFAULT 0,
    salida             INTEGER DEFAULT 0,
    precio             NUMERIC(12,2),                  -- precio de la transacción (lo pagado en compra, lo cobrado en venta) -- no es el costo
    valor              NUMERIC(12,2),
    -- costeo por promedio ponderado: costo_unitario es el costo de este movimiento puntual
    -- (en COMPRA, lo que se pagó; en VENTA/TRASLADO/APERTURA, el producto.costo vigente al
    -- momento del movimiento, ya que solo comprar cambia el promedio).
    costo_unitario     NUMERIC(12,2),
    costo_total        NUMERIC(12,2),
    stock_resultante   INTEGER NOT NULL,               -- antes: `stock_actual` — es el saldo tras este movimiento, no el stock vigente
    valor_total        NUMERIC(12,2),
    activo             BOOLEAN NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_kardex_origen_unico CHECK (
        (CASE WHEN compra_id   IS NOT NULL THEN 1 ELSE 0 END) +
        (CASE WHEN venta_id    IS NOT NULL THEN 1 ELSE 0 END) +
        (CASE WHEN traslado_id IS NOT NULL THEN 1 ELSE 0 END) <= 1
    )
);


-- =====================================================================
-- 11. PAGOS Y COBROS
-- =====================================================================
-- No existía en el original: ni compra ni venta registraban cómo se
-- pagaban. Soporta pagos parciales (varias filas por compra/venta) y
-- crédito (fecha_vencimiento en compra/venta, saldo = total - Σ pagos).

CREATE TABLE pago (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    compra_id       BIGINT REFERENCES compra(id),
    venta_id        BIGINT REFERENCES venta(id),
    metodo_pago_id  BIGINT NOT NULL REFERENCES metodo_pago(id),
    usuario_id      BIGINT NOT NULL REFERENCES usuario(id),   -- quien registró el cobro/pago
    monto           NUMERIC(12,2) NOT NULL CHECK (monto > 0),
    fecha_pago      TIMESTAMPTZ NOT NULL DEFAULT now(),
    referencia      VARCHAR(100),                              -- número de operación, voucher, etc.
    activo          BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_pago_origen_exclusivo CHECK ((compra_id IS NULL) <> (venta_id IS NULL))
);


-- =====================================================================
-- 12. TRAZABILIDAD POR SERIE
-- =====================================================================
-- La tabla existía en el original (gv_seriesproducto) sin ningún código
-- que la usara. Decisión de negocio confirmada: se activa para los
-- productos cuya categoría lo exige (categoria.requiere_serie) -- al
-- comprar se registra el número de serie de cada unidad, al vender el
-- vendedor elige manualmente cuál sale.

CREATE TABLE serie_producto (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    producto_id        BIGINT NOT NULL REFERENCES producto(id),
    numero_serie       VARCHAR(50) NOT NULL,
    -- almacén donde está físicamente esta unidad -- lo mueve un traslado (recién al
    -- completarse, no al crearse, igual que almacen_stock) y queda tal cual tras una venta,
    -- como último paradero conocido.
    almacen_id         BIGINT REFERENCES almacen(id),
    proveedor_id       BIGINT REFERENCES proveedor(id),
    detalle_compra_id  BIGINT REFERENCES detalle_compra(id),   -- línea de compra que la trajo al stock (trazabilidad real; no existía en el original)
    cliente_id         BIGINT REFERENCES cliente(id),
    detalle_venta_id   BIGINT REFERENCES detalle_venta(id),    -- línea de venta que la vendió (idem)
    vendido            BOOLEAN NOT NULL DEFAULT FALSE,
    activo             BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (producto_id, numero_serie)
);


-- =====================================================================
-- 13. ÍNDICES DE APOYO
-- =====================================================================

CREATE INDEX idx_categoria_padre         ON categoria (categoria_padre_id);
CREATE INDEX idx_producto_nombre         ON producto (nombre);
CREATE INDEX idx_producto_categoria      ON producto (categoria_id);
CREATE INDEX idx_producto_marca          ON producto (marca_id);
CREATE INDEX idx_cliente_nombre          ON cliente (nombre, apellidos);
CREATE INDEX idx_proveedor_razon_social  ON proveedor (razon_social);

CREATE INDEX idx_compra_proveedor        ON compra (proveedor_id);
CREATE INDEX idx_compra_almacen_fecha    ON compra (almacen_id, fecha);
CREATE INDEX idx_detalle_compra_compra   ON detalle_compra (compra_id);
CREATE INDEX idx_detalle_compra_producto ON detalle_compra (producto_id);

CREATE INDEX idx_venta_cliente           ON venta (cliente_id);
CREATE INDEX idx_venta_almacen_fecha     ON venta (almacen_id, fecha);
CREATE INDEX idx_detalle_venta_venta     ON detalle_venta (venta_id);
CREATE INDEX idx_detalle_venta_producto  ON detalle_venta (producto_id);

CREATE INDEX idx_traslado_origen         ON traslado_almacen (almacen_origen_id);
CREATE INDEX idx_traslado_destino        ON traslado_almacen (almacen_destino_id);
CREATE INDEX idx_detalle_traslado_trasl  ON detalle_traslado (traslado_id);

CREATE INDEX idx_kardex_producto_almacen ON kardex (producto_id, almacen_id, fecha);
CREATE INDEX idx_almacen_stock_producto  ON almacen_stock (producto_id);

CREATE INDEX idx_pago_compra             ON pago (compra_id);
CREATE INDEX idx_pago_venta              ON pago (venta_id);

CREATE INDEX idx_auditoria_usuario       ON auditoria_sesion (usuario_id, fecha_actividad);


-- =====================================================================
-- Notas de implementación
-- =====================================================================
-- * Los movimientos de stock (compra confirmada → entrada, venta confirmada
--   → salida, traslado completado → salida en origen + entrada en destino)
--   se calculan en la capa de servicio de Spring, no con triggers de base
--   de datos: mantiene la regla de negocio visible y testeable en Java, y
--   es donde vive también la validación de stock disponible.
-- * El saldo pendiente de una compra/venta a crédito se calcula como
--   total - SUM(pago.monto) en la capa de servicio o una vista; no se
--   almacena como columna para evitar que quede desincronizado.
-- * No se incluye ningún usuario semilla con contraseña: el primer
--   administrador se crea desde la aplicación (BCrypt en tiempo de
--   ejecución), nunca hardcodeado en un script SQL.
