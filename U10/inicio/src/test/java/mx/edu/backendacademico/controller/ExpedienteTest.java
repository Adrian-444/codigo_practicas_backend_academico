package mx.edu.backendacademico.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.Optional;
import mx.edu.backendacademico.config.SecurityConfig;
import mx.edu.backendacademico.domain.*;
import mx.edu.backendacademico.repository.AlumnoRepository;
import mx.edu.backendacademico.service.ExpedienteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// Se usa el ExpedienteService real para probar la regla por objeto
@WebMvcTest(ExpedienteController.class)
@Import({SecurityConfig.class, ExpedienteService.class})
class ExpedienteTest {
    @Autowired MockMvc mvc;
    @MockitoBean AlumnoRepository repository;

    @BeforeEach void datos() {
        when(repository.buscarPorMatricula("A100")).thenReturn(Optional.of(
                new Alumno(1L, "A100", "Ada", "ada@u.mx", EstatusAlumno.ACTIVO)));
        when(repository.buscarPorMatricula("A200")).thenReturn(Optional.of(
                new Alumno(2L, "A200", "Emmy", "emmy@u.mx", EstatusAlumno.ACTIVO)));
    }

    @Test void sinCredencialesRecibe401() throws Exception {
        mvc.perform(get("/api/v1/expediente")).andExpect(status().isUnauthorized());
    }

    @Test void propietarioConsultaMiExpediente() throws Exception {
        mvc.perform(get("/api/v1/expediente").with(httpBasic("A100", "a100pass")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matricula").value("A100"));
    }

    @Test void miExpedienteIgnoraElUsuarioEnviadoPorElCliente() throws Exception {
        // Un parámetro inventado no cambia la identidad: sigue devolviendo el expediente del principal.
        mvc.perform(get("/api/v1/expediente").param("usuario", "A200").with(httpBasic("A100", "a100pass")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matricula").value("A100"));
        verify(repository, never()).buscarPorMatricula("A200");
    }

    @Test void propietarioConsultaSuExpedientePorMatricula() throws Exception {
        mvc.perform(get("/api/v1/expediente/A100").with(httpBasic("A100", "a100pass")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matricula").value("A100"));
    }

    @Test void usuarioAjenoRecibe403SinConsultarLaBase() throws Exception {
        mvc.perform(get("/api/v1/expediente/A100").with(httpBasic("A200", "a200pass")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
        // Se niega antes de buscar: el ajeno no puede averiguar si la matrícula existe.
        verify(repository, never()).buscarPorMatricula(anyString());
    }

    @Test void adminConsultaCualquierExpediente() throws Exception {
        mvc.perform(get("/api/v1/expediente/A100").with(httpBasic("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matricula").value("A100"));
    }

    @Test void adminSinExpedientePropioRecibe404() throws Exception {
        mvc.perform(get("/api/v1/expediente").with(httpBasic("admin", "admin123")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("ALUMNO_NO_ENCONTRADO"));
    }
}
