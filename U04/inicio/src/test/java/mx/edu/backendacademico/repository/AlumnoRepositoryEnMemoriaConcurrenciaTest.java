package mx.edu.backendacademico.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import mx.edu.backendacademico.domain.Alumno;
import mx.edu.backendacademico.domain.EstatusAlumno;
import mx.edu.backendacademico.exception.MatriculaDuplicadaException;
import org.junit.jupiter.api.Test;

class AlumnoRepositoryEnMemoriaConcurrenciaTest {
    @Test void dosGuardadosSimultaneosDejanUnRegistroYUnConflicto() throws InterruptedException {
        var repository = new AlumnoRepositoryEnMemoria();
        var salida = new CountDownLatch(1);
        var conflictos = new AtomicInteger();

        Runnable tarea = () -> {
            try {
                // ambas tareas esperan la misma señal para intentar guardar a la vez
                salida.await();
                repository.guardar(new Alumno(null, "A001", "Ada", "ada@u.mx", EstatusAlumno.ACTIVO));
            } catch (MatriculaDuplicadaException e) {
                conflictos.incrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        var hilo1 = new Thread(tarea);
        var hilo2 = new Thread(tarea);
        hilo1.start();
        hilo2.start();
        salida.countDown();
        // join espera a que cada hilo termine
        hilo1.join();
        hilo2.join();

        assertEquals(1, repository.buscarTodos().size());
        assertEquals(1, conflictos.get());
    }
}
