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
                // 1. Ambas tareas esperan la misma señal para intentar guardar a la vez.
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
        // 2. join espera a que cada hilo termine, sin pausas arbitrarias.
        hilo1.join();
        hilo2.join();

        assertEquals(1, repository.buscarTodos().size());
        assertEquals(1, conflictos.get());
    }

    /*
     * Un AtomicInteger aislado sólo vuelve indivisible un incremento. La unicidad
     * requiere comprobar y escribir como una sola operación; eso lo garantiza
     * guardar() al ser synchronized, no un contador.
     *
     * Esta prueba muestra un entrelazado con dos hilos; no demuestra que todos
     * los entrelazados posibles respeten la regla.
     */
}
