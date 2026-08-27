-- =====================================================================
-- JVentas — Datos semilla (V2)
-- =====================================================================
-- Solo catálogo base necesario para operar. Sin usuarios: el primer
-- administrador se crea desde la aplicación (BCrypt en tiempo de
-- ejecución), nunca hardcodeado aquí.
-- =====================================================================

INSERT INTO moneda (nombre, simbolo, codigo_iso, es_predeterminada) VALUES
    ('Sol',   'S/', 'PEN', TRUE),
    ('Dólar', '$',  'USD', FALSE);

INSERT INTO impuesto (nombre, tasa, es_default) VALUES
    ('IGV',        18.00, TRUE),
    ('Exonerado',   0.00, FALSE);

INSERT INTO unidad_medida (nombre, abreviatura) VALUES
    ('Unidad',   'UND'),
    ('Caja',     'CAJ'),
    ('Millar',   'MLL'),
    ('Cartucho', 'CRT'),
    ('Paquete',  'PQT');

INSERT INTO lista_precio (nombre) VALUES
    ('Mayorista'),
    ('Minorista');

INSERT INTO cargo (nombre) VALUES
    ('Caja'),
    ('Compras'),
    ('Ventas'),
    ('Servicio Técnico'),
    ('Almacén'),
    ('Múltiple');

INSERT INTO metodo_pago (nombre) VALUES
    ('Efectivo'),
    ('Tarjeta de débito'),
    ('Tarjeta de crédito'),
    ('Transferencia'),
    ('Crédito');

INSERT INTO tipo_documento (nombre, aplica_compra, aplica_venta) VALUES
    ('FACTURA',         TRUE,  TRUE),
    ('BOLETA',          TRUE,  TRUE),
    ('GUIA',            TRUE,  TRUE),
    ('NOTA_DE_PEDIDO',  TRUE,  TRUE),
    ('DUA',             TRUE,  FALSE);   -- Documento Único Aduanero: solo importación (compra)

INSERT INTO rol (nombre, descripcion) VALUES
    ('ADMINISTRADOR',   'Acceso total al sistema'),
    ('VENTAS',          'Registrar y consultar ventas y clientes'),
    ('COMPRAS',         'Registrar y consultar compras y proveedores'),
    ('ALMACEN',         'Gestionar inventario, kardex, traslados y aperturas'),
    ('CAJA',            'Registrar cobros y ver el estado de ventas');

INSERT INTO permiso (codigo, descripcion) VALUES
    ('producto:ver',        'Consultar catálogo de productos'),
    ('producto:crear',      'Registrar productos'),
    ('producto:editar',     'Editar productos existentes'),
    ('producto:eliminar',   'Dar de baja productos'),
    ('cliente:ver',         'Consultar clientes'),
    ('cliente:crear',       'Registrar clientes'),
    ('cliente:editar',      'Editar clientes'),
    ('proveedor:ver',       'Consultar proveedores'),
    ('proveedor:crear',     'Registrar proveedores'),
    ('proveedor:editar',    'Editar proveedores'),
    ('compra:ver',          'Consultar compras'),
    ('compra:crear',        'Registrar compras'),
    ('compra:anular',       'Anular compras registradas'),
    ('venta:ver',           'Consultar ventas'),
    ('venta:crear',         'Registrar ventas'),
    ('venta:anular',        'Anular ventas registradas'),
    ('kardex:ver',          'Consultar movimientos de inventario'),
    ('stock:ver',           'Consultar el stock vigente por almacén y producto'),
    ('almacen:ver',         'Consultar almacenes'),
    ('almacen:crear',       'Registrar almacenes'),
    ('almacen:editar',      'Editar almacenes'),
    ('almacen:apertura',    'Registrar apertura de inventario'),
    ('traslado:ver',        'Consultar traslados entre almacenes'),
    ('traslado:crear',      'Registrar traslados entre almacenes'),
    ('traslado:completar',  'Confirmar la llegada de un traslado'),
    ('traslado:anular',     'Anular un traslado pendiente'),
    ('pago:ver',            'Consultar pagos y cobros registrados'),
    ('pago:registrar',      'Registrar pagos y cobros'),
    ('usuario:administrar', 'Crear y administrar usuarios y roles'),
    ('catalogo:administrar','Administrar catálogos: unidad de medida, tipo de documento, impuestos, listas de precio');

-- ADMINISTRADOR: todos los permisos
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT (SELECT id FROM rol WHERE nombre = 'ADMINISTRADOR'), id FROM permiso;

-- VENTAS
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT (SELECT id FROM rol WHERE nombre = 'VENTAS'), id FROM permiso
WHERE codigo IN ('producto:ver','cliente:ver','cliente:crear','cliente:editar','almacen:ver','stock:ver',
                  'venta:ver','venta:crear','venta:anular','kardex:ver','pago:ver','pago:registrar');

-- COMPRAS
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT (SELECT id FROM rol WHERE nombre = 'COMPRAS'), id FROM permiso
WHERE codigo IN ('producto:ver','proveedor:ver','proveedor:crear','proveedor:editar','almacen:ver','stock:ver',
                  'compra:ver','compra:crear','compra:anular','kardex:ver','pago:ver','pago:registrar');

-- ALMACEN
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT (SELECT id FROM rol WHERE nombre = 'ALMACEN'), id FROM permiso
WHERE codigo IN ('producto:ver','producto:crear','producto:editar',
                  'almacen:ver','almacen:crear','almacen:editar','stock:ver',
                  'kardex:ver','almacen:apertura',
                  'traslado:ver','traslado:crear','traslado:completar','traslado:anular');

-- CAJA
INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT (SELECT id FROM rol WHERE nombre = 'CAJA'), id FROM permiso
WHERE codigo IN ('venta:ver','cliente:ver','pago:ver','pago:registrar');
