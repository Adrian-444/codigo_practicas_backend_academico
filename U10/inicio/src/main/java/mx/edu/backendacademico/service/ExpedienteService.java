package mx.edu.backendacademico.service;

// Reto U10: consulta de expediente con regla de acceso por objeto.
//
// Regla: la identidad siempre sale del principal autenticado (Authentication.getName()),
// nunca de un parámetro que envíe el cliente. Un expediente pertenece al usuario cuyo
// nombre coincide con la matrícula del alumno. Se permite la consulta si:
//   1. el usuario autenticado es el propietario del expediente, o
//   2. el usuario tiene el rol ADMIN.
// Cualquier otro caso (otro alumno, un docente) se rechaza con 403 antes de consultar
// la base, para no revelar si la matrícula existe.

import mx.edu.backendacademico.domain.Alumno;
import mx.edu.backendacademico.exception.AlumnoNoEncontradoException;
import mx.edu.backendacademico.repository.AlumnoRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ExpedienteService {
    private final AlumnoRepository repository;

    public ExpedienteService(AlumnoRepository repository) {
        this.repository = repository;
    }

    public Alumno miExpediente(Authentication usuario) {
        // La matrícula se toma del principal; no existe un parámetro que pueda sustituirla.
        return buscar(usuario.getName());
    }

    public Alumno consultar(Authentication usuario, String matricula) {
        if (!esPropietario(usuario, matricula) && !esAdmin(usuario)) {
            throw new AccessDeniedException("El expediente pertenece a otro usuario");
        }
        return buscar(matricula);
    }

    private Alumno buscar(String matricula) {
        return repository.buscarPorMatricula(matricula).orElseThrow(() -> new AlumnoNoEncontradoException(matricula));
    }

    private static boolean esPropietario(Authentication usuario, String matricula) {
        return usuario.getName().equalsIgnoreCase(matricula.strip());
    }

    private static boolean esAdmin(Authentication usuario) {
        return usuario.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}
