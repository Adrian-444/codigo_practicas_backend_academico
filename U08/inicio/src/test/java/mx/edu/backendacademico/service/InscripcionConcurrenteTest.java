package mx.edu.backendacademico.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import mx.edu.backendacademico.domain.Alumno;
import mx.edu.backendacademico.domain.EstatusAlumno;
import mx.edu.backendacademico.exception.OperacionAcademicaException;
import mx.edu.backendacademico.repository.jpa.AlumnoEntity;
import mx.edu.backendacademico.repository.jpa.AlumnoSpringDataRepository;
import mx.edu.backendacademico.repository.jpa.InscripcionRepository;
import mx.edu.backendacademico.repository.jpa.MateriaEntity;
import mx.edu.backendacademico.repository.jpa.MateriaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class InscripcionConcurrenteTest {

    @Autowired InscripcionService service;
    @Autowired MateriaRepository materias;
    @Autowired AlumnoSpringDataRepository alumnos;
    @Autowired InscripcionRepository inscripciones;

    @Test
    void soloUnAlumnoObtieneElUnicoLugar() throws Exception {
        // 1. Materia con un solo lugar y dos alumnos distintos.
        long n = System.currentTimeMillis();
        Long materiaId = materias.save(new MateriaEntity("RETO" + n, "Reto", 4, 1)).getId();
        Long alumno1 = alumnos.save(new AlumnoEntity(
                new Alumno(null, "A" + n, "Ana", "ana" + n + "@mail.com", EstatusAlumno.ACTIVO))).getId();
        Long alumno2 = alumnos.save(new AlumnoEntity(
                new Alumno(null, "B" + n, "Beto", "beto" + n + "@mail.com", EstatusAlumno.ACTIVO))).getId();

        // 2. Dos tareas que esperan la misma señal para arrancar al mismo tiempo.
        CountDownLatch inicio = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        Future<String> tarea1 = hilos.submit(() -> inscribir(inicio, alumno1, materiaId));
        Future<String> tarea2 = hilos.submit(() -> inscribir(inicio, alumno2, materiaId));
        inicio.countDown();

        String r1 = tarea1.get();
        String r2 = tarea2.get();
        hilos.shutdown();
        System.out.println("Resultados: " + r1 + " y " + r2);

        // 3. Una inscripción persistida y la otra rechazada por cupo.
        assertEquals(1, inscripciones.countByMateriaId(materiaId));
        assertEquals(1, (r1.equals("OK") ? 1 : 0) + (r2.equals("OK") ? 1 : 0));
        assertEquals(1, (r1.equals("SIN_CUPO") ? 1 : 0) + (r2.equals("SIN_CUPO") ? 1 : 0));
    }

    private String inscribir(CountDownLatch inicio, Long alumnoId, Long materiaId) throws Exception {
        inicio.await();
        try {
            service.inscribir(alumnoId, materiaId);
            return "OK";
        } catch (OperacionAcademicaException e) {
            return e.getCodigo();
        }
    }
}
