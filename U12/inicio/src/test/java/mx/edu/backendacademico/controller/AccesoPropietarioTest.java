package mx.edu.backendacademico.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import mx.edu.backendacademico.config.SecurityConfig;
import mx.edu.backendacademico.domain.Alumno;
import mx.edu.backendacademico.domain.EstatusAlumno;
import mx.edu.backendacademico.exception.AlumnoNoEncontradoException;
import mx.edu.backendacademico.service.AccesoAlumnos;
import mx.edu.backendacademico.service.AlumnoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.CannotCreateTransactionException;

@WebMvcTest(AlumnoController.class)
@Import({SecurityConfig.class, AccesoAlumnos.class})
class AccesoPropietarioTest {
    @Autowired MockMvc mvc;
    @MockitoBean AlumnoService service;

    @BeforeEach void datos() {
        when(service.buscar(1L)).thenReturn(new Alumno(1L, "A001", "Ada Lovelace", "ada@u.mx", EstatusAlumno.ACTIVO));
        when(service.buscar(99L)).thenThrow(new AlumnoNoEncontradoException(99L));
    }

    @Test void propietarioConsultaSuRegistro() throws Exception {
        mvc.perform(get("/api/v1/alumnos/1").with(httpBasic("a001", "a001pass")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matricula").value("A001"));
    }

    @Test void alumnoAjenoRecibe403() throws Exception {
        mvc.perform(get("/api/v1/alumnos/1").with(httpBasic("alumno", "alumno123")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    @Test void alumnoNoDistingueUnIdInexistenteDeUnoAjeno() throws Exception {
        mvc.perform(get("/api/v1/alumnos/99").with(httpBasic("a001", "a001pass")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    @Test void docenteConsultaCualquierRegistroYRecibe404SiNoExiste() throws Exception {
        mvc.perform(get("/api/v1/alumnos/1").with(httpBasic("docente", "docente123")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/alumnos/99").with(httpBasic("docente", "docente123")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("ALUMNO_NO_ENCONTRADO"));
    }

    @Test void alumnoNoPuedeListarATodos() throws Exception {
        mvc.perform(get("/api/v1/alumnos").with(httpBasic("a001", "a001pass")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/alumnos").with(httpBasic("docente", "docente123")))
                .andExpect(status().isOk());
    }

    @Test void baseNoDisponibleRecibe503() throws Exception {
        when(service.buscar(2L)).thenThrow(new CannotCreateTransactionException("sin conexión"));
        mvc.perform(get("/api/v1/alumnos/2").with(httpBasic("docente", "docente123")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(jsonPath("$.codigo").value("BASE_NO_DISPONIBLE"));
    }
}
