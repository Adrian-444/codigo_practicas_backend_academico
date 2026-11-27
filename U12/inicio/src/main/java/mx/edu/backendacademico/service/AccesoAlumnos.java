package mx.edu.backendacademico.service;

import java.util.Optional;
import java.util.Set;
import mx.edu.backendacademico.domain.Alumno;
import mx.edu.backendacademico.exception.AlumnoNoEncontradoException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

@Component
public class AccesoAlumnos {
    private static final Set<String> PERSONAL = Set.of("ROLE_DOCENTE", "ROLE_ADMIN");

    private final AlumnoService service;

    public AccesoAlumnos(AlumnoService service) {
        this.service = service;
    }

    public Alumno consultar(Long id, Authentication autenticacion) {
        if (esPersonal(autenticacion)) {
            return service.buscar(id);
        }
        return buscarSiExiste(id)
                .filter(alumno -> alumno.matricula().equalsIgnoreCase(autenticacion.getName()))
                .orElseThrow(() -> new AccessDeniedException("Solo puedes consultar tu propio registro"));
    }

    private boolean esPersonal(Authentication autenticacion) {
        return autenticacion.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(PERSONAL::contains);
    }

    private Optional<Alumno> buscarSiExiste(Long id) {
        try {
            return Optional.of(service.buscar(id));
        } catch (AlumnoNoEncontradoException ex) {
            return Optional.empty();
        }
    }
}
