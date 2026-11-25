package mx.edu.backendacademico.repository.jpa;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ClaveIdempotenciaRepository {
    private final JdbcClient jdbc;

    public ClaveIdempotenciaRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public boolean reservar(String usuario, String operacion, String clave, String huella, LocalDateTime ahora) {
        int filas = jdbc.sql("""
                INSERT INTO claves_idempotencia(usuario, operacion, clave, huella, creada)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT ON CONSTRAINT uk_idempotencia DO NOTHING""")
                .params(usuario, operacion, clave, huella, ahora)
                .update();
        return filas == 1;
    }

    public void asociarAlumno(String usuario, String operacion, String clave, long alumnoId) {
        jdbc.sql("UPDATE claves_idempotencia SET alumno_id = ? WHERE usuario = ? AND operacion = ? AND clave = ?")
                .params(alumnoId, usuario, operacion, clave)
                .update();
    }

    public Optional<Registro> buscar(String usuario, String operacion, String clave) {
        return jdbc.sql("SELECT huella, alumno_id FROM claves_idempotencia WHERE usuario = ? AND operacion = ? AND clave = ?")
                .params(usuario, operacion, clave)
                .query((rs, i) -> new Registro(rs.getString("huella"), rs.getLong("alumno_id")))
                .optional();
    }

    public record Registro(String huella, long alumnoId) { }
}
