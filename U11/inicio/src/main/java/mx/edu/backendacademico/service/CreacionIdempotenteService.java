package mx.edu.backendacademico.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import mx.edu.backendacademico.domain.Alumno;
import mx.edu.backendacademico.domain.EstatusAlumno;
import mx.edu.backendacademico.exception.ClaveIdempotenciaInvalidaException;
import mx.edu.backendacademico.exception.ClaveIdempotenciaReutilizadaException;
import mx.edu.backendacademico.repository.jpa.ClaveIdempotenciaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreacionIdempotenteService {
    static final String OPERACION = "CREAR_ALUMNO";
    private static final int LONGITUD_MAXIMA = 100;

    private final AlumnoService alumnos;
    private final ClaveIdempotenciaRepository claves;
    private final Clock reloj;

    public CreacionIdempotenteService(AlumnoService alumnos, ClaveIdempotenciaRepository claves, Clock reloj) {
        this.alumnos = alumnos;
        this.claves = claves;
        this.reloj = reloj;
    }

    @Transactional
    public Resultado crear(String usuario, String clave, String matricula, String nombre, String correo) {
        validar(clave);
        var datos = new Alumno(null, matricula, nombre, correo, EstatusAlumno.ACTIVO);
        String huella = huella(datos);

        if (claves.reservar(usuario, OPERACION, clave, huella, LocalDateTime.now(reloj))) {
            var creado = alumnos.crear(datos.matricula(), datos.nombre(), datos.correo());
            claves.asociarAlumno(usuario, OPERACION, clave, creado.id());
            return new Resultado(creado, false);
        }

        var registro = claves.buscar(usuario, OPERACION, clave).orElseThrow();
        if (!registro.huella().equals(huella)) {
            throw new ClaveIdempotenciaReutilizadaException(clave);
        }
        return new Resultado(alumnos.buscar(registro.alumnoId()), true);
    }

    private static void validar(String clave) {
        if (clave == null || clave.isBlank() || clave.length() > LONGITUD_MAXIMA
                || !clave.chars().allMatch(c -> c >= 0x21 && c <= 0x7E)) {
            throw new ClaveIdempotenciaInvalidaException();
        }
    }

    static String huella(Alumno datos) {
        try {
            String canonico = String.join("\u0000", datos.matricula(), datos.nombre(), datos.correo());
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonico.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    public record Resultado(Alumno alumno, boolean repetida) { }
}
