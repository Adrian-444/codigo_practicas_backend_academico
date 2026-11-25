package mx.edu.backendacademico.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import mx.edu.backendacademico.exception.ClaveIdempotenciaReutilizadaException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CreacionIdempotenteTest {
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired CreacionIdempotenteService servicio;
    @Autowired JdbcClient jdbc;
    @Autowired MockMvc mvc;

    @Test void dosSolicitudesSimultaneasConLaMismaClaveCreanUnSoloAlumno() throws Exception {
        var barrera = new CyclicBarrier(2);
        try (var hilos = Executors.newFixedThreadPool(2)) {
            List<Future<CreacionIdempotenteService.Resultado>> futuros = List.of(
                    hilos.submit(() -> { barrera.await(); return servicio.crear("docente", "clave-simultanea", "I100", "Ada Lovelace", "ada@u.mx"); }),
                    hilos.submit(() -> { barrera.await(); return servicio.crear("docente", "clave-simultanea", "I100", "Ada Lovelace", "ada@u.mx"); }));
            var r1 = futuros.get(0).get(10, TimeUnit.SECONDS);
            var r2 = futuros.get(1).get(10, TimeUnit.SECONDS);

            assertEquals(r1.alumno().id(), r2.alumno().id(), "Ambas respuestas describen el mismo alumno");
            assertNotEquals(r1.repetida(), r2.repetida(), "Una crea y la otra es una repetición");
        }
        assertEquals(1, contarAlumnos("I100"));
        assertEquals(1, contarClaves("clave-simultanea"));
    }

    @Test void mismaClaveConDistintoContenidoSeRechazaSinCrearOtroAlumno() {
        var original = servicio.crear("docente", "clave-reusada", "I200", "Emmy Noether", "emmy@u.mx");
        assertFalse(original.repetida());

        var ex = assertThrows(ClaveIdempotenciaReutilizadaException.class, () ->
                servicio.crear("docente", "clave-reusada", "I201", "Otra Persona", "otra@u.mx"));
        assertTrue(ex.getMessage().contains("clave-reusada"));
        assertEquals(0, contarAlumnos("I201"), "El contenido distinto no se guarda");
        assertEquals(1, contarClaves("clave-reusada"));
    }

    @Test void http_repeticionDevuelveLaMismaRespuestaYContenidoDistintoRecibe422() throws Exception {
        String cuerpo = """
            {"matricula":"I300","nombre":"Grace Hopper","correo":"grace@u.mx"}""";
        var primera = mvc.perform(post("/api/v1/alumnos").with(httpBasic("docente", "docente123")).with(csrf())
                        .header("Idempotency-Key", "clave-http").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replayed"))
                .andReturn().getResponse();

        mvc.perform(post("/api/v1/alumnos").with(httpBasic("docente", "docente123")).with(csrf())
                        .header("Idempotency-Key", "clave-http").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", primera.getHeader("Location")))
                .andExpect(header().string("Idempotent-Replayed", "true"));

        mvc.perform(post("/api/v1/alumnos").with(httpBasic("docente", "docente123")).with(csrf())
                        .header("Idempotency-Key", "clave-http").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"matricula":"I301","nombre":"Grace Hopper","correo":"grace@u.mx"}"""))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("CLAVE_IDEMPOTENCIA_REUTILIZADA"));

        assertEquals(1, contarAlumnos("I300"));
        assertEquals(0, contarAlumnos("I301"));
    }

    @Test void http_claveInvalidaRecibe400() throws Exception {
        mvc.perform(post("/api/v1/alumnos").with(httpBasic("docente", "docente123")).with(csrf())
                        .header("Idempotency-Key", "x".repeat(101)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"matricula":"I400","nombre":"Alan Turing","correo":"alan@u.mx"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CLAVE_IDEMPOTENCIA_INVALIDA"));
    }

    private long contarAlumnos(String matricula) {
        return jdbc.sql("SELECT COUNT(*) FROM alumnos WHERE matricula = ?").param(matricula).query(Long.class).single();
    }

    private long contarClaves(String clave) {
        return jdbc.sql("SELECT COUNT(*) FROM claves_idempotencia WHERE clave = ?").param(clave).query(Long.class).single();
    }
}
