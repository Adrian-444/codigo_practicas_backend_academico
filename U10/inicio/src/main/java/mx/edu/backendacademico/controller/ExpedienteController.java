package mx.edu.backendacademico.controller;

// Reto U10: expone el expediente; Spring inyecta el principal autenticado.

import mx.edu.backendacademico.dto.AlumnoMapper;
import mx.edu.backendacademico.dto.AlumnoResponse;
import mx.edu.backendacademico.service.ExpedienteService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/expediente")
public class ExpedienteController {
    private final ExpedienteService service;

    public ExpedienteController(ExpedienteService service) {
        this.service = service;
    }

    // Mi expediente: no recibe usuario ni matrícula; la identidad viene de la autenticación.
    @GetMapping
    public AlumnoResponse miExpediente(Authentication usuario) {
        return AlumnoMapper.toResponse(service.miExpediente(usuario));
    }

    // La matrícula identifica el recurso, no a quien consulta; el servicio aplica la regla por objeto.
    @GetMapping("/{matricula}")
    public AlumnoResponse consultar(Authentication usuario, @PathVariable String matricula) {
        return AlumnoMapper.toResponse(service.consultar(usuario, matricula));
    }
}
