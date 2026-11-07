-- Agrega la regla sobre la tabla existente; V1 ya aplicada no se modifica.
ALTER TABLE alumnos
    ADD CONSTRAINT ck_nombre_no_vacio CHECK (LENGTH(TRIM(nombre)) > 0);
