CREATE TABLE claves_idempotencia (
                                     id BIGSERIAL PRIMARY KEY,
                                     usuario VARCHAR(100) NOT NULL,
                                     operacion VARCHAR(40) NOT NULL,
                                     clave VARCHAR(100) NOT NULL,
                                     huella CHAR(64) NOT NULL,
                                     alumno_id BIGINT,
                                     creada TIMESTAMP NOT NULL,
                                     CONSTRAINT uk_idempotencia UNIQUE (usuario, operacion, clave),
                                     CONSTRAINT ck_clave_no_vacia CHECK (LENGTH(TRIM(clave)) > 0)
);
