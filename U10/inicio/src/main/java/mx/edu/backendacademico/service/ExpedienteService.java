package mx.edu.backendacademico.service;

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
