package mx.edu.backendacademico.service;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import mx.edu.backendacademico.domain.Alumno;
import mx.edu.backendacademico.domain.EstatusAlumno;
import mx.edu.backendacademico.repository.AlumnoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CacheYAccesoIntegrationTest {
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired AlumnoService service;
    @MockitoSpyBean AlumnoRepository repository;

    @Test void laCacheEvitaLecturasPeroNoSaltaLaReglaDePropietario() throws Exception {
        long id = service.crear("A001", "Ada Lovelace", "ada@u.mx").id();
        clearInvocations(repository);

        mvc.perform(get("/api/v1/alumnos/" + id).with(httpBasic("a001", "a001pass"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/alumnos/" + id).with(httpBasic("a001", "a001pass"))).andExpect(status().isOk());
        verify(repository, times(1)).buscarPorId(id);

        mvc.perform(get("/api/v1/alumnos/" + id).with(httpBasic("alumno", "alumno123")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
        verify(repository, times(1)).buscarPorId(anyLong());

        mvc.perform(put("/api/v1/alumnos/" + id).with(httpBasic("docente", "docente123")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ada Byron\",\"correo\":\"ada.byron@u.mx\"}"))
                .andExpect(status().isOk());
        clearInvocations(repository);

        mvc.perform(get("/api/v1/alumnos/" + id).with(httpBasic("a001", "a001pass")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Ada Byron"));
        verify(repository, times(1)).buscarPorId(id);
    }

    @Test void crearOtroAlumnoNoVaciaLaCacheDeLosDemas() throws Exception {
        var existente = service.crear("A002", "Emmy Noether", "emmy@u.mx");
        service.buscar(existente.id());
        clearInvocations(repository);

        service.crear("A003", "Grace Hopper", "grace@u.mx");
        Alumno otraVez = service.buscar(existente.id());

        verify(repository, never()).buscarPorId(existente.id());
        org.junit.jupiter.api.Assertions.assertEquals(EstatusAlumno.ACTIVO, otraVez.estatus());
    }
}
